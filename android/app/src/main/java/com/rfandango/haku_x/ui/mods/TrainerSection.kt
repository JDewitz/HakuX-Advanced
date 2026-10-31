package com.rfandango.haku_x.ui.mods

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.rfandango.haku_x.data.TrainerStore
import com.rfandango.haku_x.ui.common.ActionItem
import com.rfandango.haku_x.ui.common.Hint
import com.rfandango.haku_x.ui.common.Section
import com.rfandango.haku_x.ui.common.SwitchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TrainerSection(gameKey: String) {
  val context = LocalContext.current
  val store = remember(gameKey) { TrainerStore(context, gameKey) }
  var error by remember(gameKey) { mutableStateOf<String?>(null) }
  var selection by remember(gameKey) { mutableStateOf(runCatching { store.load() }.onFailure { error = it.message }.getOrNull()) }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) scope.launch {
      busy = true; error = null
      runCatching { withContext(Dispatchers.IO) { store.importTrainer(uri) } }
        .onSuccess { selection = it }.onFailure { error = it.message }
      busy = false
    }
  }
  Section("EvoX trainer (experimental)")
  Hint("Import one classic .etm for this game. Select options, enable the trainer, then start the game fresh. Title IDs are checked; region and game revision must also match. Trainer code may freeze or crash a game. Loading a save state keeps its existing trainer patches and options.")
  ActionItem(if (busy) "Importing…" else "Import / replace .etm", "Choose a trainer file with the system file picker", null, !busy) { picker.launch(arrayOf("*/*")) }
  error?.let { Hint(it) }
  selection?.let { s ->
    Hint(s.trainer.name + "\n" + s.trainer.note)
    if (!busy) {
      SwitchItem("Enable trainer on next fresh start", null, s.enabled) {
        runCatching { store.enable(s, it) }.onSuccess { selection = it }.onFailure { error = it.message }
      }
      s.trainer.labels.forEachIndexed { i, label ->
        SwitchItem(label, null, s.options[i] == '1') {
          runCatching { store.option(s, i, it) }.onSuccess { selection = it }.onFailure { error = it.message }
        }
      }
      ActionItem("Remove trainer", "Disables it for future fresh starts", null) {
        runCatching { store.remove() }.onSuccess { selection = null }.onFailure { error = it.message }
      }
    }
  }
}
