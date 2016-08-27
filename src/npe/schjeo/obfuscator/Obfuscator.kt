package npe.schjeo.obfuscator

import npe.schjeo.obfuscator.impl.DefaultTransformer
import npe.schjeo.obfuscator.impl.ClassTransformer
import npe.schjeo.obfuscator.zip.ZipContainer
import npe.schjeo.obfuscator.zip.unzip
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.io.File
import java.util.*
import java.util.regex.Pattern


fun main(args: Array<String>) {

    val args1 = args//"-v --keepnames com/fasterxml --dictionary ABCDEF --name-length 10 -i C:/Users/I/Desktop/obftest/ccemuredux-launcher.jar -o C:/Users/I/Desktop/obftest/bsh1.jar".split(' ').toTypedArray()

    val options = Options()
    options.addOption("h", "help", false, "Display this help")
    options.addOption("v", "verbose", false, "Outputs additional info")
    options.addOption("hh", "hide", false, "Hides classes, methods and fields from some decompilers by adding to them SYNTHETIC and TRANSIENT access modifiers")
    options.addOption("km", "keepmain", false, "Keep main class from renaming")
    options.addOption("rdi", "remove-debug-info", false, "Remove source file name from classes and line numbers from methods")

    options.addOption(Option.builder("i").longOpt("in").hasArg().argName("path-to-jar").desc("Input jar").build())
    options.addOption(Option.builder("o").longOpt("out").hasArg().argName("path-to-jar").desc("Output jar").build())
    options.addOption(Option.builder("kn").longOpt("keepnames").hasArgs()
                              .desc("Keep class name if it matches this")
                              .argName("regexp...").build())
    options.addOption(Option.builder("dic").longOpt("dictionary").hasArg().argName("string")
                              .desc("Array of characters to make a new name from").build())
    options.addOption(Option.builder("nlen").longOpt("name-length").hasArg().argName("num")
                              .desc("Length of a new name").build())



    val parser = DefaultParser()
    val cmd = parser.parse(options, args1)
    if(cmd.hasOption("help")) {
        val formatter = HelpFormatter()
        formatter.printHelp("obf [args]", options)
        return
    }
    if(cmd.hasOption("verbose"))
        verbose = true
    if(cmd.hasOption("in")) {
        val inFile = File(cmd.getOptionValue("in"))
        if(!inFile.exists()) {
            println("File '$inFile' does not exist")
            return
        }
    }
    else {
        println("Missing input argument")
        return
    }
    if(cmd.hasOption("out")) {
        val outFile = File(cmd.getOptionValue("out"))
        if(outFile.parentFile != null && !outFile.parentFile.exists())
            outFile.parentFile.mkdirs()
    }
    else {
        println("Missing output argument")
        return
    }

    val obf = Obfuscator(cmd.getOptionValue("in"), cmd.getOptionValue("out"))

    if(cmd.hasOption("keepnames")) {
        cmd.getOptionValues("keepnames").forEach {
            obf.addKeepRule(it)
            verbose("Adding keep rule '$it'")
        }
    }
    obf.keepMain = cmd.hasOption("keepmain")
    obf.hideAll = cmd.hasOption("hide")
    obf.removeDebug = cmd.hasOption("rdi")
    obf.useDictionary = cmd.hasOption("dic")


    var dictionary = "0123456789abcdef"
    var nameLength = 16
    if(cmd.hasOption("dic"))
        dictionary = cmd.getOptionValue("dic")
    if(cmd.hasOption("name-length")) {
        try {
            nameLength = cmd.getOptionValue("name-length").toInt()
        }
        catch (e: Exception) {
            println("Length '${cmd.getOptionValue("name-length")}' is invalid, it has to be integer")
            return
        }
    }
    verbose("Using dictionary '$dictionary' and length $nameLength")
    obf.transformer = DefaultTransformer(obf, dictionary, nameLength)
    obf.start()

}

class Obfuscator(val inFile: String, val outFile: String) {

    val classes = ArrayList<ClassNode>()
    lateinit var transformer: ClassTransformer
    lateinit var zipContainer: ZipContainer
    val keepNames = ArrayList<Pattern>()
    var useDictionary = false
    var keepMain = false
    var hideAll = false
    var removeDebug = false
    var mainClass: String? = null

    val mapTypes = HashMap<String, String>()
    val mapFields = HashMap<OField, String>()

    init {

    }

    fun start() {
        loadAll()
        transform()
        apply()
        saveAll()
    }

    private fun loadAll() {
        println("Unzipping $inFile")
        try {
            zipContainer = unzip(inFile)
        }
        catch (e: Exception) {
            println("Failed to unzip $inFile")
            throw e
        }

        val manifest = zipContainer.entries.find { it.name == "META-INF/MANIFEST.MF" }
        if(manifest != null) {
            verbose("Found manifest file")
            val content = String(manifest.data)
            val lines = content.split("\n").toTypedArray().toMutableList()
            lines.forEachIndexed { idx, line ->
                if(line.startsWith("Main-Class: ")){
                    mainClass = line.split(":")[1].trim().replace('.', '/')
                    verbose("Found main class '$mainClass' in manifest")
                }
            }

        }
        println("Loading classes")
        zipContainer.entries.forEach { entry ->
            if(entry.name.endsWith(".class")) {
                verbose("Found class ${entry.name}")
                try {
                    val classEntry = readClass(entry.data)
                    classes.add(classEntry)
                }
                catch (e: Exception) {
                    println("Failed to load class ${entry.name}")
                    throw e
                }
            }
        }
    }

    fun shouldBeTransformed(className: String) = shouldBeTransformed(classes.find { it.name == className }!!)

    fun shouldBeTransformed(cn: ClassNode): Boolean {
        var canTransform = true
        if(keepMain && cn.name == mainClass)
            canTransform = false

        keepNames.forEach { rule ->
            if(rule.matcher(cn.name).matches()) {
                canTransform = false
            }
        }

        cn.methods.forEach { m ->
            if(m.access and Opcodes.ACC_NATIVE != 0)
                canTransform = false
        }
        return canTransform
    }


    private fun transform() {
        println("Transforming")
        classes.forEach { cn ->
            mapTypes[cn.name] = cn.name
            cn.fields.forEach { fn ->
                mapFields[OField(cn.name, fn.name, fn.desc, fn.signature)] = fn.name
            }
        }

        classes.forEach { cn ->
            cn.fields.forEach { fn ->
                if(fn.name != "<init>" && fn.name != "<clinit>") {
                    //mapFields[fn] = transformer.transformFieldName(this, fn)
                    val k = mapFields.keys.findLast {
                        it.name == fn.name &&
                        it.desc == fn.desc
                    }
                    if(k != null)
                        mapFields[k] = transformer.transformFieldName(fn, cn.name)
                }

            }
            if(shouldBeTransformed(cn)) {
                verbose("Renamed ${cn.name} --> ${transformer.transformClassName(cn.name)}")
                if(!cn.name.contains('$')) {
                    if(mapTypes[cn.name] == cn.name)
                        mapTypes[cn.name] = transformer.transformClassName(cn.name)
                }
            }
            else {
                verbose("Keep ${cn.name}")
            }
        }
        classes.forEach { cn ->
            if(shouldBeTransformed(cn)) {
                if(cn.name.contains('$')) {
                    val parts = cn.name.split('$')
                    val oname = parts[0]
                    if(oname == mapTypes[oname])
                        if(shouldBeTransformed(oname))
                            mapTypes[oname] = transformer.transformClassName(oname)
                    mapTypes[cn.name] = mapTypes[oname] + buildString { (1..parts.size-1).forEach { append("$${parts[it]}") } }
                }
            }
            else {
                verbose("Keep ${cn.name}")
            }
        }
    }

    private fun apply() {
        println("Applying")
        classes.forEach { cn ->
            if(hideAll)
                cn.access = cn.access or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_TRANSIENT
            cn.name = mapTypes[cn.name]
            if(removeDebug) {
                cn.sourceFile = null
                cn.sourceDebug = null
            }
            if(cn.signature != null)
                cn.signature = handleSignature(cn.signature)

            if(cn.superName in mapTypes)
                cn.superName = mapTypes[cn.superName]
            cn.interfaces.forEachIndexed { i, interf ->
                if(interf in mapTypes)
                    cn.interfaces[i] = mapTypes[interf]
            }



            val annHandle: (AnnotationNode)->Unit = { ann ->
                ann.desc = handleSignature(ann.desc)
                if(ann.values != null) {
                    var idx = 0
                    while(idx < ann.values.size) {
                        val aenum = ann.values[idx+1]
                        if(aenum is Array<*>) {
                            ann.values[idx+1] = arrayOf(handleSignature(aenum[0] as String), aenum[1] as String)
                        }
                        idx+=2
                    }
                }
            }
            cn.invisibleAnnotations?.forEach(annHandle)
            cn.invisibleTypeAnnotations?.forEach(annHandle)
            cn.visibleAnnotations?.forEach(annHandle)
            cn.visibleTypeAnnotations?.forEach(annHandle)

            cn.fields.forEach { fn ->
                if(hideAll)
                    fn.access = fn.access or Opcodes.ACC_SYNTHETIC
                fn.desc = handleSignature(fn.desc)
                if(fn.signature != null)
                    fn.signature = handleSignature(fn.signature)
                val k = mapFields.keys.findLast {
                    it.name == fn.name &&
                    it.desc == fn.desc
                }
                if(k != null)
                    fn.name = mapFields[k]
            }

            cn.methods.forEach { md ->
                if(hideAll)
                    md.access = md.access or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_TRANSIENT

                if(cn.name == mainClass && md.name == "main" && md.desc == "([Ljava/lang/String;)V") {
                    verbose("Skipping method main in class ${cn.name}")
                }
                else {
                    //TODO: transform method name md.name
                }

                md.desc = handleSignature(md.desc)
                if(md.signature != null)
                    md.signature = handleSignature(md.signature)
                md.exceptions.forEachIndexed { i, type ->
                    if(type in mapTypes)
                        md.exceptions[i] = mapTypes[md.exceptions[i]]
                }
                md.tryCatchBlocks.forEach { tcb ->
                    if(tcb.type != null && tcb.type in mapTypes)
                        tcb.type = mapTypes[tcb.type]
                }
                md.instructions.iterator().forEach {
                    if(it is TypeInsnNode) {
                        if(it.desc.endsWith(';'))
                            it.desc = handleSignature(it.desc)
                        else if(it.desc in mapTypes) {
                            it.desc = mapTypes[it.desc]
                        }
                    }
                    else if(it is MethodInsnNode) {
                        it.desc = handleSignature(it.desc)
                        if(it.owner.endsWith(';'))
                            it.owner = handleSignature(it.owner)
                        else
                            if(it.owner in mapTypes)
                                it.owner = mapTypes[it.owner]
                        //TODO: transform method names it.name
                    }
                    else if(it is FieldInsnNode) {
                        it.desc = handleSignature(it.desc)
                        if(it.owner in mapTypes)
                            it.owner = mapTypes[it.owner]
                        val k = mapFields.keys.findLast { fn->
                            it.name == fn.name &&
                            it.desc == fn.desc
                        }
                        if(k != null)
                            it.name = mapFields[k]

                    }
                    else if(it is LocalVariableNode) {
                        it.desc = handleSignature(it.desc)
                    }
                    else if(it is MultiANewArrayInsnNode) {
                        it.desc = handleSignature(it.desc)
                    }
                    else if(it is LdcInsnNode) {
                        val cst = it.cst
                        if(cst is Type) {
                            if(cst.internalName.endsWith(';'))
                                it.cst = Type.getType(handleSignature(cst.internalName))
                            else
                                if (cst.internalName in mapTypes)
                                    it.cst = Type.getType("L${mapTypes[cst.internalName]!!};")

                        }
                    }
                    else if(it is FrameNode) {
                        it.stack?.forEachIndexed { i, item ->
                            if(item is String) {
                                if(item.endsWith(';'))
                                    it.stack[i] = handleSignature(item)
                                else if(item in mapTypes)
                                    it.stack[i] = mapTypes[item]
                            }
                        }
                        it.local?.forEachIndexed { i, item ->
                            if(item is String) {
                                if(item.endsWith(';'))
                                    it.local[i] = handleSignature(item)
                                else if(item in mapTypes)
                                    it.local[i] = mapTypes[item]
                            }
                        }
                    }
                    else if(it is LineNumberNode) {
                        if(removeDebug)
                            md.instructions.remove(it)
                    }

                }
                transformer.transformMethod(md)
            }

            cn.innerClasses.forEach { ic ->
                if(ic.name in mapTypes)
                    ic.name = if(ic.innerName != null) mapTypes[ic.name]+"$"+ic.innerName else mapTypes[ic.name]
                if(ic.outerName != null)
                    ic.outerName = ic.name.split("$")[0]

                if(ic.name != null) {
                    if(ic.name in mapTypes)
                        ic.name = mapTypes[ic.name]
                }

            }
            if(cn.outerClass in mapTypes)
                cn.outerClass = mapTypes[cn.outerClass]
            if(cn.outerMethodDesc != null)
                cn.outerMethodDesc = handleSignature(cn.outerMethodDesc)

            //TODO: transform outer method name cn.outerMethod
        }
    }

    private fun saveAll() {
        println("Saving classes")
        val manifest = zipContainer.entries.find { it.name == "META-INF/MANIFEST.MF" }
        if(manifest != null) {
            val content = String(manifest.data)
            val lines = content.split("\n").toTypedArray().toMutableList()
            lines.forEachIndexed { idx, line ->
                if(line.startsWith("Main-Class: ")){
                    if(!keepMain && mainClass in mapTypes)
                        lines[idx] = "Main-Class: ${mapTypes[mainClass]}"
                }
            }

            if(!keepMain) {
                manifest.data = buildString {
                    lines.forEach { l -> append(l+"\n") }
                }.toByteArray()
            }
        }
        zipContainer.entries.removeIf { it.name.endsWith(".class") }
        zipContainer.enter {
            classes.forEach { classNode ->
                "${classNode.name}.class" += writeClass(classNode)
            }
        }

        try {
            zipContainer.saveTo(outFile)
        }
        catch (e: Exception) {
            println("Failed to save to $outFile")
            throw e
        }
    }

    fun handleSignature(sig: String?): String? {
        if(sig == null)
            return null
        var localSig = sig!!
        val map = HashMap<String, String>()
        val types = SignatureHelper.parseTypes(sig)
        types.forEach { type ->
            val new = if(type in mapTypes)
                mapTypes[type]
            else
                type
            map[type] = new!!
        }
        map.forEach { k, v ->
            localSig = localSig.replace(k, v)
        }
        return localSig
    }

    fun addKeepRule(rule: String) {
        keepNames.add(Pattern.compile(rule))
    }

    fun readClass(data: ByteArray): ClassNode {
        val classNode = ClassNode()
        val cr = ClassReader(data)
        cr.accept(classNode, 0)
        return classNode
    }

    fun writeClass(classNode: ClassNode): ByteArray {
        val cw = ObfClassWriter(2)
        classNode.accept(cw)
        return cw.toByteArray()
    }
}

