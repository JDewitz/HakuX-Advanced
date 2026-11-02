package com.rfandango.haku_x.advanced

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** Startup-only experiments. A per-game profile is an explicit snapshot of the global profile. */
data class AdvancedProfile(
  val ram128: Boolean = false,
  val fullX87: Boolean = false,
  val precisionControl: Boolean = true,
  val armAfp: Boolean = true,
  val quirks: Map<String, Int> = emptyMap(),
  val poolHigh: Boolean = false,
) {
  fun quirkSpec() = QUIRKS.mapNotNull { (key, _) -> quirks[key]?.takeIf { it != 0 }?.let {
    (if (it > 0) "+" else "-") + key
  } }.joinToString(",")
  fun arguments() = listOf("advanced=ram:${if (ram128) 128 else 64}",
    "advanced=x87:${if (fullX87) 80 else 64}", "advanced=pc:${if (precisionControl) 1 else 0}",
    "advanced=afp:${if (armAfp) 1 else 0}", "advanced=quirks:${quirkSpec()}", "pool=${if (poolHigh) "high" else "default"}")
  fun toJson() = JSONObject().put("ram128", ram128).put("fullX87", fullX87)
    .put("precisionControl", precisionControl).put("armAfp", armAfp)
    .put("quirks", JSONObject(quirks)).put("poolHigh", poolHigh)
  companion object {
    val QUIRKS = linkedMapOf(
      "vi_after_pipeline" to "Repeat vertex input after pipeline changes",
      "state_after_pipeline" to "Repeat all dynamic state after pipeline changes",
      "eq_depth_step" to "Depth equality tolerance",
      "lrz_clear" to "LRZ depth-clear workaround",
      "blend_const_reset" to "Restore blend constants",
      "no_push_desc" to "Avoid push descriptors",
      "ds_same_load" to "Match depth/stencil load operations",
      "z_eq_w" to "Clip-space depth workaround",
      "fresh_clear" to "Clear fresh attachments",
      "swap_w32" to "Align swapchain width to 32 pixels",
      "no_timestamps" to "Disable GPU timestamp queries",
      "present_cpu_wait" to "CPU wait before presentation",
    )
    fun fromJson(o: JSONObject) = AdvancedProfile(o.optBoolean("ram128", false),
      o.optBoolean("fullX87", false), o.optBoolean("precisionControl", true), o.optBoolean("armAfp", true),
      QUIRKS.keys.mapNotNull { key -> o.optJSONObject("quirks")?.optInt(key)?.takeIf { it == -1 || it == 1 }
        ?.let { key to it } }.toMap(), o.optBoolean("poolHigh", false))
  }
}

enum class EmulatorAction(val label: String) {
  FAST_FORWARD("Fast forward"), QUICK_SAVE("Quick save"), QUICK_LOAD("Quick load"),
  PAUSE("Pause / resume"), SCREENSHOT("Screenshot"), BENCHMARK("Record 10-second benchmark"), NEXT_SHADER("Next shader")
}

class AdvancedStore internal constructor(private val dir: File) {
  constructor(context: Context) : this(context.filesDir)
  companion object { private val WRITE_LOCK = Any() }
  private val file = File(dir, "advanced.json")
  private fun read(): JSONObject = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
  private fun change(block: (JSONObject) -> Unit) = synchronized(WRITE_LOCK) {
    // Reload under an OS file lock: launcher and game are separate processes.
    RandomAccessFile(File(dir, "advanced.lock"), "rw").use { lock ->
      lock.channel.lock().use {
        val o = read(); block(o)
        val tmp = File(dir, "advanced.json.tmp")
        tmp.outputStream().use { it.write(o.toString().toByteArray()); it.fd.sync() }
        check(tmp.renameTo(file)) { "Could not save advanced settings" }
      }
    }
  }
  var onlinePlay: Boolean
    get() = OnlinePlayPolicy.fromJson(read()).onlinePlay
    set(value) = change { it.put("onlinePlay", value) }
  fun hasProfile(game: String) = read().optJSONObject("games")?.has(game) == true
  fun profile(game: String? = null): AdvancedProfile {
    val o = read()
    return AdvancedProfile.fromJson(game?.let { o.optJSONObject("games")?.optJSONObject(it) }
      ?: o.optJSONObject("global") ?: JSONObject())
  }
  fun saveProfile(game: String?, p: AdvancedProfile) = change { o ->
    if (game == null) o.put("global", p.toJson()) else {
      val games = o.optJSONObject("games") ?: JSONObject()
      games.put(game, p.toJson()); o.put("games", games)
    }
  }
  fun resetProfile(game: String?) = change { o ->
    if (game == null) o.remove("global") else o.optJSONObject("games")?.remove(game)
  }
  fun hasShader(game: String) = read().optJSONObject("shaderGames")?.has(game) == true
  fun shader(game: String?): ShaderSelection {
    val o = read()
    return ShaderSelection.parse(game?.let { o.optJSONObject("shaderGames")?.optJSONObject(it) }
      ?: o.optJSONObject("shaderGlobal") ?: JSONObject())
  }
  fun saveShader(game: String?, selection: ShaderSelection) = change { o ->
    if (game == null) o.put("shaderGlobal", selection.json()) else {
      val games = o.optJSONObject("shaderGames") ?: JSONObject()
      games.put(game, selection.json()); o.put("shaderGames", games)
    }
  }
  fun resetShader(game: String) = change { it.optJSONObject("shaderGames")?.remove(game) }
  fun keys(): Map<EmulatorAction, Int> {
    val o = read().optJSONObject("keys") ?: JSONObject()
    return EmulatorAction.entries.filter { it != EmulatorAction.FAST_FORWARD }.mapNotNull {
      o.optInt(it.name, 0).takeIf { it > 0 }?.let { code -> it to code }
    }.toMap()
  }
  fun bind(action: EmulatorAction, key: Int?) = change { o ->
    val keys = o.optJSONObject("keys") ?: JSONObject()
    if (key == null) keys.remove(action.name) else keys.put(action.name, key)
    o.put("keys", keys)
  }
  var holdFastForward: Boolean
    get() = read().optBoolean("holdFastForward", false)
    set(v) = change { it.put("holdFastForward", v) }
  var quickSlot: Int
    get() = read().optInt("quickSlot", 1).coerceIn(1, com.rfandango.haku_x.data.SaveStateStore.SLOTS)
    set(v) = change { it.put("quickSlot", v.coerceIn(1, com.rfandango.haku_x.data.SaveStateStore.SLOTS)) }
}
