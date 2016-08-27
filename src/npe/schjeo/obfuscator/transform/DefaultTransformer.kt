package npe.schjeo.obfuscator.transform

import npe.schjeo.obfuscator.Obfuscator
import npe.schjeo.obfuscator.randomstr
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.util.*


class DefaultTransformer(val obf: Obfuscator, val dict: String, val nameLength: Int) : ClassTransformer {

    var classCount = 0
    var fieldCount = 0

    override fun transformClassName(old: String): String {
        var name: String
        if(obf.useDictionary) {
            do {
                name = randomstr(dict, nameLength)
            } while(obf.mapTypes.containsValue(name))
        }
        else {
            name = Integer.toString(classCount, 36)
            classCount++
        }
        return name
    }

    override fun transformFieldName(fn: FieldNode, owner: String): String {
        var name: String
        if(obf.useDictionary) {
            do {
                name = randomstr(dict, nameLength)
            } while(obf.mapFields.containsValue(name))
        }
        else {
            name = Integer.toString(fieldCount, 36)
            fieldCount++
        }
        return name
    }


    override fun transformMethod(md: MethodNode) {

        /*val l_deads = ArrayList<LabelNode>()
        val r = Random()
        md.instructions.iterator().forEach { instr ->
            if(instr is InsnNode) {
                val il = InsnList()
                val l_dead = LabelNode(Label())
                l_deads.add(l_dead)
                val l_enter = LabelNode(Label())
                val l_exit = LabelNode(Label())
                il.add(JumpInsnNode(Opcodes.GOTO, l_enter))
                il.add(l_dead)
                il.add(l_enter)
                val i1 = r.nextFloat()*r.nextInt().toFloat()
                var i2 = r.nextFloat()*r.nextInt().toFloat()
                while(i1 > i2)
                    i2 = r.nextFloat()*r.nextInt().toFloat()
                il.add(LdcInsnNode(i1))
                il.add(LdcInsnNode(i2))
                il.add(InsnNode(Opcodes.FCMPG))
                il.add(JumpInsnNode(Opcodes.IFEQ, l_dead))
                il.add(l_exit)
                //md.maxLocals+=3
                //md.maxStack+=3
                md.instructions.insertBefore(instr, il)
            }
        }

        if(l_deads.size > 0) {
            l_deads.forEach {
                val il = InsnList()
                il.add(LdcInsnNode(r.nextFloat() * r.nextInt().toFloat()))
                il.add(InsnNode(Opcodes.POP))
                //il.add(InsnNode(Opcodes.ICONST_0))
                //il.add(JumpInsnNode(Opcodes.IFEQ, l_deads[r.nextInt(l_deads.size)]))
                md.instructions.insert(it, il)
                //md.maxLocals+=3
                //md.maxStack+=3
            }
        }*/

        if(md.localVariables != null) {
            val idxs = ArrayList<Int>()
            val prims = arrayOf("Z", "B", "S", "C", "I", "F", "D", "J")
            md.localVariables.forEach { idxs.add(it.index) }
            md.localVariables.forEachIndexed { idx, it ->
                it.desc = "${buildString { (0..Random().nextInt(5)).forEach { append("[") } }}${prims[Random().nextInt(prims.size)]}"
                it.name = ""
                val i1 = idxs[Random().nextInt(idxs.size)]
                idxs.remove(i1)
                it.index = it.index xor 2//i1

            }
        }

    }
}