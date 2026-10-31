package com.rfandango.haku_x.advanced

/** A virtual directory tree over installed presets: source/LUT files stay hidden. */
internal object ShaderCatalog {
  data class Entry(val path: String, val name: String, val directory: Boolean)
  fun entries(presets: List<String>, folder: String): List<Entry> {
    val prefix = if (folder.isEmpty()) "" else "$folder/"
    return presets.asSequence().filter { it.startsWith(prefix) }.mapNotNull { path ->
      val tail = path.removePrefix(prefix)
      if (tail.isEmpty()) null else {
        val name = tail.substringBefore('/')
        Entry(prefix + name, name, '/' in tail)
      }
    }.distinctBy { it.path }.sortedWith(compareBy<Entry> { !it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }).toList()
  }
  fun parent(folder: String) = folder.substringBeforeLast('/', "")
  fun up(folder: String): String {
    val parent = parent(folder)
    return if (folder.substringAfterLast('/').startsWith("slang-shaders-") && '/' !in parent) "" else parent
  }
  fun open(presets: List<String>, entry: Entry): String {
    var folder = entry.path
    // GitHub ZIP root is a transport folder, not a shader category.
    if ('/' !in folder) {
      val children = entries(presets, folder)
      if (children.size == 1 && children[0].directory && children[0].name.startsWith("slang-shaders-")) folder = children[0].path
    }
    return folder
  }
  fun next(presets: List<String>, selected: String): String? {
    val folder = if (selected.isNotEmpty()) parent(selected) else
      presets.firstOrNull { it.startsWith("builtin-v1/") }?.let(::parent) ?: presets.firstOrNull()?.let(::parent) ?: return null
    val peers = entries(presets, folder).filter { !it.directory }.map { it.path }
    if (peers.isEmpty()) return null
    val current = peers.indexOf(selected)
    return peers[(current + 1) % peers.size]
  }
}
