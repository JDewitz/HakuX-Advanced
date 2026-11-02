package com.rfandango.haku_x.advanced

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CompatibilityProfilesTest {
  private val halo = "4D530004.9"
  // Synthetic approval fixture; this does not approve a shipped Halo rule.
  private val approved = CompatibilityProfiles.haloPool.copy(revisions = setOf(9), automatic = true)
  private fun resolve(mode: CompatibilityMode, key: String? = halo, version: String = "1.3.0-advanced5.9-compat",
                      rules: List<CompatibilityRule> = listOf(approved)) = CompatibilityProfiles.resolvePool(mode, key, version, rules)

  @Test fun shippedCatalogKeepsHaloOptIn() {
    assertEquals(1, CompatibilityProfiles.rules.size)
    assertFalse(CompatibilityProfiles.haloPool.automatic)
    assertTrue(CompatibilityProfiles.haloPool.revisions.isEmpty())
    val result = resolve(CompatibilityMode.AUTO, rules = CompatibilityProfiles.rules)
    assertFalse(result.enabled)
    assertNull(result.ruleId)
    assertTrue(result.reason.contains("No approved"))
    assertEquals("pool=default", result.argument)
  }
  @Test fun automaticRulesRequireTitleRevisionAndEmulatorBuild() {
    assertTrue(resolve(CompatibilityMode.AUTO).enabled)
    assertTrue(resolve(CompatibilityMode.AUTO, "4d530004.9", "1.3.0").enabled)
    assertEquals(approved.id, resolve(CompatibilityMode.AUTO).ruleId)
    for (key in listOf(null, "Halo.iso", "path:Halo", "4D530064.9", "4D530004.8", "00000000.9",
                       "4D530004.-1", "4D530004.4294967296", "4D530004.9.extra")) {
      assertFalse("Must reject $key", resolve(CompatibilityMode.AUTO, key).enabled)
    }
    for (version in listOf("", "1.2.0", "1.3.1", "1.3.0other")) assertFalse(resolve(CompatibilityMode.AUTO, version = version).enabled)
    assertFalse(resolve(CompatibilityMode.AUTO, rules = listOf(approved.copy(automatic = false))).enabled)
  }
  @Test fun missingOrWrongHashDoesNotMatchHashRestrictedRule() {
    val hash = "a".repeat(64)
    val rule = approved.copy(xbeHashes = setOf(hash))
    val identity = CompatibilityIdentity.fromGameKey(halo)!!
    assertFalse(rule.matches(identity, "1.3.0"))
    assertFalse(rule.matches(identity.copy(xbeSha256 = "b".repeat(64)), "1.3.0"))
    assertTrue(rule.matches(identity.copy(xbeSha256 = hash.uppercase()), "1.3.0"))
    // Current launch path supplies certificate ID/version only: hash-required rules stay off.
    assertFalse(resolve(CompatibilityMode.AUTO, rules = listOf(rule)).enabled)
  }
  @Test fun conflictingAutomaticRulesFailClosed() {
    assertFalse(resolve(CompatibilityMode.AUTO, rules = listOf(approved, approved.copy(id = "conflicting-rule"))).enabled)
  }
  @Test fun explicitOverridesTakePrecedenceEvenWithoutIdentity() {
    assertTrue(resolve(CompatibilityMode.ON, "path:unknown").enabled)
    assertEquals("pool=high", resolve(CompatibilityMode.ON).argument)
    assertFalse(resolve(CompatibilityMode.OFF).enabled)
    assertNull(resolve(CompatibilityMode.ON).ruleId)
    assertTrue(resolve(CompatibilityMode.OFF).reason.contains("Explicit"))
  }
  @Test fun legacyBooleanMigrationPreservesUserChoicesAndFailsClosed() {
    assertEquals(CompatibilityMode.AUTO, AdvancedProfile.fromJson(JSONObject()).poolMode)
    for ((old, expected) in listOf(true to CompatibilityMode.ON, false to CompatibilityMode.OFF)) {
      val migrated = AdvancedProfile.fromJson(JSONObject().put("poolHigh", old).put("ram128", true))
      assertEquals(expected, migrated.poolMode)
      assertTrue(migrated.ram128)
      assertEquals(migrated, AdvancedProfile.fromJson(migrated.toJson()))
      assertFalse(migrated.toJson().has("poolHigh"))
    }
    assertEquals(CompatibilityMode.OFF, AdvancedProfile.fromJson(JSONObject().put("poolMode", "typo").put("poolHigh", true)).poolMode)
    assertEquals(CompatibilityMode.AUTO, AdvancedProfile.fromJson(JSONObject().put("poolMode", "AUTO").put("poolHigh", true)).poolMode)
    for (mode in CompatibilityMode.entries) {
      val p = AdvancedProfile(poolMode = mode)
      assertEquals(p, AdvancedProfile.fromJson(p.toJson()))
      val arguments = p.arguments(halo, "1.3.0")
      assertEquals(1, arguments.count { it.startsWith("pool=") })
      assertTrue(arguments.contains(if (mode == CompatibilityMode.ON) "pool=high" else "pool=default"))
    }
  }
  @Test fun legacyPerGameChoicesAndOtherSettingsSurviveSaving() {
    val dir = kotlin.io.path.createTempDirectory("compat-migration").toFile()
    try {
      File(dir, "advanced.json").writeText(JSONObject()
        .put("global", JSONObject().put("poolHigh", true))
        .put("games", JSONObject().put(halo, JSONObject().put("poolHigh", false).put("fullX87", true)))
        .put("onlinePlay", true).toString())
      val store = AdvancedStore(dir)
      assertEquals(CompatibilityMode.OFF, store.profile(halo).poolMode)
      assertEquals(CompatibilityMode.ON, store.profile("other").poolMode)
      store.saveProfile(halo, store.profile(halo).copy(poolMode = CompatibilityMode.AUTO))
      assertTrue(store.profile(halo).fullX87)
      assertTrue(store.onlinePlay)
      assertEquals(CompatibilityMode.AUTO, AdvancedStore(dir).profile(halo).poolMode)
      store.saveProfile(null, AdvancedProfile())
      assertTrue(store.hasProfile(halo))
      store.resetProfile(halo)
      assertEquals(store.profile(), store.profile(halo))
    } finally { dir.deleteRecursively() }
  }
}
