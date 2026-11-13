package com.rfandango.haku_x.patches

import com.rfandango.haku_x.advanced.CompatibilityIdentity
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** Approval is release-owned data, never a field an imported file can grant itself. */
internal data class OnlinePatchApproval(
  val contentSha256: String,
  val semanticSha256: String,
  val patchId: String,
  val titleId: Long,
  val revisions: Set<Long>,
  val emulatorBase: String,
  val rationale: String,
  val reviewReference: String,
)

object OnlinePatchRegistry {
  // No imported game fix has been reviewed for online use yet. Halo's kernel
  // pool fix is built into CompatibilityProfiles and does not use this loader.
  internal val approvals: List<OnlinePatchApproval> = emptyList()

  /** Also bind the parsed payload, so a forged hash field cannot authorize different writes. */
  internal fun semanticHash(pack: PatchPack): String {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { out ->
      fun text(value: String) { val b = value.toByteArray(Charsets.UTF_8); out.writeInt(b.size); out.write(b) }
      text("hakux-online-approval-v1")
      for (value in listOf(pack.id, pack.name, pack.version, pack.author, pack.description, pack.emulatorBase)) text(value)
      out.writeLong(pack.titleId)
      out.writeInt(pack.revisions.size); pack.revisions.sorted().forEach { out.writeLong(it) }
      out.writeInt(pack.conflicts.size); pack.conflicts.sorted().forEach { text(it) }
      out.writeInt(pack.writes.size)
      for (op in pack.writes) {
        out.writeLong(op.address)
        out.writeInt(op.expected.size); op.expected.forEach { out.writeInt(it) }
        out.writeInt(op.replacement.size); op.replacement.forEach { out.writeInt(it) }
      }
    }
    return PatchPack.hash(bytes.toByteArray())
  }

  internal fun approval(entry: PatchEntry, game: String?, emulator: String,
                        registry: List<OnlinePatchApproval> = approvals): OnlinePatchApproval? {
    val identity = CompatibilityIdentity.fromGameKey(game) ?: return null
    if (entry.pack.compatibility != null || !entry.available || !entry.pack.matches(game, emulator)) return null
    val hashPattern = Regex("[0-9a-f]{64}")
    return registry.singleOrNull { r ->
      hashPattern.matches(r.contentSha256) && hashPattern.matches(r.semanticSha256) &&
        r.contentSha256 == entry.hash && r.semanticSha256 == semanticHash(entry.pack) &&
        r.patchId == entry.pack.id && r.titleId == identity.titleId &&
        identity.revision in r.revisions && r.revisions.isNotEmpty() &&
        r.revisions.all { it in entry.pack.revisions } &&
        r.emulatorBase == emulator.substringBefore('-') && r.emulatorBase == entry.pack.emulatorBase &&
        r.rationale.isNotBlank() && r.reviewReference.isNotBlank()
    }
  }

  fun description(entry: PatchEntry, game: String?, emulator: String): String =
    if (entry.pack.sagacious) "Renderer workaround only · allowed in protected sessions"
    else approval(entry, game, emulator)?.let { "Reviewed for online play · ${it.reviewReference}\n${it.rationale}" }
      ?: "Not approved for online play · offline only"
}

/** Pure online serializer: it cannot carry ETM, cheats, files, refresh or arbitrary manifest lines. */
internal object OnlineCompatibilityManifest {
  fun forLaunch(launch: PatchLaunch, game: String, emulator: String,
                registry: List<OnlinePatchApproval> = OnlinePatchRegistry.approvals): List<String> {
    require(launch.loaded.toSet() == launch.loadedEntries.map { it.hash }.toSet()) { "Compatibility snapshot differs" }
    // launch.lines is deliberately ignored, even if it contains legacy ETM/cheat/file commands.
    return lines(launch.loadedEntries, game, emulator, registry)
  }
  fun lines(entries: List<PatchEntry>, game: String, emulator: String,
            registry: List<OnlinePatchApproval> = OnlinePatchRegistry.approvals): List<String> {
    require(entries.map { it.hash }.distinct().size == entries.size) { "Duplicate compatibility payload" }
    val decisions = PatchPlanner.plan(entries, entries.map { it.hash }.toSet(), game, emulator,
      online = true, onlineApprovals = registry)
    require(decisions.all { it.ready }) { "Online compatibility manifest contains an unapproved or conflicting patch" }
    return entries.filterNot { it.pack.sagacious }.sortedBy { it.hash }.flatMap { e -> e.pack.writes.map { op ->
      "patch\tdefault.xbe\tp-${e.hash.take(32)}\t${op.line()}"
    } }
  }
}
