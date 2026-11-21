package com.rfandango.haku_x.data

import com.rfandango.haku_x.advanced.OnlinePlayPolicy
import com.rfandango.haku_x.patches.PatchLaunch
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class BuiltInCompatibilityModsTest {
  private val game = "5343000E.1"
  private val emulator = "1.3.0-advanced5.21-preview.1-onlinecompat"
  private val line = "patch\thakux:cache-write-pending\tcache-write-pending\t5343000E Bank_en.xwb"
  private val mod = ModInfo("cache-write-pending", "Sound bank loading fix", "", "",
    listOf("hakux:cache-write-pending" to "5343000E Bank_en.xwb"))

  @Test fun rogueCompatibilityDefaultsOnOfflineAndProtected() {
    val choices = JSONObject()
    assertTrue(ModChoices.modEnabled(choices, game, "cache-write-pending"))
    assertTrue(ModChoices.modEnabled(choices, "5343000e.1", "cache-write-pending"))
    assertEquals(listOf(line), BuiltInCompatibilityMods.protectedManifest(game, emulator, null) {
      ModChoices.modEnabled(choices, game, it)
    })
    for (key in listOf("4D530004.8", "5343000E.2", "5343000E")) {
      assertFalse(ModChoices.modEnabled(choices, key, "cache-write-pending"))
    }
    assertFalse(ModChoices.modEnabled(choices, game, "trainer"))
  }

  @Test fun explicitOffPersistsAndCanBeReenabled() {
    val choices = JSONObject()
    val key = "$game/mod/cache-write-pending"
    ModChoices.storeChoice(choices, key, false)
    val restored = JSONObject(choices.toString())
    assertTrue(restored.has(key))
    assertFalse(ModChoices.modEnabled(restored, game, "cache-write-pending"))
    assertTrue(BuiltInCompatibilityMods.protectedManifest(game, emulator, null) {
      ModChoices.modEnabled(restored, game, it)
    }.isEmpty())
    ModChoices.storeChoice(restored, key, true)
    assertTrue(ModChoices.modEnabled(restored, game, "cache-write-pending"))
  }

  @Test fun protectedRogueSelectionProducesOnlyTheCanonicalNativeRule() {
    assertEquals(listOf(line), BuiltInCompatibilityMods.protectedManifest(game, emulator, null) {
      assertEquals("cache-write-pending", it); true
    })
    assertTrue(BuiltInCompatibilityMods.protectedManifest(game, emulator, null) { false }.isEmpty())
    assertEquals(listOf(line), BuiltInCompatibilityMods.onlineLines("5343000e.1", emulator) { true })
  }

  @Test fun otherIdentitiesDoNotReadAnyLegacySelections() {
    for (key in listOf("4D530004.8", "5343000E.2", "5343000E", "path:rogue.iso")) {
      assertTrue(BuiltInCompatibilityMods.onlineLines(key, emulator) { fail("Unexpected legacy settings read"); true }.isEmpty())
    }
    assertTrue(BuiltInCompatibilityMods.onlineLines(game, "1.3.1-advanced5.21") {
      fail("Unreviewed emulator base"); true
    }.isEmpty())
  }

  @Test fun onlyTheExactBundledNativeOptionIsVisibleOnline() {
    assertTrue(BuiltInCompatibilityMods.isAllowedOnline(game, mod, emulator))
    for (other in listOf(mod.copy(id = "trainer"), mod.copy(refresh = 120),
      mod.copy(patches = mod.patches + ("default.xbe" to "0x11000: 01 -> FF")),
      mod.copy(patches = listOf("hakux:cache-write-pending" to "5343000E Other.xwb")))) {
      assertFalse(BuiltInCompatibilityMods.isAllowedOnline(game, other, emulator))
    }
    assertFalse(BuiltInCompatibilityMods.isAllowedOnline("5343000E.2", mod, emulator))
    assertFalse(BuiltInCompatibilityMods.isAllowedOnline(game, mod, "1.3.1"))
  }

  @Test fun callbackCannotSmuggleTrainersFilesCheatsOrOtherNativeRules() {
    val malicious = PatchLaunch(lines = listOf("etm\ttrainer.etm\t111", "cheat\t1\t0\tInfinite health",
      "code\tw32 0x11000 0", "file\tdefault.xbe\tmodified.xbe", "refresh\t120",
      "patch\thakux:cache-write-pending\tforged\t5343000E Other.xwb"))
    assertEquals(listOf(line), BuiltInCompatibilityMods.protectedManifest(game, emulator, malicious) { true })
    assertTrue(BuiltInCompatibilityMods.protectedManifest(game, emulator, malicious) { false }.isEmpty())
  }

  @Test fun compatibilityDoesNotEnableTrainersMemoryToolsOrBlockStateLoading() {
    val protected = OnlinePlayPolicy(true)
    assertFalse(protected.allowGuestMods)
    assertFalse(protected.allowMemoryTools)
    assertTrue(protected.allowStateLoad)
    assertEquals("nat", protected.networkMode("nat"))
    assertEquals(listOf(line), BuiltInCompatibilityMods.protectedManifest(game, emulator, PatchLaunch()) { true })
    val offline = OnlinePlayPolicy(false)
    assertTrue(offline.allowGuestMods)
    assertEquals("off", offline.networkMode("nat"))
  }
}
