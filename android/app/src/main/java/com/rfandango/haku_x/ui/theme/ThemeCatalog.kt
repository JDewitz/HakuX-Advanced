package com.rfandango.haku_x.ui.theme

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

/** Imported palettes are independent files: a damaged entry cannot hide the whole catalog. */
class ThemeCatalog(private val directory: File) {
  companion object {
    private val lock = Any()
    fun id(spec: ThemeSpec): String {
      val canonical = buildString {
        append(spec.name).append('\n')
        for (p in listOf(spec.light, spec.dark)) for (key in ThemePalette.KEYS) append(p.colors.getValue(key)).append('\n')
      }
      return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun choices(selected: ThemeSpec, imported: List<ThemeSpec>): List<ThemeSpec> =
      (BuiltInThemes.all + imported + selected).distinct()

    /** Same-name variants remain visible and distinguishable without changing their export names. */
    fun label(theme: ThemeSpec, choices: List<ThemeSpec>): String {
      if (theme in BuiltInThemes.all) return theme.name
      val variants = choices.filter { it !in BuiltInThemes.all && it.name == theme.name }
      val suffix = if (variants.size > 1) " ${variants.indexOf(theme) + 1}" else ""
      return "${theme.name} (imported$suffix)"
    }
  }

  fun read(): List<ThemeSpec> = synchronized(lock) {
    directory.listFiles().orEmpty().filter { it.isFile && it.extension == "json" }.mapNotNull { file ->
      runCatching { file.inputStream().use { ThemeSpec.parse(it.readBytesLimited().toString(Charsets.UTF_8)) } }.getOrNull()
    }.filter { it !in BuiltInThemes.all }.distinct()
      .sortedWith(compareBy<ThemeSpec> { it.name.lowercase(Locale.ROOT) }.thenBy { id(it) })
  }

  /** Exact duplicates are harmless; imported names never become filesystem paths. */
  fun add(value: ThemeSpec): Boolean = synchronized(lock) {
    val spec = ThemeSpec.parse(value.json())
    if (spec in BuiltInThemes.all) return false
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create theme library" }
    val target = File(directory, "${id(spec)}.json")
    if (target.isFile && runCatching { target.inputStream().use { ThemeSpec.parse(it.readBytesLimited().toString(Charsets.UTF_8)) } }.getOrNull() == spec) return false
    val temp = File.createTempFile("theme-", ".tmp", directory)
    try {
      FileOutputStream(temp).use { it.write(spec.json().toByteArray(Charsets.UTF_8)); it.fd.sync() }
      check(temp.renameTo(target)) { "Cannot save imported theme" }
    } finally { temp.delete() }
    true
  }
}
