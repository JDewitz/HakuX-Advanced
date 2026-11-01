package com.rfandango.haku_x.ui.library

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rfandango.haku_x.data.*

@Composable
fun LibraryViewPicker(mode: LibraryViewMode, onPick: (LibraryViewMode) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.Menu, "Library view: ${mode.label}") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      LibraryViewMode.entries.forEach { option ->
        DropdownMenuItem(text = { Text((if (option == mode) "✓ " else "") + option.label) },
          modifier = Modifier.semantics { selected = option == mode },
          onClick = { expanded = false; onPick(option) })
      }
    }
  }
}

@Composable
fun FeatureBadges(features: GameFeatures, modifier: Modifier = Modifier) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    if (features.trainerEnabled) Icon(Icons.Filled.Build, "Trainer enabled for next fresh launch",
      Modifier.size(19.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(2.dp),
      tint = MaterialTheme.colorScheme.onPrimaryContainer)
    if (features.customSetup) Icon(Icons.Filled.Settings, "Custom per-game configuration or mods",
      Modifier.size(19.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).padding(2.dp),
      tint = MaterialTheme.colorScheme.onSecondaryContainer)
  }
}

@Composable
fun LibraryStatsHeader(games: List<Game>, features: Map<android.net.Uri, GameFeatures>) {
  val context = LocalContext.current
  val bytes = LibraryMarkers.knownBytes(games.map { it.sizeBytes })
  Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
    Text("Library stats · shown games", style = MaterialTheme.typography.titleMedium)
    Text("${games.size} games · ${Formatter.formatShortFileSize(context, bytes)} known image storage")
    Text("${games.count { features[it.uri]?.trainerEnabled == true }} trainers enabled · ${games.count { features[it.uri]?.customSetup == true }} custom setups",
      style = MaterialTheme.typography.bodySmall)
    Text("Image/configuration data; playtime and gameplay progress are not tracked yet.", style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
fun LibraryGameContent(
  game: Game, disc: DiscInfo?, features: GameFeatures, mode: LibraryViewMode,
  icon: @Composable () -> Unit, onPlay: () -> Unit, onMenu: () -> Unit,
) {
  val context = LocalContext.current
  val colors = MaterialTheme.colorScheme
  val size = if (game.sizeBytes > 0) Formatter.formatShortFileSize(context,game.sizeBytes) else "Size unknown"
  val format = game.relativePath.substringAfterLast('.').uppercase()
  val regions = disc?.let { d -> listOfNotNull(
    "NTSC-U".takeIf { d.regions and 1 != 0 }, "NTSC-J".takeIf { d.regions and 2 != 0 }, "PAL".takeIf { d.regions and 4 != 0 }
  ).joinToString(" / ").ifEmpty { "Region unknown" } } ?: "Region unknown"
  @Composable fun markedIcon() {
    Box { icon(); FeatureBadges(features, Modifier.align(Alignment.TopEnd)) }
  }
  if (mode == LibraryViewMode.ICONS) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      markedIcon()
      Text(game.title, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
  } else {
    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        markedIcon()
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
          Text(game.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
          if (disc != null && disc.titleId != 0L) Text("${disc.titleIdText} · v${disc.version} · $regions",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
          if (mode != LibraryViewMode.CARDS) Text("$format · $size", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
      }
      if (mode == LibraryViewMode.STATS || mode == LibraryViewMode.INFO) {
        Text(game.relativePath, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(when {
          features.trainerBlocked -> "Trainer blocked · online play protection"
          features.trainerError -> "Trainer unavailable · import it again"
          features.trainerEnabled -> "Trainer enabled · ${features.trainerOptions} options selected"
          features.trainerImported -> "Trainer imported · disabled"
          else -> "No trainer enabled"
        }, style = MaterialTheme.typography.bodySmall)
        Text(if (features.customSetup) "Custom setup" else "Default / global setup", style = MaterialTheme.typography.bodySmall)
      }
      if (mode == LibraryViewMode.INFO) {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = onPlay) { Text("Play") }
          TextButton(onClick = onMenu) { Text("Options") }
        }
      }
    }
  }
}
