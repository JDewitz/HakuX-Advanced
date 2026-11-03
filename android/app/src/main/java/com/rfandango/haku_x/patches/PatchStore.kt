package com.rfandango.haku_x.patches

import android.content.Context
import com.rfandango.haku_x.advanced.CompatibilityIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

class PatchStore internal constructor(val dir: File) {
  constructor(context: Context): this(File(context.filesDir, "patches"))
  init { check(dir.isDirectory || dir.mkdirs()) { "Cannot create patch storage" } }
  companion object {
    private val LOCK = Any()
    private val HASH = Regex("[0-9a-f]{64}")
    fun gameKey(key: String): String {
      val i = CompatibilityIdentity.fromGameKey(key) ?: error("Patches require a certificate title ID and revision")
      return "%08X.%d".format(Locale.ROOT, i.titleId, i.revision)
    }
  }
  private val state = File(dir, "index.json")
  private fun read() = runCatching { JSONObject(state.readText()) }.getOrElse { JSONObject() }
  private fun change(block: (JSONObject) -> Unit) = synchronized(LOCK) {
    RandomAccessFile(File(dir, "index.lock"), "rw").use { file -> file.channel.lock().use {
      val o = read(); block(o)
      val tmp = File(dir, "index.tmp")
      tmp.outputStream().use { it.write(o.toString().toByteArray()); it.fd.sync() }
      check(tmp.renameTo(state)) { "Cannot save patch settings" }
    } }
  }
  private fun hashes(a: JSONArray?): Set<String> = (0 until (a?.length() ?: 0)).mapNotNull {
    a?.optString(it)?.takeIf(HASH::matches)
  }.toSet()
  var folder: String?
    get() = read().optString("folder").takeIf { it.isNotEmpty() }
    set(value) = change { o -> if (value == null) o.remove("folder") else o.put("folder", value); o.remove("folderEntries") }
  fun imported(): Set<String> = hashes(read().optJSONArray("imports"))
  fun folderEntries(): Set<String> = hashes(read().optJSONArray("folderEntries"))
  fun setFolderEntries(hashes: Set<String>) = change { it.put("folderEntries", JSONArray(hashes.sorted().take(512))) }
  fun choices(game: String): Set<String> = hashes(read().optJSONObject("choices")?.optJSONArray(gameKey(game)))
  fun configured(game: String) = runCatching { choices(game).isNotEmpty() }.getOrDefault(false)
  fun toggle(game: String, hash: String, enabled: Boolean) {
    require(HASH.matches(hash)); val key = gameKey(game)
    change { o ->
      val choices = o.optJSONObject("choices") ?: JSONObject()
      val before = hashes(choices.optJSONArray(key))
      val next = if (enabled) before + hash else before - hash
      choices.put(key, JSONArray(next.sorted())); o.put("choices", choices)
    }
  }
  fun cache(bytes: ByteArray): String = synchronized(LOCK) {
    PatchPack.parse(bytes)
    val hash = PatchPack.hash(bytes); val file = File(dir, "$hash.json")
    if (!file.isFile || !file.readBytes().contentEquals(bytes)) {
      val tmp = File(dir, "$hash.${java.util.UUID.randomUUID()}.tmp"); tmp.outputStream().use { it.write(bytes); it.fd.sync() }
      check(tmp.renameTo(file)) { "Cannot cache patch" }
    }
    hash
  }
  fun load(hash: String): PatchPack {
    require(HASH.matches(hash)); val file = File(dir, "$hash.json")
    require(file.length() in 1..PatchPack.MAX_BYTES.toLong()) { "Missing or oversized cached patch" }
    val bytes = file.readBytes(); require(PatchPack.hash(bytes) == hash) { "Cached patch hash differs" }
    return PatchPack.parse(bytes)
  }
  fun import(bytes: ByteArray): String {
    val hash = cache(bytes)
    change {
      val before = hashes(it.optJSONArray("imports"))
      require(hash in before || before.size < 512) { "Import catalog limit is 512 patches" }
      it.put("imports", JSONArray((before + hash).sorted()))
    }
    return hash
  }
}
