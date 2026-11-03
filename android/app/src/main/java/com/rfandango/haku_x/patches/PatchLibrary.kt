package com.rfandango.haku_x.patches

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream

data class PatchScan(val entries: List<PatchEntry>, val errors: List<String>)

fun InputStream.readPatchBytes(): ByteArray {
  val out = java.io.ByteArrayOutputStream()
  val buffer = ByteArray(8192)
  while (true) {
    val n = read(buffer, 0, minOf(buffer.size, PatchPack.MAX_BYTES + 1 - out.size()))
    if (n < 0) break
    if (n == 0) { val b = read(); if (b < 0) break; out.write(b) } else out.write(buffer, 0, n)
    require(out.size() <= PatchPack.MAX_BYTES) { "Patch exceeds 256 KiB" }
  }
  val bytes = out.toByteArray()
  require(bytes.size <= PatchPack.MAX_BYTES) { "Patch exceeds 256 KiB" }; return bytes
}

class PatchLibrary(private val context: Context) {
  val store = PatchStore(context)
  /** Also called immediately before a fresh launch; cached folder copies never imply availability. */
  fun scan(): PatchScan {
    val found = linkedMapOf<String, MutableList<String>>()
    val errors = mutableListOf<String>()
    val imports = store.imported(); val before = store.folderEntries()
    for (hash in imports) found.getOrPut(hash) { mutableListOf() }.add("Imported copy")
    var count = 0
    fun walk(dir: DocumentFile, depth: Int) {
      require(depth <= 8) { "Patch folder is too deep" }
      val files = dir.listFiles()
      for (f in files) {
        require(++count <= 2048) { "Patch folder scan limit reached" }
        if (f.isDirectory) walk(f, depth + 1)
        else if (f.name?.endsWith(".hakuxpatch", true) == true) {
          require(found.size < 512) { "Too many patch files" }
          try {
            val bytes = context.contentResolver.openInputStream(f.uri)?.use { it.readPatchBytes() } ?: error("Cannot read file")
            val hash = store.cache(bytes); found.getOrPut(hash) { mutableListOf() }.add("Folder: ${f.name}")
          } catch (e: Exception) { errors += "${f.name}: ${e.message}" }
        }
      }
    }
    val folder = store.folder
    if (folder != null) {
      try {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folder)) ?: error("Folder unavailable")
        check(tree.exists() && tree.isDirectory && tree.canRead()) { "Folder unavailable" }
        walk(tree, 0)
      } catch (e: Exception) {
        errors += "Folder: ${e.message}"
        // Partial scans cannot silently activate the subset they happened to see.
        found.keys.toList().filter { it !in imports }.forEach { found.remove(it) }
      }
    }
    val known = (before + found.keys.filter { found[it]?.any { s -> s.startsWith("Folder:") } == true }).take(512).toSet()
    store.setFolderEntries(known)
    val entries = (found.keys + known).distinct().mapNotNull { hash ->
      runCatching { PatchEntry(hash, store.load(hash), found[hash]?.toList() ?: listOf("Folder source unavailable"), hash in found) }
        .onFailure { errors += "Cached ${hash.take(8)}: ${it.message}" }.getOrNull()
    }.sortedWith(compareBy({ it.pack.name.lowercase() }, { it.hash }))
    return PatchScan(entries, errors)
  }
}
