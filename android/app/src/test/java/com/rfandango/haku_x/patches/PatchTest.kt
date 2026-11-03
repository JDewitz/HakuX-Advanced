package com.rfandango.haku_x.patches

import com.rfandango.haku_x.data.XboxDisc
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.io.RandomAccessFile

class PatchTest {
  private val game = "FFFF0001.1"
  private fun json(id: String = "test.patch", address: String = "0x11000") = JSONObject()
    .put("schema", 1).put("id", id).put("name", "Synthetic fixture").put("version", "1")
    .put("author", "Test").put("description", "Fixture only; not a game fix")
    .put("titleId", "FFFF0001").put("revisions", JSONArray().put(1)).put("emulatorBase", "1.3.0")
    .put("patches", JSONArray().put(JSONObject().put("xbe", "default.xbe").put("address", address)
      .put("expected", "01 02 03 04").put("replacement", "04 03 02 01")))
  private fun entry(o: JSONObject = json()): PatchEntry {
    val bytes = o.toString().toByteArray(); return PatchEntry(PatchPack.hash(bytes), PatchPack.parse(bytes), listOf("Fixture"))
  }
  private fun plan(entries: List<PatchEntry>, enabled: Set<String> = entries.map { it.hash }.toSet(), online: Boolean = false,
                   key: String? = game) = PatchPlanner.plan(entries, enabled, key, "1.3.0-advanced5.10-patches", online)
  private fun rejects(o: JSONObject) {
    try { PatchPack.parse(o.toString().toByteArray()); fail("Expected invalid manifest") } catch (_: Exception) { }
  }

  @Test fun parsesCanonicalGuardedOperationsAndExactIdentity() {
    val p = entry().pack
    assertEquals("0x11000: 01 02 03 04 -> 04 03 02 01", p.writes.single().line())
    assertTrue(p.matches("ffff0001.1", "1.3.0-advanced5.10-patches"))
    for (key in listOf("FFFF0001.2", "FFFF0002.1", "path:Synthetic fixture.iso", "00000000.1", null)) assertFalse(p.matches(key, "1.3.0"))
    assertFalse(p.matches(game, "1.3.1"))
    assertFalse(p.matches(game, "1.2.0"))
  }
  @Test fun rejectsUnsafeUnsupportedAndUnboundedInput() {
    for ((key, value) in listOf("schema" to 2, "id" to "x\nfile\tfoo", "titleId" to "00000000", "revisions" to JSONArray(),
                              "emulatorBase" to "1.2.0", "invented" to true)) rejects(json().put(key, value))
    for ((key, value) in listOf("xbe" to "../default.xbe", "xbe" to "\$u/default.xbe", "address" to "0xFFFFFFFF",
        "address" to "0x100000000", "address" to "[0x11000]", "expected" to "", "expected" to "01\n02",
        "replacement" to "00", "replacement" to "GG GG GG GG", "extra" to true)) {
      val o = json(); o.getJSONArray("patches").getJSONObject(0).put(key, value); rejects(o)
    }
    rejects(json().put("revisions", JSONArray().put(1.5)))
    rejects(json().put("revisions", JSONArray().put(-1)))
    rejects(json().put("conflicts", JSONArray().put("test.patch")))
    val overlap = json(); overlap.getJSONArray("patches").put(JSONObject(overlap.getJSONArray("patches").getJSONObject(0).toString())); rejects(overlap)
    try { PatchPack.parse(ByteArray(PatchPack.MAX_BYTES + 1)); fail("Expected byte limit") } catch (_: IllegalArgumentException) { }
  }
  @Test fun togglesPersistByGameRevisionAndContentWithoutAutoEnablingUpdates() {
    val dir = kotlin.io.path.createTempDirectory("patch-store").toFile()
    try {
      val store = PatchStore(dir); val bytes = json().toString().toByteArray(); val hash = store.import(bytes)
      assertEquals(hash, store.import(bytes)); assertEquals(setOf(hash), store.imported())
      store.toggle("ffff0001.1", hash, true)
      assertEquals(setOf(hash), PatchStore(dir).choices(game))
      assertTrue(store.choices("FFFF0001.2").isEmpty())
      val update = store.import(json().put("version", "2").toString().toByteArray())
      assertNotEquals(hash, update); assertFalse(update in store.choices(game))
      store.toggle(game, hash, false); assertTrue(PatchStore(dir).choices(game).isEmpty())
      java.io.File(dir, "$hash.json").appendText(" ")
      try { store.load(hash); fail("Changed cache must be rejected") } catch (_: IllegalArgumentException) { }
    } finally { dir.deleteRecursively() }
  }
  @Test fun plannersBlockOnlineUnavailableStatesAndIncompatibleChoices() {
    val e = entry(); assertTrue(plan(listOf(e)).single().ready)
    assertEquals("Off", plan(listOf(e), emptySet()).single().status)
    assertFalse(plan(listOf(e), online = true).single().ready)
    assertTrue(plan(listOf(e), online = true).single().status.contains("online"))
    assertFalse(plan(listOf(e.copy(available = false))).single().ready)
    assertFalse(plan(listOf(e), key = "FFFF0001.2").single().ready)
    assertFalse(plan(listOf(e), key = null).single().ready)
    for (d in listOf(
      PatchPlanner.plan(listOf(e), setOf(e.hash), game, "1.3.0", false, trainer = true).single(),
      PatchPlanner.plan(listOf(e), setOf(e.hash), game, "1.3.0", false, stateLoad = true).single(),
      PatchPlanner.plan(listOf(e), setOf(e.hash), game, "1.3.0", false, otherMods = true).single())) assertFalse(d.ready)
  }
  @Test fun overlappingDeclaredAndBuiltInConflictsNeverUseLastWriterWins() {
    val a = entry(json("a")); val b = entry(json("b", "0x11002")); val c = entry(json("c", "0x12000"))
    val result = plan(listOf(a, b, c)); assertFalse(result[0].ready); assertFalse(result[1].ready); assertTrue(result[2].ready)
    assertTrue(plan(listOf(a, b), setOf(a.hash))[0].ready)
    val declared = entry(json("d", "0x12000").put("conflicts", JSONArray().put("a")))
    assertTrue(plan(listOf(a, declared)).none { it.ready })
    val version = entry(json("a", "0x13000").put("version", "2"))
    assertTrue(plan(listOf(a, version)).none { it.ready })
    assertFalse(PatchPlanner.plan(listOf(a), setOf(a.hash), game, "1.3.0", false, builtIn = a.pack.writes).single().ready)
    assertTrue(plan(listOf(a, entry(json("adjacent", "0x11004")))).all { it.ready })
  }
  @Test fun preflightChecksEveryOperationWithoutMutatingDiscBytes() {
    val original = byteArrayOf(1, 2, 3, 4)
    assertTrue(PatchPlanner.preflight(entry().pack) { _, _ -> original })
    assertArrayEquals(byteArrayOf(1, 2, 3, 4), original)
    assertFalse(PatchPlanner.preflight(entry().pack) { _, _ -> byteArrayOf(1, 2, 3, 5) })
    assertFalse(PatchPlanner.preflight(entry().pack) { _, _ -> null })
  }
  @Test fun readsGuardedBytesThroughRealXisoAndXbeSectionMapping() {
    val dir = kotlin.io.path.createTempDirectory("patch-xiso").toFile()
    try {
      val bytes = ByteArray(0x20000); val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
      "MICROSOFT*XBOX*MEDIA".toByteArray().copyInto(bytes, 0x10000)
      b.putInt(0x10000 + 20, 40); b.putInt(0x10000 + 24, 64)
      val root = 40 * 2048; val start = 60 * 2048
      b.putInt(root + 4, 60); bytes[root + 13] = 11; "default.xbe".toByteArray().copyInto(bytes, root + 14)
      b.putInt(start, 0x48454258); b.putInt(start + 0x104, 0x10000); b.putInt(start + 0x108, 0x1000)
      b.putInt(start + 0x118, 0x10300); b.putInt(start + 0x308, 0xFFFF0001.toInt()); b.putInt(start + 0x3AC, 1)
      b.putInt(start + 0x11C, 1); b.putInt(start + 0x120, 0x10200)
      b.putInt(start + 0x204, 0x11000); b.putInt(start + 0x208, 0x100); b.putInt(start + 0x20C, 0x1000); b.putInt(start + 0x210, 0x100)
      byteArrayOf(1, 2, 3, 4).copyInto(bytes, start + 0x1000)
      val file = java.io.File(dir, "fixture.iso").apply { writeBytes(bytes) }
      RandomAccessFile(file, "r").use { raf ->
        assertEquals(0L, XboxDisc.partitionOf(raf.channel))
        val constructor = XboxDisc::class.java.getDeclaredConstructor(FileChannel::class.java, Long::class.javaPrimitiveType).apply { isAccessible = true }
        val disc = constructor.newInstance(raf.channel, 0L)
        assertEquals(0xFFFF0001L, disc.titleId); assertEquals(1L, disc.version)
        assertTrue(PatchPlanner.preflight(entry().pack, disc::readPatchBytes))
        assertNull(disc.readPatchBytes(0x10000, 4)) // header writes are forbidden
        assertNull(disc.readPatchBytes(0x110FE, 4)) // cross-section read is forbidden
        assertNull(disc.readPatchBytes(0x12000, 4)) // no file-backed section
      }
      assertArrayEquals(bytes, file.readBytes())
    } finally { dir.deleteRecursively() }
  }
  @Test fun oversizedStreamsAreRejectedAndShortStreamsPreserved() {
    val bytes = json().toString().toByteArray()
    assertArrayEquals(bytes, bytes.inputStream().readPatchBytes())
    try { ByteArray(PatchPack.MAX_BYTES + 1).inputStream().readPatchBytes(); fail("Expected stream limit") } catch (_: IllegalArgumentException) { }
  }
  @Test fun sessionLimitsFailClosedBeforeManifestConstruction() {
    val many = (0..32).map { entry(json("pack$it", "0x${(0x11000 + it * 4).toString(16)}")) }
    assertTrue(plan(many).none { it.ready })
    assertTrue(plan(many.take(32)).all { it.ready })
  }
  @Test fun independentWritersPreserveOtherGamesChoices() {
    val dir = kotlin.io.path.createTempDirectory("patch-locks").toFile()
    try {
      val store = PatchStore(dir); val hash = store.import(json().toString().toByteArray())
      val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
      val threads = (1..4).map { n -> Thread {
        try { repeat(10) { PatchStore(dir).toggle("FFFF0001.$n", hash, true) } } catch (e: Throwable) { failure.set(e) }
      }.apply { start() } }
      threads.forEach { it.join() }; assertNull(failure.get())
      for (n in 1..4) assertEquals(setOf(hash), PatchStore(dir).choices("FFFF0001.$n"))
    } finally { dir.deleteRecursively() }
  }
}
