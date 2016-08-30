package npe.schjeo.obfuscator.utils

import npe.schjeo.obfuscator.randomstr
import java.util.regex.Pattern

class RandomGenerator(val pattern: String) {

    val letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val digits = "0123456789"
    private val expr: Pattern

    init {
        expr = Pattern.compile("""(\\w|\\d|\[[a-zA-Z0-9_$/@.\-\s]+\]|[a-zA-Z0-9_$/@.\-\s]+)(\{(\d+)\})?""")
    }

    fun next(): String {
        return buildString {
            val m = expr.matcher(pattern)
            while(m.find()) {
                val len = if(m.group(3) != null) m.group(3).toInt() else 1
                val str = m.group(1)
                if(str == "\\w")
                    append(randomstr(letters, len))
                else if(str == "\\d")
                    append(randomstr(digits, len))
                else if(str.startsWith('[') && str.endsWith(']')) {
                    val dic = str.substring(1).dropLast(1)
                    append(randomstr(dic, len))
                }
                else {
                    append(str)
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    val rg = RandomGenerator("""Hi /D  \d \d \d \d \d \d \d \d""")
    println(rg.next())
}

