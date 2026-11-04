package com.rfandango.haku_x.advanced

import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Build one Slang chain: RenderUpdate first, then the user's post-render preset.
 * Unsupported history/import semantics fail closed instead of silently changing the preset.
 */
internal object ShaderStack {
  const val MAX_PASSES = 16
  private data class Value(val text: String, val origin: File)
  data class Result(val file: File, val parameters: Map<String,Float>)
  private val indexed=Regex("(shader|filter_linear|wrap_mode|float_framebuffer|srgb_framebuffer|mipmap_input|alias|frame_count_mod|scale_type|scale_type_x|scale_type_y|scale|scale_x|scale_y)([0-9]+)")
  private val identifier=Regex("[A-Za-z_][A-Za-z_0-9]*")
  private const val INPUT_ALIAS="HAKU_PreOutput"
  fun build(root: File, pre: File, preParameters: Map<String,Float>, post: File, postParameters: Map<String,Float>): Result {
    val base=root.canonicalFile
    fun confined(file: File): File {
      val f=file.canonicalFile
      require(f.path.startsWith(base.path+File.separator) && f.isFile) { "Shader dependency is missing or outside the shader folder" }
      return f
    }
    fun resolve(v: Value)=confined(File(v.text).let { if(it.isAbsolute) it else File(v.origin.parentFile,v.text) })
    fun preset(file: File, visiting: Set<String> = emptySet()): LinkedHashMap<String,Value> {
      val f=confined(file)
      require(f.path !in visiting && visiting.size<16 && f.length()<=128*1024) { "Preset reference cycle or size limit" }
      val result=linkedMapOf<String,Value>()
      val own=linkedMapOf<String,Value>()
      for(raw in f.readLines()) {
        val line=raw.trim()
        if(line.startsWith("#reference")) {
          val ref=Regex("#reference\\s+\"([^\"]+)\"\\s*(?:#.*)?").matchEntire(line)?.groupValues?.get(1)
            ?: error("Unsupported preset reference")
          result.putAll(preset(resolve(Value(ref,f)),visiting+f.path))
        } else if(line.isNotEmpty() && !line.startsWith('#')) {
          val m=Regex("([A-Za-z_][A-Za-z_0-9]*)\\s*=\\s*(?:\"([^\"]*)\"|([^#]*))\\s*(?:#.*)?").matchEntire(line)
            ?: error("Unsupported preset syntax")
          own[m.groupValues[1]]=Value((m.groups[2]?.value ?: m.groupValues[3]).trim(),f)
        }
      }
      result.putAll(own);return result
    }
    val before=preset(pre);val after=preset(post)
    fun count(p: Map<String,Value>)=p["shaders"]?.text?.toIntOrNull()?.also { n ->
      require(n in 1..MAX_PASSES && (0 until n).all { p.containsKey("shader$it") }) { "Invalid shader pass count" }
    } ?: error("Missing shader pass count")
    val offset=count(before);val postCount=count(after)
    require(offset+postCount<=MAX_PASSES) { "Stack exceeds $MAX_PASSES passes" }
    require(before["feedback_pass"]==null && before["imports"]==null && after["imports"]==null) { "Imported uniform/feedback configuration is unsupported in a stack" }
    fun list(p: Map<String,Value>, key: String)=p[key]?.text?.split(';')?.filter { it.isNotBlank() }.orEmpty()
    val preAliases=before.filterKeys { it.matches(Regex("alias[0-9]+")) }.values.map { it.text }.filter { it.isNotEmpty() }
    val postAliases=after.filterKeys { it.matches(Regex("alias[0-9]+")) }.values.map { it.text }.filter { it.isNotEmpty() }
    val aliases=preAliases+postAliases+INPUT_ALIAS
    require(aliases.all { identifier.matches(it) } && aliases.distinct().size==aliases.size) { "Conflicting or unsupported pass aliases" }
    val preTextures=list(before,"textures");val postTextures=list(after,"textures")
    require((preTextures+postTextures).distinct().size==preTextures.size+postTextures.size) { "Conflicting lookup texture names" }
    val sources=linkedMapOf<File,String>()
    val include=Regex("(?m)^\\s*#include\\s+\"([^\"]+)\"")
    fun readSource(file: File) {
      val f=confined(file)
      if(f in sources) return
      require(sources.size<256 && f.length()<=1024*1024) { "Shader source limit exceeded" }
      val text=f.readText();sources[f]=text
      require(sources.values.sumOf { it.length }<=8*1024*1024) { "Shader source budget exceeded" }
      val code=text.replace(Regex("/\\*.*?\\*/",RegexOption.DOT_MATCHES_ALL),"").replace(Regex("//[^\\n]*"),"")
      require(!Regex("\\bOriginalHistory[0-9]*").containsMatchIn(code) && !Regex("\\bPassPrev[0-9]+").containsMatchIn(code)) { "Original-history/legacy pass semantics are unsupported in a stack" }
      require(!code.contains("#include <")) { "System shader includes are unsupported in a stack" }
      for(m in include.findAll(text)) readSource(resolve(Value(m.groupValues[1],f)))
    }
    for(i in 0 until postCount) readSource(resolve(after.getValue("shader$i")))
    val declaredPost=sources.values.flatMap { text -> Regex("#pragma\\s+parameter\\s+([A-Za-z_][A-Za-z_0-9]*)").findAll(text).map { it.groupValues[1] }.toList() }.toSet()
    val preNames=(list(before,"parameters")+preParameters.keys).toSet()
    val postNames=(list(after,"parameters")+postParameters.keys+declaredPost).toSet()
    require(preNames.intersect(postNames).isEmpty()) { "Shader stages have conflicting parameter names" }
    val runtime=preParameters+postParameters
    require(runtime.size<=64 && runtime.values.all { it.isFinite() }) { "Stack parameter limit exceeded" }
    val digest=MessageDigest.getInstance("SHA-256")
    fun hash(s: String) { digest.update(s.toByteArray());digest.update(0.toByte()) }
    hash("stack-v1");hash(base.path)
    for(p in listOf(before,after)) for((key,value) in p.toSortedMap()) { hash(key);hash(value.text);hash(value.origin.path) }
    for((file,text) in sources.toSortedMap(compareBy { it.path })) { hash(file.path);hash(text) }
    for((key,value) in runtime.toSortedMap()) { hash(key);hash(value.toString()) }
    val id=digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }.take(20)
    val cache=File(base,".stack-cache").apply { check(isDirectory || mkdirs()) }
    val dest=File(cache,id)
    fun short(file: File)=file.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"),"_").replace(Regex("-+"),"-").take(28)
    val name="stack--${short(pre)}--${short(post)}--$id.slangp"
    val output=File(dest,name)
    if(output.isFile) return Result(output,runtime)
    val stage=File(cache,".stage-${UUID.randomUUID()}").apply { check(mkdirs()) }
    fun postFile(file: File)=File(dest,"post/"+file.relativeTo(base).invariantSeparatorsPath)
    fun quoted(value: String): String { require('"' !in value && '\n' !in value && '\r' !in value) { "Unsupported quote in shader path/value" };return "\"$value\"" }
    try {
      for((file,text) in sources) {
        var rewritten=include.replace(text) { m -> "#include "+quoted(postFile(resolve(Value(m.groupValues[1],file))).absolutePath) }
        rewritten=Regex("\\bPass(Output|Feedback)([0-9]+)(Size)?\\b").replace(rewritten) { m ->
          val index=m.groupValues[2].toInt();require(index<postCount) { "Invalid post-render pass reference" }
          "Pass${m.groupValues[1]}${index+offset}${m.groupValues[3]}"
        }
        rewritten=Regex("\\bOriginalSize\\b").replace(rewritten,INPUT_ALIAS+"Size")
        rewritten=Regex("\\bOriginal\\b").replace(rewritten,INPUT_ALIAS)
        val out=File(stage,"post/"+file.relativeTo(base).invariantSeparatorsPath)
        check(out.parentFile!!.isDirectory || out.parentFile!!.mkdirs());out.writeText(rewritten)
      }
      val combined=linkedMapOf("shaders" to (offset+postCount).toString())
      fun append(p: Map<String,Value>, delta: Int, isPost: Boolean) {
        val textures=list(p,"textures");val params=list(p,"parameters")
        for((key,value) in p) {
          if(key in listOf("shaders","textures","parameters")) continue
          val m=indexed.matchEntire(key)
          val outKey=if(m!=null) m.groupValues[1]+(m.groupValues[2].toInt()+delta) else key
          val path=when { m?.groupValues?.get(1)=="shader" -> if(isPost) postFile(resolve(value)).absolutePath else resolve(value).absolutePath
            key in textures -> resolve(value).absolutePath
            key=="feedback_pass" -> (value.text.toInt().also { require(it in 0 until postCount) }+delta).toString()
            else -> value.text }
          require(!combined.containsKey(outKey)) { "Conflicting preset property: $outKey" }
          combined[outKey]=path
        }
      }
      append(before,0,false);append(after,offset,true)
      combined["alias${offset-1}"]=INPUT_ALIAS
      combined["textures"]=(preTextures+postTextures).joinToString(";")
      combined["parameters"]=(list(before,"parameters")+list(after,"parameters")).distinct().joinToString(";")
      File(stage,name).writeText(combined.entries.joinToString("\n",postfix="\n") { (key,value) -> "$key = ${quoted(value)}" })
      if(!stage.renameTo(dest)) check(output.isFile) { "Cannot publish shader stack" }
      // Bound cached source copies. Never touch imported packs.
      cache.listFiles().orEmpty().filter { it.isDirectory && it.name.matches(Regex("[a-f0-9]{20}")) && it!=dest }
        .sortedByDescending { it.lastModified() }.drop(3).forEach { it.deleteRecursively() }
      return Result(output,runtime)
    } finally { stage.deleteRecursively() }
  }
}
