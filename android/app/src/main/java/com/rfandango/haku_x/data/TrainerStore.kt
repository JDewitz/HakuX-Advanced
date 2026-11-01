package com.rfandango.haku_x.data

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import org.json.JSONObject
import java.io.File

/** One active ETM per game revision, with remembered choices for each alternative hash. */
class TrainerStore private constructor(root: File, private val gameKey: String, private val resolver: ContentResolver?) {
  constructor(context: Context, gameKey: String): this(context.filesDir,gameKey,context.contentResolver)
  internal constructor(root: File, gameKey: String): this(root,gameKey,null)
  private val dir = File(root,"trainers/"+trainerHash(gameKey.toByteArray(Charsets.UTF_8)))
  private val config = File(dir,"choices.json")
  data class Selection(val trainer: Etm, val file: File, val enabled: Boolean, val options: String)
  private fun readConfig() = if (config.isFile) JSONObject(config.readText()) else JSONObject()
  fun load(): Selection? {
    val c = readConfig(); if (!c.has("file")) return null
    val id = c.getString("file")
    require(Regex("[0-9a-f]{64}\\.etm").matches(id)) { "Invalid trainer filename" }
    val file = File(dir,id)
    val bytes = file.inputStream().use { it.readTrainerBytes() }
    require(trainerHash(bytes)+".etm" == id) { "Trainer checksum mismatch; import it again" }
    val trainer = Etm.parse(bytes)
    require(trainer.matches(gameKey)) { "Trainer title ID does not match this game" }
    val options = c.getString("options")
    require(options.length == trainer.labels.size && options.all { it=='0' || it=='1' }) { "Invalid trainer options" }
    return Selection(trainer,file,c.optBoolean("enabled"),options)
  }
  private fun save(s: Selection) {
    val c = readConfig(); val history = c.optJSONObject("history") ?: JSONObject()
    if (c.has("file")) history.put(c.getString("file"),JSONObject().put("enabled",c.optBoolean("enabled")).put("options",c.optString("options")))
    history.put(s.file.name,JSONObject().put("enabled",s.enabled).put("options",s.options))
    c.put("history",history).put("file",s.file.name).put("enabled",s.enabled).put("options",s.options)
    writeTrainerFile(config,c.toString().toByteArray(Charsets.UTF_8))
  }
  fun select(bytes: ByteArray): Selection {
    val trainer = Etm.parse(bytes)
    require(trainer.matches(gameKey)) { "Trainer title ID does not match this game (${gameKey.substringBefore('.')})" }
    val file = File(dir,trainerHash(bytes)+".etm")
    if (!file.isFile || !file.readBytes().contentEquals(bytes)) writeTrainerFile(file,bytes)
    val c = readConfig()
    val old = if (c.optString("file")==file.name) c else c.optJSONObject("history")?.optJSONObject(file.name)
    val options = old?.optString("options")?.takeIf { it.length==trainer.labels.size && it.all { b -> b=='0'||b=='1' } }
      ?: "0".repeat(trainer.labels.size)
    return Selection(trainer,file,old?.optBoolean("enabled") ?: false,options).also(::save)
  }
  fun importTrainer(uri: Uri): Selection = select(resolver?.openInputStream(uri)?.use { it.readTrainerBytes() } ?: error("Cannot open trainer"))
  fun enable(s: Selection, enabled: Boolean) = s.copy(enabled=enabled).also(::save)
  fun option(s: Selection, i: Int, enabled: Boolean): Selection {
    require(i in s.options.indices) { "Unknown trainer option" }
    val bits=s.options.toCharArray();bits[i]=if(enabled)'1' else '0'
    return s.copy(options=String(bits)).also(::save)
  }
  fun remove() {
    val s=load() ?: return
    save(s.copy(enabled=false))
    val c=readConfig();c.remove("file");c.remove("enabled");c.remove("options")
    writeTrainerFile(config,c.toString().toByteArray(Charsets.UTF_8))
  }
}
