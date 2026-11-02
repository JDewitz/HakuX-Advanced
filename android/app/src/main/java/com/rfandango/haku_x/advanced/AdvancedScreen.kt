package com.rfandango.haku_x.advanced

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rfandango.haku_x.data.Binding
import com.rfandango.haku_x.data.Controllers
import com.rfandango.haku_x.data.SaveStateStore
import com.rfandango.haku_x.input.InputCapture
import com.rfandango.haku_x.ui.common.*

@Composable
fun AdvancedScreen(game: String?, onBack: () -> Unit) {
  val context = LocalContext.current
  val store = remember { AdvancedStore(context) }
  var profile by remember { mutableStateOf(store.profile(game)) }
  var own by remember { mutableStateOf(game != null && store.hasProfile(game)) }
  var keys by remember { mutableStateOf(store.keys()) }
  var ff by remember { mutableStateOf(Controllers.load(context).fastForwardKey) }
  var onlinePlay by remember { mutableStateOf(store.onlinePlay) }
  var hold by remember { mutableStateOf(store.holdFastForward) }
  var slot by remember { mutableStateOf(store.quickSlot) }
  var quirk by remember { mutableStateOf<String?>(null) }
  var slotPicker by remember { mutableStateOf(false) }
  var capture by remember { mutableStateOf<EmulatorAction?>(null) }
  var pressed by remember { mutableStateOf<Pair<Int, Int>?>(null) }
  fun message(s: String) { Toast.makeText(context, s, Toast.LENGTH_LONG).show() }
  val scope = rememberCoroutineScope()
  var exporting by remember { mutableStateOf(false) }
  val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
    if (uri != null) {
      exporting = true
      scope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { AdvancedArtifacts.export(context, uri) } }
        exporting = false
        message(result.fold({ "Advanced files exported" }, { "Export failed: ${it.message}" }))
      }
    }
  }
  fun save(p: AdvancedProfile) {
    runCatching { store.saveProfile(game, p) }.onSuccess { profile = p; own = game != null }
      .onFailure { message(it.message ?: "Could not save") }
  }
  fun bind(action: EmulatorAction, code: Int?) {
    if (code != null && ((action != EmulatorAction.FAST_FORWARD && ff == code) || keys.any { it.key != action && it.value == code })) {
      message("That key is assigned to another emulator action. Clear it there first."); return
    }
    runCatching {
      if (action == EmulatorAction.FAST_FORWARD) {
        Controllers.save(context, Controllers.load(context).copy(fastForwardKey = code)); ff = code
      } else { store.bind(action, code); keys = store.keys() }
      capture = null; pressed = null
    }.onFailure { message(it.message ?: "Could not save key") }
  }
  DisposableEffect(capture) {
    if (capture == null) return@DisposableEffect onDispose {}
    val listener: (android.view.InputEvent) -> Boolean = { e ->
      if (e is KeyEvent && !e.isCanceled && e.keyCode != KeyEvent.KEYCODE_UNKNOWN) {
        val key = e.deviceId to e.keyCode
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) pressed = key
        if (e.action == KeyEvent.ACTION_UP && pressed == key) capture?.let { bind(it, e.keyCode) }
      }
      true
    }
    InputCapture.listener = listener
    onDispose { if (InputCapture.listener === listener) InputCapture.listener = null }
  }
  BackHandler(capture != null) { capture = null; pressed = null }
  Box(Modifier.fillMaxSize()) {
    SubScreen(title = "Advanced & experimental", subtitle = game ?: "Global profile", onBack = onBack) { padding ->
      Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
        Hint("CPU, RAM and driver overrides apply at the next game start. Defaults match official 1.2.0. Larger RAM or different precision does not guarantee higher speed.")
        if (game != null) Hint(if (own) "This game has its own startup profile." else "This game follows the global startup profile. Editing creates its own profile.")
        ActionItem(title = if (game == null) "Reset startup profile" else "Use global startup profile", summary = "Hotkeys are kept.", icon = null) {
          runCatching { store.resetProfile(game); profile = store.profile(game); own = false }.onFailure { message(it.message ?: "Could not reset") }
        }
        Section("Online play · global session policy")
        SwitchItem("Online play protection", "Default on: trainers, cheats, guest mods, memory tools and save-state loading are blocked. Off: offline trainer sessions only; all Xbox networking is forced off. Fully exit and start the game again after changing this setting.", onlinePlay) { value ->
          runCatching { store.onlinePlay = value }.onSuccess { onlinePlay = value }
            .onFailure { message(it.message ?: "Could not save online protection") }
        }
        Hint("This global policy cannot be overridden by a game profile. Saved trainer options are retained; this setting controls whether they can run. NAT/Insignia, LAN and Kai are all unavailable in offline trainer mode.")
        Section("CPU & memory · restart required")
        SwitchItem("128 MB Xbox RAM", "Off: retail 64 MB. On: development-kit memory size.", profile.ram128) { save(profile.copy(ram128 = it)) }
        SwitchItem("High kernel pool placement", "Experimental Halo crash investigation. Off keeps the normal layout. Fully exit and restart the game; do not load a save state.", profile.poolHigh) { save(profile.copy(poolHigh = it)) }
        SwitchItem("Full 80-bit x87", "Off: existing reduced-precision implementation. Compare per title.", profile.fullX87) { save(profile.copy(fullX87 = it)) }
        SwitchItem("Honor x87 precision control", "Default on. Applies to reduced x87; full 80-bit mode supersedes it.", profile.precisionControl) { save(profile.copy(precisionControl = it)) }
        SwitchItem("Arm alternate floating-point optimization", "Default on. Supported CPU features are still detected by FEX.", profile.armAfp) { save(profile.copy(armAfp = it)) }
        Section("GPU driver workarounds · restart required")
        Hint("Auto retains the driver's normal workaround. Force on/off is for a specific rendering problem; restore Auto to undo an experiment.")
        for ((name, label) in AdvancedProfile.QUIRKS) {
          ListItem(headlineContent = { Text(label) }, supportingContent = {
            Text(when (profile.quirks[name]) { 1 -> "Force on"; -1 -> "Force off"; else -> "Auto" })
          }, modifier = Modifier.clickable { quirk = name })
        }
        Section("Emulator actions · global key mappings")
        Hint("Keys are reserved before Xbox input routing. Assign spare buttons. Quick save/load use the selected slot and require savestate support.")
        SwitchItem("Hold to fast forward", "Off: press once to toggle. On: restores the previous mode when released or focus is lost.", hold) {
          runCatching { store.holdFastForward = it; hold = it }.onFailure { message("Could not save") }
        }
        ListItem(headlineContent = { Text("Quick-save slot: $slot") }, supportingContent = { Text("Quick save overwrites this slot without a prompt.") }, modifier = Modifier.clickable { slotPicker = true })
        for (action in EmulatorAction.entries) {
          val code = if (action == EmulatorAction.FAST_FORWARD) ff else keys[action]
          ListItem(headlineContent = { Text(action.label) }, supportingContent = {
            Text(code?.let { Controllers.label(Binding.Key(it)) } ?: "Unbound")
          }, modifier = Modifier.clickable { pressed = null; capture = action })
        }
        ActionItem(title = if (exporting) "Exporting…" else "Export recorded Advanced files", summary = "Screenshots, benchmark CSVs and renderer dumps; available after leaving a game.", icon = null, enabled = !exporting) {
          export.launch("hakuX-advanced-${System.currentTimeMillis()}.zip")
        }
        Section("During a game")
        Hint("Open the in-game Advanced menu for live filters, a 10-second CSV benchmark, screenshots, texture/render-target dumps and memory search/peek/poke tools. Memory results appear in the tools window; files can be exported from that menu.")
      }
    }
    capture?.let { action ->
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
        Surface(Modifier.padding(24.dp), shape = MaterialTheme.shapes.large) {
          Column(Modifier.padding(24.dp)) {
            Text(action.label, style = MaterialTheme.typography.headlineSmall)
            Text("Press and release a keyboard key or controller button.", Modifier.padding(vertical = 16.dp))
            Row {
              TextButton(onClick = { capture = null; pressed = null }) { Text("Cancel") }
              TextButton(onClick = { bind(action, null) }) { Text("Clear") }
              if (action == EmulatorAction.FAST_FORWARD) TextButton(onClick = { bind(action, KeyEvent.KEYCODE_F9) }) { Text("F9") }
            }
          }
        }
      }
    }
  }
  quirk?.let { name ->
    ChoiceDialog(title = AdvancedProfile.QUIRKS.getValue(name), options = listOf(0 to "Auto", 1 to "Force on", -1 to "Force off"),
      current = profile.quirks[name] ?: 0, onPick = { value ->
        save(profile.copy(quirks = if (value == 0) profile.quirks - name else profile.quirks + (name to value))); quirk = null
      }, onDismiss = { quirk = null })
  }
  if (slotPicker) ChoiceDialog(title = "Quick-save slot", options = (1..SaveStateStore.SLOTS).map { it to "Slot $it" }, current = slot,
    onPick = { n -> runCatching { store.quickSlot = n; slot = n }.onFailure { message("Could not save") }; slotPicker = false }, onDismiss = { slotPicker = false })
}
