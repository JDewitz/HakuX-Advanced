package com.rfandango.haku_x.ui.theme

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rfandango.haku_x.ui.common.ActionItem
import com.rfandango.haku_x.ui.common.Hint
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Embedded in Display settings. Selecting a preset applies it immediately everywhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettings() {
  val context = LocalContext.current
  val selected by AppTheme.spec.collectAsState()
  val preview by AppTheme.preview.collectAsState()
  val displayed = preview ?: selected
  val imported by AppTheme.imported.collectAsState()
  val choices = ThemeCatalog.choices(selected, imported)
  val dark = appInDarkTheme()
  var message by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var expanded by remember { mutableStateOf(false) }
  var exporting by rememberSaveable { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val menuScroll = rememberScrollState()
  var focusedTheme by remember { mutableStateOf<ThemeSpec?>(null) }
  fun closeMenu() { expanded = false; focusedTheme = null; AppTheme.preview(null) }
  DisposableEffect(Unit) { onDispose { AppTheme.preview(null) } }
  LaunchedEffect(expanded, choices) {
    if (!expanded) { AppTheme.preview(null); return@LaunchedEffect }
    // Position near the saved selection after popup measurement; opening alone does not preview.
    snapshotFlow { menuScroll.maxValue }.first { it != Int.MAX_VALUE }
    menuScroll.scrollTo(ThemePreviewScroll.offset(choices.indexOf(selected), choices.size, menuScroll.maxValue))
    snapshotFlow { menuScroll.value }.drop(1).collect { offset ->
      ThemePreviewScroll.index(offset, menuScroll.maxValue, choices.size)?.let { AppTheme.preview(focusedTheme ?: choices[it]) }
    }
  }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    if (uris.isNotEmpty()) scope.launch {
      busy = true; message = null
      try {
        message = withContext(Dispatchers.IO) {
          var added = 0; var duplicates = 0
          val errors = mutableListOf<String>()
          var single: ThemeSpec? = null
          for ((index, uri) in uris.distinct().withIndex()) {
            try {
              val theme = context.contentResolver.openInputStream(uri)?.use {
                ThemeSpec.parse(it.readBytesLimited().toString(Charsets.UTF_8))
              } ?: error("Cannot open file")
              if (AppTheme.importTheme(context, theme)) added++ else duplicates++
              single = theme
            } catch (e: Exception) {
              errors += "File ${index + 1}: ${e.message ?: "Cannot import theme"}"
            }
          }
          // A batch fills the menu without changing the user's current selection.
          if (uris.distinct().size == 1 && single != null) {
            try { AppTheme.select(context, single) }
            catch (e: Exception) { errors += "Saved, but could not select: ${e.message}" }
          }
          AppTheme.refreshImported(context)
          buildString {
            append("$added added, $duplicates already available, ${errors.size} failed.")
            if (uris.distinct().size > 1) append(" Choose a theme from the menu.")
            if (errors.isNotEmpty()) append("\n").append(errors.take(5).joinToString("\n"))
            if (errors.size > 5) append("\n…and ${errors.size - 5} more.")
          }
        }
      } finally { busy = false }
    }
  }
  val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    val snapshot = exporting; exporting = null
    if (uri != null && snapshot != null) scope.launch {
      busy = true
      runCatching { withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) }
          ?: error("Cannot write theme file")
      } }.onSuccess { message = "Theme exported" }.onFailure { message = "Export failed: ${it.message}" }
      busy = false
    }
  }
  Hint("One palette for the app and pause menus. Light/dark mode is selected separately below.")
  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { if (!busy) { if (it) expanded = true else closeMenu() } },
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
  ) {
    OutlinedTextField(
      value = ThemeCatalog.label(displayed, choices),
      onValueChange = {},
      readOnly = true,
      enabled = !busy,
      singleLine = true,
      label = { Text("Color theme") },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !busy).fillMaxWidth(),
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { closeMenu() },
      modifier = Modifier.heightIn(max = 320.dp).pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) focusedTheme = null
          }
        }
      },
      scrollState = menuScroll,
    ) {
      for (theme in choices) {
        val isSelected = theme == selected
        DropdownMenuItem(
          text = { Text(ThemeCatalog.label(theme, choices)) },
          modifier = Modifier
            .background(if (theme == preview) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .onFocusChanged {
              if (it.isFocused && expanded) { focusedTheme = theme; AppTheme.preview(theme) }
              else if (focusedTheme == theme) focusedTheme = null
            }
            .semantics { this.selected = isSelected },
          trailingIcon = { if (isSelected) Icon(Icons.Filled.Check, contentDescription = null) },
          enabled = !busy,
          onClick = {
            closeMenu()
            scope.launch {
              busy = true
              try {
                runCatching { withContext(Dispatchers.IO) { AppTheme.select(context, theme) } }
                  .onSuccess { message = null }.onFailure { message = it.message }
              } finally { busy = false }
            }
          },
        )
      }
    }
  }
  Hint("Scroll or focus a theme to preview it live. Tap to keep it; close the menu to cancel.")
  val p = displayed.palette(dark)
  Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    for (key in listOf("background", "surface", "text", "accent", "secondary")) {
      Box(Modifier.size(28.dp).background(Color(p.color(key))).border(1.dp, MaterialTheme.colorScheme.outline))
    }
  }
  ActionItem(if (busy) "Working…" else "Import themes JSON", "Select one or several .json files. Imported themes stay in the menu. Extract ZIP packs first.", null, !busy) {
    picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
  }
  ActionItem("Export current theme", "Save a template to edit or share. Colors only; no images or extra files.", null, !busy && exporting == null) {
    exporting = selected.json(); exporter.launch("hakux-theme.json")
  }
  message?.let { Hint(it) }
}
