package com.rfandango.haku_x.data

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

fun trainerHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
  .joinToString("") { "%02x".format(it.toInt() and 255) }

fun InputStream.readTrainerBytes(): ByteArray {
  val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(4096)
  while (true) {
    val n = read(buf); if (n < 0) break
    require(out.size() + n <= Etm.MAX_SIZE) { "Trainer exceeds 64 KiB" }
    out.write(buf,0,n)
  }
  return out.toByteArray()
}

internal fun writeTrainerFile(file: File, bytes: ByteArray) {
  check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "Cannot create trainer storage" }
  val temp = File.createTempFile("trainer-", ".tmp", file.parentFile)
  try {
    FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
    check(temp.renameTo(file)) { "Cannot save trainer" }
  } finally { temp.delete() }
}

data class TrainerEntry(val id: String, val trainer: Etm, val file: File, val sources: List<String>)

/** Parsed executable trainers are copied into stable content-addressed storage before selection. */
class TrainerCatalog(private val directory: File) {
  private val index = File(directory,"index.json")
  fun cache(bytes: ByteArray, source: String): TrainerEntry {
    val trainer = Etm.parse(bytes); val id = trainerHash(bytes); val file = File(directory,"$id.etm")
    if (!file.isFile || !file.readBytes().contentEquals(bytes)) writeTrainerFile(file,bytes)
    return TrainerEntry(id,trainer,file,listOf(source))
  }
  fun save(entries: List<TrainerEntry>) {
    val list = JSONArray()
    merge(entries).forEach { e -> list.put(JSONObject().put("id",e.id).put("sources",JSONArray(e.sources))) }
    writeTrainerFile(index,list.toString().toByteArray(Charsets.UTF_8))
  }
  fun read(): List<TrainerEntry> {
    if (!index.exists()) return emptyList()
    require(index.length() <= 8 * 1024 * 1024) { "Trainer catalog index is too large" }
    val a = JSONArray(index.readText())
    return (0 until a.length()).mapNotNull { i -> runCatching {
      val o = a.getJSONObject(i); val id = o.getString("id")
      require(Regex("[0-9a-f]{64}").matches(id)) { "Invalid trainer identity" }
      val file = File(directory,"$id.etm")
      val bytes = file.inputStream().use { it.readTrainerBytes() }
      require(trainerHash(bytes)==id) { "Trainer cache checksum mismatch" }
      val sources = o.getJSONArray("sources")
      TrainerEntry(id,Etm.parse(bytes),file,(0 until sources.length()).map { sources.getString(it) })
    }.getOrNull() }.let(::merge)
  }
  companion object {
    fun merge(entries: List<TrainerEntry>): List<TrainerEntry> = entries.groupBy { it.id }.values.map { same ->
      same.first().copy(sources = same.flatMap { it.sources }.distinct().sorted())
    }.sortedWith(compareBy<TrainerEntry> { it.trainer.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
  }
}
