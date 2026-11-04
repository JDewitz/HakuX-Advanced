package com.rfandango.haku_x.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.IntentCompat
import com.rfandango.haku_x.BuildConfig
import com.rfandango.haku_x.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.security.MessageDigest
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Advanced-only release discovery; APK identity, signer, checksum and downgrade checks are mandatory. */
object Updates {
  private const val TAG = "hakuX-update"
  private const val TIMEOUT_MS = 15_000

  class Release internal constructor(
    /** Advanced display version, e.g. "1.3.0-advanced5.19.1". */
    val version: String,
    val title: String,
    /** The release notes, Markdown. */
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String,
    internal val key: UpdatePolicy.Version,
  )

  private val _available = MutableStateFlow<Release?>(null)

  /** The newer release a check found (the library's update button), or null. */
  val available: StateFlow<Release?> = _available

  private val startChecked = AtomicBoolean(false)

  /**
   * The version this app is: its versionName. Debug builds take the one in files/debug_update_version
   * instead, when there is one (to try the update path against an older version).
   */
  fun installedVersion(context: Context): String {
    if (BuildConfig.DEBUG) {
      runCatching { File(context.filesDir, "debug_update_version").readText().trim() }.getOrNull()
        ?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return BuildConfig.VERSION_NAME
  }

  /**
   * The latest release, when newer than this app (null: none newer, or no release with an APK).
   * Network and format failures throw.
   */
  suspend fun check(context: Context): Release? = withContext(Dispatchers.IO) {
    val previews = AppPrefs(context).updatePreviews
    val releases = JSONArray()
    // GitHub lists by publication time, not our version. Inspect a bounded five pages.
    for (page in 1..5) {
      currentCoroutineContext().ensureActive()
      val conn = open("https://api.github.com/repos/${UpdatePolicy.REPO}/releases?per_page=100&page=$page")
      conn.setRequestProperty("Accept", "application/vnd.github+json")
      try {
        when (conn.responseCode) {
          HttpURLConnection.HTTP_OK -> Unit
          HttpURLConnection.HTTP_NOT_FOUND -> break
          else -> throw IOException("GitHub answered ${conn.responseCode}")
        }
        val bytes = conn.inputStream.use { input ->
          val output = java.io.ByteArrayOutputStream()
          val buffer = ByteArray(8192)
          while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            if (output.size() + n > 2 * 1024 * 1024) throw IOException("Release listing is too large")
            output.write(buffer, 0, n)
          }
          output.toByteArray()
        }
        if (bytes.size > 2 * 1024 * 1024) throw IOException("Release listing is too large")
        val batch = JSONArray(bytes.toString(Charsets.UTF_8))
        for (i in 0 until batch.length()) releases.put(batch.getJSONObject(i))
        if (batch.length() < 100) break
      } finally { conn.disconnect() }
    }
    val candidate = UpdatePolicy.select(releases, installedVersion(context), previews)
    if (AppPrefs(context).updatePreviews != previews) return@withContext null
    candidate?.let { Release(it.version, it.title, it.notes, it.url, it.size, it.sha256, it.key) }
  }.also { _available.value = it }

  fun clearAvailable() { _available.value = null }

  /**
   * The check as the app's screens start: once a process, quietly (a failure is only logged).
   * Leftover downloads go first. The release found, or null.
   */
  suspend fun checkAtStart(context: Context): Release? {
    if (!startChecked.compareAndSet(false, true)) return null
    withContext(Dispatchers.IO) { File(context.cacheDir, "updates").deleteRecursively() }
    return runCatching { check(context) }
      .onFailure { Log.w(TAG, "check at start: ${it.message}") }
      .getOrNull()
  }

  /**
   * The release's APK, downloaded into the cache (progress: bytes so far, the total or 0), and
   * checked against its digest, current package and current signer on every build.
   * Cancelled with its coroutine.
   */
  suspend fun download(context: Context, release: Release, progress: (Long, Long) -> Unit): File =
    withContext(Dispatchers.IO) {
      val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
      val apk = File(dir, "hakuX-${release.version}.apk")
      val part = File(dir, apk.name + ".part")
      val conn = open(release.apkUrl)
      try {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("the download answered ${conn.responseCode}")
        val total = release.apkSize
        if (total !in 1..UpdatePolicy.MAX_APK_BYTES) throw IOException("Invalid update size")
        if (conn.contentLengthLong > 0 && conn.contentLengthLong != total) throw IOException("Update size changed")
        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        conn.inputStream.use { input ->
          part.outputStream().use { out ->
            val buf = ByteArray(1 shl 16)
            while (true) {
              currentCoroutineContext().ensureActive()
              val n = input.read(buf)
              if (n < 0) break
              if (done + n > total) throw IOException("Update exceeds expected size")
              digest.update(buf, 0, n)
              out.write(buf, 0, n)
              done += n
              progress(done, total)
            }
          }
        }
        if (done != total) throw IOException("the download stopped at $done of $total bytes")
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        if (actual != release.sha256) throw IOException("Update checksum does not match GitHub")
      } catch (e: Exception) {
        part.delete()
        throw e
      } finally {
        conn.disconnect()
      }
      if (!part.renameTo(apk)) throw IOException("can't keep the download")
      try { verify(context, apk, release.key) } catch (e: Exception) { apk.delete(); throw e }
      apk
    }

  /** Enforced for development builds too: same package, same current signer, strictly newer code. */
  private fun verify(context: Context, apk: File, expected: UpdatePolicy.Version? = null) {
    val pm = context.packageManager
    val info = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
      ?: throw IOException("The download isn't an APK")
    if (info.packageName != context.packageName) throw IOException("The download is not HakuX-Advanced for this install")
    val ours = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    val oursSigners = ours.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
    val theirSigners = info.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
    if (oursSigners.isEmpty() || oursSigners != theirSigners) throw IOException("The update isn't signed as this app")
    if (info.longVersionCode <= ours.longVersionCode) throw IOException("The update must have a newer Android version code")
    val key = UpdatePolicy.installedVersion(info.versionName.orEmpty()) ?: throw IOException("Not an Advanced version")
    if (expected != null && key.compareTo(expected) != 0) throw IOException("APK version does not match its release tag")
    val installed = UpdatePolicy.installedVersion(BuildConfig.VERSION_NAME) ?: throw IOException("Unknown installed version")
    if (key <= installed) throw IOException("The update must have a newer Advanced version")
  }

  /**
   * The APK to the system installer (a PackageInstaller session): it asks the user, then replaces
   * this app (UpdateInstallReceiver takes its answers).
   */
  suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
    verify(context, apk)
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
    params.setAppPackageName(context.packageName)
    params.setSize(apk.length())
    val id = installer.createSession(params)
    installer.openSession(id).use { session ->
      session.openWrite("hakuX.apk", 0, apk.length()).use { out ->
        apk.inputStream().use { it.copyTo(out, 1 shl 16) }
        session.fsync(out)
      }
      val flags = PendingIntent.FLAG_UPDATE_CURRENT or
        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
      val status = PendingIntent.getBroadcast(
        context, id, Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName), flags,
      )
      session.commit(status.intentSender)
    }
  }

  private fun open(url: String): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = TIMEOUT_MS
      readTimeout = TIMEOUT_MS
      instanceFollowRedirects = true      // a release asset redirects to GitHub's file host
      setRequestProperty("User-Agent", "hakuX/${BuildConfig.VERSION_NAME} (Android)")
    }
}

/**
 * The system installer's answers about an update (Updates.install): its confirmation to show (the
 * user's), or why it failed. On success this app is replaced, its process gone.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    when (status) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let {
          context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
      }
      PackageInstaller.STATUS_SUCCESS -> Log.i("hakuX-update", "installed")
      PackageInstaller.STATUS_FAILURE_ABORTED -> Log.i("hakuX-update", "install cancelled")
      else -> {
        val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
        Log.w("hakuX-update", "install failed: $why")
        Toast.makeText(context, context.getString(R.string.update_install_failed, why), Toast.LENGTH_LONG).show()
      }
    }
  }
}
