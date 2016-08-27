package npe.schjeo.obfuscator.zip

import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

fun unzip(filename: String): ZipContainer {
    val container = ZipContainer()
    val zipFile = ZipFile(filename)
    val entries = zipFile.entries()
    while(entries.hasMoreElements()) {
        val entry = entries.nextElement()
        val centry = ZipCEntry(entry.name, zipFile.getInputStream(entry).readBytes())
        container.entries.add(centry)
    }
    zipFile.close()
    return container
}

fun zip(fn: ZipContainer.()->Unit): ZipContainer {
    return ZipContainer().enter(fn)
}

class ZipContext(val zos: ZipOutputStream) {

    infix fun String.pack(data: ByteArray) {
        val fin = ByteArrayInputStream(data)
        val buffer = ByteArray(1024)
        val ze = ZipEntry(this)
        zos.putNextEntry(ze)

        var len: Int = fin.read(buffer)
        while (len > 0) {
            zos.write(buffer, 0, len)
            len = fin.read(buffer)
        }
        fin.close()
        zos.closeEntry()
    }
}

class ZipContainer {
    val entries = ArrayList<ZipCEntry>()

    fun enter(fn: ZipContainer.()->Unit): ZipContainer {
        this.fn()
        return this
    }
    operator fun String.plusAssign(data: ByteArray) {
        val old = entries.find { it.name == this }
        if(old != null) {
            old.data = data
        }
        else {
            entries.add(ZipCEntry(this, data))
        }
    }
    operator fun String.unaryMinus() {
        entries.removeIf { it.name == this }
    }
    fun saveTo(filename: String) {
        val fos = FileOutputStream(filename)
        val zos = ZipOutputStream(fos)
        entries.forEach { e ->
            val fin = ByteArrayInputStream(e.data)
            val buffer = ByteArray(1024)
            val ze = ZipEntry(e.name)
            zos.putNextEntry(ze)

            var len: Int = fin.read(buffer)
            while (len > 0) {
                zos.write(buffer, 0, len)
                len = fin.read(buffer)
            }
            fin.close()
            zos.closeEntry()
        }
        zos.close()
        fos.close()
    }
}

class ZipCEntry(var name: String, var data: ByteArray)

