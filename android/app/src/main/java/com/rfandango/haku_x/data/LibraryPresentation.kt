package com.rfandango.haku_x.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

/** Display preference only; switching never changes launch parameters. */
enum class LibraryViewMode(val label: String) {
  CARDS("Cards"), LIST("List"), STATS("Stats"), INFO("Info / Play cards"), ICONS("Icons");
  companion object {
    fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: CARDS
  }
}

data class GameFeatures(
  val trainerEnabled: Boolean = false,
  val trainerImported: Boolean = false,
  val trainerBlocked: Boolean = false,
  val trainerOptions: Int = 0,
  val trainerError: Boolean = false,
  val customSetup: Boolean = false,
)

object LibraryMarkers {
  fun hasSettings(game: String, settings: Map<String, *>): Boolean {
    val legacy = game.takeUnless { it.startsWith("path:") }?.substringBefore('.')
    return GameSetting.entries.any { s -> settings.containsKey("$game/${s.key}") ||
      (legacy != null && settings.containsKey("$legacy/${s.key}")) }
  }
  fun hasEnabledMods(game: String, choices: JSONObject): Boolean = choices.keys().asSequence().any {
    (it.startsWith("$game/mod/") || it.startsWith("$game/cheat/")) && choices.optBoolean(it, false)
  }
  fun knownBytes(sizes: Iterable<Long>): Long = sizes.fold(0L) { sum, size ->
    if (size <= 0) sum else if (size > Long.MAX_VALUE - sum) Long.MAX_VALUE else sum + size
  }
}

/** Disk/config reads happen in the view model on IO, never in lazy card composition. */
class LibraryFeatureIndex(private val context: Context) {
  private fun json(name: String) = runCatching { JSONObject(File(context.filesDir, name).readText()) }.getOrElse { JSONObject() }
  fun snapshot(games: List<Game>, info: Map<Uri, DiscInfo>): Map<Uri, GameFeatures> {
    val online = com.rfandango.haku_x.advanced.AdvancedStore(context).onlinePlay
    val settings = context.getSharedPreferences(GameOverrides.NAME, Context.MODE_PRIVATE).all
    val advanced = json("advanced.json")
    val modsFile = File(context.filesDir, "mod_choices.json")
    val mods = if (modsFile.exists()) json("mod_choices.json") else JSONObject().apply {
      context.getSharedPreferences(ModChoices.NAME, Context.MODE_PRIVATE).all.forEach { (key, value) -> if (value is Boolean) put(key,value) }
    }
    val patches = com.rfandango.haku_x.patches.PatchStore(context)
    return games.associate { game ->
      val d = info[game.uri]
      val key = GameOverrides.gameKey(d?.titleId ?: 0L, d?.version ?: 0L, game.relativePath)
      val trainer = runCatching { TrainerStore(context, key).load() }
      val t = trainer.getOrNull()
      game.uri to GameFeatures(
        trainerEnabled = t?.enabled == true && !online,
        trainerBlocked = t?.enabled == true && online,
        trainerImported = t != null,
        trainerOptions = t?.options?.count { it == '1' } ?: 0,
        trainerError = trainer.isFailure,
        customSetup = LibraryMarkers.hasSettings(key,settings) || advanced.optJSONObject("games")?.has(key) == true ||
          advanced.optJSONObject("shaderGames")?.has(key) == true || LibraryMarkers.hasEnabledMods(key,mods) || patches.configured(key),
      )
    }
  }
}
