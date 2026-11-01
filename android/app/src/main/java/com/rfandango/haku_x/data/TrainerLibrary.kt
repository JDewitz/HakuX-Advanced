package com.rfandango.haku_x.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrainerLibrary(private val context: Context) {
  private val prefs = context.getSharedPreferences("trainer_library", Context.MODE_PRIVATE)
  val catalog = TrainerCatalog(File(context.filesDir,"trainer-catalog"))
  var folder: Uri?
    get() = prefs.getString("folder",null)?.let(Uri::parse)
    set(value) { check(prefs.edit().putString("folder",value?.toString()).commit()) { "Cannot save trainer folder" } }
  private val mutex=Mutex()
  data class Scan(val entries: List<TrainerEntry>, val message: String)
  suspend fun scan(): Scan = mutex.withLock { scanFolder() }
  private suspend fun scanFolder(): Scan {
    val old = catalog.read()
    val uri = folder ?: return Scan(old,"Choose a folder containing classic .etm trainers, or import individual files.")
    val root = DocumentFile.fromTreeUri(context,uri)
    if (!Storage.hasReadPermission(context,uri) || root == null || !root.exists() || !root.isDirectory)
      return Scan(old,"Trainer folder unavailable. Showing cached trainers; reselect the folder if access was revoked.")
    val entries = mutableListOf<TrainerEntry>(); val errors = mutableListOf<String>(); val visited = HashSet<String>()
    var inspected = 0
    suspend fun walk(dir: Uri, path: String, depth: Int) {
      currentCoroutineContext().ensureActive()
      require(depth<=32) { "Trainer folder nesting exceeds 32 levels" }
      if (!visited.add(dir.toString())) return
      val childrenUri=DocumentsContract.buildChildDocumentsUriUsingTree(uri,DocumentsContract.getDocumentId(dir))
      val children=context.contentResolver.query(childrenUri,arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use { cursor ->
        buildList<Triple<Uri,String,Boolean>> {
          while(cursor.moveToNext()) {
            require(++inspected<=20000) { "Trainer folder exceeds 20000 entries; choose a smaller folder" }
            add(Triple(DocumentsContract.buildDocumentUriUsingTree(uri,cursor.getString(0)),
              cursor.getString(1) ?: "unnamed",cursor.getString(2)==DocumentsContract.Document.MIME_TYPE_DIR))
          }
        }
      } ?: error("Cannot list trainer subfolder: $path")
      for ((child,name,isDirectory) in children) {
        currentCoroutineContext().ensureActive()
        val rel=if(path.isEmpty()) name else "$path/$name"
        if(isDirectory) walk(child,rel,depth+1)
        else if(name.endsWith(".etm",ignoreCase=true)) {
          try {
            val bytes=context.contentResolver.openInputStream(child)?.use { it.readTrainerBytes() } ?: error("Cannot open file")
            entries+=catalog.cache(bytes,"Folder: $rel")
          } catch(e: Exception) { errors+="$rel: ${e.message}" }
        }
      }
    }
    // A failed traversal never replaces the previous index with a partial scan.
    try { walk(root.uri,"",0) }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { return Scan(old,"Scan incomplete; showing previous catalog. ${e.message}") }
    currentCoroutineContext().ensureActive()
    val manual = old.mapNotNull { e -> e.sources.filter { it.startsWith("Imported: ") }.takeIf { it.isNotEmpty() }?.let { e.copy(sources=it) } }
    val merged = TrainerCatalog.merge(entries+manual);catalog.save(merged)
    return Scan(merged,buildString {
      append("${merged.size} trainers found; ${entries.size-TrainerCatalog.merge(entries).size} duplicate files; ${errors.size} invalid/unreadable.")
      if (errors.isNotEmpty()) append("\n").append(errors.take(5).joinToString("\n"))
    })
  }
  suspend fun importFiles(entries: List<TrainerEntry>) = mutex.withLock { catalog.save(TrainerCatalog.merge(catalog.read()+entries)) }
}
