package com.rfandango.haku_x.advanced

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ShaderStackTest {
  private fun temp(block: (File) -> Unit) {
    val root=Files.createTempDirectory("shader-stack").toFile()
    try { block(root) } finally { root.deleteRecursively() }
  }
  private fun fixture(root: File): Pair<File,File> {
    File(root,"pre").mkdirs();File(root,"post/include").mkdirs()
    File(root,"pre/a.slang").writeText("// pre shader")
    File(root,"pre/pre.slangp").writeText("shaders = 1\nshader0 = a.slang\nscale_type0 = viewport\nparameters = RU_Sharpness\nRU_Sharpness = 0.25\n")
    File(root,"post/include/common.h").writeText("vec4 sourceSize=OriginalSize;\n")
    File(root,"post/a.slang").writeText("#include \"include/common.h\"\nuniform sampler2D Original;\n")
    File(root,"post/b.slang").writeText("uniform sampler2D PassOutput0;\nvec4 size=PassOutput0Size;\n")
    File(root,"post/lut.png").writeBytes(byteArrayOf(1,2,3))
    File(root,"post/base.slangp").writeText("shaders = 2\nshader0 = a.slang\nshader1 = b.slang\ntextures = LUT\nLUT = lut.png\nparameters = CRT_GAMMA\nCRT_GAMMA = 2.2\nfeedback_pass = 0\n")
    val post=File(root,"post/crt.slangp").apply { writeText("#reference \"base.slangp\"\nCRT_GAMMA = 2.4\n") }
    return File(root,"pre/pre.slangp") to post
  }
  @Test fun compositionRebasesPassesDependenciesAndOriginalInput() = temp { root ->
    val (pre,post)=fixture(root)
    val result=ShaderStack.build(root,pre,mapOf("RU_Sharpness" to .4f),post,mapOf("CRT_GAMMA" to 2.3f))
    val text=result.file.readText()
    assertTrue(text.contains("shaders = \"3\""));assertTrue(text.contains("feedback_pass = \"1\""))
    assertTrue(text.contains("CRT_GAMMA = \"2.4\""));assertTrue(text.contains(File(root,"post/lut.png").absolutePath))
    val copied=File(result.file.parentFile,"post/post/a.slang").readText()
    assertTrue(copied.contains("sampler2D HAKU_PreOutput"));assertTrue(copied.contains(result.file.parentFile.absolutePath))
    assertTrue(File(result.file.parentFile,"post/post/include/common.h").readText().contains("HAKU_PreOutputSize"))
    assertTrue(File(result.file.parentFile,"post/post/b.slang").readText().contains("PassOutput1Size"))
    assertEquals(mapOf("RU_Sharpness" to .4f,"CRT_GAMMA" to 2.3f),result.parameters)
    assertEquals(result.file,ShaderStack.build(root,pre,mapOf("RU_Sharpness" to .4f),post,mapOf("CRT_GAMMA" to 2.3f)).file)
    File(root,"post/include/common.h").appendText("// changed")
    assertNotEquals(result.file,ShaderStack.build(root,pre,mapOf("RU_Sharpness" to .4f),post,mapOf("CRT_GAMMA" to 2.3f)).file)
  }
  @Test fun unsafeReferencesAndUnsupportedHistoryAreRejected() = temp { root ->
    val (pre,post)=fixture(root)
    File(root,"post/a.slang").writeText("uniform sampler2D OriginalHistory0;")
    try { ShaderStack.build(root,pre,emptyMap(),post,emptyMap());fail("History accepted") } catch(_:IllegalArgumentException) {}
    File(root,"post/a.slang").writeText("#include \"/etc/passwd\"")
    try { ShaderStack.build(root,pre,emptyMap(),post,emptyMap());fail("Escape accepted") } catch(_:IllegalArgumentException) {}
  }
  @Test fun parameterConflictsAndPassLimitsAreRejected() = temp { root ->
    val (pre,post)=fixture(root)
    try { ShaderStack.build(root,pre,mapOf("CRT_GAMMA" to 1f),post,emptyMap());fail("Conflict accepted") } catch(_:IllegalArgumentException) {}
    post.writeText("shaders = 16\n"+(0..15).joinToString("\n") { "shader$it = a.slang" })
    try { ShaderStack.build(root,pre,emptyMap(),post,emptyMap());fail("Too many passes accepted") } catch(_:IllegalArgumentException) {}
  }
  @Test fun referenceCyclesAreRejectedAndCacheStaysBounded() = temp { root ->
    val (pre,post)=fixture(root)
    for(i in 0..7) ShaderStack.build(root,pre,mapOf("RU_Sharpness" to i/10f),post,emptyMap())
    assertEquals(4,File(root,".stack-cache").listFiles()!!.count { it.isDirectory })
    post.writeText("#reference \"crt.slangp\"")
    try { ShaderStack.build(root,pre,emptyMap(),post,emptyMap());fail("Cycle accepted") } catch(_:IllegalArgumentException) {}
  }
  @Test fun migrationPreservesOverridesAndDoesNotOptIn() = temp { root ->
    val store=AdvancedStore(root)
    val render=RenderUpdate.selection(RenderUpdate.Mode.FSR1,.3f)
    val crt=ShaderSelection("pack/crt.slangp")
    store.saveShader(null,render);store.saveShader("crt",crt);store.saveShader("off",ShaderSelection())
    store.migrateRenderStages()
    assertFalse(store.shaderStacking)
    assertEquals(render,store.renderShader(null));assertEquals(ShaderSelection(),store.shader(null))
    assertEquals(crt,store.shader("crt"));assertEquals(ShaderSelection(),store.renderShader("off"))
    store.shaderStacking=true;store.migrateRenderStages();assertTrue(store.shaderStacking)
    store.saveRenderShader("crt",RenderUpdate.selection(RenderUpdate.Mode.FXAA));assertTrue(store.hasRenderShader("crt"))
    store.resetRenderShader("crt");assertEquals(render,store.renderShader("crt"))
    store.clearShaderPack(RenderUpdate.PACK);assertEquals(ShaderSelection(),store.renderShader(null));assertEquals(crt,store.shader("crt"))
  }
  @Test fun everyBundledRenderModeCanPrecedeEveryBuiltinPostPreset() = temp { root ->
    val assets=listOf(File("src/main/assets"),File("app/src/main/assets"),File("android/app/src/main/assets")).first { it.isDirectory }
    File(assets,"renderupdate").copyRecursively(File(root,RenderUpdate.PACK))
    File(assets,"shaders").copyRecursively(File(root,"builtin-v1"))
    for(mode in RenderUpdate.Mode.entries.filter { it.file!=null }) for(postName in listOf("passthrough","scanlines","sharpen")) {
      val selection=RenderUpdate.selection(mode)
      val result=ShaderStack.build(root,File(root,selection.preset),selection.parameters,File(root,"builtin-v1/$postName.slangp"),emptyMap())
      assertTrue(result.file.readText().contains("shaders = \"${mode.passes+1}\""))
      assertTrue(File(result.file.parentFile,"post/builtin-v1/$postName.slang").isFile)
    }
  }

}
