package com.rfandango.haku_x.input

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class FastForwardHotkeyTest {
  @Test fun consumesOnlyConfiguredKeyAndTogglesOncePerPress() {
    val key = FastForwardHotkey()
    var toggles = 0
    fun event(code: Int = KeyEvent.KEYCODE_F9, action: Int = KeyEvent.ACTION_DOWN,
              repeat: Int = 0, canceled: Boolean = false, enabled: Boolean = true, device: Int = 1) =
      key.handle(code, device, action, repeat, canceled, enabled) { toggles++ }
    assertFalse(event(code = KeyEvent.KEYCODE_BUTTON_A))
    assertTrue(event())
    assertTrue(event(repeat = 1))
    assertTrue(event()) // duplicate down must not toggle
    assertEquals(1, toggles)
    assertTrue(event(action = KeyEvent.ACTION_UP))
    event()
    assertEquals(2, toggles)
    event(action = KeyEvent.ACTION_UP)
    event(canceled = true)
    event(action = KeyEvent.ACTION_UP)
    event(enabled = false)
    event(action = KeyEvent.ACTION_UP)
    assertEquals(2, toggles)
    key.keyCode = KeyEvent.KEYCODE_BUTTON_L1
    assertFalse(event())
    assertTrue(event(code = KeyEvent.KEYCODE_BUTTON_L1))
    assertEquals(3, toggles)
    key.keyCode = null
    assertFalse(event(code = KeyEvent.KEYCODE_BUTTON_L1))
    assertEquals(3, toggles)
  }

  @Test fun devicesAndLostReleaseAreIndependent() {
    val key = FastForwardHotkey()
    var toggles = 0
    fun press(device: Int, repeat: Int = 0) = key.handle(KeyEvent.KEYCODE_F9, device,
      KeyEvent.ACTION_DOWN, repeat, false, true) { toggles++ }
    press(1); press(2)
    assertEquals(2, toggles)
    key.resetPressed()
    press(1, repeat = 2) // focus returned while held: wait for a fresh press
    press(1)
    assertEquals(2, toggles)
    key.resetPressed()
    press(1)
    assertEquals(3, toggles)
  }
}
