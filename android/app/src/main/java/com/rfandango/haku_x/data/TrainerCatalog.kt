package com.rfandango.haku_x.data

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

fun trainerHash(bytes: ByteArray): String {
  val hex = "0123456789abcdef"
  return buildString(64) {
    for (b in MessageDigest.getInstance("SHA-256").digest(bytes)) {
      val n = b.toInt() and 255; append(hex[n ushr 4]); append(hex[n and 15])
    }
  }
}

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

/** Title-ID lookup over parsed metadata; no trainer files are opened by matching. */
class TrainerLookup(entries: List<TrainerEntry>) {
  private val byTitle = buildMap<Long, MutableList<TrainerEntry>> {
    for (e in entries) for (id in e.trainer.titleIds.filter { it != 0L }.distinct())
      getOrPut(id) { mutableListOf() }.add(e)
  }
  fun forGame(gameKey: String): List<TrainerEntry> =
    byTitle[gameKey.substringBefore('.').toLongOrNull(16)]?.toList() ?: emptyList()
}

/** Executable bytes are validated at import/selection, not every catalog read. */
class TrainerCatalog(private val directory: File,
  private val readPayload: (File) -> ByteArray = { it.inputStream().use { input -> input.readTrainerBytes() } }) {
  private val index = File(directory,"index.json")
  val hasIndex get() = index.isFile
  private fun <T> locked(block: () -> T): T = synchronized(WRITE_LOCK) {
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create trainer storage" }
    RandomAccessFile(File(directory,"index.lock"),"rw").use { lock -> lock.channel.lock().use { block() } }
  }
  fun cache(bytes: ByteArray, source: String): TrainerEntry {
    val trainer = Etm.parse(bytes); val id = trainerHash(bytes); val file = File(directory,"$id.etm")
    if (!file.isFile || !file.readBytes().contentEquals(bytes)) writeTrainerFile(file,bytes)
    return TrainerEntry(id,trainer,file,listOf(source))
  }
  /** Only this selected payload is reopened; cached display metadata cannot authorize execution. */
  fun verifiedBytes(entry: TrainerEntry): ByteArray {
    require(ID.matches(entry.id)) { "Invalid trainer identity" }
    val bytes = readPayload(entry.file)
    require(trainerHash(bytes) == entry.id) { "Cached trainer changed; refresh or import it again" }
    Etm.parse(bytes)
    return bytes
  }
  fun save(entries: List<TrainerEntry>) = locked { saveUnlocked(entries) }
  fun add(entries: List<TrainerEntry>) = locked { saveUnlocked(readUnlocked() + entries) }
  fun replaceFolder(entries: List<TrainerEntry>): List<TrainerEntry> = locked {
    // Read imports again under the lock so another browser's import cannot be lost.
    val manual = readUnlocked().mapNotNull { e ->
      e.sources.filter { it.startsWith("Imported: ") }.takeIf { it.isNotEmpty() }?.let { e.copy(sources=it) }
    }
    merge(entries + manual).also { saveUnlocked(it) }
  }
  private fun saveUnlocked(entries: List<TrainerEntry>) {
    val list = JSONArray()
    merge(entries).forEach { e ->
      require(ID.matches(e.id)) { "Invalid trainer identity" }
      val t = e.trainer
      list.put(JSONObject().put("id",e.id).put("sources",JSONArray(e.sources))
        .put("name",t.name).put("note",t.note).put("labels",JSONArray(t.labels)).put("titleIds",JSONArray(t.titleIds)))
    }
    val bytes = JSONObject().put("version",VERSION).put("entries",list).toString().toByteArray(Charsets.UTF_8)
    require(bytes.size <= MAX_INDEX_BYTES) { "Trainer catalog index is too large" }
    writeTrainerFile(index,bytes)
  }
  fun read(): List<TrainerEntry> = if (!index.isFile) emptyList() else locked { readUnlocked() }
  private fun readUnlocked(): List<TrainerEntry> {
    if (!index.isFile) return emptyList()
    require(index.length() <= MAX_INDEX_BYTES) { "Trainer catalog index is too large" }
    val data = JSONTokener(index.readText()).nextValue()
    // One-time migration of the old ID-only catalog. Future reads use metadata only.
    if (data is JSONArray) {
      val migrated = decode(data, legacy=true)
      saveUnlocked(migrated)
      return migrated
    }
    require(data is JSONObject && data.getInt("version") == VERSION) { "Unsupported trainer index; refresh or reimport trainers" }
    return decode(data.getJSONArray("entries"), legacy=false)
  }
  private fun decode(list: JSONArray, legacy: Boolean): List<TrainerEntry> {
    require(list.length() <= 20000) { "Too many indexed trainers" }
    return (0 until list.length()).mapNotNull { i -> runCatching {
      val o = list.getJSONObject(i); val id = o.getString("id")
      require(ID.matches(id)) { "Invalid trainer identity" }
      val file = File(directory,"$id.etm")
      val sources = o.getJSONArray("sources")
      val origins = (0 until sources.length()).map { sources.getString(it) }
      val trainer = if (legacy) {
        val bytes = readPayload(file); require(trainerHash(bytes) == id) { "Trainer cache checksum mismatch" }
        Etm.parse(bytes)
      } else {
        val labels = o.getJSONArray("labels"); val ids = o.getJSONArray("titleIds")
        require(labels.length() in 1..100 && ids.length() in 2..3) { "Invalid trainer metadata" }
        val name = o.getString("name"); val note = o.getString("note")
        val options = (0 until labels.length()).map { labels.getString(it) }
        val titles = (0 until ids.length()).map { ids.getLong(it).also { n -> require(n in 0L..0xffffffffL) } }
        require(name.length.toLong() + note.length + options.sumOf { it.length.toLong() } <= Etm.MAX_SIZE) { "Trainer metadata exceeds size limit" }
        Etm(name,note,options,titles)
      }
      TrainerEntry(id,trainer,file,origins)
    }.getOrNull() }.let(::merge)
  }
  companion object {
    private const val VERSION = 2
    private const val MAX_INDEX_BYTES = 32 * 1024 * 1024
    private val ID = Regex("[0-9a-f]{64}")
    private val WRITE_LOCK = Any()
    fun merge(entries: List<TrainerEntry>): List<TrainerEntry> = entries.groupBy { it.id }.values.map { same ->
      same.first().copy(sources = same.flatMap { it.sources }.distinct().sorted())
    }.sortedWith(compareBy<TrainerEntry> { it.trainer.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
  }
}
