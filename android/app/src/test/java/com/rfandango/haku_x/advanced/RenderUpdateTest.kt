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
    for(name in listOf("smaa.slangp","sharpen.slangp","smaa-sharpen.slangp")) File(stage,name).writeText("preset")
    File(stage,"shaders/smaa/AreaTex.png").apply { parentFile!!.mkdirs();writeBytes(byteArrayOf(1,2,3)) }
  }
  @Test fun selectionsRoundTripAndOnlySharpenModesCarryTheParameter() {
    for(mode in RenderUpdate.Mode.entries) {
      val selection=RenderUpdate.selection(mode,.4f)
      assertEquals(mode,RenderUpdate.mode(ShaderSelection.parse(selection.json())))
      assertEquals(if(mode.sharpens) mapOf(RenderUpdate.PARAMETER to .4f) else emptyMap<String,Float>(),selection.parameters)
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
}
