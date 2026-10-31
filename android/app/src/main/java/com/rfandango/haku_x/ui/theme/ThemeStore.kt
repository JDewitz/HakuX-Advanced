package com.rfandango.haku_x.ui.theme

import android.content.Context
import android.util.AtomicFile
import java.io.File

/** A complete selected palette is stored atomically; both app processes read it fresh. */
object ThemeStore {
  private fun file(context: Context) = AtomicFile(File(context.filesDir, "ui-theme.json"))
  fun read(context: Context): ThemeSpec = runCatching {
    val f = file(context)
    f.openRead().use { input ->
      val bytes = input.readBytesLimited()
      ThemeSpec.parse(bytes.toString(Charsets.UTF_8))
    }
  }.getOrDefault(BuiltInThemes.xbox)
  fun save(context: Context, spec: ThemeSpec) {
    val bytes = ThemeSpec.parse(spec.json()).json().toByteArray(Charsets.UTF_8)
    val f = file(context); val stream = f.startWrite()
    try { stream.write(bytes); f.finishWrite(stream) } catch (e: Exception) { f.failWrite(stream); throw e }
  }
}

fun java.io.InputStream.readBytesLimited(): ByteArray {
  val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(4096)
  while (true) {
    val n = read(buf); if (n < 0) break
    require(out.size() + n <= ThemeSpec.MAX_BYTES) { "Theme exceeds 32 KiB" }
    out.write(buf, 0, n)
  }
  return out.toByteArray()
}
