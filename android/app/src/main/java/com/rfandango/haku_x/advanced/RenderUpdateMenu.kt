package com.rfandango.haku_x.advanced

import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.rfandango.haku_x.NativeCoreActivity
import kotlin.math.roundToInt

/** Pause-aware dialogs: no separate settings Activity or game restart is needed. */
class RenderUpdateMenu(private val activity: NativeCoreActivity,
  private val dialog: (AlertDialog.Builder) -> AlertDialog, private val game: () -> String) {
  private val store=ShaderStore(activity)
  private var busy=false
  private fun label(selection: ShaderSelection) = RenderUpdate.mode(selection)?.label ?: "Custom preset"
  fun show() {
    val key=game()
    val items=listOf<Pair<String,()->Unit>>(
      "This game · ${label(store.selected(key))}${if(store.hasOverride(key)) " · override" else " · using global"}" to { edit(key) },
      "Global defaults · ${label(store.selected(null))}" to { edit(null) },
      "Use global settings for this game" to { change { store.reset(key) } },
      "Shader status" to { dialog(AlertDialog.Builder(activity).setTitle("RenderUpdate · shader status")
        .setMessage(NativeCoreActivity.nativeShaderStatus()).setPositiveButton("Close",null)) }
    )
    dialog(AlertDialog.Builder(activity).setTitle("RenderUpdate")
      .setItems(items.map { it.first }.toTypedArray()) { _,n -> items[n].second() }
      .setNegativeButton("Back",null))
  }
  private fun edit(scope: String?) {
    val selected=store.selected(scope)
    var mode=RenderUpdate.mode(selected) ?: RenderUpdate.Mode.SHARPEN
    var strength=(selected.parameters[RenderUpdate.PARAMETER] ?: .25f).coerceIn(0f,1f)
    val pad=(16*activity.resources.displayMetrics.density).roundToInt()
    val content=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(pad,pad,pad,pad) }
    fun text(value: String) = TextView(activity).apply { text=value;textSize=14f;setPadding(0,pad/2,0,pad/2) }.also { content.addView(it) }
    text("Current: ${label(selected)}. Applying replaces the shader preset for this scope.")
    val group=RadioGroup(activity)
    val choices=RenderUpdate.Mode.entries.associateBy { View.generateViewId() }
    for((id,choice) in choices) group.addView(RadioButton(activity).apply { this.id=id;text=choice.label })
    group.check(choices.entries.first { it.value==mode }.key);content.addView(group)
    val strengthLabel=text("")
    val slider=SeekBar(activity).apply { max=100;progress=(strength*100).roundToInt() }
    content.addView(slider)
    fun updateStrength() { slider.isEnabled=mode.sharpens;strengthLabel.text="Sharpness: ${(strength*100).roundToInt()}%${if(mode.sharpens) "" else " · not used by this mode"}" }
    group.setOnCheckedChangeListener { _,id -> choices[id]?.let { mode=it;updateStrength() } }
    slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(bar:SeekBar,progress:Int,fromUser:Boolean) { if(fromUser) { strength=progress/100f;updateStrength() } }
      override fun onStartTrackingTouch(bar:SeekBar) {}
      override fun onStopTrackingTouch(bar:SeekBar) {}
    })
    updateStrength()
    text("SMAA uses 4 passes; SMAA + sharpening uses 5. Requires Vulkan 1.3. Native FXAA and SGSR presentation are bypassed while the preset is active.")
    if(scope==null && store.hasOverride(game())) text("The current game has an override. Use global settings for this game from the RenderUpdate menu to inherit these defaults.")
    val window=dialog(AlertDialog.Builder(activity).setTitle(if(scope==null) "RenderUpdate · global defaults" else "RenderUpdate · this game")
      .setView(ScrollView(activity).apply { addView(content) })
      .setPositiveButton("Apply",null).setNegativeButton("Cancel",null))
    window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
      val selection=RenderUpdate.selection(mode,strength)
      change {
        if(mode!=RenderUpdate.Mode.OFF) store.installRenderUpdate(restore=true)
        store.save(scope,selection)
      }
      window.dismiss()
    }
  }
  private fun change(action: () -> Unit) {
    if(busy) return
    busy=true
    val key=game()
    // Open the child first so the running game stays paused as its parent closes.
    val progress=dialog(AlertDialog.Builder(activity).setTitle("RenderUpdate")
      .setMessage("Applying graphics settings…").setCancelable(false))
    Thread {
      val result=runCatching {
        action();store.apply(key)
        // Reapplying the same preset also retries a previously failed shader chain.
        NativeCoreActivity.nativeRetryShader()
      }
      activity.runOnUiThread {
        busy=false
        if(activity.isFinishing || activity.isDestroyed) return@runOnUiThread
        result.onFailure { dialog(AlertDialog.Builder(activity).setTitle("RenderUpdate")
          .setMessage(it.message ?: "Cannot apply graphics settings").setPositiveButton("Close",null)) }
        progress.dismiss()
      }
    }.start()
  }
}
