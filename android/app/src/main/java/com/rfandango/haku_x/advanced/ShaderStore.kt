package com.rfandango.haku_x.advanced

import android.content.Context
import com.rfandango.haku_x.NativeCoreActivity
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class ShaderSelection(val preset: String = "", val parameters: Map<String, Float> = emptyMap()) {
  fun json() = JSONObject().put("preset", preset).put("parameters", JSONObject(parameters))
  companion object {
    fun parse(o: JSONObject): ShaderSelection {
      val p = o.optJSONObject("parameters") ?: JSONObject()
      return ShaderSelection(o.optString("preset"), p.keys().asSequence().take(64).mapNotNull { name ->
        p.optDouble(name).toFloat().takeIf { it.isFinite() }?.let { name to it }
      }.toMap())
    }
  }
}
internal object ShaderPack {
  const val MAX_ENTRIES = 4096
  const val MAX_BYTES = 256L * 1024 * 1024
  const val MAX_FILE_BYTES = 64L * 1024 * 1024
  fun extract(input: InputStream, directory: File,
    maxEntries: Int = MAX_ENTRIES, maxBytes: Long = MAX_BYTES,
    maxFileBytes: Long = MAX_FILE_BYTES, cancelled: () -> Boolean = { false }) {
    check(directory.isDirectory || directory.mkdirs())
    val root = directory.canonicalFile
    val names = HashSet<String>()
    var total = 0L; var entries = 0
    ZipInputStream(input).use { zip ->
      while (true) {
        val e = zip.nextEntry ?: break
        check(!cancelled()) { "Cancelled" }
        require(++entries <= maxEntries) { "Too many files (maximum $maxEntries)" }
        val name = e.name
        require(name.isNotBlank() && !name.startsWith('/') && '\\' !in name && ':' !in name &&
          name.split('/').none { it == ".." || it == "." } && names.add(name)) { "Unsafe or duplicate ZIP path" }
        val dest = File(root, name).canonicalFile
        require(dest.path.startsWith(root.path + File.separator)) { "ZIP path escapes pack" }
        if (e.isDirectory) check(dest.isDirectory || dest.mkdirs()) else {
          check(dest.parentFile!!.isDirectory || dest.parentFile!!.mkdirs())
          var size = 0L
          dest.outputStream().use { output ->
            val buffer = ByteArray(32768)
            while (true) {
              check(!cancelled()) { "Cancelled" }
              val n = zip.read(buffer); if (n < 0) break
              size += n; total += n
              require(size <= maxFileBytes && total <= maxBytes) { "Shader pack exceeds size limit" }
              output.write(buffer, 0, n)
            }
          }
        }
        zip.closeEntry()
      }
    }
    require(root.walkTopDown().any { it.isFile && it.extension.equals("slangp", true) }) { "No .slangp presets found" }
  }
}
class ShaderStore(private val context: Context) {
  companion object { const val OFFICIAL_MAX_ENTRIES = 20000 }
  private val store = AdvancedStore(context)
  val root = File(context.filesDir, "shaders")
  fun installBuiltins() {
    val dir = File(root, "builtin-v1").apply { mkdirs() }
    for (name in context.assets.list("shaders").orEmpty()) {
      val file = File(dir, name)
      if (!file.exists()) {
        val tmp = File(dir, "$name.tmp")
        context.assets.open("shaders/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
        check(tmp.renameTo(file))
      }
    }
  }
  fun import(input: InputStream): Int {
    val id = UUID.randomUUID().toString(); val stage = File(root, ".import-$id")
    try {
      ShaderPack.extract(input, stage)
      val count = stage.walkTopDown().count { it.isFile && it.extension.equals("slangp", true) }
      check(stage.renameTo(File(root, "pack-$id"))) { "Could not publish shader pack" }
      return count
    } finally { stage.deleteRecursively() }
  }
  /** Content-addressed snapshots keep selected presets valid across later downloads. */
  fun importOfficial(archive: File, cancelled: () -> Boolean): Int {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    archive.inputStream().use { input ->
      val b = ByteArray(32768)
      while (true) { check(!cancelled()) { "Cancelled" }; val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) }
    }
    val key = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    val dest = File(root, "libretro-${key.take(16)}")
    if (dest.isDirectory) return dest.walkTopDown().count { it.isFile && it.extension.equals("slangp", true) }
    val stage = File(root, ".download-${UUID.randomUUID()}")
    try {
      archive.inputStream().use { ShaderPack.extract(it, stage, OFFICIAL_MAX_ENTRIES, ShaderPack.MAX_BYTES,
        ShaderPack.MAX_FILE_BYTES, cancelled) }
      check(!cancelled()) { "Cancelled" }
      val count = stage.walkTopDown().count { it.isFile && it.extension.equals("slangp", true) }
      check(stage.renameTo(dest)) { "Could not install downloaded shaders" }
      return count
    } finally { stage.deleteRecursively() }
  }
  fun presets(): List<String> = root.walkTopDown().onEnter { !it.name.startsWith('.') }
    .filter { it.isFile && it.extension.equals("slangp", true) }.take(50000)
    .map { it.relativeTo(root).invariantSeparatorsPath }.sorted().toList()
  fun file(relative: String): File {
    val f = File(root, relative).canonicalFile
    require(relative.isNotEmpty() && f.path.startsWith(root.canonicalPath + File.separator) && f.isFile) { "Preset file is missing" }
    return f
  }
  fun selected(game: String?) = store.shader(game)
  fun save(game: String?, selection: ShaderSelection) = store.saveShader(game, selection)
  fun reset(game: String) = store.resetShader(game)
  fun hasOverride(game: String) = store.hasShader(game)
  fun apply(game: String) {
    val selection = selected(game)
    val path = if (selection.preset.isEmpty()) "" else runCatching { file(selection.preset).absolutePath }.getOrDefault("")
    val params = selection.parameters.toSortedMap()
    check(NativeCoreActivity.nativeSetShader(path, params.keys.toTypedArray(), params.values.toFloatArray())) { "Shader request rejected" }
  }
}
