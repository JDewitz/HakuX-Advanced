package com.rfandango.haku_x

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.rfandango.haku_x.advanced.AdvancedScreen
import com.rfandango.haku_x.advanced.ShaderBrowserScreen
import com.rfandango.haku_x.ui.theme.HakuXTheme

/** Shared Graphics screen: no native library is loaded by the launcher. */
class ShaderSettingsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val game = intent.getStringExtra("game")
    setContent {
      HakuXTheme {
        var mapping by rememberSaveable { mutableStateOf(false) }
        androidx.activity.compose.BackHandler(mapping) { mapping = false }
        if (mapping) AdvancedScreen(null) { mapping = false }
        else ShaderBrowserScreen(game, onBack = { finish() }, onKeys = { mapping = true },
          onSelected = { if (intent.getBooleanExtra("returnToGame", false)) finish() })
      }
    }
  }
  companion object {
    fun open(context: Context, game: String? = null, returnToGame: Boolean = false) {
      context.startActivity(Intent(context, ShaderSettingsActivity::class.java)
        .putExtra("game", game).putExtra("returnToGame", returnToGame))
    }
  }
}
