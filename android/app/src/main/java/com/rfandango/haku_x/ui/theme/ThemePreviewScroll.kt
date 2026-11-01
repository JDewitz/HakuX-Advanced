package com.rfandango.haku_x.ui.theme

import kotlin.math.roundToInt

/** Scroll progress covers every theme, including the first/last partial viewport. */
object ThemePreviewScroll {
  fun index(offset: Int, maximum: Int, count: Int): Int? =
    if (count <= 0 || maximum <= 0 || maximum == Int.MAX_VALUE) null
    else ((offset.coerceIn(0, maximum).toDouble() / maximum) * (count - 1)).roundToInt()

  fun offset(index: Int, count: Int, maximum: Int): Int =
    if (count <= 1 || maximum <= 0 || maximum == Int.MAX_VALUE) 0
    else (index.coerceIn(0, count - 1).toDouble() / (count - 1) * maximum).roundToInt()
}
