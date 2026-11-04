package com.rfandango.haku_x.advanced

import java.io.File
import java.util.UUID

/** Presets use the existing Vulkan Slang pipeline; no native feature is implied. */
object RenderUpdate {
  const val PACK = "renderupdate-v1"
  const val PARAMETER = "RU_Sharpness"
  enum class Mode(val label: String, val file: String?, val sharpens: Boolean) {
    OFF("Off", null, false),
    SHARPEN("Adaptive sharpening", "sharpen.slangp", true),
    SMAA("SMAA 1x", "smaa.slangp", false),
    SMAA_SHARPEN("SMAA 1x + sharpening", "smaa-sharpen.slangp", true)
  }
  fun selection(mode: Mode, amount: Float = .25f): ShaderSelection {
    require(amount.isFinite() && amount in 0f..1f) { "Sharpness must be between 0 and 1" }
    return mode.file?.let { ShaderSelection("$PACK/$it", if (mode.sharpens) mapOf(PARAMETER to amount) else emptyMap()) }
      ?: ShaderSelection()
  }
  fun mode(selection: ShaderSelection): Mode? = if (selection.preset.isEmpty()) Mode.OFF else
    Mode.entries.firstOrNull { it.file != null && selection.preset == "$PACK/${it.file}" }

  /** Fully stage bundled assets before publishing; deletion stays respected until explicitly restored. */
  internal fun install(root: File, restore: Boolean = false, copyAssets: (File) -> Unit) {
    val removed = File(root, ".$PACK-removed")
    if (removed.exists() && !restore) return
    val dest = File(root, PACK)
    if (File(dest, ".complete").isFile && !restore) return
    check(root.isDirectory || root.mkdirs())
    val stage = File(root, ".renderupdate-${UUID.randomUUID()}").apply { check(mkdirs()) }
    try {
      copyAssets(stage)
      check(File(stage,"smaa.slangp").isFile && File(stage,"sharpen.slangp").isFile && File(stage,"smaa-sharpen.slangp").isFile)
      File(stage,".complete").writeText("1")
      if (dest.exists()) ShaderPackRemoval.delete(root, PACK)
      check(stage.renameTo(dest)) { "Cannot install RenderUpdate shaders" }
      if (removed.exists()) check(removed.delete()) { "Cannot restore RenderUpdate pack" }
    } finally { stage.deleteRecursively() }
  }
}
