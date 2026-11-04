package com.rfandango.haku_x.advanced

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import java.util.Locale
import kotlin.math.max

/** Steam Deck-style tiers, rendered only when the activity's overlay pacer allows it. */
class PerformanceHudView(context: Context) : View(context) {
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
  private val graph = Path()
  private val history = HudHistory()
  private var frame: HudFrame? = null
  private var host = HudHost()
  var level = 1
    set(value) { field = value.coerceIn(0, 4); visibility = if (field == 0) GONE else VISIBLE; requestLayout(); invalidate() }
  init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
  fun update(next: HudFrame?, system: HudHost, time: Long, running: Boolean) {
    frame = next; host = system
    if (running && next != null && next.fps > 0 && level == 4) history.add(time, next.lastMs)
    invalidate()
  }
  private fun dimensions() = when (level) { 1 -> 88f to 30f; 2 -> 454f to 48f; 3 -> 280f to 176f; else -> 300f to 278f }
  override fun onMeasure(w: Int, h: Int) {
    val (dw, dh) = dimensions(); val density = resources.displayMetrics.density
    val width = resolveSize((dw * density).toInt(), w)
    val height = resolveSize((dh * width / dw).toInt(), h)
    setMeasuredDimension(width, height)
  }
  private fun Canvas.text(value: String, x: Float, y: Float, color: Int = Color.WHITE, size: Float = 12f) {
    paint.color = color; paint.textSize = size; paint.style = Paint.Style.FILL; drawText(value, x, y, paint)
  }
  private fun number(v: Float?, digits: Int = 1) = v?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "--"
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val (dw, dh) = dimensions(); canvas.save(); canvas.scale(width / dw, height / dh)
    paint.color = Color.argb(215, 10, 14, 18); canvas.drawRect(0f, 0f, dw, dh, paint)
    val green = Color.rgb(149, 221, 72); val blue = Color.rgb(94, 185, 245); val purple = Color.rgb(195, 157, 238)
    val muted = Color.rgb(172, 183, 191); val yellow = Color.rgb(245, 212, 94)
    val fps = number(frame?.fps, 0); val avg = number(frame?.averageMs); val last = number(frame?.lastMs)
    if (level == 1) { canvas.text(fps, 9f, 22f, green, 21f); canvas.text("FPS", 59f, 21f, muted, 11f) }
    else if (level == 2) {
      canvas.text("$fps FPS", 8f, 19f, green, 15f)
      canvas.text("$avg ms", 100f, 19f)
      canvas.text("CPU APP ${number(host.cpu, 0)}%", 211f, 19f, blue)
      canvas.text("BAT ${host.battery ?: "--"}%", 363f, 19f, green)
      canvas.text("RAM ${number(host.usedGiB)}/${number(host.totalGiB)} GiB   APP ${number(host.rssMiB, 0)} MiB   BAT ${number(host.watts)} W", 8f, 39f, purple, 11f)
    } else {
      fun row(label: String, value: String, y: Float, color: Int) { canvas.text(label, 10f, y, color); canvas.text(value, 106f, y) }
      row("GPU", "N/A", 20f, green)
      row("CPU APP", "${number(host.cpu)} %", 42f, blue)
      row("SYS RAM", "${number(host.usedGiB)}/${number(host.totalGiB)} GiB", 64f, purple)
      row("APP RSS", "${number(host.rssMiB, 0)} MiB", 86f, purple)
      row("BATTERY", "${host.battery ?: "--"}%  ${number(host.watts)} W ${if (host.charging) "CHG" else ""}", 108f, green)
      canvas.text(fps, 10f, 145f, green, 29f); canvas.text("FPS", 76f, 144f, muted)
      canvas.text("$avg ms avg", 125f, 135f); canvas.text("$last ms last", 125f, 151f, muted, 11f)
      if (level == 3) canvas.text("HakuX · presentation stats", 10f, 168f, muted, 10f)
      else {
        canvas.text("BAT ${number(host.temperature)}°C · Thermal ${host.thermal}", 10f, 171f, muted, 11f)
        canvas.text("FRAME TIME · SAMPLED", 10f, 191f, yellow, 10f)
        val samples = history.snapshot(); val now = android.os.SystemClock.elapsedRealtime()
        val top = 201f; val bottom = 252f; val left = 10f; val right = 290f
        val ceiling = max(50f, (samples.maxOfOrNull { it.ms } ?: 0f) * 1.1f)
        for (ref in listOf(16.7f, 33.3f)) {
          val y = bottom - ref / ceiling * (bottom - top)
          paint.color = Color.argb(80, 170, 185, 190); paint.strokeWidth = 0.6f; canvas.drawLine(left, y, right, y, paint)
          canvas.text(number(ref), right - 28f, y - 2, muted, 8f)
        }
        graph.reset()
        var previousTime: Long? = null
        samples.filter { it.time >= now - 30_000 }.forEachIndexed { i, point ->
          val x = left + (point.time - (now - 30_000)) / 30_000f * (right - left)
          val y = bottom - point.ms / ceiling * (bottom - top)
          if (i == 0 || point.time - (previousTime ?: point.time) > 1500) graph.moveTo(x, y) else graph.lineTo(x, y)
          previousTime = point.time
        }
        paint.color = yellow; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.1f; canvas.drawPath(graph, paint); paint.style = Paint.Style.FILL
        canvas.text("30s window · ${number(ceiling, 0)} ms scale", 10f, 269f, muted, 10f)
      }
    }
    canvas.restore()
  }
}
