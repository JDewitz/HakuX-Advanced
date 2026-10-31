package com.rfandango.haku_x

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.rfandango.haku_x.data.SaveStateStore
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Two-pane pause window. All slots stay in the existing per-game store. */
class PauseMenu(
  private val activity: NativeCoreActivity,
  private val states: SaveStates,
  private val available: Boolean,
  private val show: (AlertDialog.Builder) -> AlertDialog,
  private val fastForward: () -> Boolean,
  private val setFastForward: (Boolean) -> Unit,
  private val actions: List<Pair<String, () -> Unit>>,
  private val onClosed: () -> Unit,
  private val resume: () -> Unit,
) {
  private val worker = Executors.newSingleThreadExecutor()
  private val thumbnails = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
  private var generation = 0
  private var closed = false
  private var syncing = false
  private var firstSave: Button? = null
  private val white = Color.rgb(237, 242, 250)
  private val muted = Color.rgb(170, 185, 204)
  private fun dp(n: Int) = (n * activity.resources.displayMetrics.density + 0.5f).toInt()
  private fun text(label: String, size: Float = 16f) = TextView(activity).apply {
    this.text = label; setTextSize(size); setTextColor(white); setPadding(dp(8), dp(6), dp(8), dp(6))
  }
  private fun button(label: String, action: () -> Unit) = Button(activity).apply {
    text = label; isAllCaps = false; minHeight = dp(44); setTextSize(14f)
    setOnClickListener { action() }
  }
  private fun toggle(label: String, value: Boolean, action: (Boolean) -> Unit) = Switch(activity).apply {
    text = label; isChecked = value; setTextColor(white); setTextSize(14f)
    setPadding(dp(8), dp(10), dp(8), dp(10)); minHeight = dp(52)
    setOnCheckedChangeListener { _, on -> if (!syncing) action(on) }
  }
  fun show() {
    val row = LinearLayout(activity).apply {
      orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(12), dp(12), dp(12))
      setBackgroundColor(Color.rgb(20, 27, 38))
    }
    val left = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(12), 0) }
    val leftScroll = ScrollView(activity).apply { isFillViewport = true; addView(left) }
    val right = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    val heading = text("Savestates", 22f).apply { setTypeface(null, Typeface.BOLD) }
    val hint = text(if (available) "Save, load or delete a slot. Screenshots and dates stay with this game." else "Savestates are unavailable for the current renderer.", 13f).apply { setTextColor(muted) }
    right.addView(heading); right.addView(hint)
    right.addView(ScrollView(activity).apply { addView(thumbnails) }, LinearLayout.LayoutParams(-1, 0, 1f))
    row.addView(leftScroll, LinearLayout.LayoutParams(0, -1, 0.32f))
    row.addView(right, LinearLayout.LayoutParams(0, -1, 0.68f))
    left.addView(text("Paused", 22f).apply { setTypeface(null, Typeface.BOLD) })
    lateinit var dialog: AlertDialog
    val resumeButton = button("Resume") { resume(); dialog.dismiss() }.apply { id = View.generateViewId() }
    left.addView(resumeButton, LinearLayout.LayoutParams(-1, -2))
    val ff = toggle("Fast forward", fastForward()) { setFastForward(it) }
    left.addView(ff)
    for ((label, action) in actions) left.addView(button(label) {
      action()
      syncing = true
      ff.isChecked = fastForward()
      syncing = false
    }, LinearLayout.LayoutParams(-1, -2))
    row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(v: View) {}
      override fun onViewDetachedFromWindow(v: View) { closed = true; generation++; worker.shutdownNow(); onClosed() }
    })
    dialog = show(AlertDialog.Builder(activity).setView(row))
    dialog.setCanceledOnTouchOutside(false)
    dialog.window?.apply {
      setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      decorView.setPadding(0, 0, 0, 0)
      setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      if (android.os.Build.VERSION.SDK_INT >= 30) {
        insetsController?.hide(android.view.WindowInsets.Type.systemBars())
        insetsController?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      } else decorView.systemUiVisibility = activity.window.decorView.systemUiVisibility
    }
    refreshSlots(resumeButton)
    row.post {
      if (!closed) {
        syncing = true; ff.isChecked = fastForward(); syncing = false
        resumeButton.nextFocusRightId = firstSave?.id ?: View.NO_ID
        resumeButton.requestFocus()
      }
    }
  }
  private fun refreshSlots(leftFocus: View) {
    if (closed) return
    generation++; val current = generation
    thumbnails.removeAllViews(); firstSave = null
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    for (slot in 1..SaveStateStore.SLOTS) {
      val info = states.info(slot)
      val card = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8))
        setBackgroundColor(Color.rgb(32, 43, 59))
      }
      val head = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
      val image = ImageView(activity).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.rgb(13, 18, 26))
        contentDescription = if (info.thumbnail != null) "Screenshot for slot $slot" else "No screenshot for slot $slot"
        setImageResource(android.R.drawable.ic_menu_gallery)
      }
      head.addView(image, LinearLayout.LayoutParams(dp(96), dp(64)))
      val label = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
      label.addView(text("Slot $slot", 17f).apply { setTypeface(null, Typeface.BOLD) })
      label.addView(text(if (info.exists) format.format(Date(info.timestamp)) else "Empty slot", 13f).apply { setTextColor(muted) })
      if (info.exists && info.thumbnail == null) label.addView(text("No preview available", 12f).apply { setTextColor(muted) })
      head.addView(label, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(head)
      val controls = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
      val save = button(if (info.exists) "Overwrite" else "Save") {
        states.save(slot) { refreshSlots(leftFocus) }
      }.apply { isEnabled = available; id = View.generateViewId(); nextFocusLeftId = leftFocus.id }
      val load = button("Load") { states.load(slot) }.apply { isEnabled = available && info.exists }
      val delete = button("Delete") { states.deleteSlot(slot) { refreshSlots(leftFocus) } }.apply { isEnabled = info.exists }
      if (firstSave == null) firstSave = listOf(save, load, delete).firstOrNull { it.isEnabled }
      for (b in listOf(save, load, delete)) {
        b.setTextSize(12f); b.minWidth = 0; b.setPadding(dp(2), dp(4), dp(2), dp(4))
        controls.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
      }
      card.addView(controls)
      thumbnails.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
      info.thumbnail?.let { file ->
        worker.execute {
          val bitmap = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
              inSampleSize = SaveSlotInfo.sampleSize(bounds.outWidth, bounds.outHeight, dp(96), dp(64))
            })
          }.getOrNull()
          activity.runOnUiThread {
            if (closed || current != generation) bitmap?.recycle() else if (bitmap != null) image.setImageBitmap(bitmap)
          }
        }
      }
    }
  }
}
