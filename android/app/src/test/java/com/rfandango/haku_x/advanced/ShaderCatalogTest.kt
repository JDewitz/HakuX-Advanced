package com.rfandango.haku_x.advanced

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class ShaderCatalogTest {
  private val paths = listOf("libretro-abc/slang-shaders-master/crt/zebra.slangp",
    "libretro-abc/slang-shaders-master/crt/alpha.slangp", "libretro-abc/slang-shaders-master/crt/sub/child.slangp",
    "libretro-abc/slang-shaders-master/sharpen/cas.slangp", "builtin-v1/passthrough.slangp",
    "builtin-v1/sharpen.slangp", "imported/crt-extra.slangp")
  @Test fun browserShowsOnlyImmediateFoldersAndPresets() {
    val root = ShaderCatalog.entries(paths, "")
    assertEquals(listOf("builtin-v1", "imported", "libretro-abc"), root.map { it.name })
    assertTrue(root.all { it.directory })
    val crt = ShaderCatalog.entries(paths, "libretro-abc/slang-shaders-master/crt")
    assertEquals(listOf("sub", "alpha.slangp", "zebra.slangp"), crt.map { it.name })
    assertTrue(crt[0].directory); assertFalse(crt[1].directory)
    assertTrue(ShaderCatalog.entries(paths, "missing").isEmpty())
  }
  @Test fun skipsArchiveWrapperButKeepsCategoryNavigation() {
    val pack = ShaderCatalog.entries(paths, "").first { it.name == "libretro-abc" }
    val opened = ShaderCatalog.open(paths, pack)
    assertEquals("libretro-abc/slang-shaders-master", opened)
    assertEquals(listOf("crt", "sharpen"), ShaderCatalog.entries(paths, opened).map { it.name })
    assertEquals("", ShaderCatalog.up(opened))
    assertEquals(opened, ShaderCatalog.up("$opened/crt"))
  }
  @Test fun nextShaderStaysInFolderAndWraps() {
    val crt = "libretro-abc/slang-shaders-master/crt/"
    assertEquals(crt+"zebra.slangp", ShaderCatalog.next(paths, crt+"alpha.slangp"))
    assertEquals(crt+"alpha.slangp", ShaderCatalog.next(paths, crt+"zebra.slangp"))
    assertEquals(crt+"alpha.slangp", ShaderCatalog.next(paths, crt+"missing.slangp"))
    assertEquals("builtin-v1/passthrough.slangp", ShaderCatalog.next(paths, ""))
    assertNull(ShaderCatalog.next(emptyList(), ""))
    assertEquals("imported/crt-extra.slangp", ShaderCatalog.next(paths, "imported/crt-extra.slangp"))
  }
  @Test fun nextShaderMappingConsumesBothEdgesAndIgnoresHeldRepeats() {
    val activated = mutableListOf<EmulatorAction>()
    val keys = EmulatorHotkeys({ activated += it }, {})
    keys.bindings = mapOf(EmulatorAction.NEXT_SHADER to KeyEvent.KEYCODE_F8)
    assertTrue(keys.handle(KeyEvent.KEYCODE_F8, 1, KeyEvent.ACTION_DOWN, 0, false, true))
    assertTrue(keys.handle(KeyEvent.KEYCODE_F8, 1, KeyEvent.ACTION_DOWN, 1, false, true))
    assertTrue(keys.handle(KeyEvent.KEYCODE_F8, 1, KeyEvent.ACTION_UP, 0, false, true))
    assertEquals(listOf(EmulatorAction.NEXT_SHADER), activated)
    keys.handle(KeyEvent.KEYCODE_F8, 1, KeyEvent.ACTION_DOWN, 0, false, false)
    keys.reset(); assertEquals(1, activated.size)
  }
}
