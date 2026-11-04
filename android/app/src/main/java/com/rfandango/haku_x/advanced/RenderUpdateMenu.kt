package com.rfandango.haku_x.advanced

import android.app.AlertDialog
import android.view.View
import android.widget.CheckBox
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
      "This game · ${label(store.renderSelected(key))}${if(store.renderHasOverride(key)) " · override" else " · using global"}" to { edit(key) },
      "Global defaults · ${label(store.renderSelected(null))}" to { edit(null) },
      "Use global settings for this game" to { change { store.resetRender(key) } },
      "Experimental stacking · ${if(store.allowStacking) "ON" else "OFF"}" to ::stacking,
      "Post-render shader · ${store.selected(key).preset.substringAfterLast('/').ifEmpty { "Off" }}" to { ShaderMenu(activity,dialog,game).show() },
      "Shader status" to { dialog(AlertDialog.Builder(activity).setTitle("RenderUpdate · shader status")
        .setMessage(NativeCoreActivity.nativeShaderStatus()).setPositiveButton("Close",null)) }
    )
    dialog(AlertDialog.Builder(activity).setTitle("RenderUpdate")
      .setItems(items.map { it.first }.toTypedArray()) { _,n -> items[n].second() }
      .setNegativeButton("Back",null))
  }
  fun stacking() {
    val enabled=store.allowStacking
    dialog(AlertDialog.Builder(activity).setTitle("Experimental rendering stack")
      .setMessage("Opt in to running RenderUpdate BEFORE the selected CRT/custom Slang preset in the same frame. This adds GPU work, memory use and shader compilation. It can lower FPS, increase heat or expose driver bugs. Unsupported presets are blocked and use the post-render preset alone.\n\nDefault is OFF. With both stages selected and stacking OFF, the post-render shader takes priority. Disabling this restores that single-stage policy. This is a global setting; existing per-game stage selections remain separate.\n\nCurrent: ${if(enabled) "ON" else "OFF"}")
      .setPositiveButton(if(enabled) "Disable stacking" else "I understand · enable") { _,_ -> change { store.allowStacking=!enabled } }
      .setNegativeButton("Cancel",null))
  }
  private fun edit(scope: String?) {
    val selected=store.renderSelected(scope)
    var mode=RenderUpdate.mode(selected) ?: RenderUpdate.Mode.SHARPEN
    var strength=RenderUpdate.strength(selected)
    var budget=RenderUpdate.budget(selected)
    var cubic=RenderUpdate.cubic(selected)
    val pad=(16*activity.resources.displayMetrics.density).roundToInt()
    val content=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(pad,pad,pad,pad) }
    fun text(value: String) = TextView(activity).apply { text=value;textSize=14f;setPadding(0,pad/2,0,pad/2) }.also { content.addView(it) }
    text("Current: ${label(selected)}. This configures the pre-shader stage. CRT/custom Slang is selected separately.")
    val group=RadioGroup(activity)
    val choices=RenderUpdate.Mode.entries.associateBy { View.generateViewId() }
    for((id,choice) in choices) group.addView(RadioButton(activity).apply { this.id=id;text=choice.label })
    group.check(choices.entries.first { it.value==mode }.key);content.addView(group)
    val details=text(mode.description)
    val budgetLabel=text("AA search budget")
    val budgetGroup=RadioGroup(activity).apply { orientation=RadioGroup.HORIZONTAL }
    val budgets=RenderUpdate.Budget.entries.associateBy { View.generateViewId() }
    for((id,b) in budgets) budgetGroup.addView(RadioButton(activity).apply { this.id=id;text=b.label })
    budgetGroup.check(budgets.entries.first { it.value==budget }.key);content.addView(budgetGroup)
    budgetGroup.setOnCheckedChangeListener { _,id -> budgets[id]?.let { budget=it } }
    val subpixel=CheckBox(activity).apply { text="FXAA subpixel smoothing · may soften fine detail";isChecked=RenderUpdate.subpixel(selected) }
    content.addView(subpixel)
    val cubicLabel=text("Bicubic shape")
    val cubicGroup=RadioGroup(activity).apply { orientation=RadioGroup.HORIZONTAL }
    val cubics=RenderUpdate.Cubic.entries.associateBy { View.generateViewId() }
    for((id,b) in cubics) cubicGroup.addView(RadioButton(activity).apply { this.id=id;text=b.label })
    cubicGroup.check(cubics.entries.first { it.value==cubic }.key);content.addView(cubicGroup)
    cubicGroup.setOnCheckedChangeListener { _,id -> cubics[id]?.let { cubic=it } }
    val strengthLabel=text("")
    val slider=SeekBar(activity).apply { max=100;progress=(strength*100).roundToInt() }
    content.addView(slider)
    fun updateStrength() {
      details.text="${mode.passes} passes · ${mode.description}"
      budgetLabel.visibility=if(mode.budgeted) View.VISIBLE else View.GONE;budgetGroup.visibility=budgetLabel.visibility
      subpixel.visibility=if(mode.fxaa) View.VISIBLE else View.GONE
      cubicLabel.visibility=if(mode==RenderUpdate.Mode.BICUBIC) View.VISIBLE else View.GONE;cubicGroup.visibility=cubicLabel.visibility
      slider.isEnabled=mode.sharpens;strengthLabel.text="Sharpness: ${(strength*100).roundToInt()}%${if(mode.sharpens) "" else " · not used by this mode"}" }
    group.setOnCheckedChangeListener { _,id -> choices[id]?.let { mode=it;updateStrength() } }
    slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(bar:SeekBar,progress:Int,fromUser:Boolean) { if(fromUser) { strength=progress/100f;updateStrength() } }
      override fun onStartTrackingTouch(bar:SeekBar) {}
      override fun onStopTrackingTouch(bar:SeekBar) {}
    })
    updateStrength()
    text("Requires Vulkan 1.3. Experimental stacking is ${if(store.allowStacking) "ON" else "OFF"}. With both stages selected and stacking OFF, post-render Slang takes priority. Enable stacking from Advanced to run both. Native FXAA/SGSR remain bypassed by the Slang path.")
    if(scope==null && store.renderHasOverride(game())) text("The current game has an override. Use global settings for this game from the RenderUpdate menu to inherit these defaults.")
    val window=dialog(AlertDialog.Builder(activity).setTitle(if(scope==null) "RenderUpdate · global defaults" else "RenderUpdate · this game")
      .setView(ScrollView(activity).apply { addView(content) })
      .setPositiveButton("Apply",null).setNegativeButton("Cancel",null))
    window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
      val selection=RenderUpdate.selection(mode,strength,budget,subpixel.isChecked,cubic)
      change {
        if(mode!=RenderUpdate.Mode.OFF) store.installRenderUpdate(restore=true)
        store.saveRender(scope,selection)
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
