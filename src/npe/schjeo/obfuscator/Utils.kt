package npe.schjeo.obfuscator

import java.util.*


var verbose = false
fun verbose(str: String) {
    if(verbose)
        println("# $str")
}

fun randomstr(dic: String, size: Int): String {
    val sb = StringBuilder()
    val r = Random()
    (1..size).forEach {
        sb.append(dic[r.nextInt(dic.length)])
    }
    return sb.toString()
}