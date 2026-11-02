package com.rfandango.haku_x.advanced

import android.view.KeyEvent
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdvancedTest {
  @Test fun startupDefaultsAndProfileRoundTrip() {
    val base = AdvancedProfile.fromJson(JSONObject())
    assertEquals(AdvancedProfile(), base)
    assertEquals(listOf("advanced=ram:64", "advanced=x87:64", "advanced=pc:1", "advanced=afp:1", "advanced=quirks:", "pool=default"), base.arguments())
    val custom = AdvancedProfile(true, true, false, false, mapOf("lrz_clear" to -1, "z_eq_w" to 1))
    assertEquals(custom, AdvancedProfile.fromJson(JSONObject(custom.toJson().toString())))
    assertEquals("-lrz_clear,+z_eq_w", custom.quirkSpec())
    val all = AdvancedProfile(quirks = AdvancedProfile.QUIRKS.keys.associateWith { 1 })
    assertTrue(all.quirkSpec().length > 91)
    assertEquals(all, AdvancedProfile.fromJson(all.toJson()))
    val invalid = JSONObject().put("quirks", JSONObject().put("lrz_clear", 42).put("invented", 1))
    assertTrue(AdvancedProfile.fromJson(invalid).quirks.isEmpty())
  }
  @Test fun poolPlacementSurvivesProfilesAndUsesExplicitLaunchArguments() {
    val old = AdvancedProfile.fromJson(JSONObject().put("ram128", true))
    assertFalse(old.poolHigh)
    assertTrue(old.ram128)
    assertTrue(old.arguments().contains("pool=default"))
    val high = old.copy(poolMode = CompatibilityMode.ON)
    assertEquals(high, AdvancedProfile.fromJson(high.toJson()))
    assertTrue(high.arguments().contains("pool=high"))
    assertFalse(high.arguments().contains("pool=default"))
    val dir = kotlin.io.path.createTempDirectory("pool-profile").toFile()
    try {
      val store = AdvancedStore(dir)
      store.saveProfile("halo", high)
      assertTrue(AdvancedStore(dir).profile("halo").poolHigh)
      assertFalse(AdvancedStore(dir).profile("other").poolHigh)
      store.resetProfile("halo")
      assertFalse(store.profile("halo").poolHigh)
    } finally { dir.deleteRecursively() }
  }
  @Test fun memoryInputIsCanonicalAndRangeChecked() {
    assertEquals("u32=8", MemoryCommand.search("u32", "0008"))
    assertEquals("u16=65535", MemoryCommand.search("u16", "0xFFFF"))
    assertEquals("u32=4294967295", MemoryCommand.search("u32", "4294967295"))
    assertEquals("f32=1.5", MemoryCommand.search("f32", "1.5"))
    assertEquals("peek=80001000:40", MemoryCommand.peek("0x80001000"))
    assertEquals("poke=80001000:ff:1", MemoryCommand.poke("80001000", "ff", 1))
    for (bad in listOf("-1", "4294967296", "1;other=1", "")) assertFails { MemoryCommand.search("u32", bad) }
    assertFails { MemoryCommand.search("u16", "65536") }
    assertFails { MemoryCommand.search("f32", "NaN") }
    assertFails { MemoryCommand.search("f32", "Infinity") }
    assertFails { MemoryCommand.peek("ffffffff") }
    assertFails { MemoryCommand.poke("ffffffff", "ff", 4) }
    assertFails { MemoryCommand.poke("1000", "100", 1) }
    assertFails { MemoryCommand.poke("1000", "ff", 3) }
  }
  @Test fun persistedProfilesFollowGlobalUntilExplicitlyOverridden() {
    val dir = java.nio.file.Files.createTempDirectory("advanced-profiles").toFile()
    try {
      val first = AdvancedStore(dir); val second = AdvancedStore(dir)
      val global = AdvancedProfile(ram128 = true)
      first.saveProfile(null, global)
      assertEquals(global, second.profile("game-a"))
      second.saveProfile("game-a", global.copy(fullX87 = true))
      first.saveProfile(null, AdvancedProfile(armAfp = false))
      assertTrue(first.profile("game-a").fullX87)
      assertFalse(second.profile("game-b").armAfp)
      first.resetProfile("game-a")
      assertFalse(second.hasProfile("game-a"))
      assertEquals(second.profile(), second.profile("game-a"))
      first.bind(EmulatorAction.QUICK_SAVE, KeyEvent.KEYCODE_F5)
      first.holdFastForward = true; first.quickSlot = 999
      assertEquals(KeyEvent.KEYCODE_F5, second.keys()[EmulatorAction.QUICK_SAVE])
      assertTrue(second.holdFastForward)
      assertEquals(10, second.quickSlot)
      second.resetProfile(null)
      assertEquals(AdvancedProfile(), first.profile())
      assertTrue(first.holdFastForward) // startup reset must preserve controls
      second.bind(EmulatorAction.QUICK_SAVE, null)
      assertTrue(first.keys().isEmpty())
    } finally { dir.deleteRecursively() }
  }
  @Test fun concurrentStoresDoNotLoseIndependentUpdates() {
    val dir = java.nio.file.Files.createTempDirectory("advanced-writes").toFile()
    try {
      val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
      val actions = listOf(EmulatorAction.QUICK_SAVE, EmulatorAction.QUICK_LOAD, EmulatorAction.PAUSE, EmulatorAction.SCREENSHOT)
      val threads = actions.mapIndexed { index, action -> Thread {
        try { repeat(20) { AdvancedStore(dir).bind(action, 100 + index) } } catch (e: Throwable) { failure.set(e) }
      }.apply { start() } }
      threads.forEach { it.join() }
      assertNull(failure.get())
      assertEquals(actions.size, AdvancedStore(dir).keys().size)
    } finally { dir.deleteRecursively() }
  }
  private fun assertFails(block: () -> Unit) {
    try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
  }
  @Test fun actionsConsumeBothEdgesAndIgnoreRepeatsAndCancellation() {
    val calls = mutableListOf<EmulatorAction>()
    val keys = EmulatorHotkeys({ calls += it }, {})
    keys.bindings = mapOf(EmulatorAction.QUICK_SAVE to KeyEvent.KEYCODE_F5, EmulatorAction.PAUSE to KeyEvent.KEYCODE_F6)
    fun event(code: Int, action: Int = KeyEvent.ACTION_DOWN, repeat: Int = 0, canceled: Boolean = false, enabled: Boolean = true) =
      keys.handle(code, 1, action, repeat, canceled, enabled)
    assertFalse(event(KeyEvent.KEYCODE_BUTTON_A))
    assertTrue(event(KeyEvent.KEYCODE_F5))
    assertTrue(event(KeyEvent.KEYCODE_F5, repeat = 1))
    assertTrue(event(KeyEvent.KEYCODE_F5))
    assertEquals(listOf(EmulatorAction.QUICK_SAVE), calls)
    assertTrue(event(KeyEvent.KEYCODE_F5, KeyEvent.ACTION_UP))
    event(KeyEvent.KEYCODE_F5)
    event(KeyEvent.KEYCODE_F6, canceled = true); event(KeyEvent.KEYCODE_F6, KeyEvent.ACTION_UP)
    event(KeyEvent.KEYCODE_F6, enabled = false); event(KeyEvent.KEYCODE_F6, KeyEvent.ACTION_UP)
    assertEquals(2, calls.size)
    event(KeyEvent.KEYCODE_F6)
    assertEquals(EmulatorAction.PAUSE, calls.last())
  }
  @Test fun heldFastForwardTracksDevicesAndRestoresOnFocusLoss() {
    val held = mutableListOf<Boolean>()
    val keys = EmulatorHotkeys({ fail("Hold must not toggle") }, { held += it })
    keys.bindings = mapOf(EmulatorAction.FAST_FORWARD to KeyEvent.KEYCODE_F9)
    keys.holdFastForward = true
    fun event(device: Int, action: Int, repeat: Int = 0) = keys.handle(KeyEvent.KEYCODE_F9, device, action, repeat, false, true)
    event(1, KeyEvent.ACTION_DOWN); event(1, KeyEvent.ACTION_DOWN, 2); event(2, KeyEvent.ACTION_DOWN)
    assertEquals(listOf(true), held)
    event(1, KeyEvent.ACTION_UP); assertEquals(listOf(true), held)
    event(2, KeyEvent.ACTION_UP); assertEquals(listOf(true, false), held)
    event(1, KeyEvent.ACTION_DOWN); keys.reset()
    assertEquals(listOf(true, false, true, false), held)
    event(1, KeyEvent.ACTION_DOWN, 4)
    assertEquals(4, held.size)
    event(1, KeyEvent.ACTION_UP); event(1, KeyEvent.ACTION_DOWN)
    assertEquals(true, held.last())
  }
  @Test fun unboundAndChangedMappingsDontTriggerOldActions() {
    var calls = 0
    val keys = EmulatorHotkeys({ calls++ }, {})
    keys.bindings = mapOf(EmulatorAction.FAST_FORWARD to KeyEvent.KEYCODE_F9)
    assertTrue(keys.handle(KeyEvent.KEYCODE_F9, 1, KeyEvent.ACTION_DOWN, 0, false, true))
    keys.reset(); keys.bindings = mapOf(EmulatorAction.SCREENSHOT to KeyEvent.KEYCODE_F8)
    assertFalse(keys.handle(KeyEvent.KEYCODE_F9, 1, KeyEvent.ACTION_DOWN, 0, false, true))
    assertTrue(keys.handle(KeyEvent.KEYCODE_F8, 1, KeyEvent.ACTION_DOWN, 0, false, true))
    assertEquals(2, calls)
    keys.reset(); keys.bindings = emptyMap()
    assertFalse(keys.handle(KeyEvent.KEYCODE_F8, 1, KeyEvent.ACTION_UP, 0, false, true))
  }
}
