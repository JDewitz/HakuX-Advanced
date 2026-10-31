package com.rfandango.haku_x.ui.theme

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Framework dialogs and Compose use ThemeSpec, including controller focus states. */
object ThemeViews {
  const val CUSTOM = "hakux-themed-content"
  fun palette(context: Context) = ThemeStore.read(context).palette(AppTheme.isDark(context))
  private fun drawable(context: Context, fill: Int, stroke: Int, width: Int = 1) = GradientDrawable().apply {
    setColor(fill); cornerRadius = 8 * context.resources.displayMetrics.density
    setStroke((width * context.resources.displayMetrics.density).toInt().coerceAtLeast(1), stroke)
  }
  private fun focusBackground(context: Context, p: ThemePalette) = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), drawable(context, p.color("raised"), p.color("accent"), 3))
    addState(intArrayOf(), drawable(context, 0, 0))
  }
  fun button(b: Button, p: ThemePalette) {
    val enabled = android.R.attr.state_enabled; val focus = android.R.attr.state_focused; val pressed = android.R.attr.state_pressed
    b.backgroundTintList = null
    b.background = StateListDrawable().apply {
      addState(intArrayOf(-enabled), drawable(b.context, p.color("raised"), p.color("outline")))
      addState(intArrayOf(focus), drawable(b.context, p.color("accent"), p.color("text"), 3))
      addState(intArrayOf(pressed), drawable(b.context, p.color("accent"), p.color("text"), 3))
      addState(intArrayOf(), drawable(b.context, p.color("raised"), p.color("outline")))
    }
    b.setTextColor(ColorStateList(arrayOf(intArrayOf(-enabled), intArrayOf(focus), intArrayOf(pressed), intArrayOf()),
      intArrayOf(p.color("muted"), p.on("accent"), p.on("accent"), p.color("text"))))
  }
  fun toggle(v: Switch, p: ThemePalette) {
    v.background = focusBackground(v.context, p)
    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
    v.thumbTintList = ColorStateList(states, intArrayOf(p.color("accent"), p.color("muted")))
    v.trackTintList = ColorStateList(states, intArrayOf(p.color("outline"), p.color("raised")))
    v.setTextColor(p.color("text"))
  }
  fun dialog(d: AlertDialog, context: Context) {
    val p = palette(context)
    d.window?.setBackgroundDrawable(drawable(context, p.color("surface"), p.color("outline")))
    fun visit(v: View) {
      if (v.tag == CUSTOM) return
      when(v) {
        is Switch -> toggle(v, p)
        is CompoundButton -> { v.background = focusBackground(v.context, p); v.buttonTintList = ColorStateList.valueOf(p.color("accent")); v.setTextColor(p.color("text")) }
        is Button -> button(v, p)
        is TextView -> {
          v.setTextColor(p.color("text"))
          if (v is EditText) v.backgroundTintList = ColorStateList.valueOf(p.color("accent"))
          if (v is CheckedTextView) { v.background = focusBackground(v.context, p); v.checkMarkTintList = ColorStateList.valueOf(p.color("accent")) }
        }
      }
      if (v is SeekBar) { v.progressTintList = ColorStateList.valueOf(p.color("accent")); v.thumbTintList = ColorStateList.valueOf(p.color("accent")) }
      if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
    }
    d.window?.decorView?.let(::visit)
    d.listView?.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
      override fun onChildViewAdded(parent: View?, child: View?) { child?.let(::visit) }
      override fun onChildViewRemoved(parent: View?, child: View?) {}
    })
  }
}
