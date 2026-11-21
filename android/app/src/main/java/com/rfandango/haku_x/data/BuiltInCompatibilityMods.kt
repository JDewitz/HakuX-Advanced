package com.rfandango.haku_x.data

import com.rfandango.haku_x.patches.OnlineCompatibilityManifest
import com.rfandango.haku_x.patches.PatchLaunch

/** Release-owned native compatibility settings, separate from gameplay mods/trainers. */
object BuiltInCompatibilityMods {
  const val ROGUE_GAME = "5343000E.1"
  const val CACHE_MOD = "cache-write-pending"
  private const val SECTION = "hakux:cache-write-pending"
  private const val RULE = "5343000E Bank_en.xwb"
  private const val LINE = "patch\t$SECTION\t$CACHE_MOD\t$RULE"

  private fun matches(game: String, emulator: String) =
    game.equals(ROGUE_GAME, ignoreCase = true) && emulator.substringBefore('-') == "1.3.0"

  fun isAllowedOnline(game: String, mod: ModInfo, emulator: String): Boolean =
    matches(game, emulator) && mod.id == CACHE_MOD && mod.refresh == 0 &&
      mod.patches == listOf(SECTION to RULE)

  /** Read only this game's specific native-setting toggle; never serialize mod-supplied lines. */
  fun onlineLines(game: String, emulator: String, enabled: (String) -> Boolean): List<String> =
    if (matches(game, emulator) && enabled(CACHE_MOD)) listOf(LINE) else emptyList()

  internal fun protectedManifest(game: String, emulator: String, imported: PatchLaunch?,
                                 enabled: (String) -> Boolean): List<String> =
    onlineLines(game, emulator, enabled) +
      (imported?.let { OnlineCompatibilityManifest.forLaunch(it, game, emulator) } ?: emptyList())
}
