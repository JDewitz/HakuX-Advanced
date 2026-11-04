package com.rfandango.haku_x.advanced

/** Native frame snapshots, not a per-frame trace or an emulation-speed estimate. */
data class HudFrame(val fps: Float, val averageMs: Float, val lastMs: Float) {
  companion object {
    fun fromNative(s: FloatArray?): HudFrame? = s?.takeIf { it.size >= 3 && it.take(3).all { v -> v.isFinite() && v >= 0 } && it.take(3).any { v -> v > 0 } }
      ?.let { HudFrame(it[0], it[1], it[2]) }
  }
}
object HudLevels {
  val labels = arrayOf("0 · Off", "1 · FPS only", "2 · Compact bar", "3 · Detailed panel", "4 · Full panel + frame-time graph")
  fun resolve(saved: Int?, enabled: Boolean) = if (!enabled) 0 else saved?.takeIf { it in 1..4 } ?: 1
}
class HudCpuTracker {
  private var previous: Pair<Long, Long>? = null
  fun sample(now: Long, cpu: Long, cores: Int): Float? {
    val old = previous; previous = now to cpu
    if (old == null || now - old.first !in 1..4000 || cpu < old.second || cores < 1) return null
    return ((cpu - old.second) * 100f / (now - old.first) / cores).coerceIn(0f, 100f)
  }
}
class HudHistory {
  data class Point(val time: Long, val ms: Float)
  private val points = ArrayDeque<Point>()
  fun add(time: Long, ms: Float) {
    if (!ms.isFinite() || ms <= 0 || (points.lastOrNull()?.time ?: Long.MIN_VALUE) >= time) return
    points.addLast(Point(time, ms))
    while (points.size > 160 || points.first().time < time - 30_000) points.removeFirst()
  }
  fun snapshot() = points.toList()
}
data class HudHost(
  val cpu: Float? = null, val rssMiB: Float? = null, val usedGiB: Float? = null,
  val totalGiB: Float? = null, val battery: Int? = null, val watts: Float? = null,
  val temperature: Float? = null, val charging: Boolean = false, val thermal: String = "N/A",
)

/** Native status is the authority: a requested preset may still be pending or have failed. */
object HudRenderStatus {
  fun line(status: String, stackBlocked: Boolean = false): String {
    val value=status.trim()
    return when {
      value.equals("Off",ignoreCase=true) -> "FX: Native presentation"
      value.contains("Using normal presentation",ignoreCase=true) -> "FX: Native · shader fallback"
      value.startsWith("Pending",ignoreCase=true) -> "FX: Pending · resume to apply"
      value.startsWith("Active: ",ignoreCase=true) -> {
        val name=value.substringAfter(":").trim().substringAfterLast('/').substringAfterLast('\\')
          .replace(Regex("[\\p{Cntrl}\\s]+")," ").trim().take(120)
        if(name.isEmpty()) "FX: Shader status unknown" else if(stackBlocked) "FX: Stack blocked · $name" else {
          val parts=name.removeSuffix(".slangp").split("--")
          if(parts.size==4 && parts[0]=="stack") "FX: ${parts[1]} → ${parts[2]}" else "FX: $name"
        }
      }
      else -> "FX: Shader status unknown"
    }
  }
}
