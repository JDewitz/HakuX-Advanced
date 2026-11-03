package com.rfandango.haku_x.advanced

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rfandango.haku_x.ui.common.SubScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ShaderBrowserScreen(game: String?, onBack: () -> Unit, onKeys: () -> Unit, onSelected: () -> Unit) {
  val context = LocalContext.current
  val store = remember { ShaderStore(context) }
  val scope = rememberCoroutineScope()
  var selected by remember { mutableStateOf(store.selected(game)) }
  var own by remember { mutableStateOf(game != null && store.hasOverride(game)) }
  var presets by remember { mutableStateOf(emptyList<String>()) }
  var roots by remember { mutableStateOf(emptyList<String>()) }
  var folder by rememberSaveable { mutableStateOf(ShaderCatalog.parent(selected.preset)) }
  var query by rememberSaveable { mutableStateOf("") }
  var loading by remember { mutableStateOf(true) }
  var message by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var canCancel by remember { mutableStateOf(true) }
  var progress by remember { mutableStateOf("") }
  var packs by remember { mutableStateOf(false) }
  var deleting by rememberSaveable { mutableStateOf<String?>(null) }
  val cancel = remember { AtomicBoolean(false) }
  val listState = rememberLazyListState()
  fun friendly(path: String): String {
    if ('/' in path) return path.substringAfterLast('/')
    return when {
      path == "builtin-v1" -> "Built-in examples"
      path.startsWith("libretro-") -> "Libretro shaders · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(java.io.File(store.root, path).lastModified()))
      path.startsWith("pack-") -> "Imported pack · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(java.io.File(store.root, path).lastModified()))
      else -> path
    }
  }
  suspend fun reload() {
    val catalog = withContext(Dispatchers.IO) { store.installBuiltins(); store.presets() to store.packRoots() }
    presets = catalog.first; roots = catalog.second
    loading = false
  }
  LaunchedEffect(Unit) { runCatching { reload() }.onFailure { loading = false; message = it.message ?: "Could not read shader packs" } }
  LaunchedEffect(folder, query) { listState.scrollToItem(0) }
  BackHandler(folder.isNotEmpty()) { folder = ShaderCatalog.up(folder); query = "" }
  fun save(selection: ShaderSelection) {
    runCatching { store.save(game, selection); selected = selection; own = game != null }
      .onSuccess { message = "Selected ${selection.preset.substringAfterLast('/').ifEmpty { "Off" }}"; onSelected() }
      .onFailure { message = it.message ?: "Could not save preset" }
  }
  fun import(uri: Uri) {
    if (busy) return
    busy = true; cancel.set(false); progress = "Importing shader pack…"
    scope.launch {
      val result = runCatching { withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { store.import(it) { cancel.get() } } ?: error("Could not open ZIP")
      } }
      busy = false
      result.onSuccess { reload(); folder = ""; query = ""; message = "Imported $it presets. Open the new pack." }
        .onFailure { message = if (cancel.get()) "Import cancelled" else "Import failed: ${it.message}" }
    }
  }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) import(uri) }
  fun download() {
    if (busy) return
    busy = true; cancel.set(false); progress = "Connecting to Libretro…"
    scope.launch {
      val job = coroutineContext[Job]
      val result = runCatching { withContext(Dispatchers.IO) {
        ShaderDownload.fetch(context.cacheDir, { cancel.get() || job?.isActive != true },
          { text -> scope.launch { progress = text } },
          { file -> store.importOfficial(file) { cancel.get() || job?.isActive != true } })
      } }
      busy = false
      result.onSuccess { reload(); folder = ""; query = ""; message = "Installed $it presets. Open Libretro shaders." }
        .onFailure { message = if (cancel.get()) "Download cancelled" else "Download failed: ${it.message}. Try Import ZIP." }
    }
  }
  DisposableEffect(Unit) { onDispose { cancel.set(true) } }
  val entries = remember(presets, roots, folder, query) {
    val all = if (folder.isEmpty()) roots.map { ShaderCatalog.Entry(it, it, true) } else ShaderCatalog.entries(presets, folder)
    all.filter { it.name.contains(query, ignoreCase = true) }
  }
  SubScreen(title = "Shaders", subtitle = if (game == null) "Global graphics" else if (own) "Graphics for this game" else "This game · using global",
    onBack = { if (folder.isNotEmpty()) { folder = ShaderCatalog.up(folder); query = "" } else onBack() },
    actions = { TextButton(onClick = onKeys) { Text("Keys") } }) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      Text("Selected: ${selected.preset.substringAfterLast('/').ifEmpty { "Off" }}", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { folder = ShaderCatalog.up(folder); query = "" }, enabled = folder.isNotEmpty()) { Text("Up") }
        TextButton(enabled = !busy, onClick = { save(ShaderSelection()) }) { Text("Off") }
        if (game != null) TextButton(enabled = !busy, onClick = {
          runCatching { store.reset(game); selected = store.selected(game); own = false; onSelected() }.onFailure { message = "Could not reset preset" }
        }) { Text("Use global") }
        TextButton(enabled = !busy, onClick = { packs = true }) { Text("Packs") }
      }
      OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search this folder") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
      val crumb = folder.split('/').filter { it.isNotEmpty() }.mapIndexed { i, name -> if (i == 0) friendly(name) else name }
        .filterNot { it.startsWith("slang-shaders-") }.joinToString(" / ")
      Text(crumb.ifEmpty { "Shader packs" }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
      if (busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
          Text(progress, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
          if (canCancel) TextButton(onClick = { cancel.set(true) }) { Text("Cancel") }
        }
      }
      if (message.isNotEmpty()) Text(message, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
      if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
      LazyColumn(Modifier.weight(1f), state = listState) {
        if (!loading && entries.isEmpty()) item { Text("No presets here. Go Up or use Packs to download/import a ZIP.", Modifier.padding(16.dp)) }
        items(entries, key = { it.path }) { entry ->
          ListItem(headlineContent = { Text(if (entry.directory) friendly(entry.path) else entry.name.removeSuffix(".slangp"), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(if (entry.directory) "Folder" else if (entry.path == selected.preset) "Selected preset" else "Load preset") },
            leadingContent = {
              if (entry.directory) {
                val tint = MaterialTheme.colorScheme.primary
                androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
                  drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * .08f, size.height * .25f)
                    lineTo(size.width * .42f, size.height * .25f)
                    lineTo(size.width * .52f, size.height * .38f)
                    lineTo(size.width * .92f, size.height * .38f)
                    lineTo(size.width * .92f, size.height * .82f)
                    lineTo(size.width * .08f, size.height * .82f); close()
                  }, tint)
                }
              } else Text(if (entry.path == selected.preset) "✓" else "·")
            },
            trailingContent = {
              if (entry.directory) Row(verticalAlignment = Alignment.CenterVertically) {
                if (folder.isEmpty()) IconButton(enabled = !busy, onClick = { deleting = entry.path }) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete ${friendly(entry.path)}", tint = MaterialTheme.colorScheme.error)
                }
                Text("›")
              }
            },
            modifier = Modifier.clickable(enabled = !busy) {
              if (entry.directory) { folder = ShaderCatalog.open(presets, entry); query = "" } else save(ShaderSelection(entry.path))
            })
        }
      }
      Text("Keys → Next shader: cycles presets in the selected folder.", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
    }
  }
  deleting?.let { pack ->
    AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete shader pack?") },
      text = { Text("Delete ${friendly(pack)} and all its presets, shader sources and textures? Affected global and per-game selections will switch Off. This cannot be undone.") },
      confirmButton = { TextButton(enabled = !busy, onClick = {
        deleting = null; busy = true; canCancel = false; progress = "Deleting shader pack…"
        scope.launch {
          val result = runCatching { withContext(Dispatchers.IO) { store.deletePack(pack) } }
          // Refresh even after a partial filesystem failure; settings may already have been cleared.
          selected = store.selected(game); own = game != null && store.hasOverride(game)
          folder = ""; query = ""
          runCatching { reload() }.onFailure { message = it.message ?: "Could not refresh shader packs" }
          busy = false; canCancel = true
          result.onSuccess { message = "Shader pack deleted" }
            .onFailure { message = "Could not fully delete pack: ${it.message}. Affected selections are Off; retry deletion." }
        }
      }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
      dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
  }
  if (packs) AlertDialog(onDismissRequest = { packs = false }, title = { Text("Shader packs") },
    text = { Text("Download Libretro shaders or use the Android file picker to import a ZIP with its sources, includes and textures.") },
    confirmButton = { TextButton(enabled = !busy, onClick = { packs = false; download() }) { Text("Download") } },
    dismissButton = { TextButton(enabled = !busy, onClick = { packs = false; picker.launch(arrayOf("*/*")) }) { Text("Import ZIP…") } })
}
