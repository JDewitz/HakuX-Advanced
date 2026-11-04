package com.rfandango.haku_x.advanced

import java.io.File
import java.util.UUID

/** Presets use the existing Vulkan Slang pipeline; no native feature is implied. */
object RenderUpdate {
  const val PACK = "renderupdate-v1"
  const val PARAMETER = "RU_Sharpness"
  const val REVISION = "2"
  enum class Mode(val label: String, val file: String?, val sharpens: Boolean, val passes: Int, val description: String) {
    OFF("Off · normal presentation", null, false, 0, "Uses the normal launch FXAA/upscaler settings."),
    SHARPEN("Adaptive sharpening", "sharpen.slangp", true, 1, "One output-sized pass. Adds local contrast; does not reconstruct missing artwork."),
    BICUBIC("Fast bicubic scaling", "bicubic.slangp", false, 1, "Five-sample spatial scaling. Try Balanced first; Crisp may ring around text."),
    FXAA("FXAA · single-pass AA", "fxaa.slangp", false, 1, "Output-sized edge smoothing. Lower search budgets and disabling subpixel smoothing preserve more detail at a quality tradeoff."),
    FSR_EASU("FSR 1 · EASU only", "fsr-easu.slangp", false, 1, "Edge-adaptive spatial upscaling, with the RCAS pass omitted. Intended for input smaller than the output."),
    FSR1("FSR 1 · EASU + RCAS", "fsr1.slangp", true, 2, "Spatial upscaling plus robust sharpening. Try a lower internal game scale to trade rendering cost for reconstructed output. No temporal data or generated frames."),
    FXAA_FSR1("FXAA + FSR 1", "fxaa-fsr1.slangp", true, 3, "FXAA runs at source resolution before EASU and RCAS. This reduces AA pixel work when the source is smaller than the display; it may soften HUD text."),
    SMAA("SMAA 1x", "smaa.slangp", false, 4, "Source-sized edge/search passes, followed by output-sized blending. Search budget trades edge coverage for less work."),
    SMAA_SHARPEN("SMAA 1x + sharpening", "smaa-sharpen.slangp", true, 5, "SMAA plus a final sharpening pass. The highest pass count in this batch.")
    ;
    val smaa get() = this == SMAA || this == SMAA_SHARPEN
    val fxaa get() = this == FXAA || this == FXAA_FSR1
    val rcas get() = this == FSR1 || this == FXAA_FSR1
    val budgeted get() = smaa || fxaa
  }
  enum class Budget(val label: String, val steps: Float, val diagonal: Float, val threshold: Float) {
    FAST("Fast", 8f, 4f, .15f), BALANCED("Balanced", 16f, 8f, .10f), QUALITY("Quality", 32f, 16f, .05f)
  }
  enum class Cubic(val label: String, val b: Float, val c: Float) {
    SOFT("Soft", 1f, 0f), BALANCED("Balanced", 1f/3f, 1f/3f), CRISP("Crisp", 0f, .5f)
  }
  fun selection(mode: Mode, amount: Float = .25f, budget: Budget = Budget.QUALITY,
    subpixel: Boolean = true, cubic: Cubic = Cubic.BALANCED): ShaderSelection {
    require(amount.isFinite() && amount in 0f..1f) { "Sharpness must be between 0 and 1" }
    val parameters = buildMap<String, Float> {
      if (mode.sharpens) put(if (mode.rcas) "FSR_SHARPENING" else PARAMETER, if (mode.rcas) 2f*(1f-amount) else amount)
      if (mode.rcas) put("FSR_FILMGRAIN", 0f)
      if (mode.smaa) { put("SMAA_MAX_SEARCH_STEPS", budget.steps); put("SMAA_MAX_SEARCH_STEPS_DIAG", budget.diagonal); put("SMAA_THRESHOLD", budget.threshold) }
      if (mode.fxaa) { put("RU_FXAA_STEPS", budget.steps); put("RU_FXAA_SUBPIX", if (subpixel) 1f else 0f) }
      if (mode == Mode.BICUBIC) { put("B", cubic.b); put("C", cubic.c) }
    }
    return mode.file?.let { ShaderSelection("$PACK/$it", parameters) } ?: ShaderSelection()
  }
  fun mode(selection: ShaderSelection): Mode? = if (selection.preset.isEmpty()) Mode.OFF else
    Mode.entries.firstOrNull { it.file != null && selection.preset == "$PACK/${it.file}" }
  fun strength(selection: ShaderSelection): Float = (if (mode(selection)?.rcas == true)
    1f - (selection.parameters["FSR_SHARPENING"] ?: 1.5f)/2f else selection.parameters[PARAMETER] ?: .25f).coerceIn(0f,1f)
  fun budget(selection: ShaderSelection): Budget {
    val steps = selection.parameters[if (mode(selection)?.smaa == true) "SMAA_MAX_SEARCH_STEPS" else "RU_FXAA_STEPS"] ?: 32f
    return Budget.entries.minBy { kotlin.math.abs(it.steps - steps) }
  }
  fun subpixel(selection: ShaderSelection) = (selection.parameters["RU_FXAA_SUBPIX"] ?: 1f) >= .5f
  fun cubic(selection: ShaderSelection) = Cubic.entries.minBy {
    kotlin.math.abs(it.b - (selection.parameters["B"] ?: 1f/3f)) + kotlin.math.abs(it.c - (selection.parameters["C"] ?: 1f/3f))
  }

  /** Fully stage bundled assets before publishing; deletion stays respected until explicitly restored. */
  internal fun install(root: File, restore: Boolean = false, copyAssets: (File) -> Unit) {
    val removed = File(root, ".$PACK-removed")
    if (removed.exists() && !restore) return
    val dest = File(root, PACK)
    if (runCatching { File(dest, ".complete").readText() == REVISION }.getOrDefault(false) && !restore) return
    check(root.isDirectory || root.mkdirs())
    val stage = File(root, ".renderupdate-${UUID.randomUUID()}").apply { check(mkdirs()) }
    try {
      copyAssets(stage)
      check(Mode.entries.mapNotNull { it.file }.all { File(stage,it).isFile }) { "Incomplete RenderUpdate pack" }
      File(stage,".complete").writeText(REVISION)
      if (dest.exists()) ShaderPackRemoval.delete(root, PACK)
      check(stage.renameTo(dest)) { "Cannot install RenderUpdate shaders" }
      if (removed.exists()) check(removed.delete()) { "Cannot restore RenderUpdate pack" }
    } finally { stage.deleteRecursively() }
  }
}
