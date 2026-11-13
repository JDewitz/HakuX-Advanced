package com.rfandango.haku_x.advanced

import java.util.Locale

enum class CompatibilityMode(val label: String) { AUTO("Auto"), ON("On"), OFF("Off") }

/** Only certificate identity counts; path/folder names never identify a title. */
data class CompatibilityIdentity(val titleId: Long, val revision: Long, val xbeSha256: String? = null) {
  companion object {
    fun fromGameKey(key: String?): CompatibilityIdentity? {
      val match = key?.let { Regex("^([0-9a-fA-F]{8})\\.([0-9]+)$").matchEntire(it) } ?: return null
      val title = match.groupValues[1].toLongOrNull(16)?.takeIf { it != 0L } ?: return null
      val revision = match.groupValues[2].toLongOrNull()?.takeIf { it in 0..0xffffffffL } ?: return null
      return CompatibilityIdentity(title, revision)
    }
  }
}

data class CompatibilityRule(
  val id: String,
  val titleId: Long,
  val revisions: Set<Long>,
  val emulatorBase: String,
  val automatic: Boolean,
  val reason: String,
  val evidence: String,
  val xbeHashes: Set<String> = emptySet(),
  val restartRequired: Boolean = true,
  val onlinePolicy: String = "Allowed in protected online sessions: allocation layout only; no guest code injection",
) {
  fun matches(identity: CompatibilityIdentity?, emulatorVersion: String): Boolean = identity != null &&
    identity.titleId == titleId && identity.revision in revisions &&
    emulatorVersion.substringBefore('-') == emulatorBase &&
    (xbeHashes.isEmpty() || identity.xbeSha256?.lowercase(Locale.ROOT)?.let { it in xbeHashes } == true)
}

data class CompatibilityDecision(val enabled: Boolean, val mode: CompatibilityMode, val reason: String, val ruleId: String?) {
  val summary: String get() = "${mode.label} → ${if (enabled) "On" else "Off"} · $reason"
  val argument: String get() = if (enabled) "pool=high" else "pool=default"
}

/** Bundled, reviewed rules only. This is not an executable patch-pack loader. */
object CompatibilityProfiles {
  internal const val JADE_EMPIRE_TITLE_ID = 0x4D53006DL

  fun isJadeEmpire(game: String?) = CompatibilityIdentity.fromGameKey(game)?.titleId == JADE_EMPIRE_TITLE_ID

  fun useSagacious(game: String?, enabled: Boolean, forceOn: Boolean) =
    isJadeEmpire(game) && (enabled || forceOn)

  fun prebuildShadersDefault(game: String?, appDefault: Boolean) =
    appDefault && !isJadeEmpire(game)

  val haloPool = CompatibilityRule(
    id = "halo-ce-high-pool-v1", titleId = 0x4D530004L,
    revisions = setOf(8L), emulatorBase = "1.3.0", automatic = true,
    reason = "High initial kernel pool placement for Halo CE's new-game bay crash",
    evidence = "docs/HALO5.9.1.md: user reports successful workaround and 5.9 operation; certificate title ID/revision supplied as 4D530004.8; independent logs/repeat tests pending",
  )
  val rules: List<CompatibilityRule> = listOf(haloPool)

  fun resolvePool(mode: CompatibilityMode, gameKey: String?, emulatorVersion: String,
                  catalog: List<CompatibilityRule> = rules): CompatibilityDecision {
    if (mode != CompatibilityMode.AUTO) return CompatibilityDecision(mode == CompatibilityMode.ON, mode, "Explicit startup profile override", null)
    val identity = CompatibilityIdentity.fromGameKey(gameKey)
      ?: return CompatibilityDecision(false, mode, "No verified title/revision identity", null)
    val rule = catalog.singleOrNull { it.automatic && it.matches(identity, emulatorVersion) }
    if (rule != null) return CompatibilityDecision(true, mode, rule.reason, rule.id)
    val candidate = catalog.firstOrNull { it.titleId == identity.titleId }
    return CompatibilityDecision(false, mode,
      if (candidate != null) "No approved automatic rule for this revision/build; use On to test"
      else "No matching automatic rule", null)
  }
}
