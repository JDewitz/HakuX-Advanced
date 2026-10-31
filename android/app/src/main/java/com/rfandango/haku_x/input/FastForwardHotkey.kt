package com.rfandango.haku_x.input

import android.view.KeyEvent

/** One global Android keycode, consumed before Xbox input routing. */
class FastForwardHotkey {
  var keyCode: Int? = KeyEvent.KEYCODE_F9
    set(value) { field = value; down.clear() }
  private val down = HashSet<Int>() // device IDs: identical keyboards remain independent

  fun resetPressed() { down.clear() }

  fun handle(code: Int, device: Int, action: Int, repeat: Int, canceled: Boolean,
             enabled: Boolean, toggle: () -> Unit): Boolean {
    if (keyCode == null || code != keyCode) return false
    when (action) {
      KeyEvent.ACTION_DOWN -> {
        val first = down.add(device)
        if (first && repeat == 0 && !canceled && enabled) toggle()
      }
      KeyEvent.ACTION_UP -> down.remove(device)
    }
    return true
  }
}
