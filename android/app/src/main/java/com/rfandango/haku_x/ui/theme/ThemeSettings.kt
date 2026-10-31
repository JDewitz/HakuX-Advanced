package com.rfandango.haku_x.ui.theme

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rfandango.haku_x.ui.common.ActionItem
import com.rfandango.haku_x.ui.common.Hint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Embedded in Display settings. Selecting a preset applies it immediately everywhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettings() {
  val context = LocalContext.current
  val selected by AppTheme.spec.collectAsState()
  val dark = appInDarkTheme()
  var message by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var expanded by remember { mutableStateOf(false) }
  var exporting by rememberSaveable { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) scope.launch {
      busy = true; message = null
      runCatching {
        val theme = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { ThemeSpec.parse(it.readBytesLimited().toString(Charsets.UTF_8)) }
            ?: error("Cannot open theme file")
        }
        AppTheme.select(context, theme)
        "Imported ${theme.name}"
      }.onSuccess { message = it }.onFailure { message = "Theme not changed: ${it.message}" }
      busy = false
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
    onExpandedChange = { if (!busy) expanded = it },
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
  ) {
    OutlinedTextField(
      value = selected.name,
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
      onDismissRequest = { expanded = false },
      modifier = Modifier.heightIn(max = 320.dp),
    ) {
      for (theme in BuiltInThemes.choices(selected)) {
        val isSelected = theme == selected
        DropdownMenuItem(
          text = { Text(theme.name + if (theme !in BuiltInThemes.all) " (imported)" else "") },
          modifier = Modifier.semantics { this.selected = isSelected },
          trailingIcon = { if (isSelected) Icon(Icons.Filled.Check, contentDescription = null) },
          enabled = !busy,
          onClick = {
            expanded = false
            runCatching { AppTheme.select(context, theme) }.onSuccess { message = null }.onFailure { message = it.message }
          },
        )
      }
    }
  }
  val p = selected.palette(dark)
  Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    for (key in listOf("background", "surface", "text", "accent", "secondary")) {
      Box(Modifier.size(28.dp).background(Color(p.color(key))).border(1.dp, MaterialTheme.colorScheme.outline))
    }
  }
  ActionItem(if (busy) "Working…" else "Import theme JSON", "Choose a .json file. Invalid or unreadable palettes leave the current theme unchanged.", null, !busy) {
    picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
  }
  ActionItem("Export current theme", "Save a template to edit or share. Colors only; no images or extra files.", null, !busy && exporting == null) {
    exporting = selected.json(); exporter.launch("hakux-theme.json")
  }
  message?.let { Hint(it) }
}
