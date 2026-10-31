package com.rfandango.haku_x.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LibraryPresentationTest {
  @Test fun unknownSavedViewFallsBackToCards() {
    assertEquals(LibraryViewMode.CARDS, LibraryViewMode.parse(null))
    assertEquals(LibraryViewMode.CARDS, LibraryViewMode.parse("future-mode"))
    LibraryViewMode.entries.forEach { assertEquals(it, LibraryViewMode.parse(it.name)) }
  }

  @Test fun badgesRecognizeExplicitDefaultsAndLegacyOverridesButNotOtherGames() {
    assertTrue(LibraryMarkers.hasSettings("12345678.1", mapOf("12345678.1/uncapped" to "false")))
    assertTrue(LibraryMarkers.hasSettings("12345678.1", mapOf("12345678/render_scale" to "2")))
    assertFalse(LibraryMarkers.hasSettings("12345678.1", mapOf("12345679.1/render_scale" to "2")))
    assertFalse(LibraryMarkers.hasSettings("12345678.1", mapOf("render_scale" to "2")))
    assertFalse(LibraryMarkers.hasSettings("12345678.1", mapOf("12345678.1/unknown" to "2")))
  }

  @Test fun pathKeysKeepDotsAndSubdirectoriesIntact() {
    val key = "path:folder/Title.v2.iso"
    assertTrue(LibraryMarkers.hasSettings(key, mapOf("$key/aspect" to "16:9")))
    assertFalse(LibraryMarkers.hasSettings(key, mapOf("path:folder/Title/aspect" to "16:9")))
  }

  @Test fun onlyEnabledModsAndCheatsForThisExactGameCount() {
    val o = JSONObject().put("123.1/mod/a", false).put("123.10/mod/b", true)
    assertFalse(LibraryMarkers.hasEnabledMods("123.1", o))
    o.put("123.1/cheat/Health", true)
    assertTrue(LibraryMarkers.hasEnabledMods("123.1", o))
    o.remove("123.1/cheat/Health"); o.put("123.1/mod/a", true)
    assertTrue(LibraryMarkers.hasEnabledMods("123.1", o))
  }

  @Test fun unknownSizesAreExcludedAndHugeLibrariesDoNotOverflow() {
    assertEquals(30L, LibraryMarkers.knownBytes(listOf(-1L, 0L, 10L, 20L)))
    assertEquals(0L, LibraryMarkers.knownBytes(emptyList()))
    assertEquals(Long.MAX_VALUE, LibraryMarkers.knownBytes(listOf(Long.MAX_VALUE, 1L)))
  }
}
