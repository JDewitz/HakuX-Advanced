package com.rfandango.haku_x.advanced

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PerformanceHudTest {
  @Test fun legacyVisibilityAndRememberedLevelCooperate() {
    assertEquals(1, HudLevels.resolve(null, true))
    assertEquals(0, HudLevels.resolve(4, false))
    assertEquals(4, HudLevels.resolve(4, true))
    assertEquals(1, HudLevels.resolve(0, true))
    assertEquals(1, HudLevels.resolve(99, true))
  }
  @Test fun settingPersistsAndRejectsInvalidLevels() {
    val dir = Files.createTempDirectory("hud").toFile()
    try {
      val store = AdvancedStore(dir)
      assertNull(store.performanceHudLevel)
      store.performanceHudLevel = 4
      assertEquals(4, AdvancedStore(dir).performanceHudLevel)
      store.performanceHudLevel = 0
      assertEquals(0, AdvancedStore(dir).performanceHudLevel)
      try { store.performanceHudLevel = 5; fail("Invalid level accepted") } catch (_: IllegalArgumentException) {}
      assertEquals(0, AdvancedStore(dir).performanceHudLevel)
      store.performanceHudLevel = null
      assertNull(AdvancedStore(dir).performanceHudLevel)
    } finally { dir.deleteRecursively() }
  }
  @Test fun cpuUsesProcessTimeNormalizedAcrossCoresAndResetsAfterPause() {
    val tracker = HudCpuTracker()
    assertNull(tracker.sample(1000, 100, 8))
    assertEquals(12.5f, tracker.sample(2000, 1100, 8)!!, .001f)
    assertNull(tracker.sample(10000, 1200, 8))
    assertEquals(25f, tracker.sample(11000, 3200, 8)!!, .001f)
    assertNull(tracker.sample(11000, 3200, 8))
    assertNull(tracker.sample(12000, 100, 8))
    assertNull(tracker.sample(13000, 100, 0))
  }
  @Test fun historyBoundsSamplesAndRejectsInvalidPoints() {
    val history = HudHistory()
    history.add(1000, 20f); history.add(1000, 30f); history.add(999, 40f)
    history.add(1100, Float.NaN); history.add(1200, 0f)
    assertEquals(listOf(HudHistory.Point(1000, 20f)), history.snapshot())
    for (i in 1..200) history.add(1000 + i * 10L, 16f)
    assertEquals(160, history.snapshot().size)
    history.add(40000, 100f)
    assertEquals(listOf(HudHistory.Point(40000, 100f)), history.snapshot())
  }
  @Test fun nativeCountersRejectStartupAndInvalidDataButAcceptZeroFpsStall() {
    assertNull(HudFrame.fromNative(null))
    assertNull(HudFrame.fromNative(floatArrayOf(30f)))
    assertNull(HudFrame.fromNative(floatArrayOf(0f, 0f, 0f)))
    assertNull(HudFrame.fromNative(floatArrayOf(30f, Float.NaN, 33f)))
    assertNull(HudFrame.fromNative(floatArrayOf(-1f, 33f, 33f)))
    assertEquals(HudFrame(0f, 33f, 200f), HudFrame.fromNative(floatArrayOf(0f, 33f, 200f, 1f)))
  }
}
