package npe.schjeo.obfuscator

import org.objectweb.asm.ClassWriter

class ObfClassWriter(flags: Int) : ClassWriter(flags) {

    /**
     * Default implementation tries to load given classes but we can't do that
     */
    override fun getCommonSuperClass(type1: String?, type2: String?): String {
        return "java/lang/Object"
    }
}