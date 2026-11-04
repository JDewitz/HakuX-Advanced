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
  var selected by remember(game) { mutableStateOf(store.renderSelected(game)) }
  var open by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var mode by remember { mutableStateOf(RenderUpdate.Mode.SHARPEN) }
  var strength by remember { mutableFloatStateOf(.25f) }
  var budget by remember { mutableStateOf(RenderUpdate.Budget.QUALITY) }
  var cubic by remember { mutableStateOf(RenderUpdate.Cubic.BALANCED) }
  var subpixel by remember { mutableStateOf(true) }
  var message by remember { mutableStateOf("") }
  LifecycleResumeEffect(game) { selected=store.renderSelected(game); onPauseOrDispose {} }
  val current = RenderUpdate.mode(selected)
  ActionItem(title="RenderUpdate", summary=current?.label ?: "Custom shader preset active", icon=null) {
    selected=store.renderSelected(game)
    mode=RenderUpdate.mode(selected) ?: RenderUpdate.Mode.SHARPEN
    strength=RenderUpdate.strength(selected);budget=RenderUpdate.budget(selected);cubic=RenderUpdate.cubic(selected);subpixel=RenderUpdate.subpixel(selected)
    message="";open=true
  }
  fun save(useGlobal: Boolean = false) {
    if (busy) return
    busy=true; message=""
    scope.launch {
      val result=runCatching { withContext(Dispatchers.IO) {
        if (useGlobal && game!=null) store.resetRender(game) else {
          if (mode!=RenderUpdate.Mode.OFF) store.installRenderUpdate(restore=true)
          store.saveRender(game,RenderUpdate.selection(mode,strength,budget,subpixel,cubic))
        }
      } }
      busy=false
      result.onSuccess { selected=store.renderSelected(game);open=false;onSaved() }
        .onFailure { message=it.message ?: "Cannot save RenderUpdate settings" }
    }
  }
  if (open) AlertDialog(onDismissRequest={if(!busy)open=false},title={Text("RenderUpdate")},
    text={ Column(Modifier.verticalScroll(rememberScrollState())) {
      Text(if(game==null) "Global graphics" else "Graphics for this game",style=MaterialTheme.typography.labelLarge)
      Text("Pre-shader stage. Requires Vulkan 1.3. Select CRT/custom Slang separately; opt in to simultaneous use through Pause → Advanced → Experimental rendering stack.",Modifier.padding(vertical=8.dp))
      for (choice in RenderUpdate.Mode.entries) Row(Modifier.fillMaxWidth().clickable(enabled=!busy){mode=choice}) {
        RadioButton(selected=mode==choice,onClick={mode=choice},enabled=!busy)
        Text(choice.label,Modifier.padding(top=12.dp))
      }
      Text("${mode.passes} passes · ${mode.description}",style=MaterialTheme.typography.bodySmall)
      if(mode.budgeted) {
        Text("AA search budget",Modifier.padding(top=8.dp))
        for(b in RenderUpdate.Budget.entries) Row(Modifier.fillMaxWidth().clickable(enabled=!busy){budget=b}) {
          RadioButton(selected=budget==b,onClick={budget=b},enabled=!busy);Text(b.label,Modifier.padding(top=12.dp))
        }
      }
      if(mode.fxaa) Row(Modifier.fillMaxWidth().clickable(enabled=!busy){subpixel=!subpixel}) {
        Checkbox(checked=subpixel,onCheckedChange={subpixel=it},enabled=!busy);Text("FXAA subpixel smoothing",Modifier.padding(top=12.dp))
      }
      if(mode==RenderUpdate.Mode.BICUBIC) {
        Text("Bicubic shape",Modifier.padding(top=8.dp))
        for(c in RenderUpdate.Cubic.entries) Row(Modifier.fillMaxWidth().clickable(enabled=!busy){cubic=c}) {
          RadioButton(selected=cubic==c,onClick={cubic=c},enabled=!busy);Text(c.label,Modifier.padding(top=12.dp))
        }
      }
      if(mode.sharpens) {
        Text("Sharpness: ${(strength*100).toInt()}%",Modifier.padding(top=8.dp))
        Slider(value=strength,onValueChange={strength=it},enabled=!busy,valueRange=0f..1f,steps=19)
      }
      Text("Internal resolution scaling still applies. All modes add processing cost; lowering game resolution is a separate tradeoff. With stacking OFF and both stages selected, post-render Slang takes priority. Native FXAA and SGSR remain bypassed by the Slang path.",style=MaterialTheme.typography.bodySmall)
      if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp))
      if(message.isNotEmpty()) Text(message,color=MaterialTheme.colorScheme.error)
    } },
    confirmButton={TextButton(enabled=!busy,onClick={save()}){Text("Apply")}},
    dismissButton={Row {
      if(game!=null) TextButton(enabled=!busy,onClick={save(useGlobal=true)}){Text("Use global")}
      TextButton(enabled=!busy,onClick={open=false}){Text("Cancel")}
    }})
}
