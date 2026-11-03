package com.rfandango.haku_x.patches

import com.rfandango.haku_x.advanced.CompatibilityIdentity
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Locale

data class PatchWrite(val address: Long, val expected: List<Int>, val replacement: List<Int>) {
  val end: Long get() = address + expected.size
  fun overlaps(other: PatchWrite) = address < other.end && other.address < end
  fun line(): String = "0x${address.toString(16)}: ${hex(expected)} -> ${hex(replacement)}"
  companion object { fun hex(bytes: List<Int>) = bytes.joinToString(" ") { "%02X".format(Locale.ROOT, it) } }
}

data class PatchPack(val id: String, val name: String, val version: String, val author: String,
  val description: String, val titleId: Long, val revisions: Set<Long>, val emulatorBase: String,
  val conflicts: Set<String>, val writes: List<PatchWrite>) {
  fun matches(game: String?, emulator: String): Boolean {
    val identity = CompatibilityIdentity.fromGameKey(game) ?: return false
    return identity.titleId == titleId && identity.revision in revisions && emulator.substringBefore('-') == emulatorBase
  }
  companion object {
    const val MAX_BYTES = 256 * 1024
    private val ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun parse(bytes: ByteArray): PatchPack {
      require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Patch must be 1–256 KiB" }
      val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
      val o = JSONObject(text)
      fun keys(obj: JSONObject, allowed: Set<String>) { require(obj.keys().asSequence().all { it in allowed }) { "Unsupported patch field" } }
      keys(o, setOf("schema", "id", "name", "version", "author", "description", "titleId", "revisions", "emulatorBase", "conflicts", "patches"))
      require(o.opt("schema") is Number && o.getInt("schema") == 1 && o.get("schema").toString() == "1") { "Unsupported schema" }
      fun field(name: String, max: Int = 200): String {
        val v = o.get(name); require(v is String && v.isNotBlank() && v.length <= max && v.none { it == '\t' || it == '\n' || it == '\r' || it.code < 32 }) { "Invalid $name" }; return v
      }
      val id = field("id"); require(ID.matches(id)) { "Invalid patch id" }
      val title = field("titleId"); require(title.matches(Regex("[0-9A-Fa-f]{8}")) && title.toLong(16) != 0L) { "Invalid titleId" }
      val revisions = o.getJSONArray("revisions"); require(revisions.length() in 1..64) { "Explicit revisions required" }
      val versions = (0 until revisions.length()).map { i ->
        val v = revisions.get(i); require(v is Number && v.toString().matches(Regex("[0-9]+"))) { "Revision must be an integer" }
        v.toString().toLong().also { require(it in 0..0xffffffffL) { "Revision out of range" } }
      }.toSet()
      val base = field("emulatorBase"); require(base == "1.3.0") { "Only the 1.3.0 core is supported" }
      val conflicts = o.optJSONArray("conflicts")
      require(!o.has("conflicts") || conflicts != null) { "conflicts must be an array" }
      require((conflicts?.length() ?: 0) <= 64) { "Too many conflicts" }
      val conflictIds = (0 until (conflicts?.length() ?: 0)).map {
        val v = conflicts!!.get(it); require(v is String && ID.matches(v) && v != id) { "Invalid conflict id" }; v
      }.toSet()
      val patches = o.getJSONArray("patches"); require(patches.length() in 1..256) { "Use 1–256 guarded operations" }
      fun bytes(value: Any): List<Int> {
        require(value is String && value.matches(Regex("[0-9a-fA-F]{2}( [0-9a-fA-F]{2}){0,255}"))) { "Bytes must be space-separated hex pairs, at most 256" }
        return value.split(' ').map { it.toInt(16) }
      }
      val writes = (0 until patches.length()).map {
        val p = patches.getJSONObject(it); keys(p, setOf("xbe", "address", "expected", "replacement"))
        require(p.get("xbe") == "default.xbe") { "Version 1 supports default.xbe only" }
        val address = p.get("address"); require(address is String && address.matches(Regex("0x[0-9a-fA-F]{1,8}"))) { "Address must be a 32-bit hex string" }
        val old = bytes(p.get("expected")); val new = bytes(p.get("replacement"))
        require(old.size == new.size) { "Expected and replacement lengths must match" }
        PatchWrite(address.substring(2).toLong(16), old, new).also { require(it.address >= 0x10000 && it.end <= 0x100000000L) { "Address range is invalid" } }
      }
      for (i in writes.indices) require(writes.drop(i + 1).none { writes[i].overlaps(it) }) { "Overlapping operations inside patch" }
      return PatchPack(id, field("name"), field("version", 40), field("author"), field("description", 2000), title.toLong(16), versions, base, conflictIds, writes)
    }
  }
}

data class PatchEntry(val hash: String, val pack: PatchPack, val sources: List<String>, val available: Boolean = true)
data class PatchDecision(val entry: PatchEntry, val enabled: Boolean, val status: String, val ready: Boolean = false)

object PatchPlanner {
  fun plan(entries: List<PatchEntry>, enabled: Set<String>, game: String?, emulator: String, online: Boolean,
           builtIn: List<PatchWrite> = emptyList(), otherMods: Boolean = false, trainer: Boolean = false,
           stateLoad: Boolean = false): List<PatchDecision> {
    val eligible = entries.filter { it.available && it.hash in enabled && it.pack.matches(game, emulator) }
    return entries.map { e ->
      val on = e.hash in enabled
      val reason = when {
        !e.available -> "Unavailable · source missing or unreadable"
        game == null -> "Select a game to configure"
        !e.pack.matches(game, emulator) -> "Incompatible · title, revision or core build differs"
        !on -> "Off"
        online -> "Blocked by online play protection"
        stateLoad -> "Blocked · imported patches require a cold start"
        eligible.size > 32 || eligible.sumOf { it.pack.writes.size } > 256 -> "Conflicting · session patch limit exceeded"
        trainer -> "Conflicting · disable the active trainer first"
        otherMods -> "Conflicting · active built-in XBE file replacement or unsupported patch syntax"
        e.pack.writes.any { a -> builtIn.any { a.overlaps(it) } } -> "Conflicting · overlaps an enabled built-in mod"
        eligible.any { x -> x.hash != e.hash && (x.pack.id == e.pack.id || x.pack.id in e.pack.conflicts || e.pack.id in x.pack.conflicts || e.pack.writes.any { a -> x.pack.writes.any { a.overlaps(it) } }) } -> "Conflicting · another enabled imported patch"
        else -> null
      }
      PatchDecision(e, on, reason ?: "Enabled for next cold launch", reason == null)
    }
  }
  fun preflight(pack: PatchPack, read: (Long, Int) -> ByteArray?): Boolean = pack.writes.all { op ->
    read(op.address, op.expected.size)?.contentEquals(op.expected.map { it.toByte() }.toByteArray()) == true
  }
}
