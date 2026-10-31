package com.rfandango.haku_x.data

import org.junit.Assert.*
import org.junit.Test

class EtmTest {
  private fun put(d: ByteArray, p: Int, v: Long) { repeat(4) { d[p+it] = (v shr (8*it)).toByte() } }
  private fun fixture(): ByteArray = ByteArray(96).also { d ->
    listOf(0 to 96L, 6 to 35L, 10 to 44L, 14 to 45L, 18 to 36L, 22 to 35L,
      36 to 0x12345678L, 45 to 61L, 49 to 66L, 53 to 71L).forEach { (p, v) -> put(d,p,v) }
    d[4] = 1; d[35] = 0xc3.toByte()
    "Name\u0000Note\u0000Option\u0000".toByteArray().copyInto(d,61)
  }
  private fun rejected(d: ByteArray) {
    try { Etm.parse(d); fail("Malformed trainer accepted") } catch (_: IllegalArgumentException) { }
  }
  @Test fun labelsAndTitleGate() {
    val t = Etm.parse(fixture())
    assertEquals("Name",t.name); assertEquals("Note",t.note); assertEquals(listOf("Option"),t.labels)
    assertTrue(t.matches("12345678.6")); assertFalse(t.matches("00000000.0"))
    assertFalse(t.matches("87654321.6")); assertFalse(t.matches("garbage"))
  }
  @Test fun everyTruncationIsRejected() { val d=fixture(); for (n in d.indices) rejected(d.copyOf(n)) }
  @Test fun invalidOffsetsAndCounts() {
    for (p in listOf(0,6,10,14,18,22,26,45,49,53)) rejected(fixture().also { put(it,p,0xffffffffL) })
  }
  @Test fun unterminatedLabel() { rejected(fixture().also { it.fill(65,71) }) }
  @Test fun oversizedAndXbtfRejected() {
    rejected(ByteArray(Etm.MAX_SIZE+1)); rejected(fixture().also { put(it,26,35) })
  }
  @Test fun terminatorIsRequired() { rejected(fixture().also { put(it,57,71) }) }
  @Test fun archiveSamplesWhenAvailable() {
    val dir = System.getenv("HAKUX_ETM_SAMPLES") ?: return
    val files = java.io.File(dir).listFiles().orEmpty().filter { it.extension.equals("etm", true) }
    assertTrue(files.isNotEmpty())
    files.forEach { assertTrue(Etm.parse(it.readBytes()).labels.isNotEmpty()) }
    assertEquals(22,Etm.parse(java.io.File(dir,"evt-scda.etm").readBytes()).labels.size)
  }
  @Test fun paddedOptionCapacityIsAccepted() {
    val original = fixture()
    val d = original.copyOfRange(0,45) + byteArrayOf(0) + original.copyOfRange(45,original.size)
    put(d,0,97); put(d,14,46)
    put(d,46,62); put(d,50,67); put(d,54,72)
    assertEquals(1,Etm.parse(d).labels.size)
  }
}
