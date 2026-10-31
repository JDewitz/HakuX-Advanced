package com.rfandango.haku_x.ui.theme

import org.json.JSONObject
import kotlin.math.pow

/** Portable, colors-only theme. No paths, scripts, fonts or remote assets. */
data class ThemePalette(val colors: Map<String, String>) {
  fun color(key: String): Int = (colors.getValue(key).drop(1).toLong(16) or 0xff000000L).toInt()
  fun on(key: String): Int = if (luminance(color(key)) > 0.179) 0xff000000.toInt() else 0xffffffff.toInt()
  fun json() = JSONObject().apply { KEYS.forEach { put(it, colors.getValue(it)) } }
  companion object {
    val KEYS = listOf("background", "surface", "raised", "text", "muted", "accent", "secondary", "outline")
    fun luminance(c: Int): Double {
      fun channel(shift: Int): Double {
        val v = ((c ushr shift) and 255) / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
      }
      return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
    fun contrast(a: Int, b: Int): Double {
      val x = luminance(a); val y = luminance(b)
      return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
    fun parse(o: JSONObject): ThemePalette {
      require(o.keys().asSequence().toSet() == KEYS.toSet()) { "Each palette needs exactly: ${KEYS.joinToString()}" }
      val p = ThemePalette(KEYS.associateWith { k ->
        val v = o.get(k)
        require(v is String && Regex("#[0-9a-fA-F]{6}").matches(v)) { "$k must be an opaque #RRGGBB color" }
        v.uppercase()
      })
      for (bg in listOf("background", "surface", "raised")) {
        require(contrast(p.color("text"), p.color(bg)) >= 4.5) { "Text needs 4.5:1 contrast against $bg" }
        require(contrast(p.color("muted"), p.color(bg)) >= 3.0) { "Muted text needs 3:1 contrast against $bg" }
      }
      return p
    }
  }
}

data class ThemeSpec(val name: String, val light: ThemePalette, val dark: ThemePalette) {
  fun palette(isDark: Boolean) = if (isDark) dark else light
  fun json(): String = JSONObject().put("version", 1).put("name", name)
    .put("light", light.json()).put("dark", dark.json()).toString(2)
  companion object {
    const val MAX_BYTES = 32768
    fun parse(text: String): ThemeSpec {
      require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Theme exceeds 32 KiB" }
      val o = JSONObject(text)
      require(o.keys().asSequence().toSet() == setOf("version", "name", "light", "dark")) { "Expected version, name, light and dark" }
      require(o.get("version") is Number && o.getDouble("version") == 1.0) { "Unsupported theme version" }
      val name = o.get("name")
      require(name is String && name.isNotBlank() && name.length <= 64 && name.none { it.isISOControl() }) { "Theme name must be 1–64 printable characters" }
      return ThemeSpec(name.trim(), ThemePalette.parse(o.getJSONObject("light")), ThemePalette.parse(o.getJSONObject("dark")))
    }
  }
}

object BuiltInThemes {
  private fun p(vararg values: String) = ThemePalette(ThemePalette.KEYS.zip(values.map { "#$it" }).toMap())
  val xbox = ThemeSpec("Xbox", p("FFFFFF","FAFAFA","E8E8E8","1C1B1F","5F6368","2E7D32","457A28","757575"),
    p("121212","1A1A1A","2B2B2B","E6E6E6","B4B8BC","81C784","A6D57A","6E7378"))
  val satisfactory = ThemeSpec("Satisfactory", p("F5F0E8","FFFCF7","E7DED1","272A30","59534A","A84900","796019","857667"),
    p("141B26","202B3B","2A3749","EDF2FA","AAB9CC","FFA144","E3B879","788BA4"))
  val halo = ThemeSpec("Halo inspired", p("EDF2E6","FAFDF6","DAE4D2","162D2B","4B6258","4C6527","18647A","75876B"),
    p("101B20","19282D","26373B","E5F5EA","A9C4BD","ADC776","75CFE7","6A8B80"))
  val aero = ThemeSpec("Frutiger Aero", p("E9F7FF","F7FDFF","D3EDF3","143A4A","365B6B","006B96","33731A","5D8999"),
    p("0D2435","14394B","1D4B5E","EFFBFF","BCDCE8","6EDCF5","B3E975","669EAD"))
  val midnight = ThemeSpec("Midnight", p("EFF1FA","FBFBFF","DFE4F3","20253D","535D7A","4D54A7","4C6291","7B84A0"),
    p("101323","191D33","272C48","ECEEFF","B7BEDD","B3AEFF","88C6E8","7883AF"))
  val amber = ThemeSpec("Amber Terminal", p("FFF6DF","FFFCF3","EBDDB8","392700","6B5524","855000","6B5B30","9C8659"),
    p("17130B","241E10","352C18","FFE8B0","D0B878","FFBC52","DCCC7A","A38C56"))
  val pocket = ThemeSpec("Pocket LCD", p("E6EDC5","F3F6DE","D1DCAC","24361C","4E623E","41662B","3D6551","7C925D"),
    p("121B10","1E2C18","2D3E23","E0ECC2","B4C993","B7D773","8EC7A0","7F9C5C"))
  val synthwave = ThemeSpec("Synthwave", p("FFF0FC","FFFAFF","EADAF2","391D4D","6C4D7D","9C246F","315B9E","A47BAF"),
    p("1B102A","281A3D","3B2650","FBEAFF","D7B8E8","FF8BCB","72D7EE","AA7BBE"))
  val all = listOf(xbox, satisfactory, halo, aero, midnight, amber, pocket, synthwave)

  /** Keep a custom active palette visible without confusing it with a built-in of the same name. */
  fun choices(selected: ThemeSpec): List<ThemeSpec> = if (selected in all) all else all + selected
}
