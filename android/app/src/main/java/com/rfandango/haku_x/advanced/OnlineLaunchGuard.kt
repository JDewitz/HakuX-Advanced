package com.rfandango.haku_x.advanced

import java.util.concurrent.TimeUnit

/** Legacy debug NC_NET overrides run after CLI parsing, so offline sessions must reject them. */
object OnlineLaunchGuard {
  fun hasNetworkOverride(environment: String?, properties: List<String?>): Boolean =
    !environment.isNullOrBlank() || properties.any { value ->
      value == null || value.split(';').any { it.substringBefore('=').trim() == "NC_NET" }
    }
  fun pin(requested: OnlinePlayPolicy, environment: String?, properties: List<String?>): OnlinePlayPolicy =
    if (!requested.onlinePlay && hasNetworkOverride(environment,properties)) OnlinePlayPolicy(true) else requested

  fun resolve(requested: OnlinePlayPolicy): OnlinePlayPolicy {
    if(requested.onlinePlay) return requested
    val properties=listOf("debug.hakux.env","debug.hakux.env2").map { name -> runCatching {
      val p=ProcessBuilder("/system/bin/getprop",name).start()
      try {
        check(p.waitFor(2,TimeUnit.SECONDS) && p.exitValue()==0) { "Cannot verify native network overrides" }
        p.inputStream.bufferedReader().use { it.readText().trim() }
      } finally { p.destroy() }
    }.getOrNull() }
    return pin(requested,System.getenv("NC_NET"),properties)
  }
}
