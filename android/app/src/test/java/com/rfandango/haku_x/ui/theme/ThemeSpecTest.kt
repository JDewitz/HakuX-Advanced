package com.rfandango.haku_x.ui.theme

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ThemeSpecTest {
  private fun reject(text: String) {
    try { ThemeSpec.parse(text); fail("Invalid theme accepted") } catch (_: IllegalArgumentException) {} catch (_: org.json.JSONException) {}
  }
  @Test fun builtInsRoundTripAndStayReadable() {
    assertEquals(4, BuiltInThemes.all.size)
    BuiltInThemes.all.forEach { t -> assertEquals(t, ThemeSpec.parse(t.json())) }
  }
  @Test fun buttonForegroundIsReadable() {
    BuiltInThemes.all.forEach { t -> listOf(t.light,t.dark).forEach { p ->
      for (key in listOf("accent","secondary")) assertTrue(ThemePalette.contrast(p.color(key),p.on(key)) >= 4.5)
    } }
  }
  @Test fun acceptsEditedPaletteAndName() {
    val o=JSONObject(BuiltInThemes.xbox.json()).put("name","My theme")
    o.getJSONObject("dark").put("accent","#00ffff")
    val t=ThemeSpec.parse(o.toString());assertEquals("My theme",t.name);assertEquals("#00FFFF",t.dark.colors["accent"])
  }
  @Test fun rejectsWrongSchema() {
    val o=JSONObject(BuiltInThemes.xbox.json())
    reject(o.put("version",2).toString());reject(o.put("version",1).put("script","https://example.org").toString())
    reject("{}");reject("[]");reject("not json")
  }
  @Test fun rejectsMissingAndUnknownColors() {
    val o=JSONObject(BuiltInThemes.xbox.json());o.getJSONObject("dark").remove("text");reject(o.toString())
    val extra=JSONObject(BuiltInThemes.xbox.json());extra.getJSONObject("dark").put("image","x.png");reject(extra.toString())
  }
  @Test fun rejectsAlphaAndWrongTypes() {
    for (v in listOf<Any>("#ff000000","red","#xyzxyz",0,true)) {
      val o=JSONObject(BuiltInThemes.xbox.json());o.getJSONObject("dark").put("accent",v);reject(o.toString())
    }
  }
  @Test fun rejectsUnreadableText() {
    val o=JSONObject(BuiltInThemes.xbox.json());o.getJSONObject("dark").put("text","#121212");reject(o.toString())
    val m=JSONObject(BuiltInThemes.xbox.json());m.getJSONObject("dark").put("muted","#121212");reject(m.toString())
  }
  @Test fun importedStreamIsBounded() {
    val bytes=ByteArray(ThemeSpec.MAX_BYTES) { 65 }
    assertArrayEquals(bytes, bytes.inputStream().readBytesLimited())
    try { ByteArray(ThemeSpec.MAX_BYTES+1).inputStream().readBytesLimited(); fail("Oversize stream accepted") }
    catch (_: IllegalArgumentException) {}
  }
  @Test fun boundsAndNames() {
    reject(" ".repeat(ThemeSpec.MAX_BYTES+1))
    for (v in listOf("", "x".repeat(65), "bad\nname")) reject(JSONObject(BuiltInThemes.xbox.json()).put("name",v).toString())
  }
}
