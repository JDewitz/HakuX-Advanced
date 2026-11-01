package com.rfandango.haku_x.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.RippleDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.rfandango.haku_x.data.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One mapping for every Compose screen. Framework views use the same palette. */
private fun ThemePalette.scheme(dark: Boolean): androidx.compose.material3.ColorScheme {
  fun c(k: String) = Color(color(k))
  val base = if (dark) darkColorScheme() else lightColorScheme()
  val error = if (dark) Color(0xFFFFB4AB) else Color(0xFFB3261E)
  return base.copy(
    primary=c("accent"), onPrimary=Color(on("accent")), primaryContainer=c("raised"), onPrimaryContainer=c("text"),
    secondary=c("secondary"), onSecondary=Color(on("secondary")), secondaryContainer=c("raised"), onSecondaryContainer=c("text"),
    tertiary=c("secondary"), onTertiary=Color(on("secondary")), tertiaryContainer=c("raised"), onTertiaryContainer=c("text"),
    background=c("background"), onBackground=c("text"), surface=c("surface"), onSurface=c("text"),
    surfaceVariant=c("raised"), onSurfaceVariant=c("muted"), surfaceTint=c("accent"),
    surfaceContainerLowest=c("background"), surfaceContainerLow=c("surface"), surfaceContainer=c("surface"),
    surfaceContainerHigh=c("raised"), surfaceContainerHighest=c("raised"), surfaceBright=c("raised"), surfaceDim=c("background"),
    inverseSurface=c("text"), inverseOnSurface=c("background"), inversePrimary=c("accent"),
    outline=c("outline"), outlineVariant=c("outline"), error=error,
  )
}

/**
 * The app's theme setting (AppPrefs.theme: the phone's, light or dark), held for the screens to
 * follow as it changes (the settings set it).
 */
object AppTheme {
  private val _spec = MutableStateFlow(BuiltInThemes.xbox)
  val spec: StateFlow<ThemeSpec> = _spec
  private val _preview = MutableStateFlow<ThemeSpec?>(null)
  val preview: StateFlow<ThemeSpec?> = _preview
  /** Transient settings preview; never writes the active theme or catalog. */
  fun preview(value: ThemeSpec?) { _preview.value = value }

  private val _imported = MutableStateFlow<List<ThemeSpec>>(emptyList())
  val imported: StateFlow<List<ThemeSpec>> = _imported
  private fun catalog(context: Context) = ThemeCatalog(java.io.File(context.filesDir, "ui-themes"))

  fun refreshImported(context: Context) { _imported.value = catalog(context).read() }

  fun importTheme(context: Context, value: ThemeSpec): Boolean = catalog(context).add(value)

  fun select(context: Context, value: ThemeSpec) {
    // Preserve a legacy selection before the user switches away from it.
    catalog(context).add(_spec.value)
    catalog(context).add(value)
    refreshImported(context)
    ThemeStore.save(context, value)
    _spec.value = value
    _preview.value = null
  }

  private val _mode = MutableStateFlow(AppPrefs.THEME_SYSTEM)
  val mode: StateFlow<String> = _mode

  fun load(context: Context) {
    _mode.value = AppPrefs(context).theme
    _spec.value = ThemeStore.read(context)
    // Migration is best-effort: a storage error must not replace the active palette.
    runCatching { catalog(context).add(_spec.value) }
    refreshImported(context)
  }

  fun set(context: Context, value: String) {
    AppPrefs(context).theme = value
    _mode.value = value
  }

  /** Dark, for the setting and the phone's own night mode (systemDark). */
  fun isDark(mode: String, systemDark: Boolean) =
    mode == AppPrefs.THEME_DARK || (mode == AppPrefs.THEME_SYSTEM && systemDark)

  /** Dark, as a view-based screen starts (the game's activity: its menus): the setting, read now. */
  fun isDark(context: Context) = isDark(
    AppPrefs(context).theme,
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
  )
}

/** Dark, as the theme setting is now (Compose: followed as it or the phone's night mode changes). */
@Composable
fun appInDarkTheme(): Boolean {
  val mode by AppTheme.mode.collectAsState()
  return AppTheme.isDark(mode, isSystemInDarkTheme())
}

/**
 * What has a controller's (or keyboard's) focus stands out: its highlight at 24 %, Material's 10 % is
 * hard to find on a handheld's screen. Presses and the rest are Material's.
 */
@OptIn(ExperimentalMaterial3Api::class)
private val FocusRipple = RippleDefaults.RippleAlpha.let {
  RippleConfiguration(
    rippleAlpha = RippleAlpha(
      draggedAlpha = it.draggedAlpha,
      focusedAlpha = 0.24f,
      hoveredAlpha = it.hoveredAlpha,
      pressedAlpha = it.pressedAlpha,
    ),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HakuXTheme(dark: Boolean = appInDarkTheme(), content: @Composable () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  androidx.compose.runtime.LaunchedEffect(context) { AppTheme.load(context) }
  val spec by AppTheme.spec.collectAsState()
  val preview by AppTheme.preview.collectAsState()
  MaterialTheme(colorScheme = (preview ?: spec).palette(dark).scheme(dark)) {
    CompositionLocalProvider(LocalRippleConfiguration provides FocusRipple, content = content)
  }
}
