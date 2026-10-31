package com.rfandango.haku_x.data

import android.view.KeyEvent
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FastForwardConfigTest {
  @Test fun legacyConfigsDefaultToF9() {
    assertEquals(KeyEvent.KEYCODE_F9, Controllers.fastForwardKeyFromJson(JSONObject()))
  }

  @Test fun customAndClearedMappingsRoundTrip() {
    for (code in listOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_F8, null)) {
      val encoded = Controllers.Config(fastForwardKey = code).toJson().toString()
      assertEquals(code, Controllers.fastForwardKeyFromJson(JSONObject(encoded)))
    }
    assertNull(Controllers.fastForwardKeyFromJson(JSONObject().put("fastForwardKey", -1)))
  }
}
