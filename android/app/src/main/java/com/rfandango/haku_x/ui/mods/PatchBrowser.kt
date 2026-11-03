package com.rfandango.haku_x.ui.mods

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rfandango.haku_x.advanced.AdvancedStore
import com.rfandango.haku_x.data.*
import com.rfandango.haku_x.patches.*
import com.rfandango.haku_x.ui.common.Hint
import com.rfandango.haku_x.ui.common.SubScreen
import com.rfandango.haku_x.ui.library.LibraryViewModel
import kotlinx.coroutines.*
import java.util.Locale

@Composable
fun PatchBrowser(gameKey: String? = null, title: String = "", onBack: () -> Unit) {
  val context = LocalContext.current
  val library = remember { PatchLibrary(context) }
  val scope = rememberCoroutineScope()
  val state = viewModel<LibraryViewModel>().state.collectAsStateWithLifecycle().value
  var target by rememberSaveable { mutableStateOf(gameKey) }
  var folder by remember { mutableStateOf(library.store.folder) }
  var entries by remember { mutableStateOf<List<PatchEntry>>(emptyList()) }
  var decisions by remember { mutableStateOf<List<PatchDecision>>(emptyList()) }
  var message by remember { mutableStateOf("") }
  var query by rememberSaveable { mutableStateOf("") }
  var picked by rememberSaveable { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var gamePicker by remember { mutableStateOf(false) }
  var showAll by rememberSaveable { mutableStateOf(gameKey == null) }
  var details by rememberSaveable { mutableStateOf(false) }
  var scanJob by remember { mutableStateOf<Job?>(null) }
  fun refresh() {
    scanJob?.cancel()
    scanJob = scope.launch {
      busy = true
      try {
        val result = withContext(Dispatchers.IO) { library.scan() }
        entries = result.entries
        decisions = withContext(Dispatchers.IO) { PatchRun.preview(context, result.entries, target, AdvancedStore(context).onlinePlay) }
        message = result.errors.take(10).joinToString("\n")
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { entries = emptyList(); decisions = emptyList(); message = "Cannot read patches: ${e.message}" }
      finally { if (isActive) busy = false }
    }
  }
  val refreshNow by rememberUpdatedState { refresh() }
  LifecycleResumeEffect(folder, target) { refreshNow(); onPauseOrDispose {} }
  val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) runCatching {
      Storage.persistPermission(context, uri, write = false)
      library.store.folder = uri.toString(); folder = uri.toString()
    }.onFailure { message = it.message ?: "Cannot use folder" }
  }
  val importFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    if (uris.isNotEmpty()) scope.launch {
      busy = true
      try {
        val result = withContext(Dispatchers.IO) {
          var imported = 0; val errors = mutableListOf<String>()
          for ((index, uri) in uris.distinct().take(64).withIndex()) runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readPatchBytes() } ?: error("Cannot read file")
            library.store.import(bytes); imported++
          }.onFailure { errors += "File ${index + 1}: ${it.message}" }
          if (uris.size > 64) errors += "Only the first 64 selected files were processed"
          "$imported imported. ${errors.joinToString("\n")}".trim()
        }
        val scanned = withContext(Dispatchers.IO) { library.scan() }
        entries = scanned.entries
        decisions = withContext(Dispatchers.IO) { PatchRun.preview(context, scanned.entries, target, AdvancedStore(context).onlinePlay) }
        message = result + "\n" + scanned.errors.take(10).joinToString("\n")
      } catch (e: Exception) { message = e.message ?: "Import failed" }
      finally { busy = false }
    }
  }
  val games = remember(state.games, state.info) { state.games.mapNotNull { g ->
    state.info[g.uri]?.takeIf { it.titleId != 0L }?.let { d -> GameOverrides.gameKey(d.titleId, d.version, g.relativePath) to g.title }
  }.distinctBy { it.first }.sortedBy { it.second.lowercase() } }
  val visible = entries.filter { e -> (showAll || target == null || e.pack.matches(target, com.rfandango.haku_x.BuildConfig.VERSION_NAME)) &&
    (query.isBlank() || "${e.pack.name} ${e.pack.description} ${e.pack.id} ${e.pack.author} ${"%08X".format(Locale.ROOT, e.pack.titleId)}".contains(query, true)) }
  val selected = entries.firstOrNull { it.hash == picked }
  SubScreen(title = "Game patches", subtitle = title.ifBlank { target ?: "Import and manage patches" }, onBack = onBack) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(enabled = !busy, onClick = { importFiles.launch(arrayOf("*/*")) }) { Text("Import") }
        TextButton(enabled = !busy, onClick = { chooseFolder.launch(folder?.let(Uri::parse)) }) { Text("Folder") }
        TextButton(enabled = !busy, onClick = { refresh() }) { Text("Refresh") }
        if (folder != null) TextButton(enabled = !busy, onClick = { runCatching { library.store.folder = null; folder = null }.onFailure { message = it.message ?: "Cannot unlink folder" } }) { Text("Unlink") }
      }
      TextButton(onClick = { gamePicker = true }, enabled = !busy) { Text("Game: ${games.firstOrNull { it.first == target }?.second ?: target ?: "Select…"}") }
      OutlinedTextField(query, { query = it }, label = { Text("Search patches") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true)
      Row(Modifier.padding(horizontal = 12.dp)) {
        Checkbox(showAll, { showAll = it }); Text("Show incompatible patches", Modifier.padding(top = 12.dp))
      }
      if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      if (message.isNotBlank()) Text(message, Modifier.padding(horizontal = 12.dp).heightIn(max = 96.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
      BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
          if (wide || !details) LazyColumn(Modifier.weight(if (wide) 0.42f else 1f).fillMaxHeight()) {
            if (visible.isEmpty()) item { Hint("No matching patches. Import .hakuxpatch files, select a folder, or show incompatible patches.") }
            items(visible, key = { it.hash }) { e ->
              ListItem(headlineContent = { Text(e.pack.name) }, supportingContent = { Text("${e.pack.version} · ${decisions.firstOrNull { it.entry.hash == e.hash }?.status ?: "Not checked"}") },
                modifier = Modifier.clickable { picked = e.hash; details = true }, colors = ListItemDefaults.colors(containerColor = if (picked == e.hash) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface))
            }
          }
          if (wide || details) Column(Modifier.weight(if (wide) 0.58f else 1f).fillMaxHeight().padding(16.dp).verticalScroll(rememberScrollState())) {
            if (!wide) TextButton(onClick = { details = false }) { Text("Back to patches") }
            if (selected == null) Text("Select a patch to view details and configure its toggle.") else {
              val d = decisions.firstOrNull { it.entry.hash == selected.hash }
              Text(selected.pack.name, style = MaterialTheme.typography.headlineSmall)
              Text("${selected.pack.version} · ${selected.pack.author}")
              Text(selected.pack.description, Modifier.padding(vertical = 12.dp))
              Text("Title: ${"%08X".format(Locale.ROOT, selected.pack.titleId)} · revisions ${selected.pack.revisions.sorted().joinToString()}")
              Text("Core: ${selected.pack.emulatorBase} · cold start required")
              Text("${selected.pack.writes.size} guarded operations · default.xbe")
              Text(selected.sources.joinToString("\n"), Modifier.padding(vertical = 8.dp))
              Text(d?.status ?: "Not checked", color = MaterialTheme.colorScheme.primary)
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enabled for this game", Modifier.weight(1f).padding(top = 12.dp))
                Switch(checked = d?.enabled == true, enabled = !busy && target != null && (d?.enabled == true || (selected.available && selected.pack.matches(target, com.rfandango.haku_x.BuildConfig.VERSION_NAME))), onCheckedChange = { on ->
                  runCatching { library.store.toggle(target!!, selected.hash, on) }.onSuccess { refresh() }.onFailure { message = it.message ?: "Cannot save toggle" }
                })
              }
              Hint("Choices are saved per game and patch content. Online protection blocks execution. Source and byte checks run again before launch; replacement versions start disabled. Turning this off takes effect at the next cold start.")
              Hint("Imported patches currently support cold starts only. Save-state saving/loading is unavailable in a patched session. Loaded at launch does not prove every native write applied; inspect the patch log.")
              Text("SHA-256: ${selected.hash}", style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      }
    }
  }
  if (gamePicker) AlertDialog(onDismissRequest = { gamePicker = false }, title = { Text("Select game revision") }, text = {
    LazyColumn(Modifier.heightIn(max = 400.dp)) {
      items(games, key = { it.first }) { (key, name) -> ListItem(headlineContent = { Text(name) }, supportingContent = { Text(key) }, modifier = Modifier.clickable { target = key; gamePicker = false }) }
      if (games.isEmpty()) item { Text("Refresh your game library first so certificate identities are available.") }
    }
  }, confirmButton = {}, dismissButton = { TextButton(onClick = { gamePicker = false }) { Text("Back") } })
}
