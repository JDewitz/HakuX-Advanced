package com.rfandango.haku_x.advanced

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import java.io.File
import kotlin.math.abs

/** Called on a single worker at most once a second. Never performs I/O in onDraw. */
class PerformanceHudSampler(private val context: Context) {
  private val cpu = HudCpuTracker()
  fun sample(): HudHost {
    val usage = cpu.sample(SystemClock.elapsedRealtime(), Process.getElapsedCpuTime(), Runtime.getRuntime().availableProcessors())
    val rss = runCatching { File("/proc/self/status").useLines { lines ->
      lines.firstOrNull { it.startsWith("VmRSS:") }?.substringAfter(":")?.trim()?.substringBefore(" ")?.toFloatOrNull()?.div(1024f)
    } }.getOrNull()
    val memory = runCatching { ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) } }.getOrNull()
    val battery = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val voltage = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
    val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val current = runCatching { (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull()
    val thermal = runCatching {
      if (Build.VERSION.SDK_INT >= 29) listOf("Normal", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown")
        .getOrNull((context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus) ?: "N/A" else "N/A"
    }.getOrDefault("N/A")
    return HudHost(usage, rss,
      memory?.takeIf { it.totalMem > 0 }?.let { (it.totalMem - it.availMem).coerceAtLeast(0) / 1073741824f },
      memory?.takeIf { it.totalMem > 0 }?.let { it.totalMem / 1073741824f },
      if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null,
      current?.takeIf { it != Int.MIN_VALUE && voltage > 0 }?.let { abs(it.toDouble()).toFloat() * voltage / 1e9f },
      temp.takeIf { it != Int.MIN_VALUE }?.div(10f),
      status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL, thermal)
  }
}
