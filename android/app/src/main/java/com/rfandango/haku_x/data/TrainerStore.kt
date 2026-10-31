package com.rfandango.haku_x.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** One imported trainer per game/version. Changes take a fresh game start.
 * Content-addressed binaries keep a pending launch's file stable on reimport. */
class TrainerStore(context: Context, private val gameKey: String) {
  private val app = context.applicationContext
  private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 255) }
  private val dir = File(app.filesDir, "trainers/" + hash(gameKey.toByteArray()))
  private val config = File(dir, "choices.json")
  data class Selection(val trainer: Etm, val file: File, val enabled: Boolean, val options: String)
  fun load(): Selection? {
    if (!config.isFile) return null
    val c = JSONObject(config.readText())
    val id = c.getString("file")
    require(Regex("[0-9a-f]{64}\\.etm").matches(id)) { "Invalid trainer filename" }
    val file = File(dir, id)
    require(file.length() in 35..Etm.MAX_SIZE.toLong()) { "Trainer file is missing or too large" }
    val trainer = Etm.parse(file.readBytes())
    require(trainer.matches(gameKey)) { "Trainer title ID does not match this game" }
    val options = c.getString("options")
    require(options.length == trainer.labels.size && options.all { it == '0' || it == '1' }) { "Invalid trainer options" }
    return Selection(trainer, file, c.optBoolean("enabled"), options)
  }
  private fun save(s: Selection) {
    dir.mkdirs()
    val c = JSONObject().put("file", s.file.name).put("enabled", s.enabled).put("options", s.options)
    val tmp = File(dir, "choices.tmp")
    tmp.writeText(c.toString())
    check(tmp.renameTo(config)) { "Could not save trainer options" }
  }
  fun importTrainer(uri: Uri): Selection {
    val d = app.contentResolver.openInputStream(uri)?.use { input ->
      val out = java.io.ByteArrayOutputStream()
      val buf = ByteArray(4096)
      while (true) {
        val n = input.read(buf)
        if (n < 0) break
        require(out.size()+n <= Etm.MAX_SIZE) { "Trainer exceeds 64 KiB" }
        out.write(buf, 0, n)
      }
      out.toByteArray()
    } ?: error("Cannot open trainer")
    val trainer = Etm.parse(d)
    require(trainer.matches(gameKey)) { "Trainer title ID does not match this game (${gameKey.substringBefore('.')})" }
    dir.mkdirs()
    val file = File(dir, hash(d)+".etm")
    if (!file.exists()) {
      val tmp = File(dir, "import.tmp"); tmp.writeBytes(d)
      check(tmp.renameTo(file)) { "Could not store trainer" }
    }
    return Selection(trainer, file, false, "0".repeat(trainer.labels.size)).also(::save)
  }
  fun enable(s: Selection, enabled: Boolean) = s.copy(enabled = enabled).also(::save)
  fun option(s: Selection, i: Int, enabled: Boolean): Selection {
    val bits = s.options.toCharArray(); bits[i] = if (enabled) '1' else '0'
    return s.copy(options = String(bits)).also(::save)
  }
  fun remove() { check(!config.exists() || config.delete()) { "Could not remove trainer" } }
}
