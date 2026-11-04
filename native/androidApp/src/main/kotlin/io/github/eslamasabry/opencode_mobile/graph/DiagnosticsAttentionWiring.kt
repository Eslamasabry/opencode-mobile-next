package io.github.eslamasabry.opencode_mobile.graph

import android.app.ActivityManager
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashDiagnosticsState
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashFile
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashStartup
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashStartupEvidence
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashStore
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportBuilder
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportPreview
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportResult
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashShareResult
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashShareSheet
import io.github.eslamasabry.oc.engine.diagnostics.device.DeviceDiagnostics
import io.github.eslamasabry.oc.engine.diagnostics.device.DeviceExitHistory
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitHistory
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import io.github.eslamasabry.oc.engine.diagnostics.exit.LifecycleSource
import io.github.eslamasabry.oc.engine.diagnostics.exit.PolicyExitHistory
import io.github.eslamasabry.oc.engine.diagnostics.exit.RawLaunchReport
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import io.github.eslamasabry.oc.engine.diagnostics.problem.ProblemEventKind
import io.github.eslamasabry.oc.engine.diagnostics.problem.ProblemReport
import io.github.eslamasabry.oc.engine.diagnostics.problem.ProblemReportClipboard
import io.github.eslamasabry.oc.engine.diagnostics.problem.ProblemReportEvent
import io.github.eslamasabry.oc.engine.diagnostics.problem.ProblemReports
import io.github.eslamasabry.oc.engine.diagnostics.report.AppErrorCapture
import io.github.eslamasabry.oc.engine.diagnostics.report.ReportProblemStartup
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptDiagnosticsHost
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.reportProblemDiagnosticsCopied
import io.github.eslamasabry.oc.ui.words.res.reportProblemLinkCopiesWhole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.text.DateFormat
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Instant

/*
 * Unit BUILD-DIAGNOSTICS-ATTENTION: the Android halves of the diagnostics engines. The persisted problem report
 * (ReportProblemStartup over AndroidReportFiles), the opt-in crash store (CrashStartup over app-private files), the
 * exit history (ApplicationExitInfo, API 30+), device diagnostics and the Everything-this-phone-kept host, and the
 * Report a problem › Send as a GitHub issue sender. Started once from OcApplication.
 */

@BindingContainer
@ContributesTo(AppScope::class)
object DiagnosticsAttentionBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun perfTrace(redactor: Redactor, clock: Clock): PerfTrace = PerfTrace(redactor, clock)

    @Provides
    @SingleIn(AppScope::class)
    fun reportProblemStartup(application: Application, diagnostics: Diagnostics, trace: PerfTrace, redactor: Redactor, clock: Clock): ReportProblemStartup =
        ReportProblemStartup({ AndroidReportFiles(application.filesDir) }, diagnostics, trace, redactor, clock)

    @Provides
    @SingleIn(AppScope::class)
    fun lifecycleSource(application: Application): LifecycleSource = AndroidLifecycleSource(application)

    @Provides
    @SingleIn(AppScope::class)
    fun exitHistory(source: LifecycleSource): ExitHistory = PolicyExitHistory(source)

    @Provides
    @SingleIn(AppScope::class)
    fun crashStartup(
        application: Application,
        source: LifecycleSource,
        diagnostics: Diagnostics,
        clock: Clock,
        appScope: AppCoroutineScope,
        dispatchers: AppDispatchers,
    ): CrashStartup =
        CrashStartup(
            source = {
                val exits = (source.retainedExits() as? Outcome.Ok)?.value.orEmpty()
                Outcome.Ok(CrashStartupEvidence(AndroidCrashStore(File(application.filesDir, "crash")), exits = exits))
            },
            diagnostics = diagnostics,
            clock = clock,
            scope = appScope,
            dispatchers = dispatchers,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun deviceDiagnostics(exits: ExitHistory, crash: CrashStartup, application: Application, dispatchers: AppDispatchers): DeviceDiagnostics {
        val sheet = CrashShareSheet { text ->
            withContext(dispatchers.main) {
                try {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    application.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                } catch (e: RuntimeException) {
                    AppLog.tagged("Diagnostics").w(e) { "share sheet unavailable" }
                    false
                }
            }
        }
        // Helper deaths need the built-in Linux performance read, which is not exposed to the app graph yet.
        return DeviceDiagnostics(exits, null, CrashReportBuilder({ crash.ready.await() }, sheet))
    }
}

/** The once-per-process start: the persisted report, the crash store, the framework error hook and the exit entry. */
@SingleIn(AppScope::class)
@Inject
class DiagnosticsAttentionStartup(
    private val report: ReportProblemStartup,
    private val crash: CrashStartup,
    private val exits: ExitHistory,
    private val diagnostics: Diagnostics,
    private val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        val capture = AppErrorCapture(diagnostics)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        // A fatal error leaves only its category (B-STATE-074); the platform handler then ends the process as before.
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            capture.capture(error)
            previous?.uncaughtException(thread, error)
        }
        crash.start()
        appScope.launch(dispatchers.io) {
            val session = report.start() ?: return@launch
            exits.launchReport().exit?.let { session.recordAndroidExit(it) } // B-STATE-077: once per exit
        }
    }
}

@ContributesTo(AppScope::class)
interface DiagnosticsAttentionAccessors {
    val diagnosticsAttention: DiagnosticsAttentionStartup
}

/** Everything this phone kept over the real engines. */
@OptIn(ExperimentalCoroutinesApi::class)
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AppKeptDiagnosticsHost(
    private val crashStartup: CrashStartup,
    private val device: DeviceDiagnostics,
    private val report: ReportProblemStartup,
    private val diagnostics: Diagnostics,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) : KeptDiagnosticsHost {
    private val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    private val reopened = MutableStateFlow(0)

    override val crash: StateFlow<CrashDiagnosticsState?> =
        crashStartup.current
            .flatMapLatest { controller -> controller?.state ?: flowOf(null) }
            .stateIn(appScope, SharingStarted.Eagerly, crashStartup.current.value?.state?.value)

    override val errorsKept: StateFlow<Int> =
        combine(diagnostics.errors, reopened) { ring, _ -> ring }
            .flatMapLatest { ring ->
                val session = report.current
                session?.report?.state?.map { s -> s.entries.count { it.kind.name == "Error" || it.kind.name == "AndroidExit" } }
                    ?: flowOf(ring.size)
            }
            .stateIn(appScope, SharingStarted.Eagerly, diagnostics.errors.value.size)

    override suspend fun setCrashCapture(on: Boolean): Boolean = crashStartup.ready.await()?.setEnabled(on) ?: false

    override suspend fun deleteCrashReports(): Boolean = crashStartup.ready.await()?.clear() ?: false

    override suspend fun exitHistory(): DeviceExitHistory = withContext(dispatchers.io) { device.exitHistory() }

    override suspend fun previewCrashReport(): CrashReportResult = device.previewCrashReport()

    override suspend fun shareCrashReport(preview: CrashReportPreview): CrashShareResult = device.shareCrashReport(preview)

    override suspend fun clearErrors(): Outcome<Unit> =
        withContext(dispatchers.io) {
            // Diagnostics.clear also erases the saved report and restored crash evidence through its listeners.
            val cleared = report.current?.report?.clear() ?: Outcome.Ok(Unit)
            diagnostics.clear()
            reopened.value++
            cleared
        }

    override fun timeLabel(at: Instant): String = format.format(Date(at.toEpochMilliseconds()))
}

/**
 * Report a problem › Send as a GitHub issue (B-SEC-129): the report is built here with no address, name or credential
 * (B-SEC-128); a short one is prefilled whole in the app-authored link, a long one is copied first and the form says
 * to paste. The link goes through ExternalLinkPolicy → ExternalLinkOpener; nothing is filed until the person submits.
 */
@SingleIn(AppScope::class)
@Inject
class GitHubProblemReporter(
    private val application: Application,
    private val redactor: Redactor,
    private val diagnostics: Diagnostics,
    private val report: ReportProblemStartup,
    private val opener: ExternalLinkOpener,
    private val build: AppBuildInfo,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private val policy = ExternalLinkPolicy()

    /**
     * The errors Report a problem lists, oldest first: the persisted report's (they survive a restart, B-STATE-075),
     * else null so the caller keeps this run's ring. Each is (time, source, message), already redacted.
     */
    fun keptErrors(): List<Triple<String, String, String>>? =
        report.current?.report?.entries
            ?.filter { it.kind.name == "Error" || it.kind.name == "AndroidExit" }
            ?.map { Triple(it.at.toString(), it.source, it.message) }

    fun send(connection: String?) {
        scope.launch {
            val stored =
                report.current?.report?.entries?.map { e ->
                    ProblemReportEvent(ProblemEventKind.entries.first { it.name == e.kind.name }, e.at, e.source, e.message, e.stack)
                }
            val problem =
                ProblemReport.build(
                    redactor = redactor,
                    description = "",
                    version = "${build.versionName}+${build.versionCode}",
                    platform = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    connection = connection,
                    fallbackTitle = diagnostics.errors.value.lastOrNull()?.message,
                    events = ProblemReports.events(stored, diagnostics.errors.value),
                )
            val link = problem.link()
            val approved = ProblemReports.approve(policy, link) ?: return@launch
            withContext(dispatchers.main) {
                link.clipboardText?.let { text ->
                    attempt({ Failure.StorageRefused() }) {
                        application.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("OpenCode Mobile report", text))
                    }
                    val words =
                        when (link.clipboard) {
                            ProblemReportClipboard.Whole -> getString(Res.string.reportProblemLinkCopiesWhole)
                            else -> getString(Res.string.reportProblemDiagnosticsCopied)
                        }
                    Toast.makeText(application, words, Toast.LENGTH_LONG).show()
                }
                opener.open(approved.link)
            }
        }
    }
}

/** The crash store's four app-private files; atomic writes, size-capped reads, no symbolic links followed. */
class AndroidCrashStore(private val dir: File) : CrashStore {
    private fun target(file: CrashFile): File {
        if (Files.isSymbolicLink(dir.toPath())) throw java.io.IOException("refused")
        if (!dir.isDirectory && !dir.mkdirs()) throw java.io.IOException("unavailable")
        val f = File(dir, "${file.name.lowercase()}.json")
        if (Files.isSymbolicLink(f.toPath())) throw java.io.IOException("refused")
        return f
    }

    override suspend fun read(file: CrashFile, maxBytes: Int): Outcome<String?> {
        val f = attempt({ Failure.StorageRefused() }) { target(file) }
        if (f !is Outcome.Ok) return f as Outcome.Failed
        if (!Files.exists(f.value.toPath(), LinkOption.NOFOLLOW_LINKS)) return Outcome.Ok(null)
        if (f.value.length() > maxBytes) return Outcome.Failed(Failure.UnreadableReply())
        return attempt({ Failure.StorageRefused() }) { f.value.readText() }
    }

    override suspend fun write(file: CrashFile, text: String): Outcome<Unit> =
        attempt({ Failure.StorageRefused() }) {
            val f = target(file)
            val pending = File(f.parentFile, f.name + ".pending")
            FileOutputStream(pending).use { out ->
                out.write(text.encodeToByteArray())
                out.fd.sync()
            }
            Files.move(pending.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }

    override suspend fun delete(file: CrashFile): Outcome<Unit> =
        attempt({ Failure.StorageRefused() }) { Files.deleteIfExists(target(file).toPath()) }
}

/**
 * Android's record of each time the app's process ended (ApplicationExitInfo, API 30+). The launch report is the newest
 * exit not reported before; the last reported time is kept app-wide (not per profile) in `oc.diag.lastExitSeen`.
 */
class AndroidLifecycleSource(private val application: Application) : LifecycleSource {
    override val exitHistorySupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    override val mainProcessName: String = application.packageName

    override suspend fun retainedExits(): Outcome<List<ExitRecord>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Outcome.Ok(emptyList())
        return attempt({ Failure.StorageRefused() }) {
            val am = application.getSystemService(ActivityManager::class.java)
            am.getHistoricalProcessExitReasons(application.packageName, 0, 0).map { info ->
                ExitRecord(
                    reason = info.reason,
                    timestampMs = info.timestamp,
                    status = info.status,
                    importance = info.importance,
                    processName = info.processName,
                    description = info.description,
                )
            }
        }
    }

    override suspend fun launchReport(): Outcome<RawLaunchReport> {
        val all = retainedExits()
        if (all !is Outcome.Ok) return Outcome.Ok(RawLaunchReport())
        val prefs = application.getSharedPreferences("oc.diag", Context.MODE_PRIVATE)
        val seen = prefs.getLong("oc.diag.lastExitSeen", 0L)
        val newest = all.value.filter { it.processName == mainProcessName }.maxByOrNull { it.timestampMs }
        if (newest == null || newest.timestampMs <= seen) return Outcome.Ok(RawLaunchReport())
        prefs.edit().putLong("oc.diag.lastExitSeen", newest.timestampMs).apply()
        return Outcome.Ok(RawLaunchReport(exit = newest))
    }
}
