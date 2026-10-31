package com.rfandango.haku_x.advanced

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AdvancedArtifacts {
  /** Only finished tool outputs and renderer dumps, never the HDD or shader caches. */
  fun export(context: Context, uri: Uri, memory: String? = null) {
    try {
      val roots = listOf(File(context.getExternalFilesDir(null) ?: context.filesDir, "advanced"), File(context.filesDir, "native/hle"))
      val files = roots.flatMapIndexed { index, dir ->
        if (!dir.exists()) emptyList() else dir.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.mapNotNull { file ->
          val path = file.relativeTo(dir).invariantSeparatorsPath
          if (index == 1 && !path.startsWith("hle_shadow/")) null else file to ((if (index == 0) "advanced/" else "renderer/") + path)
        }.toList()
      }
      require(files.sumOf { it.first.length() } <= 512L * 1024 * 1024) { "Captures exceed the 512 MiB export limit" }
      context.contentResolver.openOutputStream(uri)?.use { out -> ZipOutputStream(out).use { zip ->
        if (memory != null) {
          zip.putNextEntry(ZipEntry("memory-results.txt")); zip.write(memory.toByteArray()); zip.closeEntry()
        }
        for ((file, path) in files) {
          zip.putNextEntry(ZipEntry(path)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
        }
        zip.putNextEntry(ZipEntry("README.txt"))
        zip.write("hakuX Advanced tool outputs. CSVs contain sampled native statistics, not per-frame traces. Renderer dumps are the core's raw formats, not texture-replacement packs.\n".toByteArray()); zip.closeEntry()
      } } ?: error("Could not open the export destination")
    } catch (e: Exception) {
      runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
      throw e
    }
  }
}
