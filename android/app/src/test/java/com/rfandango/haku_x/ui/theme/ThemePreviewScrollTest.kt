package com.rfandango.haku_x.ui.theme

import org.junit.Assert.*
import org.junit.Test

class ThemePreviewScrollTest {
  @Test fun scrollingCanPreviewBothEndsAndEveryTheme() {
    val reached = (0..2000).mapNotNull { ThemePreviewScroll.index(it,2000,28) }.toSet()
    assertEquals((0..27).toSet(),reached)
    assertEquals(0,ThemePreviewScroll.index(-5,2000,28))
    assertEquals(27,ThemePreviewScroll.index(2100,2000,28))
  }
  @Test fun openingNearTheSavedThemeRoundTrips() {
    for (i in 0..27) assertEquals(i,ThemePreviewScroll.index(ThemePreviewScroll.offset(i,28,2000),2000,28))
  }
  @Test fun emptyUnmeasuredAndNonScrollingMenusDoNotPreview() {
    assertNull(ThemePreviewScroll.index(0,0,8))
    assertNull(ThemePreviewScroll.index(0,2000,0))
    assertNull(ThemePreviewScroll.index(0,Int.MAX_VALUE,28))
    assertEquals(0,ThemePreviewScroll.offset(1,1,100))
  }
}
