package com.rfandango.haku_x.advanced

import org.json.JSONObject

/** Immutable per-process session contract: injected trainers and networking never coexist. */
data class OnlinePlayPolicy(val onlinePlay: Boolean = true) {
  val allowGuestMods get() = !onlinePlay
  // Snapshot loading is independent of trainer/network protection.
  val allowStateLoad get() = true
  val allowMemoryTools get() = !onlinePlay
  fun networkMode(configured: String): String = if (onlinePlay) configured else "off"
  fun propagateFrom(parent: Boolean?) = OnlinePlayPolicy(parent ?: onlinePlay)
  companion object {
    // Missing, damaged and non-boolean settings fail closed, including legacy installs.
    fun fromJson(o: JSONObject) = OnlinePlayPolicy(o.opt("onlinePlay") as? Boolean ?: true)
    const val BLOCK_MESSAGE = "Blocked by online play protection. Fully exit the game, disable Online play protection in Advanced, then start an offline trainer session."
  }
}
