package com.rfandango.haku_x.advanced

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rfandango.haku_x.ui.common.ActionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RenderUpdateControl(game: String? = null, observed: ShaderSelection? = null, onSaved: () -> Unit = {}) {
  val context = LocalContext.current
  val store = remember { ShaderStore(context) }
  val scope = rememberCoroutineScope()
  var selected by remember(game) { mutableStateOf(store.selected(game)) }
  var open by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var mode by remember { mutableStateOf(RenderUpdate.Mode.SHARPEN) }
  var strength by remember { mutableFloatStateOf(.25f) }
  var message by remember { mutableStateOf("") }
  LifecycleResumeEffect(game) { selected=store.selected(game); onPauseOrDispose {} }
  val current = RenderUpdate.mode(observed ?: selected)
  ActionItem(title="RenderUpdate", summary=current?.label ?: "Custom shader preset active", icon=null) {
    selected=store.selected(game)
    mode=RenderUpdate.mode(selected) ?: RenderUpdate.Mode.SHARPEN
    strength=(selected.parameters[RenderUpdate.PARAMETER] ?: .25f).coerceIn(0f,1f)
    message="";open=true
  }
  fun save(useGlobal: Boolean = false) {
    if (busy) return
    busy=true; message=""
    scope.launch {
      val result=runCatching { withContext(Dispatchers.IO) {
        if (useGlobal && game!=null) store.reset(game) else {
          if (mode!=RenderUpdate.Mode.OFF) store.installRenderUpdate(restore=true)
          store.save(game,RenderUpdate.selection(mode,strength))
        }
      } }
      busy=false
      result.onSuccess { selected=store.selected(game);open=false;onSaved() }
        .onFailure { message=it.message ?: "Cannot save RenderUpdate settings" }
    }
  }
  if (open) AlertDialog(onDismissRequest={if(!busy)open=false},title={Text("RenderUpdate")},
    text={ Column(Modifier.verticalScroll(rememberScrollState())) {
      Text(if(game==null) "Global graphics" else "Graphics for this game",style=MaterialTheme.typography.labelLarge)
      Text("Uses the shader slot and replaces its selected preset. Requires the Vulkan 1.3 Slang runtime.",Modifier.padding(vertical=8.dp))
      for (choice in RenderUpdate.Mode.entries) Row(Modifier.fillMaxWidth().clickable(enabled=!busy){mode=choice}) {
        RadioButton(selected=mode==choice,onClick={mode=choice},enabled=!busy)
        Text(choice.label,Modifier.padding(top=12.dp))
      }
      if(mode.sharpens) {
        Text("Sharpness: ${(strength*100).toInt()}%",Modifier.padding(top=8.dp))
        Slider(value=strength,onValueChange={strength=it},enabled=!busy,valueRange=0f..1f,steps=19)
      }
      Text("SMAA uses 4 passes; adding sharpening uses 5. Internal resolution scaling still applies. The normal FXAA and SGSR presentation paths are bypassed while a Slang preset is active.",style=MaterialTheme.typography.bodySmall)
      if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp))
      if(message.isNotEmpty()) Text(message,color=MaterialTheme.colorScheme.error)
    } },
    confirmButton={TextButton(enabled=!busy,onClick={save()}){Text("Apply")}},
    dismissButton={Row {
      if(game!=null) TextButton(enabled=!busy,onClick={save(useGlobal=true)}){Text("Use global")}
      TextButton(enabled=!busy,onClick={open=false}){Text("Cancel")}
    }})
}
