package com.rfandango.haku_x.patches

import com.rfandango.haku_x.advanced.CompatibilityMode
import com.rfandango.haku_x.advanced.CompatibilityProfiles
import com.rfandango.haku_x.advanced.OnlinePlayPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OnlineCompatibilityTest {
  private val game = "FFFF0001.1"
  private val emulator = "1.3.0-advanced5.11-onlinecompat"
  private fun json(id: String = "test.compat", address: String = "0x11000") = JSONObject()
    .put("schema", 1).put("id", id).put("name", "Synthetic reviewed compatibility fixture")
    .put("version", "1").put("author", "Test").put("description", "Fixture, not a real online game fix")
    .put("titleId", "FFFF0001").put("revisions", JSONArray().put(1)).put("emulatorBase", "1.3.0")
    .put("patches", JSONArray().put(JSONObject().put("xbe", "default.xbe").put("address", address)
      .put("expected", "01 02").put("replacement", "03 04")))
  private fun entry(o: JSONObject = json()): PatchEntry {
    val bytes = o.toString().toByteArray();return PatchEntry(PatchPack.hash(bytes), PatchPack.parse(bytes), listOf("Fixture"))
  }
  private fun approve(e: PatchEntry) = OnlinePatchApproval(e.hash, OnlinePatchRegistry.semanticHash(e.pack),
    e.pack.id, e.pack.titleId, e.pack.revisions, e.pack.emulatorBase,
    "Synthetic test approval only", "unit-test fixture")
  private fun decision(e: PatchEntry, approvals: List<OnlinePatchApproval>, key: String = game, version: String = emulator) =
    PatchPlanner.plan(listOf(e), setOf(e.hash), key, version, online = true, onlineApprovals = approvals).single()
  private fun rejected(block: () -> Unit) {
    try { block();fail("Expected rejection") } catch (_: IllegalArgumentException) { }
  }
  @Test fun reviewedContentCanUseOnlineCompatibilityWhileTrainersRemainOff() {
    val e = entry();val registry = listOf(approve(e)); val policy = OnlinePlayPolicy(true)
    assertTrue(decision(e, registry).ready)
    assertFalse(policy.allowGuestMods);assertFalse(policy.allowMemoryTools);assertTrue(policy.allowStateLoad)
    assertEquals("nat", policy.networkMode("nat"))
    val lines = OnlineCompatibilityManifest.lines(listOf(e), game, emulator, registry)
    assertEquals(listOf("patch\tdefault.xbe\tp-${e.hash.take(32)}\t0x11000: 01 02 -> 03 04"), lines)
    assertTrue(lines.all { it.substringBefore('\t') == "patch" })
    assertFalse(lines.any { it.startsWith("etm\t") || it.startsWith("cheat\t") || it.startsWith("file\t") || it.startsWith("refresh\t") })
  }
  @Test fun editedFilesForgedHashFieldsAndWrongIdentityAreRejected() {
    val e = entry();val a = approve(e)
    val edited = entry(json().put("description", "Changed file"))
    assertFalse(decision(edited, listOf(a)).ready)
    assertFalse(decision(e.copy(pack = edited.pack), listOf(a)).ready) // preserve hash field, alter parsed content
    val op = e.pack.writes.single().copy(replacement = listOf(0, 0))
    assertFalse(decision(e.copy(pack = e.pack.copy(writes = listOf(op))), listOf(a)).ready)
    assertFalse(decision(e.copy(available = false), listOf(a)).ready)
    for (key in listOf("FFFF0001.2", "FFFF0002.1", "path:fixture.iso")) assertFalse(decision(e, listOf(a), key).ready)
    assertFalse(decision(e, listOf(a), version = "1.3.1").ready)
    assertFalse(decision(e, listOf(a.copy(rationale = ""))).ready)
    assertFalse(decision(e, listOf(a.copy(reviewReference = ""))).ready)
    assertFalse(decision(e, listOf(a.copy(revisions = setOf(1, 2)))).ready)
  }
  @Test fun protectedManifestIgnoresAllLegacyLinesEvenFromACallback() {
    val e = entry();val registry = listOf(approve(e))
    val legacy = listOf("etm\ttrainer.etm\t111", "cheat\t1\t0\tInfinite health", "code\tw32 0x11000 0",
      "file\tdefault.xbe\tmodified.xbe", "refresh\t120", "patch\tdefault.xbe\tunchecked\t0x11000: FF FF")
    val launch = PatchLaunch(loaded = listOf(e.hash), lines = legacy, loadedEntries = listOf(e))
    val lines = OnlineCompatibilityManifest.forLaunch(launch, game, emulator, registry)
    assertEquals(listOf("patch\tdefault.xbe\tp-${e.hash.take(32)}\t0x11000: 01 02 -> 03 04"), lines)
    assertTrue(OnlineCompatibilityManifest.forLaunch(PatchLaunch(lines = legacy), game, emulator).isEmpty())
    rejected { OnlineCompatibilityManifest.forLaunch(launch.copy(loaded = emptyList()), game, emulator, registry) }
  }
  @Test fun approvalRevocationAndAmbiguousRecordsFailClosed() {
    val e = entry();val a = approve(e)
    assertFalse(decision(e, emptyList()).ready)
    assertFalse(decision(e, listOf(a, a.copy(reviewReference = "duplicate"))).ready)
    rejected { OnlineCompatibilityManifest.lines(listOf(e), game, emulator, emptyList()) }
    rejected { OnlineCompatibilityManifest.lines(listOf(e, e), game, emulator, listOf(a)) }
    assertTrue(OnlineCompatibilityManifest.lines(emptyList(), game, emulator).isEmpty())
  }
  @Test fun unapprovedChoicesCannotPoisonReviewedPatchesOrEnterTheirManifest() {
    val good = entry();val unreviewed = entry(json("unreviewed")) // overlapping, but cannot load online
    val registry = listOf(approve(good))
    val decisions = PatchPlanner.plan(listOf(good, unreviewed), setOf(good.hash, unreviewed.hash), game, emulator,
      online = true, onlineApprovals = registry)
    assertTrue(decisions[0].ready);assertFalse(decisions[1].ready)
    rejected { OnlineCompatibilityManifest.lines(listOf(good, unreviewed), game, emulator, registry) }
    assertEquals(1, OnlineCompatibilityManifest.lines(listOf(good), game, emulator, registry).size)
  }
  @Test fun conflictsAndStateRestrictionsStillApplyToApprovedPatches() {
    val a = entry(json("one"));val b = entry(json("two", "0x11001"))
    val registry = listOf(approve(a), approve(b))
    rejected { OnlineCompatibilityManifest.lines(listOf(a, b), game, emulator, registry) }
    val state = PatchPlanner.plan(listOf(a), setOf(a.hash), game, emulator, online = true, stateLoad = true, onlineApprovals = registry).single()
    assertFalse(state.ready)
  }
  @Test fun fileMetadataCannotSelfApproveAndProductionRegistryDoesNotApproveFixtures() {
    for (field in listOf("onlineSafe", "approved", "compatibility", "registry", "onlinePlay")) {
      try { PatchPack.parse(json().put(field, true).toString().toByteArray()); fail("Unknown permission field must fail") } catch (_: IllegalArgumentException) { }
    }
    val e = entry();assertFalse(decision(e, OnlinePatchRegistry.approvals).ready)
    assertTrue(OnlinePatchRegistry.approvals.isEmpty())
    assertTrue(CompatibilityProfiles.resolvePool(CompatibilityMode.AUTO, "4D530004.8", emulator).enabled)
    assertFalse(OnlinePlayPolicy(true).allowGuestMods) // Halo does not need trainer mode to run its built-in fix
  }
  @Test fun sagaciousPatchCanForceOnForAnyJadeEmpireRevision() {
    val e = entry(json("sagacious").put("titleId", "4D53006D").put("compatibility", "sagacious").apply { remove("patches"); remove("revisions") })
    assertTrue(e.pack.sagacious)
    assertTrue(e.pack.writes.isEmpty())
    val jade = "4D53006D.1"
    val launch = PatchLaunch(loaded = listOf(e.hash), loadedEntries = listOf(e))
    assertTrue(launch.sagacious)
    assertFalse(launch.hasGuestWrites)
    assertTrue(PatchPlanner.plan(listOf(e), setOf(e.hash), jade, emulator, online = true, stateLoad = true).single().ready)
    assertTrue(OnlineCompatibilityManifest.forLaunch(launch, jade, emulator).isEmpty())
    assertFalse(PatchPlanner.plan(listOf(e), emptySet(), jade, emulator, online = true).single().ready)
    assertTrue(decision(e, emptyList(), "4D53006D.2").ready)
    for (key in listOf("4D530004.8", "path:jade.iso")) {
      assertFalse(decision(e, emptyList(), key).ready)
    }
  }
  @Test fun sagaciousRejectsOtherCompatibilityOptionsAndGuestWrites() {
    val o = json("sagacious").put("titleId", "4D53006D").put("compatibility", "sagacious")
    rejected { entry(o) }
    o.remove("patches")
    rejected { entry(JSONObject(o.toString()).put("compatibility", "unknown")) }
    rejected { entry(JSONObject(o.toString()).put("titleId", "4D530004")) }
    val scoped = entry(JSONObject(o.toString()).put("revisions", JSONArray().put(2)))
    assertFalse(decision(scoped, emptyList(), "4D53006D.1").ready)
    assertTrue(decision(scoped, emptyList(), "4D53006D.2").ready)
    assertTrue(runCatching { entry(json().apply { remove("revisions") }) }.isFailure)
  }
  @Test fun sagaciousDefaultsOnForJadeAndCanBeDisabledUnlessForced() {
    assertTrue(CompatibilityProfiles.useSagacious("4D53006D.1", enabled = true, forceOn = false))
    assertTrue(CompatibilityProfiles.useSagacious("4d53006d.2", enabled = true, forceOn = false))
    assertFalse(CompatibilityProfiles.useSagacious("4D53006D.1", enabled = false, forceOn = false))
    assertTrue(CompatibilityProfiles.useSagacious("4D53006D.1", enabled = false, forceOn = true))
    assertFalse(CompatibilityProfiles.useSagacious("4D530004.8", enabled = true, forceOn = true))
    assertFalse(CompatibilityProfiles.useSagacious("path:jade.iso", enabled = true, forceOn = true))
  }
  @Test fun jadeEmpireUsesTheSaferShaderPrebuildDefault() {
    assertFalse(CompatibilityProfiles.prebuildShadersDefault("4D53006D.1", true))
    assertFalse(CompatibilityProfiles.prebuildShadersDefault("4d53006d.2", true))
    assertTrue(CompatibilityProfiles.prebuildShadersDefault("4D530004.8", true))
    assertFalse(CompatibilityProfiles.prebuildShadersDefault("4D530004.8", false))
    assertTrue(CompatibilityProfiles.prebuildShadersDefault(null, true))
  }
  @Test fun semanticFingerprintIsDeterministicAndBindsOperationPayload() {
    val e = entry()
    val reconstructed = entry(JSONObject(json().toString()))
    assertEquals(OnlinePatchRegistry.semanticHash(e.pack), OnlinePatchRegistry.semanticHash(reconstructed.pack))
    assertNotEquals(OnlinePatchRegistry.semanticHash(e.pack), OnlinePatchRegistry.semanticHash(e.pack.copy(version = "2")))
  }
}
