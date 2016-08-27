package npe.schjeo.obfuscator.transform

import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode


interface ClassTransformer {

    fun transformClassName(old: String): String
    fun transformFieldName(fn: FieldNode, owner: String): String
    fun transformMethod(md: MethodNode)
}
