package io.github.eslamasabry.oc.android.tiles.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.engine.updates.APP_UPDATE_LATEST_URL
import io.github.eslamasabry.oc.engine.updates.ApkInfo
import io.github.eslamasabry.oc.engine.updates.InstallEvent
import io.github.eslamasabry.oc.engine.updates.InstallState
import io.github.eslamasabry.oc.engine.updates.InstalledApp
import io.github.eslamasabry.oc.engine.updates.UpdateFeed
import io.github.eslamasabry.oc.engine.updates.UpdatePlatform
import io.github.eslamasabry.oc.engine.updates.UpdateRecord
import io.github.eslamasabry.oc.engine.updates.UpdateStore
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Exception class (and status) only: never the message (plan 03 S6, rule 5). */
private fun classify(e: Throwable): Failure =
    when (e) {
        is UnknownHostException -> Failure.Network(NetworkWhy.Dns, Redacted.shape(e))
        is ConnectException -> Failure.Network(NetworkWhy.Refused, Redacted.shape(e))
        is SocketTimeoutException -> Failure.TimedOut(Redacted.shape(e))
        is HttpStatus -> Failure.Server(e.status, details = Redacted.shape(null, e.status))
        is FileNotFoundException -> Failure.StorageRefused(Redacted.shape(e))
        is SecurityException -> Failure.StorageRefused(Redacted.shape(e))
        is IOException -> Failure.Network(NetworkWhy.Unknown, Redacted.shape(e))
        else -> Failure.Unexpected(Redacted.shape(e))
    }

private class HttpStatus(val status: Int) : IOException()

/**
 * GitHub over HTTPS with the platform `HttpURLConnection` (port of Flutter `DioAppUpdateSource`). Redirects to the
 * release CDN are followed (same scheme only). A partial file `<dest>.part` resumes with a Range request; 416 starts
 * over; the finished file is renamed into place.
 */
public class AndroidUpdateFeed(
    private val io: CoroutineDispatcher,
    private val userAgent: String,
    private val feedUrl: String = APP_UPDATE_LATEST_URL,
) : UpdateFeed {
    override suspend fun latest(): Outcome<String> = text(feedUrl, json = true)

    override suspend fun text(url: String): Outcome<String> = text(url, json = false)

    private suspend fun text(url: String, json: Boolean): Outcome<String> =
        withContext(io) {
            attempt(::classify) {
                val c = open(url)
                if (json) c.setRequestProperty("Accept", "application/vnd.github+json")
                try {
                    if (c.responseCode != HttpURLConnection.HTTP_OK) throw HttpStatus(c.responseCode)
                    c.inputStream.use { it.readBytes().decodeToString() }
                } finally {
                    c.disconnect()
                }
            }
        }

    override suspend fun download(url: String, dest: String, onProgress: (Long, Long) -> Unit): Outcome<Unit> =
        withContext(io) {
            attempt(::classify) {
                val target = File(dest)
                target.parentFile?.mkdirs()
                val part = File("$dest.part")
                var have = if (part.isFile) part.length() else 0L
                var c = open(url, readTimeoutMs = 30_000)
                if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                if (c.responseCode == 416) {
                    // The partial file is already all of it, or stale: start over.
                    c.disconnect()
                    part.delete()
                    have = 0
                    c = open(url, readTimeoutMs = 30_000)
                }
                try {
                    val code = c.responseCode
                    if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) throw HttpStatus(code)
                    val resumed = code == HttpURLConnection.HTTP_PARTIAL
                    if (!resumed) have = 0
                    val length = c.contentLengthLong
                    val total = if (length < 0) -1L else length + have
                    var received = have
                    c.inputStream.use { input ->
                        java.io.FileOutputStream(part, resumed).use { out ->
                            val buffer = ByteArray(BUFFER)
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                received += n
                                onProgress(received, total)
                            }
                            out.fd.sync()
                        }
                    }
                    if (target.exists()) target.delete()
                    if (!part.renameTo(target)) throw FileNotFoundException()
                } finally {
                    c.disconnect()
                }
            }
        }

    private fun open(url: String, readTimeoutMs: Int = 20_000): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
        }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}

/** PackageManager, files and PackageInstaller (port of the Flutter `oc/app_update` channel, AppUpdateChannel.kt). */
public class AndroidUpdatePlatform(context: Context, private val io: CoroutineDispatcher) : UpdatePlatform {
    private val app = context.applicationContext

    override suspend fun installed(): InstalledApp =
        withContext(io) {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            InstalledApp(app.packageName, info.versionName.orEmpty(), versionCode(info).toInt())
        }

    override suspend fun supportedAbis(): List<String> = Build.SUPPORTED_ABIS.toList()

    override suspend fun updateDir(): String = File(app.cacheDir, "app-update").absolutePath

    override suspend fun sha256(path: String): Outcome<String> =
        withContext(io) {
            attempt(::classify) {
                val digest = MessageDigest.getInstance("SHA-256")
                File(path).inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }

    override suspend fun exists(path: String): Boolean = withContext(io) { File(path).isFile }

    override suspend fun delete(path: String) {
        withContext(io) { File(path).delete() }
    }

    override suspend fun clearUpdateDir() {
        withContext(io) { File(app.cacheDir, "app-update").deleteRecursively() }
    }

    override suspend fun inspectApk(path: String): ApkInfo? =
        withContext(io) {
            val info = app.packageManager.getPackageArchiveInfo(path, signingFlags()) ?: return@withContext null
            ApkInfo(info.packageName, versionCode(info), UpdateSigners.signerString(certificates(info)))
        }

    override suspend fun ownSignerSha256(): String =
        withContext(io) {
            val info = app.packageManager.getPackageInfo(app.packageName, signingFlags())
            UpdateSigners.signerString(certificates(info))
        }

    override suspend fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    override fun openInstallPermission() {
        app.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun install(path: String): Flow<InstallEvent> =
        callbackFlow {
            // Subscribed before the session commits, so no answer is missed.
            val listener =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    UpdateInstallEvents.events.collect { event ->
                        send(event)
                        if (event.state != InstallState.PendingUser) close()
                    }
                }
            val started =
                attempt(::classify) {
                    val file = File(path)
                    if (!file.isFile) throw FileNotFoundException()
                    commit(file)
                }
            if (started is Outcome.Failed) {
                send(InstallEvent(InstallState.Failure, started.failure::class.simpleName))
                close()
            }
            awaitClose { listener.cancel() }
        }.flowOn(io)

    private fun commit(file: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(app.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("opencode-mobile.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val intent = Intent().setClassName(app, STATUS_RECEIVER)
                val pending =
                    PendingIntent.getBroadcast(
                        app,
                        sessionId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                session.commit(pending.intentSender)
            }
        } catch (e: IOException) {
            installer.abandonSession(sessionId)
            throw e
        } catch (e: SecurityException) {
            installer.abandonSession(sessionId)
            throw e
        }
    }

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()

    private fun certificates(info: PackageInfo): List<ByteArray> {
        val signers =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signing = info.signingInfo
                when {
                    signing == null -> emptyArray()
                    signing.hasMultipleSigners() -> signing.apkContentsSigners
                    else -> signing.signingCertificateHistory.takeLast(1).toTypedArray()
                }
            } else {
                @Suppress("DEPRECATION")
                info.signatures ?: emptyArray()
            }
        return signers.map { it.toByteArray() }
    }

    public companion object {
        public const val STATUS_RECEIVER: String = "io.github.eslamasabry.opencode_mobile.AppUpdateStatusReceiver"
    }
}

/** The pure parts (Flutter `AppUpdatePolicy`): PackageInstaller status → state, and how signers are written down. */
public object UpdateSigners {
    private const val STATUS_PENDING_USER_ACTION = -1
    private const val STATUS_SUCCESS = 0
    private const val STATUS_FAILURE_ABORTED = 3

    public fun stateFor(status: Int): InstallState =
        when (status) {
            STATUS_PENDING_USER_ACTION -> InstallState.PendingUser
            STATUS_SUCCESS -> InstallState.Success
            STATUS_FAILURE_ABORTED -> InstallState.Aborted
            else -> InstallState.Failure
        }

    /** Upper-case hex SHA-256 of one certificate, no separators. */
    public fun sha256Hex(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02X".format(it) }

    /** Several signers are sorted and joined with a comma; one is itself. */
    public fun signerString(certificates: List<ByteArray>): String = certificates.map(::sha256Hex).sorted().joinToString(",")
}

/** Where AppUpdateStatusReceiver reports; the install flow listens while it runs. */
public object UpdateInstallEvents {
    public val events: MutableSharedFlow<InstallEvent> = MutableSharedFlow(extraBufferCapacity = 8)
}

/** App-wide `oc.appUpdate.*` keys in their own preferences file (not per profile, so no deletion sweep). */
public class PrefsUpdateStore(context: Context, private val io: CoroutineDispatcher) : UpdateStore {
    private val prefs = context.applicationContext.getSharedPreferences("oc_updates", Context.MODE_PRIVATE)

    override suspend fun read(): UpdateRecord =
        withContext(io) {
            UpdateRecord(
                enabled = prefs.getBoolean(ENABLED, true),
                lastCheckMs = if (prefs.contains(LAST_CHECK)) prefs.getLong(LAST_CHECK, 0) else null,
                releaseJson = prefs.getString(RELEASE, null),
            )
        }

    override suspend fun write(record: UpdateRecord): Outcome<Unit> =
        withContext(io) {
            val edit =
                prefs.edit().putBoolean(ENABLED, record.enabled).apply {
                    if (record.lastCheckMs == null) remove(LAST_CHECK) else putLong(LAST_CHECK, record.lastCheckMs!!)
                    if (record.releaseJson == null) remove(RELEASE) else putString(RELEASE, record.releaseJson)
                }
            if (edit.commit()) Outcome.Ok(Unit) else Outcome.Failed(Failure.StorageRefused())
        }

    private companion object {
        const val ENABLED = "oc.appUpdate.enabled"
        const val LAST_CHECK = "oc.appUpdate.lastCheckMs"
        const val RELEASE = "oc.appUpdate.release"
    }
}
