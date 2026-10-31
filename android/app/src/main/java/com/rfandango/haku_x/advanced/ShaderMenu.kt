package com.rfandango.haku_x.advanced

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import com.rfandango.haku_x.NativeCoreActivity

class ShaderMenu(private val activity: NativeCoreActivity,
  private val dialog: (AlertDialog.Builder) -> AlertDialog, private val game: () -> String) {
  private val store = ShaderStore(activity)
  private var busy = false
  private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
  private fun choice(title: String, items: List<Pair<String, () -> Unit>>) {
    dialog(AlertDialog.Builder(activity).setTitle(title).setItems(items.map { it.first }.toTypedArray()) { _, n -> items[n].second() }
      .setNegativeButton("Back", null))
  }
  fun show() {
    runCatching { store.installBuiltins() }.onFailure { toast("Could not install examples: ${it.message}") }
    val current = store.selected(game())
    choice("Slang shaders · ${current.preset.substringAfterLast('/').ifEmpty { "Off" }}", listOf(
      "Shader status" to { dialog(AlertDialog.Builder(activity).setTitle("Shader status")
        .setMessage(NativeCoreActivity.nativeShaderStatus()).setPositiveButton("Close", null)) },
      "Preset for this game${if (store.hasOverride(game())) " · override" else " · using global"}" to { select(game()) },
      "Global preset" to { select(null) },
      "Parameters for this game" to { parameters(game()) },
      "Global parameters" to { parameters(null) },
      "Use global settings for this game" to { change { store.reset(game()) } },
      "Import shader pack ZIP" to ::import,
      "Retry selected preset" to { NativeCoreActivity.nativeRetryShader(); store.apply(game()); toast("Retry queued; resume the game") },
      "Turn shaders Off for this game" to { change { store.save(game(), ShaderSelection()) } },
    ))
  }
  private fun change(action: () -> Unit) {
    runCatching { action(); store.apply(game()) }.onSuccess { toast("Saved. Resume to apply; check Shader status if unchanged.") }
      .onFailure { toast(it.message ?: "Could not save shader settings") }
  }
  private fun select(scope: String?) {
    choice(if (scope == null) "Global preset" else "Preset for this game",
      listOf("Off" to { change { store.save(scope, ShaderSelection()) } }) +
      store.presets().map { p -> p to { change { store.save(scope, ShaderSelection(p)) } } })
  }
  private data class Parameter(val name: String, val description: String, val initial: Float, val min: Float, val max: Float, val step: Float)
  private fun parameters(scope: String?) {
    if (busy) { toast("Shader operation in progress"); return }
    val selection = store.selected(scope)
    if (selection.preset.isEmpty()) { toast("Select a preset first"); return }
    busy = true; toast("Reading parameters…")
    Thread {
      val result = runCatching {
        val text = NativeCoreActivity.nativeShaderParameters(store.file(selection.preset).absolutePath)
          ?: error("Could not read preset; check sources and include paths")
        text.lines().filter { it.isNotBlank() }.mapNotNull { line ->
          val f = line.split('\t')
          if (f.size != 6) null else Parameter(f[0], f[1], f[2].toFloat(), f[3].toFloat(), f[4].toFloat(), f[5].toFloat())
        }
      }
      activity.runOnUiThread {
        busy = false
        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
        if (store.selected(scope) != selection) { toast("Settings changed; reopen Parameters"); return@runOnUiThread }
        result.onSuccess { params ->
          if (params.isEmpty()) toast("No adjustable parameters") else choice("Shader parameters", params.map { p ->
            val value = selection.parameters[p.name] ?: p.initial
            "${p.description}: $value" to { edit(scope, selection, p, value) }
          } + ("Reset parameters to preset defaults" to { change { store.save(scope, selection.copy(parameters = emptyMap())) } }))
        }.onFailure { toast(it.message ?: "Could not read parameters") }
      }
    }.start()
  }
  private fun edit(scope: String?, selection: ShaderSelection, p: Parameter, value: Float) {
    val input = EditText(activity).apply {
      inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
      setText(value.toString()); selectAll()
    }
    val window = dialog(AlertDialog.Builder(activity).setTitle(p.description).setMessage("Range ${p.min} – ${p.max}; step ${p.step}")
      .setView(input).setPositiveButton("Save", null).setNegativeButton("Cancel", null))
    window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
      val n = input.text.toString().toFloatOrNull()
      if (n == null || !n.isFinite() || n < p.min || n > p.max) input.error = "Enter a value within range" else {
        change { store.save(scope, selection.copy(parameters = selection.parameters + (p.name to n))) }; window.dismiss()
      }
    }
  }
  private fun import() {
    if (busy) { toast("Shader operation in progress"); return }
    runCatching { activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
      .addCategory(Intent.CATEGORY_OPENABLE), IMPORT_REQUEST) }.onFailure { toast("Could not open file picker") }
  }
  fun activityResult(request: Int, result: Int, data: Intent?): Boolean {
    if (request != IMPORT_REQUEST) return false
    val uri = if (result == Activity.RESULT_OK) data?.data else null
    if (uri != null && !busy) {
      busy = true; toast("Importing shader pack…")
      Thread {
        val outcome = runCatching { activity.contentResolver.openInputStream(uri)?.use(store::import) ?: error("Could not open ZIP") }
        activity.runOnUiThread {
          busy = false
          if (!activity.isFinishing && !activity.isDestroyed) toast(outcome.fold({ "Imported $it presets. Choose one from the preset menu." }, { "Import failed: ${it.message}" }))
        }
      }.start()
    }
    return true
  }
  companion object { const val IMPORT_REQUEST = 9012 }
}
