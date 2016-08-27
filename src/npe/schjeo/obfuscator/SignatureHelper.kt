package npe.schjeo.obfuscator

import java.util.*
import java.util.regex.Pattern


class SignatureHelper {

    companion object {
        val pattern: Pattern = Pattern.compile("""L([\w\d\s/_\$]+)[<;]""")

        fun parseTypes(sig: String): ArrayList<String> {
            val list = ArrayList<String>()
            val m = pattern.matcher(sig)
            while (m.find()) {
                val s = m.group()
                list.add(s.substring(1, s.length-1))
            }
            return list
        }
    }
}