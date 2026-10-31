package com.rfandango.haku_x.advanced

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.rfandango.haku_x.BuildConfig
import com.rfandango.haku_x.NativeCoreActivity
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The live tools use the activity's existing pause-aware menu window. */
class AdvancedMenu(
  private val activity: NativeCoreActivity,
  private val show: (AlertDialog.Builder) -> AlertDialog,
  private val capture: (((Bitmap?) -> Unit) -> Unit),
  private val game: () -> String,
  private val paused: () -> Boolean,
  private val launchDescription: () -> String,
  private val fastForward: () -> Boolean,
) {
  private val store = AdvancedStore(activity)
  private val handler = Handler(Looper.getMainLooper())
  private val root get() = File(activity.getExternalFilesDir(null) ?: activity.filesDir, "advanced").apply { mkdirs() }
  private var token = 0L
  private var filters = ""
  private var bench: StringBuilder? = null
  private var started = 0L
  private var warmup = 0L
  private var samples = 0
  private var fpsSum = 0.0
  private var exporting = false
  private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
  private fun property(name: String, value: String): Boolean {
    val ok = NativeCoreActivity.nativeSetAdvanced("debug.hakux.$name", value)
    if (!ok) toast("Command rejected: value too long or memory queue is full")
    return ok
  }
  private fun command(name: String) = property(name, "${SystemClock.elapsedRealtimeNanos()}-${token++}")
  private fun choice(title: String, items: List<Pair<String, () -> Unit>>) {
    show(AlertDialog.Builder(activity).setTitle(title).setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
      .setNegativeButton("Back", null))
  }
  fun show() = choice("Advanced & experimental", listOf(
    "Startup profile for this game · restart required" to ::startup,
    "GPU driver workaround overrides · restart required" to ::quirks,
    "Live presentation filters" to ::presentation,
    "Record 10-second benchmark" to ::recordBenchmark,
    "Save screenshot" to ::screenshot,
    "Memory search & cheat tools" to ::memoryMenu,
    "Dump next frame's render targets" to { command("rtdump"); toast("Queued; resume the game, then export Advanced files") },
    "Dump a texture by ID" to ::texture,
    "Export Advanced files as ZIP" to ::export,
  ))
  private fun startup() {
    val p = store.profile(game())
    val labels = arrayOf("128 MB Xbox RAM", "Full 80-bit x87", "Honor reduced-x87 precision control", "Arm alternate floating-point optimization")
    val values = booleanArrayOf(p.ram128, p.fullX87, p.precisionControl, p.armAfp)
    show(AlertDialog.Builder(activity).setTitle("This game · changes require restart")
      .setMultiChoiceItems(labels, values) { _, index, checked -> values[index] = checked }
      .setPositiveButton("Save for this game") { _, _ ->
        runCatching { store.saveProfile(game(), p.copy(ram128 = values[0], fullX87 = values[1], precisionControl = values[2], armAfp = values[3])) }
          .onSuccess { toast("Profile saved. Restart the game to apply it.") }.onFailure { toast(it.message ?: "Save failed") }
      }.setNeutralButton("Use global") { _, _ ->
        runCatching { store.resetProfile(game()) }.onSuccess { toast("Global profile will apply at the next start") }.onFailure { toast("Reset failed") }
      }.setNegativeButton("Cancel", null))
  }
  private fun quirks() = choice("Driver overrides · next start", AdvancedProfile.QUIRKS.map { (name, label) ->
    val state = when (store.profile(game()).quirks[name]) { 1 -> "On"; -1 -> "Off"; else -> "Auto" }
    "$label: $state" to {
      choice(label, listOf(0 to "Auto", 1 to "Force on", -1 to "Force off").map { (n, text) ->
        text to {
          val p = store.profile(game())
          runCatching { store.saveProfile(game(), p.copy(quirks = if (n == 0) p.quirks - name else p.quirks + (name to n))) }
            .onSuccess { toast("Saved for the next game start") }.onFailure { toast(it.message ?: "Save failed") }
        }
      })
    }
  })
  private fun presentation() = choice("Live filters · temporary override", listOf(
    "Force FXAA on" to { filters = filters.replace("nofxaa", "").replace("fxaa", "") + " fxaa"; property("present", filters.trim()) },
    "Force FXAA off" to { filters = filters.replace("nofxaa", "").replace("fxaa", "") + " nofxaa"; property("present", filters.trim()) },
    "Use SGSR upscaling" to { filters = filters.replace("bilinear", "").replace("sgsr", "") + " sgsr"; property("present", filters.trim()) },
    "Use bilinear upscaling" to { filters = filters.replace("bilinear", "").replace("sgsr", "") + " bilinear"; property("present", filters.trim()) },
    "Disable averaged downsampling" to { filters = filters.replace("noaverage", "") + " noaverage"; property("present", filters.trim()) },
    "Restore averaged downsampling" to { filters = filters.replace("noaverage", ""); property("present", filters.trim()) },
    "Restore all launch filter settings" to { filters = ""; property("present", "") },
  ))
  fun screenshot() {
    capture { bitmap ->
      if (bitmap == null) { toast("No frame could be captured"); return@capture }
      Thread {
        val result = runCatching {
          val file = File(root, "screenshot-${System.currentTimeMillis()}.png")
          val tmp = File(root, file.name + ".tmp")
          tmp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
          check(tmp.renameTo(file))
          file.name
        }
        bitmap.recycle()
        activity.runOnUiThread { toast(result.fold({ "Saved $it. Use Export Advanced files." }, { "Screenshot failed: ${it.message}" })) }
      }.start()
    }
  }
  fun onMenuOpened() { if (started != 0L) stopBenchmark("menu opened") }
  fun onPause() { stopBenchmark("activity paused") }
  fun recordBenchmark() {
    if (bench != null) { toast("A benchmark is already recording"); return }
    started = 0; warmup = 0; samples = 0; fpsSum = 0.0
    bench = StringBuilder("# hakuX ${BuildConfig.VERSION_NAME}\n# device=${Build.MANUFACTURER} ${Build.MODEL}\n# game=${game()}\n# launch=${launchDescription()}\n# filters=$filters\n# fast_forward=${fastForward()}\n# sampled native frame statistics, not individual frame events\nelapsed_ms,fps,avg_frame_ms,last_frame_ms,gpu_busy_ms,fast_forward\n")
    toast("Benchmark armed. Resume the game; recording begins after a brief warmup.")
    handler.postDelayed(sample, 250)
  }
  private val sample = object : Runnable {
    override fun run() {
      val csv = bench ?: return
      if (paused()) { handler.postDelayed(this, 100); return }
      val now = SystemClock.elapsedRealtime()
      if (warmup == 0L) warmup = now
      if (now - warmup < 750) { handler.postDelayed(this, 100); return }
      if (started == 0L) {
        started = now
        property("perfsec", "10"); command("perf")
      }
      val s = NativeCoreActivity.nativeGetFrameStats()
      if (s != null && s.size >= 3) {
        csv.append(String.format(Locale.ROOT, "%d,%.3f,%.3f,%.3f,%d,%s\n", now - started, s[0], s[1], s[2], NativeCoreActivity.nativeGpuBusyMs(), fastForward()))
        samples++; fpsSum += s[0]
      }
      if (now - started >= 10000) stopBenchmark("complete") else handler.postDelayed(this, 100)
    }
  }
  fun stopBenchmark(reason: String) {
    val text = bench ?: return
    if (started != 0L && reason != "complete") property("perf", "stop")
    bench = null; handler.removeCallbacks(sample)
    val elapsed = if (started == 0L) 0 else SystemClock.elapsedRealtime() - started
    val summary = "benchmark $reason; ${elapsed}ms, $samples samples, mean sampled FPS " + String.format(Locale.ROOT, "%.2f", if (samples > 0) fpsSum / samples else 0.0)
    text.append("# $summary\n"); started = 0
    Thread {
      val result = runCatching {
        val file = File(root, "benchmark-${System.currentTimeMillis()}.csv")
        val tmp = File(root, file.name + ".tmp"); tmp.writeText(text.toString()); check(tmp.renameTo(file))
      }
      Log.i("hakuX-advanced", summary)
      activity.runOnUiThread { toast(if (result.isSuccess) "$summary. Use Export Advanced files." else "Benchmark save failed") }
    }.start()
  }
  private fun prompt(title: String, hint: String, run: (String) -> Unit) {
    val input = EditText(activity).apply { this.hint = hint; setSingleLine(true) }
    val dialog = show(AlertDialog.Builder(activity).setTitle(title).setView(input).setPositiveButton("Queue & resume", null).setNegativeButton("Cancel", null))
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
      runCatching { run(input.text.toString()) }.onSuccess { dialog.dismiss() }.onFailure { input.error = it.message }
    }
  }
  private fun queueMemory(text: String) {
    if (!property("memscan", "$text,${SystemClock.elapsedRealtimeNanos()}")) return
    toast("Queued. It runs after you resume; reopen Memory results afterward.")
  }
  private fun memoryMenu() = choice("Memory search & cheat tools", listOf(
    "Snapshot RAM" to { queueMemory("snap") },
    "Compare snapshot for frame counters" to { queueMemory("diff") },
    "Search unsigned 16-bit value" to { prompt("Find u16", "Decimal or 0x hexadecimal") { queueMemory(MemoryCommand.search("u16", it)) } },
    "Search unsigned 32-bit value" to { prompt("Find u32", "Decimal or 0x hexadecimal") { queueMemory(MemoryCommand.search("u32", it)) } },
    "Search 32-bit float" to { prompt("Find float", "Decimal number") { queueMemory(MemoryCommand.search("f32", it)) } },
    "Peek 64 bytes at address" to { prompt("Peek memory", "Hexadecimal address") { queueMemory(MemoryCommand.peek(it)) } },
    "Write memory once (poke)" to ::poke,
    "Find rigid 4×4 matrices" to { queueMemory("mats") },
    "View memory results" to ::results,
  ))
  private fun poke() {
    val layout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 0, 24, 0) }
    val addr = EditText(activity).apply { hint = "Hex address"; setSingleLine() }
    val value = EditText(activity).apply { hint = "Hex value"; setSingleLine() }
    val width = EditText(activity).apply { hint = "Bytes: 1, 2 or 4"; setText("4"); setSingleLine() }
    layout.addView(addr); layout.addView(value); layout.addView(width)
    val dialog = show(AlertDialog.Builder(activity).setTitle("One-time memory write").setView(layout).setPositiveButton("Review write", null).setNegativeButton("Cancel", null))
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
      runCatching { MemoryCommand.poke(addr.text.toString(), value.text.toString(), width.text.toString().toIntOrNull() ?: 0) }
        .onFailure { value.error = it.message }.onSuccess { cmd ->
          show(AlertDialog.Builder(activity).setTitle("Write this value?").setMessage("$cmd\n\nThis changes guest RAM once and can affect saves or game state. Save a state first if you need to undo it.")
            .setPositiveButton("Write & resume") { _, _ -> queueMemory(cmd); dialog.dismiss() }.setNegativeButton("Cancel", null))
        }
    }
  }
  private fun results() {
    val text = NativeCoreActivity.nativeAdvancedReport().ifEmpty { "No memory results yet. Queue a command, resume the game briefly, then reopen this window." }
    val view = TextView(activity).apply { this.text = text; setTextIsSelectable(true); setPadding(16, 16, 16, 16); typeface = android.graphics.Typeface.MONOSPACE }
    show(AlertDialog.Builder(activity).setTitle("Recent memory results · last 32 KiB").setView(ScrollView(activity).apply { addView(view) }).setPositiveButton("Close", null))
  }
  private fun texture() = prompt("Dump texture on its next upload", "Texture ID from renderer logs (decimal)") {
    val n = it.trim().toLongOrNull(); require(n != null && n in 1..0xffffffffL) { "Enter a positive decimal texture ID" }
    check(File(activity.filesDir, "native/hle/hle_shadow").let { dir -> dir.isDirectory || dir.mkdirs() }) { "Could not create dump directory" }
    check(property("texdump", "$n,${token++}")) { "Rejected" }; toast("Queued. Resume the game and cause that texture to upload, then export Advanced files.")
  }
  private fun export() {
    if (exporting) return
    exporting = true
    runCatching { activity.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
      .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "hakuX-advanced-${System.currentTimeMillis()}.zip"), EXPORT_REQUEST) }
      .onFailure { exporting = false; toast("Could not open file picker") }
  }
  fun activityResult(request: Int, result: Int, data: Intent?): Boolean {
    if (request != EXPORT_REQUEST) return false
    exporting = false
    val uri = if (result == android.app.Activity.RESULT_OK) data?.data else null
    if (uri != null) Thread {
      val done = runCatching {
        AdvancedArtifacts.export(activity, uri, NativeCoreActivity.nativeAdvancedReport())
      }
      activity.runOnUiThread { toast(done.fold({ "Advanced files exported" }, { "Export failed: ${it.message}" })) }
    }.start()
    return true
  }
  companion object { const val EXPORT_REQUEST = 9011 }
}
