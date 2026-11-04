package com.rfandango.haku_x.ui.mods

import android.net.Uri
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rfandango.haku_x.data.*
import com.rfandango.haku_x.ui.common.Hint
import com.rfandango.haku_x.ui.common.SubScreen
import com.rfandango.haku_x.ui.library.LibraryViewModel
import kotlinx.coroutines.*
import java.util.Locale

/** Folder-backed, two-pane ETM browser. Browsing never changes the active trainer. */
@Composable
fun TrainerBrowser(gameKey: String? = null, title: String = "", onBack: () -> Unit) {
  val context = LocalContext.current
  val library = remember { TrainerLibrary(context) }
  val scope = rememberCoroutineScope()
  val libraryState = if (gameKey == null) viewModel<LibraryViewModel>().state.collectAsStateWithLifecycle().value else null
  var folder by remember { mutableStateOf(library.folder) }
  var entries by remember { mutableStateOf<List<TrainerEntry>>(emptyList()) }
  var message by remember { mutableStateOf("") }
  var scanning by remember { mutableStateOf(false) }
  var writing by remember { mutableStateOf(false) }
  var scanJob by remember { mutableStateOf<Job?>(null) }
  var query by rememberSaveable { mutableStateOf("") }
  var picked by rememberSaveable { mutableStateOf<String?>(null) }
  var target by rememberSaveable { mutableStateOf(gameKey) }
  var gameMenu by remember { mutableStateOf(false) }
  var active by remember { mutableStateOf<TrainerStore.Selection?>(null) }
  var loadedKey by remember { mutableStateOf<String?>(null) }
  var revision by remember { mutableIntStateOf(0) }
  var listOnly by rememberSaveable { mutableStateOf(true) }

  fun refresh(rebuild: Boolean = false) {
    scanJob?.cancel()
    scanJob = scope.launch {
      scanning = true
      try {
        val cached = withContext(Dispatchers.IO) { library.cached() }
        entries = cached.entries; message = cached.message
        if (rebuild || library.needsScan) {
          val owner = coroutineContext[Job]
          message = "Indexing trainer folder…"
          val result = withContext(Dispatchers.IO) { library.scan { count ->
            scope.launch { if (owner?.isActive == true) message = "Indexed $count trainer files…" }
          } }
          entries = result.entries; message = result.message
        }
        revision++
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { message = "Cannot scan trainer library: ${e.message}" }
      finally { if(isActive) scanning = false }
    }
  }
  val refreshNow by rememberUpdatedState(newValue = { rebuild: Boolean -> refresh(rebuild) })
  val writingNow by rememberUpdatedState(writing)
  LifecycleResumeEffect(folder) { refreshNow(false); onPauseOrDispose {} }
  DisposableEffect(folder) {
    var pending: Job? = null
    val observer = object: ContentObserver(Handler(Looper.getMainLooper())) {
      override fun onChange(selfChange: Boolean) {
        pending?.cancel(); pending = scope.launch { delay(700); if (!writingNow) message = "Trainer folder changed. Refresh to update the index." }
      }
    }
    val registered = folder?.let { runCatching { context.contentResolver.registerContentObserver(it,true,observer); true }.getOrDefault(false) } == true
    onDispose { pending?.cancel(); if (registered) context.contentResolver.unregisterContentObserver(observer) }
  }
  val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) runCatching {
      Storage.persistPermission(context,uri,write=false)
      library.folder = uri; if (folder == uri) refresh(true) else folder = uri
    }.onFailure { message = "Cannot select folder: ${it.message}" }
  }
  val importFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    if (uris.isNotEmpty()) scope.launch {
      writing=true
      try {
        message=withContext(Dispatchers.IO) {
          val good=mutableListOf<TrainerEntry>();val errors=mutableListOf<String>()
          for ((i,uri) in uris.distinct().withIndex()) {
            currentCoroutineContext().ensureActive()
            if (i % 25 == 0) withContext(Dispatchers.Main) { message = "Importing and indexing trainer ${i+1} of ${uris.distinct().size}…" }
            try {
              val name=runCatching { context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null } }.getOrNull() ?: "File ${i+1}"
              val bytes=context.contentResolver.openInputStream(uri)?.use { it.readTrainerBytes() } ?: error("Cannot open trainer")
              good+=library.catalog.cache(bytes,"Imported: $name")
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { errors+="File ${i+1}: ${e.message}" }
          }
          library.importFiles(good)
          "${good.size} valid files imported; ${errors.size} failed.\n"+errors.take(5).joinToString("\n")
        }
        entries=withContext(Dispatchers.IO) { library.catalog.read() };revision++
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { message="Cannot import trainers: ${e.message}" }
      finally { writing=false }
    }
  }

  LaunchedEffect(target,revision) {
    loadedKey=null;active=null
    val key=target
    if(key!=null) {
      try { active=withContext(Dispatchers.IO) { TrainerStore(context,key).load() } }
      catch(e: Exception) { message="Saved trainer cannot be loaded: ${e.message}" }
    }
    loadedKey=key
  }
  val legacy = active?.let { s -> TrainerEntry(s.file.name.removeSuffix(".etm"),s.trainer,s.file,listOf("Previously selected for this game")) }
  val all = remember(entries,legacy) { TrainerCatalog.merge(entries + listOfNotNull(legacy)) }
  val lookup = remember(all) { TrainerLookup(all) }
  val searchText = remember(all) { all.associate { e -> e.id to
    listOf(e.trainer.name,e.trainer.note,e.sources.joinToString(),e.trainer.titleIds.joinToString { it.toString(16).padStart(8,'0') })
      .joinToString("\n").lowercase(Locale.ROOT) } }
  val visible = remember(all,lookup,gameKey,query,searchText) {
    val needle = query.trim().lowercase(Locale.ROOT)
    (gameKey?.let { lookup.forGame(it) } ?: all).filter { needle.isEmpty() || searchText[it.id]?.contains(needle) == true }
  }
  val selected = visible.firstOrNull { it.id==picked } ?: visible.firstOrNull { active?.file?.name=="${it.id}.etm" } ?: visible.firstOrNull()
  val detailsScroll = rememberScrollState()
  LaunchedEffect(selected?.id,target) { detailsScroll.scrollTo(0) }
  val gameChoices = remember(libraryState?.visibleGames,libraryState?.info) {
    libraryState?.visibleGames.orEmpty().mapNotNull { g ->
      libraryState?.info?.get(g.uri)?.takeIf { it.titleId!=0L }?.let { d ->
        GameOverrides.gameKey(d.titleId,d.version,g.relativePath) to "${g.title} · ${d.titleIdText}.${d.version}"
      }
    }.distinctBy { it.first }.sortedBy { it.second.lowercase(Locale.ROOT) }
  }
  val gamesByTitle = remember(gameChoices) { gameChoices.groupBy { it.first.substringBefore('.').toLongOrNull(16) } }
  val matches = remember(gamesByTitle,selected?.trainer) {
    selected?.trainer?.titleIds.orEmpty().filter { it != 0L }.distinct()
      .flatMap { gamesByTitle[it].orEmpty() }.distinctBy { it.first }.sortedBy { it.second.lowercase(Locale.ROOT) }
  }
  val online = com.rfandango.haku_x.advanced.AdvancedStore(context).onlinePlay
  val ready = !online && !writing && !scanning && target!=null && loadedKey==target
  val activeHere = active?.file?.name?.removeSuffix(".etm")==selected?.id
  fun change(block: (TrainerStore) -> TrainerStore.Selection?) {
    if(online||writing||scanning||loadedKey!=target) return
    val key=target ?: return
    writing=true
    scope.launch {
      try { active=withContext(Dispatchers.IO) { block(TrainerStore(context,key)) };message="Saved for the next fresh game start." }
      catch(e: Exception) { message="Cannot save trainer: ${e.message}" }
      finally { writing=false }
    }
  }

  SubScreen(title="Trainers",subtitle=title.takeIf { it.isNotBlank() },onBack=onBack) { padding ->
    BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
      val wide=maxWidth>=600.dp
      Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
          TextButton(enabled=!writing&&!scanning,onClick={ chooseFolder.launch(folder) }) { Text("Choose folder") }
          TextButton(enabled=!writing&&!scanning,onClick={ importFiles.launch(arrayOf("*/*")) }) { Text("Import files") }
          TextButton(enabled=!writing&&!scanning,onClick={ refresh(true) }) { Text("Refresh index") }
        }
        Text(folder?.let { runCatching { Storage.folderPlace(context,it) }.getOrDefault(it.toString()) } ?: "No trainer folder selected",
          Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
        if(online) Text("Online play protection is ON. Trainer activation is blocked. Choose offline trainer mode in Advanced to use trainers; Xbox networking will be forced off.",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
        if(scanning||writing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank()) Text(message,Modifier.padding(horizontal=16.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall,maxLines=4,overflow=TextOverflow.Ellipsis)
        if(!wide) TextButton(onClick={ listOnly=!listOnly }) { Text(if(listOnly) "Show trainer options" else "Show trainer list") }
        Row(Modifier.weight(1f).fillMaxWidth()) {
          if(wide||listOnly) Column(Modifier.weight(if(wide)0.42f else 1f).fillMaxHeight()) {
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(8.dp),label={Text("Search trainers / title IDs")},singleLine=true)
            Text("${visible.size} matching trainers",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f)) {
              items(visible,key={it.id}) { e ->
                ListItem(headlineContent={ Text(e.trainer.name.ifBlank { e.sources.firstOrNull() ?: e.id.take(8) }) },
                  supportingContent={Text(e.sources.joinToString("\n"),maxLines=2,overflow=TextOverflow.Ellipsis)},
                  trailingContent={if(active?.file?.name=="${e.id}.etm") Text(if(active?.enabled==true) { if(online) "BLOCKED" else "ON" } else "OFF")},
                  colors=ListItemDefaults.colors(containerColor=if(selected?.id==e.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                  modifier=Modifier.clickable(enabled=!writing) {picked=e.id;if(!wide)listOnly=false})
              }
            }
          }
          if(wide) VerticalDivider()
          if(wide||!listOnly) Column(Modifier.weight(if(wide)0.58f else 1f).fillMaxHeight().verticalScroll(detailsScroll).padding(12.dp)) {
            if(selected==null) Text("Choose a trainer folder or import .etm files. This game's trainers are matched using embedded title IDs.")
            else {
              Text(selected.trainer.name,style=MaterialTheme.typography.titleLarge)
              Text(selected.trainer.note,style=MaterialTheme.typography.bodyMedium)
              Text(selected.sources.joinToString("\n"),style=MaterialTheme.typography.bodySmall)
              Text("Title IDs: "+selected.trainer.titleIds.filter{it!=0L}.joinToString { "%08X".format(it) },style=MaterialTheme.typography.bodySmall)
              Text("Trainer ID: ${selected.id.take(12)}",style=MaterialTheme.typography.bodySmall)
              if(gameKey==null) {
                Box {
                  OutlinedButton(onClick={gameMenu=true},enabled=!writing) {Text(gameChoices.firstOrNull {it.first==target}?.second ?: "Choose a matching game / revision")}
                  DropdownMenu(gameMenu,{gameMenu=false}) {
                    matches.forEach { (key,label) -> DropdownMenuItem(text={Text(label)},onClick={target=key;gameMenu=false}) }
                    if(matches.isEmpty()) DropdownMenuItem(text={Text(if(libraryState?.loading==true) "Reading game library…" else "No matching library game")},onClick={},enabled=false)
                  }
                }
              } else Text("Game / revision: $gameKey",style=MaterialTheme.typography.bodyMedium)
              Hint("Title ID matched does not verify the region or executable revision. Options take a fresh start; loading a save state keeps its existing injected code.")
              val compatible=target?.let { selected.trainer.matches(it) }==true
              if(!activeHere) {
                Button(enabled=ready&&compatible,onClick={change { it.select(library.catalog.verifiedBytes(selected)) }}) {Text("Use this trainer for this game")}
                Text(if(compatible) "Browsing only. Select this trainer to edit its remembered options." else "Choose a matching library game to use this trainer.",style=MaterialTheme.typography.bodySmall)
                selected.trainer.labels.forEach { Text("• $it",Modifier.padding(vertical=4.dp)) }
              } else active?.let { s ->
                TrainerToggle("Enabled on next fresh start",s.enabled,ready) {v -> change {it.enable(s,v)}}
                s.trainer.labels.forEachIndexed { i,label -> TrainerToggle(label,s.options[i]=='1',ready) {v ->change {it.option(s,i,v)}} }
                TextButton(enabled=ready,onClick={change {it.remove();null}}) {Text("Clear game's trainer selection")}
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TrainerToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean)->Unit) {
  ListItem(headlineContent={Text(label)}, trailingContent={Switch(checked,onCheckedChange=null,enabled=enabled)},
    modifier=Modifier.toggleable(value=checked,enabled=enabled,role=Role.Switch,onValueChange=onChange))
}
