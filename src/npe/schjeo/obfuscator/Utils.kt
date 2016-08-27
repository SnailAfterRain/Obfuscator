package npe.schjeo.obfuscator

import java.util.*


var verbose = false
fun verbose(str: String) {
    if(verbose)
        println("# $str")
}

fun randomstr(vocab: String, size: Int): String {
    val sb = StringBuilder()
    val r = Random()
    (1..size).forEach {
        sb.append(vocab[r.nextInt(vocab.length)])
    }
    return sb.toString()
}