package com.rfandango.haku_x.advanced

import android.view.KeyEvent

/** Discrete emulator actions; consume both edges before routing to Xbox input. */
class EmulatorHotkeys(private val activate: (EmulatorAction) -> Unit,
                      private val hold: (Boolean) -> Unit) {
  var bindings = emptyMap<EmulatorAction, Int>()
  var holdFastForward = false
  private val down = HashSet<Pair<Int, Int>>()
  private val held = HashSet<Pair<Int, Int>>()
  fun reset() {
    if (held.isNotEmpty()) hold(false)
    held.clear(); down.clear()
  }
  fun handle(code: Int, device: Int, action: Int, repeat: Int, canceled: Boolean, enabled: Boolean): Boolean {
    val mapped = bindings.entries.firstOrNull { it.value == code }?.key ?: return false
    val source = device to code
    when (action) {
      KeyEvent.ACTION_DOWN -> if (down.add(source) && repeat == 0 && !canceled && enabled) {
        if (mapped == EmulatorAction.FAST_FORWARD && holdFastForward) {
          if (held.isEmpty()) hold(true)
          held.add(source)
        } else activate(mapped)
      }
      KeyEvent.ACTION_UP -> {
        down.remove(source)
        if (held.remove(source) && held.isEmpty()) hold(false)
      }
    }
    return true
  }
}
