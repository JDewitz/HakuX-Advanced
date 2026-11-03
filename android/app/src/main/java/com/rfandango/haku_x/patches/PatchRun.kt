package com.rfandango.haku_x.patches

import android.content.Context
import android.net.Uri
import android.util.Log
import com.rfandango.haku_x.BuildConfig
import com.rfandango.haku_x.data.*
import com.rfandango.haku_x.data.hdd.HddStore
import com.rfandango.haku_x.data.hdd.XboxHdd
import org.json.JSONArray
import java.io.File

data class PatchLaunch(val loaded: List<String> = emptyList(), val decisions: List<PatchDecision> = emptyList(),
  val errors: List<String> = emptyList(), val lines: List<String> = emptyList(),
  val loadedEntries: List<PatchEntry> = emptyList())

object PatchRun {
  fun preview(context: Context, entries: List<PatchEntry>, game: String?, online: Boolean): List<PatchDecision> {
    if (game == null) return PatchPlanner.plan(entries, emptySet(), null, BuildConfig.VERSION_NAME, online)
    val enabled = runCatching { PatchStore(context).choices(game) }.getOrDefault(emptySet())
    val mods = if (online) emptyList() else ModCatalog.load(context, game).mods.filter { ModChoices(context, game).mod(it.id) }
    val (ranges, bad) = blockers(mods)
    val replaced = !online && mods.any { ModCatalog.extract(context, game, it.id).any { (path, _) -> path.replace('\\', '/').equals("default.xbe", true) } }
    return PatchPlanner.plan(entries, enabled, game, BuildConfig.VERSION_NAME, online, ranges, bad || replaced,
      !online && runCatching { TrainerStore(context, game).load()?.enabled == true }.getOrDefault(true)).map {
      if (it.ready && hasUpdate(context, game)) it.copy(ready = false, status = "Incompatible · installed title updates are unsupported") else it
    }
  }
  /** Conservative conflicts with the bundled mod engine: imported packs never win by ordering. */
  fun blockers(mods: List<ModInfo>): Pair<List<PatchWrite>, Boolean> {
    val ranges = mutableListOf<PatchWrite>(); var unsupported = false
    for (mod in mods) for ((xbe, raw) in mod.patches) {
      if (!xbe.equals("default.xbe", true)) continue
      try {
        val line = raw.substringBefore('#').trim(); val address = line.substringBefore(':').trim()
        val va = if (address.startsWith("0x", true)) address.drop(2).toLong(16) else address.toLong()
        val values = line.substringAfter(':', "").substringAfter("->").trim().split(Regex("\\s+"))
        require(values.isNotEmpty() && values.all { it.matches(Regex("[0-9A-Fa-f]{2}")) })
        require(va in 0..0xffffffffL && va + values.size <= 0x100000000L)
        ranges += PatchWrite(va, List(values.size) { 0 }, List(values.size) { 0 })
      } catch (_: Exception) { unsupported = true }
    }
    return ranges to unsupported
  }
  fun hasUpdate(context: Context, game: String): Boolean {
    fun child(dir: File, name: String) = dir.listFiles()?.firstOrNull { it.name.equals(name, true) }
    val title = game.substringBefore('.')
    val tdata = child(XboxHdd.folder(HddStore(context).root, 1), "TDATA") ?: return false
    val update = child(child(tdata, title) ?: return false, "\$u") ?: return false
    return update.walkTopDown().any { it.isFile }
  }
  fun prepare(context: Context, game: String, uri: Uri, online: Boolean, mods: List<ModInfo>, trainer: Boolean,
              stateLoad: Boolean, pinned: String?): PatchLaunch {
    val library = PatchLibrary(context); val errors = mutableListOf<String>()
    val entries: List<PatchEntry>; val enabled: Set<String>
    if (pinned != null) {
      val a = JSONArray(pinned); require(a.length() <= 32) { "Too many pinned patches" }
      enabled = (0 until a.length()).map { a.getString(it) }.toSet()
      entries = enabled.map { hash -> PatchEntry(hash, library.store.load(hash), listOf("Session snapshot")) }
    } else {
      val scan = library.scan(); entries = scan.entries; errors += scan.errors; enabled = library.store.choices(game)
    }
    val (ranges, unsupported) = blockers(mods)
    val replaced = mods.any { ModCatalog.extract(context, game, it.id).any { (path, _) -> path.replace('\\', '/').equals("default.xbe", true) } }
    val decisions = PatchPlanner.plan(entries, enabled, game, BuildConfig.VERSION_NAME, online, ranges, unsupported || replaced, trainer, stateLoad)
      .map { if (it.ready && hasUpdate(context, game)) it.copy(ready = false, status = "Incompatible · installed title updates are unsupported") else it }
    val ready = decisions.filter { it.ready }
    if (ready.sumOf { it.entry.pack.writes.size } > 256 || ready.size > 32) return PatchLaunch(decisions = decisions.map {
      if (it.ready) it.copy(ready = false, status = "Conflicting · session patch limit exceeded") else it
    }, errors = errors)
    val accepted = if (ready.isEmpty()) emptySet() else XboxDisc.open(context, uri) { disc ->
      val actualKey = PatchStore.gameKey(GameOverrides.gameKey(disc.titleId, disc.version, null))
      if (actualKey != PatchStore.gameKey(game)) emptySet() else ready.filter { PatchPlanner.preflight(it.entry.pack, disc::readPatchBytes) }.map { it.entry.hash }.toSet()
    }.orEmpty()
    val resolved = decisions.map { if (it.ready && it.entry.hash !in accepted) it.copy(ready = false, status = "Incompatible · original XBE bytes or identity did not match") else it }
    val loaded = resolved.filter { it.ready }.sortedBy { it.entry.hash }
    for (d in resolved.filter { it.enabled }) Log.i("hakuX-patches", "$game ${d.entry.pack.id} ${d.entry.hash}: ${d.status}")
    errors.forEach { Log.w("hakuX-patches", it) }
    return PatchLaunch(loaded.map { it.entry.hash }, resolved.map { if (it.ready) it.copy(status = "Loaded at launch · native writes remain guarded") else it }, errors, loaded.flatMap { d -> d.entry.pack.writes.map {
      "patch\tdefault.xbe\tp-${d.entry.hash.take(32)}\t${it.line()}"
    } }, loaded.map { it.entry })
  }
}
