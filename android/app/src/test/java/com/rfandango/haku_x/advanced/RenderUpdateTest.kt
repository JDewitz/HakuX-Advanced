package com.rfandango.haku_x.advanced

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RenderUpdateTest {
  private fun temp(block: (File) -> Unit) {
    val root=Files.createTempDirectory("renderupdate-test").toFile()
    try { block(root) } finally { root.deleteRecursively() }
  }
  private fun assets(stage: File) {
    for(name in RenderUpdate.Mode.entries.mapNotNull { it.file }) File(stage,name).writeText("preset")
    File(stage,"shaders/smaa/AreaTex.png").apply { parentFile!!.mkdirs();writeBytes(byteArrayOf(1,2,3)) }
  }
  @Test fun selectionsRoundTripAndOnlySharpenModesCarryTheParameter() {
    for(mode in RenderUpdate.Mode.entries) {
      val selection=RenderUpdate.selection(mode,.4f)
      assertEquals(mode,RenderUpdate.mode(ShaderSelection.parse(selection.json())))
      if(mode.sharpens) assertEquals(.4f,RenderUpdate.strength(selection),.0001f)
      else assertFalse(selection.parameters.containsKey(RenderUpdate.PARAMETER))
      if(!mode.rcas) assertFalse(selection.parameters.containsKey("FSR_SHARPENING"))
    }
    assertNull(RenderUpdate.mode(ShaderSelection("pack-other/crt.slangp")))
    for(value in listOf(Float.NaN,Float.POSITIVE_INFINITY,-.1f,1.1f)) {
      try { RenderUpdate.selection(RenderUpdate.Mode.SHARPEN,value);fail("Invalid strength accepted") } catch(_:IllegalArgumentException) {}
    }
  }
  @Test fun gameOverrideAndGlobalInheritanceRemainIndependent() = temp { root ->
    val store=AdvancedStore(root)
    val global=RenderUpdate.selection(RenderUpdate.Mode.SMAA_SHARPEN,.2f)
    store.saveShader(null,global);store.saveShader("game",RenderUpdate.selection(RenderUpdate.Mode.SHARPEN,.6f))
    assertEquals(global,store.shader("other"));assertEquals(.6f,store.shader("game").parameters[RenderUpdate.PARAMETER])
    store.saveShader("game",RenderUpdate.selection(RenderUpdate.Mode.OFF));assertEquals(ShaderSelection(),store.shader("game"))
    store.resetShader("game");assertEquals(global,store.shader("game"))
  }
  @Test fun deletedPackStaysDeletedUntilExplicitlyRestored() = temp { root ->
    RenderUpdate.install(root,copyAssets=::assets)
    val pack=File(root,RenderUpdate.PACK);assertTrue(File(pack,"shaders/smaa/AreaTex.png").isFile)
    ShaderPackRemoval.delete(root,RenderUpdate.PACK);File(root,".${RenderUpdate.PACK}-removed").writeText("")
    RenderUpdate.install(root) { fail("Automatically reinstalled deleted pack") }
    assertFalse(pack.exists())
    RenderUpdate.install(root,restore=true,copyAssets=::assets)
    assertTrue(File(pack,".complete").isFile);assertFalse(File(root,".${RenderUpdate.PACK}-removed").exists())
  }
  @Test fun failedStagingPreservesInstalledPackAndCleansTemporaryFiles() = temp { root ->
    RenderUpdate.install(root,copyAssets=::assets)
    val before=File(root,"${RenderUpdate.PACK}/smaa.slangp").readText()
    try { RenderUpdate.install(root,restore=true) { error("Copy failed") };fail("Expected copy failure") } catch(_:IllegalStateException) {}
    assertEquals(before,File(root,"${RenderUpdate.PACK}/smaa.slangp").readText())
    assertFalse(root.listFiles().orEmpty().any { it.name.startsWith(".renderupdate-") })
  }
  @Test fun budgetsAndSubpixelPersistPerGameWithoutLeakingIntoOtherModes() = temp { root ->
    val store=AdvancedStore(root)
    for(mode in listOf(RenderUpdate.Mode.SMAA,RenderUpdate.Mode.FXAA_FSR1)) {
      for(budget in RenderUpdate.Budget.entries) {
        val selection=RenderUpdate.selection(mode,.6f,budget,subpixel=false)
        store.saveShader("game",selection)
        val restored=AdvancedStore(root).shader("game")
        assertEquals(budget,RenderUpdate.budget(restored))
        if(mode.fxaa) assertFalse(RenderUpdate.subpixel(restored))
      }
    }
    val plain=RenderUpdate.selection(RenderUpdate.Mode.FSR_EASU,.6f,RenderUpdate.Budget.FAST,subpixel=false)
    assertTrue(plain.parameters.isEmpty())
    assertTrue(RenderUpdate.selection(RenderUpdate.Mode.OFF).parameters.isEmpty())
  }
  @Test fun rcasSliderMapsMonotonicallyAndDoesNotEnableFilmGrain() {
    val low=RenderUpdate.selection(RenderUpdate.Mode.FSR1,0f)
    val high=RenderUpdate.selection(RenderUpdate.Mode.FSR1,1f)
    assertEquals(2f,low.parameters["FSR_SHARPENING"]!!,0f)
    assertEquals(0f,high.parameters["FSR_SHARPENING"]!!,0f)
    assertEquals(0f,high.parameters["FSR_FILMGRAIN"]!!,0f)
    assertFalse(high.parameters.containsKey(RenderUpdate.PARAMETER))
    assertEquals(.25f,RenderUpdate.strength(ShaderSelection("${RenderUpdate.PACK}/sharpen.slangp")),0f)
    assertEquals(RenderUpdate.Budget.QUALITY,RenderUpdate.budget(ShaderSelection("${RenderUpdate.PACK}/smaa.slangp")))
  }
  @Test fun cubicShapesRoundTripWithoutExtraSharpening() {
    for(shape in RenderUpdate.Cubic.entries) {
      val selection=ShaderSelection.parse(RenderUpdate.selection(RenderUpdate.Mode.BICUBIC,cubic=shape).json())
      assertEquals(shape,RenderUpdate.cubic(selection))
      assertEquals(setOf("B","C"),selection.parameters.keys)
    }
  }
  @Test fun oldInstalledPackUpgradesOnceAndKeepsExistingPresetPaths() = temp { root ->
    val dest=File(root,RenderUpdate.PACK).apply { mkdirs() }
    File(dest,".complete").writeText("1");File(dest,"sharpen.slangp").writeText("old")
    RenderUpdate.install(root,copyAssets=::assets)
    assertEquals(RenderUpdate.REVISION,File(dest,".complete").readText())
    assertTrue(File(dest,"fsr1.slangp").isFile)
    RenderUpdate.install(root) { fail("Upgraded pack recopied unnecessarily") }
  }
  @Test fun incompleteUpgradeLeavesOldPackIntact() = temp { root ->
    RenderUpdate.install(root,copyAssets=::assets)
    val before=File(root,"${RenderUpdate.PACK}/sharpen.slangp").readText()
    try { RenderUpdate.install(root,restore=true) { File(it,"sharpen.slangp").writeText("partial") };fail("Incomplete pack accepted") } catch(_:IllegalStateException) {}
    assertEquals(before,File(root,"${RenderUpdate.PACK}/sharpen.slangp").readText())
  }

}
