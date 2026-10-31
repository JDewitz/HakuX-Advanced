package com.rfandango.haku_x.advanced
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
class ShaderTest {
  private fun zip(vararg files: Pair<String,String>) = ByteArrayOutputStream().also { b ->
    ZipOutputStream(b).use { z -> files.forEach { (n,c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
  }.toByteArray()
  private fun dir(action: (java.io.File)->Unit) {
    val f=Files.createTempDirectory("shader-test").toFile(); try { action(f) } finally { f.deleteRecursively() }
  }
  private fun rejects(action: ()->Unit) { try { action(); fail("Expected validation failure") } catch (_: IllegalArgumentException) {} }
  @Test fun preservesSharedIncludesAndTextures() = dir { root ->
    ShaderPack.extract(ByteArrayInputStream(zip("crt/x.slangp" to "shader0=../shared/pass.slang", "shared/pass.slang" to "#version 450", "lut/a.png" to "image")),root)
    assertEquals("#version 450", java.io.File(root,"shared/pass.slang").readText())
    assertTrue(java.io.File(root,"lut/a.png").isFile)
  }
  @Test fun rejectsUnsafePaths() {
    for (path in listOf("../x.slangp","/x.slangp","C:/x.slangp","x\\a.slangp","x/../a.slangp","./x.slangp"))
      dir { root -> rejects { ShaderPack.extract(ByteArrayInputStream(zip(path to "invalid")),root) } }
  }
  @Test fun requiresPresetsAndBoundsEntries() = dir { root ->
    rejects { ShaderPack.extract(ByteArrayInputStream(zip("readme" to "empty")),root) }
    val entries=(0..ShaderPack.MAX_ENTRIES).map { "file$it.slangp" to "" }.toTypedArray()
    rejects { ShaderPack.extract(ByteArrayInputStream(zip(*entries)),root) }
  }
  @Test fun perGameOffMasksGlobalAndResetRestoresInheritance() = dir { root ->
    val a=AdvancedStore(root);val b=AdvancedStore(root)
    val global=ShaderSelection("builtin-v1/scanlines.slangp",mapOf("Amount" to 0.4f))
    a.saveShader(null,global);assertEquals(global,b.shader("game"))
    a.saveShader("game",ShaderSelection());assertTrue(b.hasShader("game"));assertEquals("",b.shader("game").preset)
    b.saveShader(null,global.copy(parameters=mapOf("Amount" to 0.7f)));assertEquals("",a.shader("game").preset)
    a.resetShader("game");assertFalse(b.hasShader("game"));assertEquals(0.7f,b.shader("game").parameters["Amount"])
    assertEquals(AdvancedProfile(),a.profile("game"))
  }
}
