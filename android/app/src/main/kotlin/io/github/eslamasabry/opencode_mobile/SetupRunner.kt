package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Fresh storage admission for setup and the private agent host. */
object SetupDiskSpace {
    const val MIN_INSTALL_BYTES = 300_000_000L
    const val MIN_LAUNCH_BYTES = 128_000_000L
    private const val LOW_SPACE =
        "There is not enough free space on this phone. Free some storage and try again."

    fun requiredInstallBytes(downloadBytes: Long): Long {
        val nonnegative = downloadBytes.coerceAtLeast(0L)
        val doubled = if (nonnegative > Long.MAX_VALUE / 2L) Long.MAX_VALUE else nonnegative * 2L
        return maxOf(doubled, MIN_INSTALL_BYTES)
    }

    fun error(directory: File, requiredBytes: Long): String? {
        var existing = directory
        while (!existing.exists()) {
            existing = existing.parentFile ?: return LOW_SPACE
        }
        return if (existing.usableSpace >= requiredBytes) null else LOW_SPACE
    }
}

/**
 * Runs a phone setup job (docs/design/phone-setup-v2-2026-09-24.md, "Running a
 * setup job"): the components the app asked for, in order, on one background
 * thread of this process, under a foreground service so it survives the app
 * leaving the screen.
 *
 * The app decides what to run and hands over finished scripts; this class
 * only runs them, reads their `::oc` lines, and keeps `files/linux/setup.json`
 * current so the job's state outlives the process. Three kinds of component:
 * - `native`: the Linux base itself, downloaded and unpacked here;
 * - `step`: done by the app (starting OpenCode); the job waits for
 *   [completeStep];
 * - anything else: a script run inside Ubuntu.
 * A component the app found installed arrives as `skipped` and is only
 * recorded, so the checklist and the percent cover the whole job.
 */
class SetupRunner private constructor(private val context: Context) {
    class Spec(
        val id: String,
        val script: String?,
        val native: Boolean,
        val step: Boolean,
        val weight: Double,
        val skipped: Boolean,
        val version: String?,
        val stage: String?,
        val labels: Map<String, String>,
        val data: Map<String, String>,
        val agentUser: Boolean = false,
    )

    /** What the ongoing notification says; the app sends it localised. */
    class Texts(
        val channel: String,
        val title: String,
        /** Holds `{percent}`. */
        val progress: String,
        val done: String,
        val stopped: String,
    )

    private val linux = BuiltinLinux.get(context)
    private val file = File(linux.home, "setup.json")
    val logFile = File(linux.home, "setup.log")

    private val lock = Object()
    private var job: SetupJobState? = null
    private var params: JSONObject? = null
    private var texts: Texts? = null
    private var worker: Thread? = null
    private var process: Process? = null
    @Volatile private var cancelled = false
    private var stepResult: Pair<Boolean, String?>? = null
    private var stepVersion: String? = null
    private val tail = LogTail()
    private var log: FileWriter? = null

    // setup.json is written at most four times a second, the notification
    // at most once a second; state changes are written at once.
    private var dirty = false
    private var lastNotified = 0L
    private var lastNotifiedText = ""

    init {
        // A job this process did not start cannot be running: the process
        // that ran it is gone. Say so on disk before anyone reads it.
        read()?.let { stale ->
            if (stale.state == "running") {
                stale.markInterrupted(System.currentTimeMillis())
                job = stale
                try {
                    synchronized(lock) { writeFile(stale, params) }
                } catch (_: SetupPersistenceException) {
                    persistenceFailed()
                }
            }
        }
    }

    val running: Boolean get() = synchronized(lock) { worker?.isAlive == true }

    /** The live job while one runs, else what setup.json says; null for none. */
    fun status(): String? = synchronized(lock) {
        val live = job
        if (live != null && (worker?.isAlive == true || live.errorCode == SetupPersistenceException.CODE)) {
            live.logTail = tail.value()
            return json(live, params).toString()
        }
        return if (file.isFile) file.readText() else null
    }

    fun start(jobId: String, specs: List<Spec>, params: JSONObject?, texts: Texts) {
        synchronized(linux) {
            synchronized(lock) {
                if (worker?.isAlive == true) {
                    if (job?.jobId == jobId) return
                    error("A setup job is already running")
                }
                val now = System.currentTimeMillis()
                val state = SetupJobState(
                    jobId = jobId,
                    state = "running",
                    components = specs.map { spec ->
                        SetupComponentStatus(
                            id = spec.id,
                            state = if (spec.skipped) "skipped" else "pending",
                            weight = spec.weight,
                            version = spec.version,
                            data = spec.data,
                        )
                    },
                    startedAt = now,
                )
                job = state
                this.params = params
                this.texts = texts
                cancelled = false
                stepResult = null
                tail.clear()
                try { log?.close() } catch (_: Exception) { }
                // Open the optional details log only once storage admission
                // succeeds. The durable state writer creates the home first.
                log = null
                try {
                    writeNow()
                } catch (error: SetupPersistenceException) {
                    persistenceFailed()
                    throw error
                }
                lastNotifiedText = progressText(state.overall())
                lastNotified = System.currentTimeMillis()
                var workScope: SetupWorkScopes.Scope? = null
                try {
                    val prepared = linux.prepareSetupWork()
                    workScope = prepared
                    SetupService.start(context, texts.channel, texts.title, lastNotifiedText)
                    worker = Thread({ linux.withSetupWork(prepared) { runJob(specs) } }, "oc-setup").apply { start() }
                } catch (_: Throwable) {
                    workScope?.let { linux.closeSetupWork(it) }
                    state.state = "failed"
                    state.error = "The phone setup could not start. Try again."
                    try { writeNow() } catch (_: SetupPersistenceException) { persistenceFailed() }
                    try { log?.close() } catch (_: Exception) { }
                    log = null
                    try { SetupService.stop(context) } catch (_: Throwable) { }
                    throw IllegalStateException("The phone setup could not start. Try again.")
                }
            }
        }
    }

    fun cancel() {
        val running: Process?
        synchronized(lock) {
            if (worker?.isAlive != true) return
            cancelled = true
            running = process
            (lock as Object).notifyAll()
        }
        // The script and everything it started (npm, curl, apt) end with it.
        running?.let { try { linux.stopInstaller(it) } catch (_: Exception) { } }
    }

    /** The app finished (or failed) a `step` component of [jobId]. */
    fun completeStep(jobId: String, id: String, ok: Boolean, error: String?, version: String?) {
        synchronized(lock) {
            val current = job ?: return
            if (current.jobId != jobId || current.current != id) return
            stepResult = ok to error
            stepVersion = version
            (lock as Object).notifyAll()
        }
    }

    // ---- the job thread ----------------------------------------------------

    private fun runJob(specs: List<Spec>) {
        val writer = Thread({ flushLoop() }, "oc-setup-writer").apply { isDaemon = true }
        var failure: String? = null
        var logOpened = false
        try {
            writer.start()
            for (spec in specs) {
                if (spec.skipped) continue
                if (cancelled) break
                val requiredBytes = spec.data["requiredFreeBytes"]?.toLongOrNull()
                    ?.takeIf { it >= SetupDiskSpace.MIN_INSTALL_BYTES }
                    ?: SetupDiskSpace.MIN_INSTALL_BYTES
                val storageError = SetupDiskSpace.error(linux.home, requiredBytes)
                if (storageError != null) {
                    synchronized(lock) {
                        job!!.current = spec.id
                        job!!.component(spec.id).apply {
                            state = "failed"
                            error = storageError
                            endedAt = System.currentTimeMillis()
                        }
                    }
                    failure = storageError
                    break
                }
                if (!logOpened) {
                    synchronized(lock) {
                        log = try { FileWriter(logFile, false) } catch (_: Exception) { null }
                    }
                    logOpened = true
                }
                val component = synchronized(lock) {
                    job!!.current = spec.id
                    job!!.component(spec.id).apply {
                        state = "running"
                        stage = spec.stage
                        done = null
                        total = null
                        percent = null
                        error = null
                        startedAt = System.currentTimeMillis()
                        endedAt = null
                    }
                }
                writeNow()
                val error = try {
                    when {
                        spec.native -> runNative(spec, component)
                        spec.step -> waitForStep(spec, component)
                        else -> runScript(spec, component)
                    }
                } catch (error: SetupPersistenceException) {
                    throw error
                } catch (e: Throwable) {
                    // Agent process exceptions can include private home paths.
                    Log.e(BuiltinLinux.TAG, "setup component failed")
                    "The setup step could not finish. Try again."
                }
                synchronized(lock) {
                    component.endedAt = System.currentTimeMillis()
                    when {
                        cancelled -> component.state = "pending"
                        error == null -> component.state = "done"
                        else -> {
                            component.state = "failed"
                            component.error = error
                        }
                    }
                }
                if (cancelled) break
                if (error != null) {
                    failure = error
                    break
                }
            }
        } catch (_: SetupPersistenceException) {
            persistenceFailed()
        } catch (_: Throwable) {
            failure = "The setup step could not finish. Try again."
            synchronized(lock) {
                job?.components?.filter { it.state == "running" }?.forEach {
                    it.state = "failed"
                    it.error = failure
                    it.endedAt = System.currentTimeMillis()
                }
            }
        } finally {
            // Stop admission and drain the periodic owner before the terminal
            // snapshot. interrupt alone cannot cancel file IO already in flight.
            writer.interrupt()
            try { writer.join() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        synchronized(lock) {
            val state = job!!
            if (state.state == "running") {
                state.state = when {
                    cancelled -> "cancelled"
                    failure != null -> "failed"
                    else -> "done"
                }
                state.error = failure
                if (state.state == "done") state.current = null
            }
        }
        try {
            writeNow()
        } catch (_: SetupPersistenceException) {
            persistenceFailed()
        }
        val ended = synchronized(lock) { job!!.state }
        val words = texts
        try {
            if (words == null || ended == "cancelled") {
                SetupService.stop(context)
            } else {
                SetupService.finish(
                    context,
                    words.channel,
                    if (ended == "done") words.done else words.stopped,
                    done = ended == "done",
                )
            }
        } catch (_: Throwable) {
            // Durable terminal truth is already stored; notification denial
            // must never escape the worker and crash the foreground app.
        }
        synchronized(lock) {
            try {
                log?.close()
            } catch (_: Exception) {
            }
            log = null
        }
    }

    private fun runNative(spec: Spec, component: SetupComponentStatus): String? {
        if (spec.id != "linux") return "Unknown native component ${spec.id}"
        val downloading = spec.labels["download"] ?: "Downloading Linux base"
        val unpacking = spec.labels["unpack"] ?: "Unpacking Linux base"
        linux.install(
            progress = object : BuiltinLinux.InstallProgress {
                override fun stage(which: BuiltinLinux.InstallStage) {
                    val label = if (which == BuiltinLinux.InstallStage.DOWNLOAD) downloading else unpacking
                    logLine("==> $label")
                    event(component, SetupProtocol.Event.Stage(label))
                }

                override fun bytes(done: Long, total: Long) =
                    event(component, SetupProtocol.Event.Bytes(done, total))

                override fun log(line: String) = logLine(line)

                override val cancelled: Boolean get() = this@SetupRunner.cancelled
            },
        )
        if (cancelled) return "cancelled"
        event(component, SetupProtocol.Event.Version(BuiltinLinux.VERSION))
        return null
    }

    private fun runScript(spec: Spec, component: SetupComponentStatus): String? {
        if (!linux.installed) return "The Linux base is not installed"
        val script = spec.script ?: return "No script for ${spec.id}"
        val started = linux.startInstaller(script, NativeInstallerOwnership.targetsForComponent(spec.id),
            InstallerOperation.INSTALL, agentUser = spec.agentUser)
        return try { runInstallerScript(spec, component, started) }
        finally { linux.finishInstaller(started) }
    }

    private fun runInstallerScript(spec: Spec, component: SetupComponentStatus, started: Process): String? {
        val wasCancelled = synchronized(lock) {
            process = started
            cancelled
        }
        if (wasCancelled) {
            linux.stopInstaller(started)
            return "cancelled"
        }
        var last: String? = null
        try {
            started.outputStream.close()
            started.inputStream.bufferedReader().forEachLine { line ->
                val parsed = SetupProtocol.parse(line)
                val event = installerEvent(spec, parsed)
                if (event == null) {
                    if (!spec.agentUser) {
                        logLine(line)
                        if (line.isNotBlank()) last = line.trim()
                    }
                } else {
                    if (event is SetupProtocol.Event.Stage) logLine("==> ${event.label}")
                    event(component, event)
                }
            }
        } catch (error: SetupPersistenceException) {
            linux.stopInstaller(started)
            synchronized(lock) { process = null }
            throw error
        } catch (_: Exception) {
            // A failed/closed pipe must not leak a running child.
            try { linux.stopInstaller(started) } catch (_: Exception) { }
        }
        val code = try { started.waitFor() } finally {
            synchronized(lock) { process = null }
        }
        return if (cancelled) "cancelled" else installerFailure(spec, component, code, last)
    }

    private fun installerFailure(spec: Spec, component: SetupComponentStatus, code: Int, last: String?): String? =
        if (code == 0) null else if (spec.agentUser) {
            if (spec.id == "agent-paseo") when (component.stage) {
                "Checking Paseo checksum" -> "Paseo did not pass its checksum check. Run setup again."
                "Checking Paseo launch command" -> "Paseo did not pass its launch command check. Run setup again."
                "Checking Paseo version" -> "Paseo did not pass its version check. Run setup again."
                else -> "The agent setup could not finish. Try again."
            } else "The agent setup could not finish. Try again."
        } else (last ?: "exit $code").take(300)

    private fun installerEvent(spec: Spec, parsed: SetupProtocol.Event?): SetupProtocol.Event? =
        if (!spec.agentUser) parsed else when (parsed) {
            is SetupProtocol.Event.Stage -> SetupProtocol.Event.Stage(
                if (spec.id == "agent-paseo" && parsed.label in PASEO_CHECK_STAGES)
                    parsed.label else spec.stage ?: "Installing agent",
            )
            is SetupProtocol.Event.Version -> if (parsed.text == spec.version) parsed else null
            else -> parsed
        }

    /**
     * Waits for the app to report the step: ten minutes, or the step's own
     * `waitMinutes` (an app-side download, up to four hours).
     */
    private fun waitForStep(spec: Spec, component: SetupComponentStatus): String? {
        val minutes = spec.data["waitMinutes"]?.toLongOrNull()?.coerceIn(1L, 240L) ?: 10L
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes)
        synchronized(lock) {
            while (stepResult == null && !cancelled) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) return "The app did not finish this step"
                (lock as Object).wait(left)
            }
            if (cancelled) return "cancelled"
            val (ok, error) = stepResult!!
            stepVersion?.let { component.version = it }
            stepResult = null
            return if (ok) null else (error ?: "failed")
        }
    }

    private fun event(component: SetupComponentStatus, event: SetupProtocol.Event) {
        synchronized(lock) {
            component.apply(event)
            job?.updatedAt = System.currentTimeMillis()
            dirty = true
        }
        if (event is SetupProtocol.Event.Stage) writeNow()
    }

    private fun logLine(line: String) {
        tail.append(line)
        synchronized(lock) {
            dirty = true
            try {
                log?.apply {
                    write(line)
                    write("\n")
                    flush()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun flushLoop() {
        try {
            while (true) {
                Thread.sleep(WRITE_INTERVAL_MS)
                val due = synchronized(lock) { dirty }
                if (due) writeNow() else notifyProgress()
            }
        } catch (_: InterruptedException) {
        } catch (_: SetupPersistenceException) {
            persistenceFailed()
        } catch (_: Throwable) {
            // A notification update is optional; unexpected writer failure
            // leaves a durable failure rather than an uncaught JVM exception.
            persistenceFailed()
        }
    }

    private fun persistenceFailed() {
        val running = synchronized(lock) {
            cancelled = true
            job?.apply {
                state = "failed"
                errorCode = SetupPersistenceException.CODE
                error = null
                components.filter { it.state == "running" }.forEach { it.state = "pending" }
                updatedAt = System.currentTimeMillis()
            }
            (lock as Object).notifyAll()
            process
        }
        running?.let { try { linux.stopInstaller(it) } catch (_: Exception) { } }
    }

    private fun writeNow() {
        // One owner covers both the snapshot and the entire atomic commit.
        // A mutable job reference must never escape this lock to a writer.
        synchronized(lock) {
            val snapshot = job ?: return
            snapshot.updatedAt = System.currentTimeMillis()
            snapshot.logTail = tail.value()
            writeFile(snapshot, params)
            dirty = false
        }
        notifyProgress()
    }

    /**
     * Mirrors the percent in the notification, at most once a second and
     * only when it changed. Also called when nothing was written: a new
     * stage can move the percent without any further output.
     */
    private fun notifyProgress() {
        val text: String
        synchronized(lock) {
            val state = job ?: return
            val now = System.currentTimeMillis()
            if (state.state != "running" || now - lastNotified < NOTIFY_INTERVAL_MS) return
            text = progressText(state.overall())
            if (text == lastNotifiedText) return
            lastNotified = now
            lastNotifiedText = text
        }
        val words = texts ?: return
        try { SetupService.update(context, words.channel, words.title, text) }
        catch (_: Throwable) { /* Progress notifications are best effort. */ }
    }

    private fun progressText(overall: Double): String {
        val words = texts ?: return ""
        return words.progress.replace("{percent}", (overall * 100).roundToInt().coerceIn(0, 100).toString())
    }

    private fun json(state: SetupJobState, params: JSONObject?): JSONObject =
        state.toJson().apply { put("params", params ?: JSONObject()) }

    private fun writeFile(state: SetupJobState, params: JSONObject?) {
        check(Thread.holdsLock(lock))
        SetupPersistence.replace(file, json(state, params).toString())
    }

    private fun read(): SetupJobState? = try {
        if (file.isFile) {
            val json = JSONObject(file.readText())
            params = json.optJSONObject("params")
            SetupJobState.fromJson(json)
        } else {
            null
        }
    } catch (error: Exception) {
        Log.w(BuiltinLinux.TAG, "setup.json unreadable", error)
        null
    }

    companion object {
        private val PASEO_CHECK_STAGES = setOf(
            "Checking Paseo checksum", "Checking Paseo launch command", "Checking Paseo version",
        )
        private const val WRITE_INTERVAL_MS = 250L
        private const val NOTIFY_INTERVAL_MS = 1000L

        @Volatile private var instance: SetupRunner? = null

        fun get(context: Context): SetupRunner =
            instance ?: synchronized(this) {
                instance ?: SetupRunner(context.applicationContext).also { instance = it }
            }
    }
}
