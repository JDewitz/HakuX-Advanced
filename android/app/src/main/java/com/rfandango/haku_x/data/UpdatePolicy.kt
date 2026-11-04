package com.rfandango.haku_x.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Only this fork's versioned arm64 APK releases are eligible, never source/theme packs. */
internal object UpdatePolicy {
  const val REPO = "JDewitz/HakuX-Advanced"
  const val MAX_APK_BYTES = 128L * 1024 * 1024
  private val tagPattern = Regex("^advanced-(\\d+\\.\\d+\\.\\d+)-(\\d+(?:\\.\\d+){1,2})(?:-preview\\.(\\d+))?$")
  private val installedPattern = Regex("^(\\d+\\.\\d+\\.\\d+)-advanced(\\d+(?:\\.\\d+){1,2})(?:-preview\\.(\\d+))?(?:-[A-Za-z][A-Za-z0-9.-]*)?$")
  data class Version(val base: List<Int>, val advanced: List<Int>, val preview: Int?) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
      for ((a, b) in listOf(base to other.base, advanced to other.advanced)) {
        for (i in 0 until maxOf(a.size, b.size)) {
          val c = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
          if (c != 0) return c
        }
      }
      if (preview == null && other.preview != null) return 1
      if (preview != null && other.preview == null) return -1
      return (preview ?: 0).compareTo(other.preview ?: 0)
    }
  }
  private fun parse(m: MatchResult?): Version? = runCatching {
    m ?: return null
    Version(m.groupValues[1].split('.').map(String::toInt),
      m.groupValues[2].split('.').map(String::toInt), m.groupValues[3].takeIf(String::isNotEmpty)?.toInt())
  }.getOrNull()
  fun tagVersion(tag: String) = parse(tagPattern.matchEntire(tag))
  fun installedVersion(name: String) = parse(installedPattern.matchEntire(name))
  data class Candidate(val key: Version, val tag: String, val version: String, val title: String,
    val notes: String, val url: String, val size: Long, val sha256: String)

  fun select(releases: JSONArray, installed: String, previews: Boolean): Candidate? {
    val current = installedVersion(installed) ?: return null // Unknown identity: fail closed.
    return (0 until releases.length()).mapNotNull { i ->
      runCatching { candidate(releases.getJSONObject(i), previews) }.getOrNull()
    }.filter { it.key > current }.maxByOrNull { it.key }
  }
  private fun candidate(r: JSONObject, previews: Boolean): Candidate? {
    if (r.optBoolean("draft")) return null
    val tag = r.getString("tag_name")
    val key = tagVersion(tag) ?: return null
    if (!previews && (r.optBoolean("prerelease") || key.preview != null)) return null
    val base = key.base.joinToString(".")
    val advanced = key.advanced.joinToString(".")
    val prefix = "HakuX-Advanced-$base-$advanced"
    val pattern = Regex("^${Regex.escape(prefix)}(?:-[A-Za-z][A-Za-z0-9.-]*)?-arm64\\.apk$")
    val assets = r.optJSONArray("assets") ?: return null
    val matches = (0 until assets.length()).map { assets.getJSONObject(it) }
      .filter { pattern.matches(it.optString("name")) && it.optString("state") == "uploaded" }
    if (matches.size != 1) return null
    val apk = matches.single()
    val name = apk.getString("name")
    val url = apk.getString("browser_download_url")
    val uri = URI(url)
    if (uri.scheme != "https" || uri.host != "github.com" || uri.userInfo != null ||
      uri.port != -1 || uri.query != null || uri.fragment != null ||
      uri.path != "/$REPO/releases/download/$tag/$name") return null
    val size = apk.optLong("size")
    if (size !in 1..MAX_APK_BYTES) return null
    val digest = apk.optString("digest")
    if (!Regex("sha256:[a-fA-F0-9]{64}").matches(digest)) return null
    return Candidate(key, tag, "$base-advanced$advanced" + (key.preview?.let { "-preview.$it" } ?: ""),
      r.optString("name").ifBlank { "HakuX-Advanced $advanced" }, r.optString("body"),
      url, size, digest.removePrefix("sha256:").lowercase())
  }
}
