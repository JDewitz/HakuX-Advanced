package com.rfandango.haku_x.ui.theme

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ThemeCatalogTest {
  private fun withCatalog(block: (File, ThemeCatalog) -> Unit) {
    val dir = Files.createTempDirectory("hakux-themes").toFile()
    try { block(dir, ThemeCatalog(dir)) } finally { dir.deleteRecursively() }
  }
  private fun custom(name: String) = BuiltInThemes.xbox.copy(name = name)

  @Test fun importsSurviveNewInstancesAndSwitchingBackToPresets() = withCatalog { dir, catalog ->
    val a = custom("Dew"); val b = custom("Doritos")
    assertTrue(catalog.add(a)); assertTrue(catalog.add(b))
    val restored = ThemeCatalog(dir).read()
    assertEquals(setOf(a,b), restored.toSet())
    val menu = ThemeCatalog.choices(BuiltInThemes.halo, restored)
    assertTrue(menu.containsAll(listOf(a,b,BuiltInThemes.halo)))
    assertEquals(10, menu.size)
  }
  @Test fun repeatedImportsAndPresetsDoNotDuplicate() = withCatalog { _, catalog ->
    val a = custom("Dew")
    assertTrue(catalog.add(a)); assertFalse(catalog.add(ThemeSpec.parse(a.json())))
    assertFalse(catalog.add(BuiltInThemes.xbox))
    assertEquals(listOf(a), catalog.read())
    assertEquals(9, ThemeCatalog.choices(a, listOf(a,a)).size)
  }
  @Test fun nameCollisionsKeepBothPalettesAndDistinctMenuLabels() = withCatalog { _, catalog ->
    val a = custom("Xbox"); val b = a.copy(dark = BuiltInThemes.halo.dark)
    // a is exactly the built-in: it must not be stored as an imported duplicate.
    assertFalse(catalog.add(a)); assertTrue(catalog.add(b))
    val c = b.copy(light = BuiltInThemes.halo.light); assertTrue(catalog.add(c))
    val menu = ThemeCatalog.choices(BuiltInThemes.xbox,catalog.read())
    assertEquals(10, menu.size)
    assertEquals("Xbox", ThemeCatalog.label(BuiltInThemes.xbox,menu))
    assertNotEquals(ThemeCatalog.label(b,menu), ThemeCatalog.label(c,menu))
    assertNotEquals(ThemeCatalog.id(b),ThemeCatalog.id(c))
  }
  @Test fun corruptEntryAndInterruptedTempFileDoNotHideValidImports() = withCatalog { dir, catalog ->
    val good = custom("Valid"); catalog.add(good)
    File(dir,"broken.json").writeText("broken")
    File(dir,"interrupted.tmp").writeText(custom("Temp").json())
    File(dir,"oversize.json").writeBytes(ByteArray(ThemeSpec.MAX_BYTES+1))
    assertEquals(listOf(good), ThemeCatalog(dir).read())
  }
  @Test fun corruptDuplicateCanBeRepaired() = withCatalog { dir, catalog ->
    val good = custom("Valid"); catalog.add(good)
    File(dir,"${ThemeCatalog.id(good)}.json").writeText("broken")
    assertTrue(catalog.add(good)); assertEquals(listOf(good),catalog.read())
  }
  @Test fun namesDoNotBecomePathsAndOldSelectionCanBeRetained() = withCatalog { dir, catalog ->
    val old = custom("../Old theme")
    catalog.add(old)
    assertTrue(File(dir,"${ThemeCatalog.id(old)}.json").exists())
    assertTrue(ThemeCatalog.choices(BuiltInThemes.halo,catalog.read()).contains(old))
  }
  @Test fun invalidThemeCannotCreateCatalogFiles() = withCatalog { dir, catalog ->
    try { catalog.add(custom("bad\nname")); fail("Accepted invalid palette") }
    catch (_: IllegalArgumentException) {}
    assertTrue(dir.listFiles().orEmpty().isEmpty())
  }
}
