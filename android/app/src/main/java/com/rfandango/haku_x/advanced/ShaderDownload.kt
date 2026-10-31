package com.rfandango.haku_x.advanced

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import android.os.SystemClock

/** Official repository only; bounded download and staged installation, with cancellation. */
internal object ShaderDownload {
  const val URL = "https://codeload.github.com/libretro/slang-shaders/zip/refs/heads/master"
  const val MAX_DOWNLOAD = 256L * 1024 * 1024
  fun copy(input: InputStream, output: OutputStream, cancelled: () -> Boolean, progress: (Long) -> Unit): Long {
    val buffer = ByteArray(32768); var bytes = 0L
    while (true) {
      check(!cancelled()) { "Cancelled" }
      val n = input.read(buffer); if (n < 0) break
      bytes += n
      require(bytes <= MAX_DOWNLOAD) { "Download exceeds 256 MiB limit" }
      output.write(buffer, 0, n); progress(bytes)
    }
    check(!cancelled()) { "Cancelled" }; return bytes
  }
  fun fetch(cache: File, cancelled: () -> Boolean, progress: (String) -> Unit, install: (File) -> Int): Int {
    val archive = File.createTempFile("libretro-shaders-", ".zip", cache)
    val connection = URI(URL).toURL().openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = 15000; connection.readTimeout = 15000
      connection.instanceFollowRedirects = false
      connection.setRequestProperty("User-Agent", "hakuX-Shaders-Test")
      connection.setRequestProperty("Accept-Encoding", "identity")
      check(!cancelled()) { "Cancelled" }
      check(connection.responseCode == 200) { "GitHub returned HTTP ${connection.responseCode}" }
      val expected = connection.contentLengthLong
      require(expected <= MAX_DOWNLOAD) { "Download exceeds 256 MiB limit" }
      var last = 0L
      val received = connection.inputStream.use { input -> archive.outputStream().use { output ->
        copy(input, output, cancelled) { bytes ->
          val now = SystemClock.elapsedRealtime()
          if (now - last >= 500) {
            last = now
            val size = "%.1f".format(java.util.Locale.ROOT, bytes / 1048576.0)
            progress("Downloaded $size MiB" + if (expected > 0) " (${bytes * 100 / expected}%)" else "")
          }
        }
      } }
      check(expected < 0 || received == expected) { "Incomplete download" }
      check(!cancelled()) { "Cancelled" }
      progress("Installing shader sources, presets and textures…")
      return install(archive)
    } finally { connection.disconnect(); archive.delete() }
  }
}
