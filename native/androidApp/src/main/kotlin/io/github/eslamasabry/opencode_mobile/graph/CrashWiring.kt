package io.github.eslamasabry.opencode_mobile.graph

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.platform.NativeCrashStore
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.crash.*
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import io.github.eslamasabry.oc.feature.settings.presentation.CrashSettings
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.secCrashReportHeader
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

@ContributesTo(AppScope::class)
interface CrashAccessors {
    val crashDiagnostics: AppCrashDiagnostics
}

/**
 * Single application owner; files and imports open off the main thread, without delaying the first
 * frame.
 */
@SingleIn(AppScope::class)
@Inject
class AppCrashDiagnostics(
    private val app: Application,
    private val diagnostics: Diagnostics,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) : CrashSettings {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private lateinit var directory: File
    private lateinit var native: NativeCrashStore
    private val startup =
        CrashStartup(
            source =
                CrashStartupSource {
                    withContext(dispatchers.io) {
                        attempt({ Failure.StorageRefused() }) {
                            // Android Flutter crash files were stored directly in filesDir (plan 05
                            // §0.1).
                            directory = app.filesDir.canonicalFile
                            native = NativeCrashStore(directory)
                            // The uncaught handler itself is installed first thing in
                            // OcApplication.onCreate.
                            ensureDirectory()
                            CrashStartupEvidence(
                                AndroidCrashFiles(directory, dispatchers),
                                NativeCrashRecord.from(native.read()),
                                when (val history = attempt({ Failure.Device() }) { exits() }) {
                                    is Outcome.Ok -> history.value
                                    is Outcome.Failed -> emptyList()
                                },
                            )
                        }
                    }
                },
            diagnostics = diagnostics,
            clock = clock,
            scope = scope,
            dispatchers = dispatchers,
        )
    private val mutable = MutableStateFlow<CrashDiagnosticsState?>(null)
    override val state: StateFlow<CrashDiagnosticsState?> = mutable.asStateFlow()
    private val reports =
        CrashReportBuilder(
            { startup.ready.await() },
            { getString(Res.string.secCrashReportHeader) },
        ) { preview ->
            withContext(dispatchers.main) {
                val current = startup.current.value?.state?.value
                // A consent change while switching to the main dispatcher cannot send stale
                // evidence.
                if (current == null || !preview.matches(current))
                    Outcome.Failed(Failure.StorageRefused())
                else
                    attempt({ Failure.Device() }) {
                        val send =
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, preview.text)
                        app.startActivity(
                            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
            }
        }

    fun start() {
        startup.start()
        scope.launch {
            startup.current.collect { controller ->
                if (controller == null) mutable.value = null
                else launch { controller.state.collect { mutable.value = it } }
            }
        }
    }

    override suspend fun history():
        io.github.eslamasabry.oc.feature.settings.presentation.DeviceExitHistory =
        withContext(dispatchers.io) {
            var unavailable = false
            val appExits =
                when (val result = attempt({ Failure.Device() }) { exits() }) {
                    is Outcome.Ok ->
                        result.value
                            .sortedByDescending { it.timestampMs }
                            .take(10)
                            .map {
                                io.github.eslamasabry.oc.feature.settings.presentation.DeviceExit(
                                    it.at.toString(),
                                    it.reason,
                                )
                            }
                    is Outcome.Failed -> {
                        unavailable = true
                        emptyList()
                    }
                }
            val helpers =
                when (
                    val result =
                        attempt({ Failure.Device() }) {
                            val services =
                                io.github.eslamasabry.opencode_mobile.BuiltinLinux.get(app)
                                    .performance()["services"]
                                    as? Map<*, *>
                            services
                                .orEmpty()
                                .entries
                                .filter { (key, _) ->
                                    key is String && key.startsWith("agent-host.")
                                }
                                .mapNotNull { (_, value) ->
                                    val data = value as? Map<*, *> ?: return@mapNotNull null
                                    val at = data["lastExitAtMs"] as? Long ?: return@mapNotNull null
                                    val code =
                                        data["lastExitCode"] as? Int ?: return@mapNotNull null
                                    if (at <= 0) null
                                    else
                                        io.github.eslamasabry.oc.feature.settings.presentation
                                            .DeviceExit(
                                                kotlin.time.Instant.fromEpochMilliseconds(at)
                                                    .toString(),
                                                code,
                                            )
                                }
                                .sortedByDescending { it.at }
                                .take(10)
                        }
                ) {
                    is Outcome.Ok -> result.value
                    is Outcome.Failed -> {
                        unavailable = true
                        emptyList()
                    }
                }
            io.github.eslamasabry.oc.feature.settings.presentation.DeviceExitHistory(
                appExits,
                helpers,
                unavailable,
            )
        }

    override suspend fun setEnabled(on: Boolean): Boolean =
        startup.ready.await()?.setEnabled(on) ?: false

    override suspend fun clear(): Boolean = startup.ready.await()?.clear() ?: false

    override suspend fun preview(): CrashReportResult = reports.preview()

    override suspend fun share(preview: CrashReportPreview): CrashReportError? =
        reports.share(preview)

    private fun ensureDirectory() {
        // This is the app-private files root, shared with the adopted Flutter crash store.
        // Its sandbox permissions belong to Android; only our individual files receive mode 0600.
        check(!Files.isSymbolicLink(directory.toPath()))
        check(directory.isDirectory && directory.canonicalFile == directory.absoluteFile)
    }

    private fun exits(): List<ExitRecord> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        val manager = app.getSystemService(ActivityManager::class.java) ?: return emptyList()
        return manager
            .getHistoricalProcessExitReasons(app.packageName, 0, 0)
            .filter { it.processName == app.packageName }
            .map {
                ExitRecord(
                    reason = it.reason,
                    subReason = 0,
                    importance = it.importance,
                    timestampMs = it.timestamp,
                    processName = it.processName,
                )
            }
    }
}

/** Fixed filenames, bounded reads, no symlink traversal, atomic owner-only writes. */
private class AndroidCrashFiles(
    private val directory: File,
    private val dispatchers: AppDispatchers,
) : CrashStore {
    private fun file(kind: CrashFile): File {
        check(
            directory.canonicalFile == directory.absoluteFile &&
                !Files.isSymbolicLink(directory.toPath())
        )
        val name =
            when (kind) {
                CrashFile.Consent -> "crash-diagnostics-consent"
                CrashFile.Records -> "crash-diagnostics.json"
                CrashFile.Pending -> "crash-diagnostics.pending"
                CrashFile.NativeLastCrash -> "native-last-crash.properties"
            }
        return File(directory, name).also { check(!Files.isSymbolicLink(it.toPath())) }
    }

    override suspend fun read(file: CrashFile, maxBytes: Int): Outcome<String?> =
        withContext(dispatchers.io) {
            // CrashStore contract: over the cap (or not UTF-8) is UnreadableReply, so the owner
            // discards it as Dart does; only an unusable file system is StorageRefused.
            var unreadable = false
            val read =
                attempt({ Failure.StorageRefused() }) {
                    val target = file(file)
                    if (!Files.exists(target.toPath(), NOFOLLOW_LINKS)) return@attempt null
                    check(Files.isRegularFile(target.toPath(), NOFOLLOW_LINKS))
                    if (target.length() > maxBytes) {
                        unreadable = true
                        return@attempt null
                    }
                    target.inputStream().use { input ->
                        val bytes = ByteArray(maxBytes + 1)
                        var length = 0
                        while (length < bytes.size) {
                            val read = input.read(bytes, length, bytes.size - length)
                            if (read < 0) break
                            length += read
                        }
                        if (length > maxBytes) {
                            unreadable = true
                            return@attempt null
                        }
                        try {
                            bytes.copyOf(length).decodeToString(throwOnInvalidSequence = true)
                        } catch (_: CharacterCodingException) {
                            unreadable = true
                            null
                        }
                    }
                }
            if (unreadable) Outcome.Failed(Failure.UnreadableReply()) else read
        }

    override suspend fun write(file: CrashFile, text: String): Outcome<Unit> =
        withContext(dispatchers.io) {
            attempt({ Failure.StorageRefused() }) {
                val destination = file(file)
                val pending = file(CrashFile.Pending)
                check(!Files.isSymbolicLink(pending.toPath()))
                check(!Files.exists(pending.toPath(), NOFOLLOW_LINKS) || pending.delete())
                Files.createFile(
                    pending.toPath(),
                    java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
                    ),
                )
                try {
                    FileOutputStream(pending).use { out ->
                        out.write(text.encodeToByteArray())
                        out.fd.sync()
                    }
                    file(file)
                    Files.move(
                        pending.toPath(),
                        destination.toPath(),
                        ATOMIC_MOVE,
                        REPLACE_EXISTING,
                    )
                    Unit
                } finally {
                    Files.deleteIfExists(pending.toPath())
                }
            }
        }

    override suspend fun delete(file: CrashFile): Outcome<Unit> =
        withContext(dispatchers.io) {
            attempt({ Failure.StorageRefused() }) {
                Files.deleteIfExists(file(file).toPath())
                if (file == CrashFile.NativeLastCrash) {
                    val pendingNative = File(directory, "native-last-crash.properties.tmp")
                    check(!Files.isSymbolicLink(pendingNative.toPath()))
                    Files.deleteIfExists(pendingNative.toPath())
                }
                Unit
            }
        }
}
