package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.app.ActivityManager
import android.os.Build
import android.os.PowerManager
import android.os.Process as AndroidProcess
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.system.ErrnoException
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Ubuntu inside the app, with no Termux.
 *
 * The APK ships proot, its loader and the two libraries it needs as native
 * libraries (see tool/builtin_linux/fetch_proot.sh): Android unpacks those into
 * an executable folder, while this app may not run programs from its own
 * storage. proot runs Ubuntu's programs through its loader, so every program
 * in the Ubuntu tree is started by proot, never by the system.
 *
 * Ubuntu is Canonical's own minimal root filesystem (Ubuntu Base), pinned by
 * version and SHA-256, unpacked into app storage. Uninstalling the app removes
 * it.
 */
class BuiltinLinux(private val context: Context) {
    data class Image(val url: String, val sha256: String)

    data class Result(val exitCode: Int, val output: String)

    val home = File(context.filesDir, "linux")
    val rootfs = File(home, "ubuntu")
    private val projectStorage = BuiltinProjectStorage(
        context.filesDir, File(context.cacheDir, "ubuntu-base.tar.gz"),
    ) { file ->
        try {
            val stat = Os.lstat(file.absolutePath)
            val kind = when {
                OsConstants.S_ISLNK(stat.st_mode) -> BuiltinProjectStorage.Kind.LINK
                OsConstants.S_ISDIR(stat.st_mode) -> BuiltinProjectStorage.Kind.DIRECTORY
                OsConstants.S_ISREG(stat.st_mode) -> BuiltinProjectStorage.Kind.FILE
                else -> BuiltinProjectStorage.Kind.OTHER
            }
            BuiltinProjectStorage.Entry(kind, stat.st_size)
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) null else throw error
        }
    }
    private val processes = mutableListOf<Process>()
    private var installingRuntime = false
    private val ready = File(home, "ubuntu.ready")
    private val nativeDir = context.applicationInfo.nativeLibraryDir
    private val phoneEngine = PhoneEngineNative(context)
    private val protectionMarker = File(context.filesDir, "oc.teamEngine.protection-required")
    @Volatile private var protectedProot = markerPresent()
    @Volatile private var protectedTier: String? = null
    private val prootMaskedPids = mutableSetOf<Int>()
    private var latestBoundaryControls = emptyMap<String, Any?>()
    internal fun phoneBoundaryControls(): Map<String, Any?> = latestBoundaryControls.toMap()
    private val processConfinement = java.util.IdentityHashMap<Process, Boolean>()
    // Captured before an authored foreground launch; a late registration cannot recapture after Stop.
    private val agentWorkAdmissions = java.util.IdentityHashMap<Process, Long>()

    // A corrupt/symlink marker still requires protection. Never fall back to
    // legacy execution merely because marker bytes or a packaged ELF changed.
    private fun markerPresent(): Boolean = try { Os.lstat(protectionMarker.absolutePath); true
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) false else throw e
    }

    val prootIsConfined: Boolean get() = protectedProot || markerPresent()

    private fun protectionTier(): String {
        protectedTier?.let { return it }
        if (!markerPresent()) return "none"
        // Unknown/corrupt old markers continue to require the stronger launcher.
        return try {
            val fd = Os.open(protectionMarker.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            val bytes = ByteArray(32)
            val text = java.io.FileInputStream(fd).use { input ->
                val n = input.read(bytes)
                if (n > 0) String(bytes, 0, n, Charsets.US_ASCII) else ""
            }
            if (text == "required-proot-v1") "proot" else "landlock"
        } catch (_: Exception) { "landlock" }
    }

    private fun hasUnconfinedChildren(): Boolean {
        if (processes.any { it.isAlive && processConfinement[it] != true }) return true
        val terminal = LocalTerminal.get(context)
        if (terminal.hasUnconfinedSessions()) return true
        val known = mutableSetOf(AndroidProcess.myPid())
        // Trust only zygote processes registered by ActivityManager. A worker
        // can forge /proc cmdline or exec -a, but cannot invent an AMS record.
        val registered = try {
            context.getSystemService(ActivityManager::class.java)?.runningAppProcesses
        } catch (_: Exception) { null }
        known.addAll(registeredAppProcessIds(registered, context.packageName, AndroidProcess.myUid()))
        for (process in processes.filter { it.isAlive }) {
            val pid = pidOf(process) ?: return true
            known.add(pid)
            known.addAll(descendants(pid))
        }
        services[PHONE_ENGINE]?.process?.takeIf { it.isAlive }?.let {
            known.add(pidOf(it) ?: return true)
        }
        for (session in terminal.list().filter { it.running }) {
            known.add(session.pid)
            known.addAll(descendants(session.pid))
        }
        // A surviving, untracked app-UID child is not silently trusted after
        // app restart. Inventory failure is also an unavailable prerequisite.
        val entries = File("/proc").listFiles() ?: return true
        for (entry in entries) {
            val pid = entry.name.toIntOrNull() ?: continue
            if (pid in known) continue
            val uid = try { Os.stat(entry.absolutePath).st_uid } catch (e: ErrnoException) {
                if (e.errno == OsConstants.ENOENT) continue else return true
            }
            if (uid == AndroidProcess.myUid()) return true
            // PR_SET_DUMPABLE=0 can make an app-UID /proc directory appear
            // root-owned. Directory ownership alone cannot prove its UID.
            if (uid == 0) {
                val realUid = try {
                    File(entry, "status").readLines().firstOrNull { it.startsWith("Uid:") }
                        ?.substringAfter(':')?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toIntOrNull()
                        ?: return true
                } catch (e: java.io.FileNotFoundException) {
                    if (!entry.exists()) continue else return true
                } catch (_: Exception) { return true }
                if (realUid == AndroidProcess.myUid()) return true
            }
        }
        return false
    }

    val installed: Boolean get() = ready.isFile

    enum class InstallStage { DOWNLOAD, UNPACK }

    /** How [install] reports to a setup job (SetupRunner.kt), and hears a cancel. */
    interface InstallProgress {
        fun stage(which: InstallStage) {}
        fun bytes(done: Long, total: Long) {}
        fun log(line: String) {}
        val cancelled: Boolean get() = false
    }

    class Cancelled : Exception("cancelled")

    fun install(image: Image = imageForDevice(), log: (String) -> Unit = {}) =
        install(
            image,
            object : InstallProgress {
                override fun log(line: String) = log(line)
            },
        )

    /**
     * Downloads (resuming a partial archive) and unpacks Ubuntu. The archive
     * stays in the cache until the unpack has finished, so an install killed
     * while unpacking starts again from the unpack, not the download.
     */
    fun install(image: Image = imageForDevice(), progress: InstallProgress) {
        synchronized(this) {
            check(!installingRuntime) { "A runtime installation is already running" }
            projectStorage.prepare()
            if (installed) return
            installingRuntime = true
        }
        try {
            home.mkdirs()
            val archive = File(context.cacheDir, "ubuntu-base.tar.gz")
            progress.stage(InstallStage.DOWNLOAD)
            progress.log("Downloading ${image.url}")
            download(image, archive, progress)
            progress.stage(InstallStage.UNPACK)
            progress.log("Unpacking Ubuntu Base $VERSION")
            projectStorage.resetRootfs()
            rootfs.mkdirs()
            unpack(archive, rootfs, progress)
            configure()
            projectStorage.prepare()
            ready.writeText(image.sha256)
            phase = "ready"
            message = null
            archive.delete()
            progress.log("Ubuntu Base $VERSION is installed")
        } finally {
            synchronized(this) { installingRuntime = false }
        }
    }

    /** Runs [script] with /bin/sh inside Ubuntu as root (faked by proot). */
    fun run(script: String, timeoutSeconds: Long = 600, agentUser: Boolean = false): Result {
        val began = SystemClock.elapsedRealtime()
        val timeoutMillis = TimeUnit.SECONDS.toMillis(timeoutSeconds).coerceAtLeast(0L)
        val process = admitCheckProcess(script, timeoutMillis, began, agentUser)
        val output = StringBuilder()
        val readerFailed = java.util.concurrent.atomic.AtomicBoolean()
        var reader: Thread? = null
        try {
            process.outputStream.close()
            reader = Thread({
                try {
                    process.inputStream.bufferedReader().use { input ->
                        input.forEachLine { line ->
                            val projected = if (agentUser) PhoneAgentCheckOutput.accept(line) else line
                            if (projected != null) {
                                if (!agentUser) Log.i(TAG, projected)
                                synchronized(output) {
                                    output.appendLine(projected)
                                    if (output.length > OUTPUT_CAP * 2) {
                                        output.delete(0, output.length - OUTPUT_CAP)
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                    readerFailed.set(true)
                }
            }, "phone-setup-check").apply { isDaemon = true; start() }
            val remainingMillis = (timeoutMillis - (SystemClock.elapsedRealtime() - began)).coerceAtLeast(0L)
            val finished = process.waitFor(remainingMillis, TimeUnit.MILLISECONDS)
            if (!finished) stopInstaller(process)
            reader.join(2000)
            val complete = finished && !reader.isAlive && !readerFailed.get()
            val text = if (complete) synchronized(output) { output.takeLast(OUTPUT_CAP).toString() } else ""
            return Result(if (complete) process.exitValue() else -1, text)
        } finally {
            // IO/security failure or Thread.start refusal must not leak a child
            // or replace the original safe channel failure during cleanup.
            try { if (process.isAlive) stopInstaller(process) } catch (_: Throwable) { }
            try { process.outputStream.close() } catch (_: Throwable) { }
            try { process.inputStream.close() } catch (_: Throwable) { }
            try { reader?.join(2000) }
            catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            catch (_: Throwable) { }
            synchronized(output) { output.setLength(0) }
            finishInstaller(process)
        }
    }

    private fun admitCheckProcess(script: String, timeoutMillis: Long, began: Long, agentUser: Boolean): Process {
        val queueDeadline = began + timeoutMillis.coerceAtMost(RUN_ADMISSION_WINDOW_MS)
        var admitted: Process? = null
        while (admitted == null) {
            admitted = synchronized(this) {
                reclaimDeadInstaller()
                if (installerProcess == null) {
                    check(SystemClock.elapsedRealtime() - began <= timeoutMillis) {
                        "Another phone task is running. Wait a moment and try again."
                    }
                    startInstaller(script, InstallerTarget.entries.toSet(), InstallerOperation.CHECK, agentUser)
                } else null
            }
            if (admitted == null) {
                check(SystemClock.elapsedRealtime() < queueDeadline) {
                    "Another phone task is running. Wait a moment and try again."
                }
                Thread.sleep(PROCESS_POLL_MS)
            }
        }
        return admitted
    }

    /**
     * Starts [script] inside Ubuntu with its output appended to [log], or piped
     * back when [log] is null. proot is given --kill-on-exit, so stopping it
     * stops everything the script started.
     */
    @Synchronized
    fun start(script: String, log: File?, agentUser: Boolean = false): Process {
        check(installed) { "Ubuntu is not installed in the app yet" }
        processes.removeAll { !it.isAlive }
        return ProcessBuilder(prootCommand(listOf("/bin/sh", "-c", script), agentUser))
            .redirectErrorStream(true)
            .apply {
                environment().clear()
                environment().putAll(prootEnvironment())
                if (log != null) {
                    log.parentFile?.mkdirs()
                    redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                }
            }
            .start().also { processes.add(it); processConfinement[it] = prootIsConfined }
    }

    private val installerPreferences by lazy {
        context.getSharedPreferences("builtin_component_writer", Context.MODE_PRIVATE)
    }
    private val installerGuard = Any()
    @Volatile private var installerProcess: Process? = null
    private var installerLaunchId: String? = null
    private var componentUpdatesRecovered = false
    private val componentUpdateFailure = "A component update could not be restored. Run setup again."
    private val componentRecovery by lazy { NativeComponentUpdateRecovery(context.filesDir, home, rootfs) }

    private fun installerRootfsGeneration(): String {
        installerPreferences.getString("rootfsGeneration", null)?.let {
            check(Regex("[0-9a-f]{64}").matches(it)) { componentUpdateFailure }
            return it
        }
        check(!installerPreferences.contains("ticket")) { componentUpdateFailure }
        val value = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        check(installerPreferences.edit().putString("rootfsGeneration", value).commit()) { componentUpdateFailure }
        return value
    }

    private fun installerTicket(): InstallerTicket? {
        val raw = installerPreferences.getString("ticket", null) ?: return null
        check(raw.length <= MAX_RECEIPT_CHARS) { componentUpdateFailure }
        fun decode(value: Any?, depth: Int = 0): Any? {
            check(depth <= 16) { componentUpdateFailure }
            return when (value) {
                JSONObject.NULL -> null
                is JSONObject -> value.keys().asSequence().associateWith { decode(value.get(it), depth + 1) }
                is JSONArray -> (0 until value.length()).map { decode(value.get(it), depth + 1) }
                else -> value
            }
        }
        @Suppress("UNCHECKED_CAST")
        return InstallerTicket.read(decode(JSONObject(raw)) as Map<String, Any?>)
    }

    private fun saveInstaller(ticket: InstallerTicket) {
        check(installerPreferences.edit().putString("ticket", JSONObject(ticket.map()).toString())
            .putLong("generation", ticket.ownership.generation).commit()) {
            componentUpdateFailure
        }
    }

    private fun installerPlan(ticket: InstallerTicket): NativeRuntimeOwnership.Drain =
        NativeInstallerOwnership.drainPlan(ticket, installerRootfsGeneration(), bootIdentity(), sameUidInventory(),
            registeredRuntimePids(), ::installerAbsenceConfirmed, ::nonceMatches)

    /** Only live completion can admit current native peers; cold ownership is never broadened. */
    private fun liveInstallerPlan(
        ticket: InstallerTicket, expectedProcess: Process, launchId: String,
    ): NativeRuntimeOwnership.Drain {
        check(installerProcess === expectedProcess && installerLaunchId == launchId && ticket.id == launchId) {
            componentUpdateFailure
        }
        val peers = knownOtherRuntime(exclude = expectedProcess, includeServer = true)
        val inventory = sameUidInventory()
        val live = NativeInstallerOwnership.withCurrentRuntimePeers(ticket, peers, inventory)
        return NativeInstallerOwnership.drainPlan(live, installerRootfsGeneration(), bootIdentity(), inventory,
            registeredRuntimePids(), ::installerAbsenceConfirmed, ::nonceMatches)
    }

    /** Signal zero only checks a recorded PID; missing /proc data is not proof of exit. */
    private fun installerAbsenceConfirmed(pid: Int): Boolean {
        check(pid > 1) { componentUpdateFailure }
        return try {
            Os.kill(pid, 0)
            false
        } catch (error: ErrnoException) {
            error.errno == OsConstants.ESRCH
        } catch (_: Throwable) {
            false
        }
    }

    private fun signalInstaller(identity: RuntimeProcessIdentity, signal: Int) {
        if (identity.sameProcess(kernelIdentity(identity.pid))) {
            check(processUid(identity.pid) == AndroidProcess.myUid()) { componentUpdateFailure }
            if (identity.sameProcess(kernelIdentity(identity.pid))) Os.kill(identity.pid, signal)
        }
    }

    private fun drainInstaller(ticket: InstallerTicket, expectedProcess: Process? = null, launchId: String? = null) {
        fun plan(): NativeRuntimeOwnership.Drain = if (expectedProcess == null) installerPlan(ticket) else {
            liveInstallerPlan(ticket, expectedProcess, launchId ?: error(componentUpdateFailure))
        }
        val deadline = SystemClock.elapsedRealtime() + OWNED_DRAIN_TIMEOUT_MS
        for (identity in plan().server) signalInstaller(identity, OsConstants.SIGTERM)
        while (plan().server.isNotEmpty() &&
            SystemClock.elapsedRealtime() < deadline - OWNED_DRAIN_KILL_WINDOW_MS) Thread.sleep(PROCESS_POLL_MS)
        for (identity in plan().server) signalInstaller(identity, OsConstants.SIGKILL)
        while (plan().server.isNotEmpty() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(PROCESS_POLL_MS)
        check(plan().server.isEmpty()) { componentUpdateFailure }
    }

    /** A failed launch/completion must not permanently reserve a dead warm writer. Never signals. */
    @Synchronized
    private fun reclaimDeadInstaller(): Boolean = synchronized(installerGuard) {
        val cached = installerProcess
        if (cached?.isAlive == true) return@synchronized false
        // The first cold admission retains its existing boot/rollback recovery authority.
        if (!componentUpdatesRecovered && cached == null) return@synchronized false
        val launchId = installerLaunchId
        val currentOwner = {
            installerProcess === cached && installerLaunchId == launchId && cached?.isAlive != true
        }
        NativeInstallerAdmission.reclaim(installerTicket(), false, currentOwner, ::installerTicket,
            { ticket ->
                val plan = if (cached == null) installerPlan(ticket)
                    else liveInstallerPlan(ticket, cached, launchId ?: error(componentUpdateFailure))
                plan.server.isEmpty()
            },
            { ticket ->
                check(currentOwner() && installerTicket() == ticket) { componentUpdateFailure }
                if (!installerPreferences.edit().remove("ticket").commit()) false else {
                    installerProcess = null
                    installerLaunchId = null
                    // A dead INSTALL can leave a journal: rollback still precedes the next guest command.
                    componentUpdatesRecovered = false
                    cached?.let { workLeases.release(it) }
                    true
                }
            })
    }

    /** One cold admission precedes the first guest command; no live install is rolled back. */
    @Synchronized
    private fun recoverColdComponentUpdates() {
        if (componentUpdatesRecovered) return
        try {
            check(installerProcess == null) { componentUpdateFailure }
            val pending = componentRecovery.hasPending()
            val ticket = installerTicket()
            if (ticket != null) {
                if (ticket.ownership.boot == bootIdentity()) drainInstaller(ticket)
                else check(sameUidInventory().all { it.pid in registeredRuntimePids() }) { componentUpdateFailure }
            }
            if (pending) {
                // A saved server receipt proves exact children, never that they cannot write.
                // Drain it before recovery rather than treating old helpers as trusted readers.
                recoverPendingServerReceipt()
                componentRecovery.recoverAfterQuiescence {
                    installerProcess == null && processes.none { it.isAlive } &&
                        agentProcessProfiles.keys.none { it.isAlive } &&
                        sameUidInventory().all { it.pid in registeredRuntimePids() }
                }
            }
            if (ticket != null) check(installerPreferences.edit().remove("ticket").commit()) { componentUpdateFailure }
            componentUpdatesRecovered = true
        } catch (_: Throwable) { throw IllegalStateException(componentUpdateFailure) }
    }

    private fun recoverPendingServerReceipt() {
        val profile = recoveryPreferences.getString("restoreOwner", null)
        if (profile != null) {
            val old = ownership(profile)
            if (old.boot == bootIdentity()) drainPreviousServer(old)
        }
    }

    private fun drainPreviousServer(old: NativeRuntimeReceipt) {
        fun oldMembers(): List<RuntimeProcessIdentity> {
            val current = sameUidInventory()
            NativeInstallerVisibility.requireMissingGone(
                listOfNotNull(old.root, old.leader) + old.other, current, ::installerAbsenceConfirmed)
            val plan = NativeRuntimeOwnership.plan(old, bootIdentity(), current, registeredRuntimePids(),
                requireCompleteInventory = false, nonceMatches = ::nonceMatches)
            return (plan.server + current.filter { it.pid in plan.other }).distinctBy { it.pid }
        }
        val deadline = SystemClock.elapsedRealtime() + OWNED_DRAIN_TIMEOUT_MS
        for (identity in oldMembers()) signalInstaller(identity, OsConstants.SIGTERM)
        while (oldMembers().isNotEmpty() &&
            SystemClock.elapsedRealtime() < deadline - OWNED_DRAIN_KILL_WINDOW_MS) Thread.sleep(PROCESS_POLL_MS)
        for (identity in oldMembers()) signalInstaller(identity, OsConstants.SIGKILL)
        while (oldMembers().isNotEmpty() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(PROCESS_POLL_MS)
        check(oldMembers().isEmpty()) { componentUpdateFailure }
    }

    /** Fixed typed scope; script bytes and account/output values never enter the durable ticket. */
    @Synchronized
    internal fun startInstaller(script: String, targets: Set<InstallerTarget>, operation: InstallerOperation,
        agentUser: Boolean = false): Process = try {
        startQualifiedInstaller(script, targets, operation, agentUser)
    } catch (_: Throwable) { throw IllegalStateException(componentUpdateFailure) }

    private fun startQualifiedInstaller(script: String, targets: Set<InstallerTarget>, operation: InstallerOperation,
        agentUser: Boolean = false): Process {
        check(installed) { componentUpdateFailure }
        reclaimDeadInstaller()
        check(installerProcess == null) { componentUpdateFailure }
        recoverColdComponentUpdates()
        check(installerTicket() == null) { componentUpdateFailure }
        val nonce = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val others = knownOtherRuntime() + services[SERVER]?.process?.takeIf { it.isAlive }?.let { process ->
            val pid = pidOf(process) ?: error(componentUpdateFailure)
            (listOf(pid) + descendants(pid)).map { kernelIdentity(it) ?: error(componentUpdateFailure) }
        }.orEmpty()
        val generation = installerPreferences.getLong("generation", 0L) + 1L
        check(generation > 0L) { componentUpdateFailure }
        val prepared = InstallerTicket(nonce, installerRootfsGeneration(), targets, operation,
            NativeRuntimeReceipt(bootIdentity(), nonce, generation, null, null, others.distinctBy { it.pid }))
        saveInstaller(prepared)
        val gate = "printf 'OC-INSTALL-1 %s %s\\n' '$nonce' \"\$\$\"\n" +
            "IFS= read -r permit || exit 78\n[ \"\$permit\" = '$nonce' ] || exit 78\n" +
            "exec /bin/sh -c '" + script.replace("'", "'\"'\"'") + "'"
        var process: Process? = null
        var exactRoot: RuntimeProcessIdentity? = null
        var committedTicket: InstallerTicket? = null
        try {
            val launched = ProcessBuilder(prootCommand(listOf("/usr/bin/env", "OC_RUNTIME_OWNER=$nonce",
                "/usr/bin/setsid", "/bin/sh", "-c", gate), agentUser)).redirectErrorStream(true).apply {
                environment().clear()
                environment().putAll(prootEnvironment())
                environment()["OC_RUNTIME_OWNER"] = nonce
            }.start()
            process = launched
            installerProcess = launched
            installerLaunchId = nonce
            processes.add(launched); processConfinement[launched] = prootIsConfined
            exactRoot = pidOf(launched)?.let { kernelIdentity(it) }
            check(exactRoot != null && processUid(exactRoot!!.pid) == AndroidProcess.myUid()) { componentUpdateFailure }
            val committed = readInstallerIdentity(launched, nonce, prepared)
            committedTicket = committed
            installerPlan(committed) // No unknown previous writer can pass the workload gate.
            synchronized(installerGuard) { saveInstaller(committed) }
            if (setupWorkScopes.childNeedsLease()) trackWork(launched, WorkLeases.Kind.SETUP)
            launched.outputStream.write("$nonce\n".toByteArray(Charsets.US_ASCII))
            launched.outputStream.flush(); launched.outputStream.close()
            monitorInstallerOwnership(launched, nonce, committed)
            return launched
        } catch (_: Throwable) {
            try { process?.outputStream?.close() } catch (_: Throwable) { }
            drainFailedInstaller(committedTicket, exactRoot)
            installerProcess = null
            installerLaunchId = null
            throw IllegalStateException(componentUpdateFailure)
        }
    }

    private fun readInstallerIdentity(launched: Process, nonce: String, prepared: InstallerTicket): InstallerTicket {
        val read = java.util.concurrent.FutureTask<String> {
            val bytes = java.io.ByteArrayOutputStream()
            while (bytes.size() < 128) {
                val value = launched.inputStream.read(); check(value >= 0) { componentUpdateFailure }
                if (value == 10) return@FutureTask bytes.toString("US-ASCII")
                bytes.write(value)
            }
            error(componentUpdateFailure)
        }
        Thread(read, "phone-installer-gate").apply { isDaemon = true; start() }
        val header = read.get(5, TimeUnit.SECONDS).split(' ')
        check(header.size == 3 && header[0] == "OC-INSTALL-1" && header[1] == nonce) { componentUpdateFailure }
        val pid = pidOf(launched) ?: error(componentUpdateFailure)
        val root = kernelIdentity(pid) ?: error(componentUpdateFailure)
        val leader = kernelIdentity(header[2].toInt()) ?: error(componentUpdateFailure)
        check(processUid(pid) == AndroidProcess.myUid() && processUid(leader.pid) == AndroidProcess.myUid() &&
            leader.pid in descendants(pid) && nonceMatches(leader.pid, nonce)) { componentUpdateFailure }
        return NativeInstallerOwnership.committed(prepared, root, leader)
    }

    private fun monitorInstallerOwnership(launched: Process, nonce: String, committed: InstallerTicket) {
        Thread({
            while (installerProcess === launched) {
                try {
                    synchronized(this@BuiltinLinux) { synchronized(installerGuard) {
                        if (installerProcess !== launched) return@Thread
                        val ticket = installerTicket() ?: return@Thread
                        if (ticket.id != nonce) return@Thread
                        val observed = liveInstallerPlan(ticket, launched, nonce).server
                        if (observed != ticket.observed) saveInstaller(ticket.copy(observed = observed))
                    } }
                } catch (_: Throwable) {
                    synchronized(this@BuiltinLinux) { synchronized(installerGuard) {
                        if (installerProcess !== launched) return@Thread
                        try { drainInstaller(committed, launched, nonce) } catch (_: Throwable) { }
                    } }
                    return@Thread // Durable receipt remains; completion must still prove the drain.
                }
                Thread.sleep(OWNERSHIP_POLL_MS)
            }
        }, "phone-installer-ownership").apply { isDaemon = true; start() }
    }

    private fun drainFailedInstaller(committed: InstallerTicket?, exactRoot: RuntimeProcessIdentity?) {
        try {
            committed?.let { drainInstaller(it) } ?: exactRoot?.let { root ->
                drainExactInstallerRoot(root)
            }
        } catch (_: Throwable) { }
    }

    private fun drainExactInstallerRoot(root: RuntimeProcessIdentity) {
        // Before permit, capture descendants only while the original root is current.
        if (root.sameProcess(kernelIdentity(root.pid))) {
            val captured = (descendants(root.pid).mapNotNull { kernelIdentity(it) } + root)
                .filter { processUid(it.pid) == AndroidProcess.myUid() }
            for (identity in captured) signalInstaller(identity, OsConstants.SIGKILL)
        }
    }

    @Synchronized
    internal fun stopInstaller(process: Process) {
        try {
            check(installerProcess === process) { componentUpdateFailure }
            synchronized(installerGuard) {
                drainInstaller(installerTicket() ?: error(componentUpdateFailure), process,
                    installerLaunchId ?: error(componentUpdateFailure))
            }
        } catch (_: Throwable) { throw IllegalStateException(componentUpdateFailure) }
    }

    @Synchronized
    internal fun finishInstaller(process: Process) {
        workLeases.release(process)
        try {
            check(installerProcess === process) { componentUpdateFailure }
            synchronized(installerGuard) {
                val ticket = installerTicket() ?: error(componentUpdateFailure)
                drainInstaller(ticket, process, installerLaunchId ?: error(componentUpdateFailure))
                check(installerPreferences.edit().remove("ticket").commit()) { componentUpdateFailure }
                installerProcess = null
                installerLaunchId = null
            }
        } catch (_: Throwable) { throw IllegalStateException(componentUpdateFailure) }
    }

    /** proot's own path: the program a terminal session (LocalTerminal.kt) starts. */
    val prootPath: String get() = "$nativeDir/libproot.so"

    /**
     * The proot command line that runs [program] inside Ubuntu as root, with
     * a clean environment. [start] and the local terminal (LocalTerminal.kt)
     * both use it, so a shell sees exactly what the app's scripts see.
     */
    @Synchronized
    fun prootCommand(program: List<String>, agentUser: Boolean = false): List<String> {
        check(!installingRuntime) { "Runtime installation is still running" }
        recoverColdComponentUpdates()
        projectStorage.prepare()
        val agentRoot = if (agentUser) PhoneAgentPaths.prepare(context.filesDir, "linux/agent-root-view").apply {
            PhoneAgentPaths.prepare(this, "projects")
        } else null
        val command = listOf(
            prootPath,
            if (agentUser) "--change-id=1000:1000" else "--root-id",
            "--kill-on-exit",
            // Android does not let apps make hard links; dpkg and git do.
            "--link2symlink",
            "-L",
            "--sysvipc",
            "--rootfs=${rootfs.absolutePath}",
            "--bind=/dev",
            "--bind=/proc",
            "--bind=/sys",
            "--bind=${File(rootfs, "tmp").absolutePath}:/dev/shm",
        ) + (if (agentRoot != null) listOf("--bind=${agentRoot.absolutePath}:/root") else emptyList()) +
            listOf("--bind=${projectStorage.projects.absolutePath}:/root/projects") + sharedStorageBinds() + fakeProcBinds + (if (protectionTier() == "proot") {
            // PRoot exposes host proc by default. Hide native app/daemon entries
            // rather than depending on Linux cmdline permissions alone.
            val mask = File(home.canonicalFile, "proc/phone-engine-hidden").apply { mkdirs() }
            check(mask.isDirectory && mask.canonicalFile == mask.absoluteFile && mask.listFiles()?.isEmpty() == true)
            (prootMaskedPids + AndroidProcess.myPid()).sorted().flatMap {
                listOf("--bind=${mask.absolutePath}:/proc/$it")
            }
        } else emptyList()) + listOf(
            if (agentUser) "--cwd=/root/projects" else "--cwd=/root",
            "/usr/bin/env", "-i",
            if (agentUser) "HOME=/home/oc" else "HOME=/root",
            "LANG=C.UTF-8",
            if (agentUser) "PATH=/home/oc/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                else "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "TMPDIR=/tmp",
        ) + program
        workLeases.observeTerminalsSoon()
        return if (prootIsConfined && protectionTier() != "proot") protectedCommand(command) else command
    }

    private val blockedAgentProfiles = mutableSetOf<String>()
    private val agentProcessProfiles = mutableMapOf<Process, String>()
    private val authOtherOwners = PhoneAgentAuthOtherOwners { process ->
        pidOf(process)?.let { pid -> kernelIdentity(pid)?.let { it.pid to it.startTicks.toString() } }
    }

    /**
     * An agent's own sign-in (`claude auth login`, `codex login`, …) for
     * [profileId]'s agent account, to run on a terminal: the person signs in
     * through the agent's prompts and its provider's page, and the app never
     * reads a code or key. The page the agent asks the system to open is
     * written to the returned file by a stand-in `xdg-open`, and the
     * terminal opens it in the browser. Only the catalog's agent programs
     * and plain arguments run.
     */
    @Synchronized
    fun agentSignInCommand(profileId: String, program: List<String>): Pair<List<String>, File> {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profileId)) { "The agent host is unavailable." }
        check(installed && profileId !in blockedAgentProfiles) { "The agent host is unavailable." }
        check(program.isNotEmpty() && program.size <= 5 && program.first() in SIGN_IN_PROGRAMS &&
            program.drop(1).all { Regex("^[A-Za-z0-9-]{1,32}$").matches(it) }) { "That sign-in can't run here." }
        val profileHome = PhoneAgentPaths.prepare(context.filesDir, "linux/ubuntu/home/oc/.oc-profiles/$profileId")
        Os.chmod(profileHome.absolutePath, 448)
        val guestHome = "/home/oc/.oc-profiles/$profileId"
        val bin = File(profileHome, ".oc-bin").apply { mkdirs() }
        val opener = "#!/bin/sh\n" +
            "printf '%s\\n' \"${'$'}1\" > \"${'$'}HOME/.oc-open-url.tmp\" && " +
            "mv \"${'$'}HOME/.oc-open-url.tmp\" \"${'$'}HOME/.oc-open-url\"\n"
        for (name in listOf("xdg-open", "open-url")) {
            File(bin, name).apply {
                writeText(opener)
                setReadable(true, false)
                setExecutable(true, false)
            }
        }
        val request = File(profileHome, ".oc-open-url").apply { delete() }
        val command = listOf("/usr/bin/env", "HOME=$guestHome", "CLAUDE_CONFIG_DIR=$guestHome/claude",
            "BROWSER=$guestHome/.oc-bin/open-url",
            "PATH=$guestHome/.oc-bin:/home/oc/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "CODEX_HOME=$guestHome/codex",
            "TERM=xterm-256color", "LANG=C.UTF-8",
            "DISABLE_AUTOUPDATER=1", "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1") + program
        return prootCommand(command, agentUser = true) to request
    }

    /**
     * Removes Claude's OAuth refresh lock (`<config dir>/.oauth_refresh.lock`,
     * an empty folder) for [profileId] when none of that profile's agent
     * processes runs: one Android stopped mid-refresh never releases it.
     */
    @Synchronized
    fun clearStaleAgentLoginLock(profileId: String) {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profileId)) { "The agent host is unavailable." }
        val busy = agentProcessProfiles.any { (process, owner) -> owner == profileId && process.isAlive }
        if (busy) return
        val lock = File(context.filesDir, "linux/ubuntu/home/oc/.oc-profiles/$profileId/claude/.oauth_refresh.lock")
        if (lock.isDirectory && lock.canonicalFile.parentFile?.name == "claude") {
            lock.listFiles()?.forEach { it.delete() }
            if (lock.delete()) Log.i(TAG, "cleared a stale agent login lock")
        }
    }

    /** Private agent process: fixed uid, private host home, no transcript/log. */
    @Synchronized
    fun startAgentProcess(profileId: String, argv: List<String>, foreground: Boolean = false,
        privateOutput: Boolean = false): Process =
        startPrivateAgentProcess(profileId, argv, foreground, privateOutput, null)

    @Synchronized
    internal fun startSignInProcess(profileId: String, argv: List<String>, foreground: Boolean): Process =
        startPrivateAgentProcess(profileId, argv, foreground, false, WorkLeases.Kind.SIGN_IN)

    private fun startPrivateAgentProcess(profileId: String, argv: List<String>, foreground: Boolean,
        privateOutput: Boolean, workKind: WorkLeases.Kind?): Process {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profileId)) { "The agent host is unavailable." }
        check(installed && argv.isNotEmpty() && profileId !in blockedAgentProfiles) { "The agent host is unavailable." }
        val hostTicket = agentHostTicket.get()
        if (hostTicket != null) synchronized(recoveryLock) {
            check(hostTicket.profile == profileId && agentHostAdmissionCurrent(hostTicket))
        }
        val workGeneration = if (synchronized(recoveryLock) { activityResumed })
            workLeases.foregroundGeneration() else null
        val profileHome = PhoneAgentPaths.prepare(context.filesDir, "linux/ubuntu/home/oc/.oc-profiles/$profileId")
        Os.chmod(profileHome.absolutePath, 448)
        val guestHome = "/home/oc/.oc-profiles/$profileId"
        val command = listOf("/usr/bin/env", "HOME=$guestHome", "CLAUDE_CONFIG_DIR=$guestHome/claude",
            "CODEX_HOME=$guestHome/codex", "DISABLE_AUTOUPDATER=1", "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1") + argv
        val builder = ProcessBuilder(prootCommand(command, agentUser = true)).apply {
            environment().clear()
            environment().putAll(prootEnvironment())
            if (privateOutput) redirectError(File("/dev/null")) else redirectErrorStream(true)
        }
        val process = if (hostTicket == null) builder.start() else synchronized(recoveryLock) {
            check(agentHostAdmissionCurrent(hostTicket))
            if (hostTicket.idleGeneration != null) check(serverRunning)
            builder.start()
        }
        agentHostAdmissions.entries.removeAll { !it.key.isAlive }
        hostTicket?.let { agentHostAdmissions[process] = it }
        processes.add(process)
        agentProcessProfiles.entries.removeAll { !it.key.isAlive }
        agentProcessProfiles[process] = profileId
        agentWorkAdmissions.entries.removeAll { !it.key.isAlive }
        workGeneration?.let { agentWorkAdmissions[process] = it }
        authOtherOwners.register(process, privateOutput)
        processConfinement[process] = prootIsConfined
        try {
            if (foreground) trackPrivateAgentService("agent-auth.$profileId", process, null)
            workKind?.let { trackWork(process, it) }
            return process
        } catch (_: Throwable) {
            try { stopAgentProcess(process) } catch (_: Throwable) { }
            agentProcessProfiles.remove(process)
            throw IllegalStateException("The agent host could not start.")
        }
    }

    /** Uses the existing Android foreground-service deadline and exact PID stop. */
    @Synchronized
    fun trackPrivateAgentService(name: String, process: Process, port: Int?) {
        check(name.startsWith("agent-auth.") || name.startsWith("agent-host."))
        val hostTicket = if (name.startsWith("agent-host.")) {
            agentHostAdmissions[process]?.also { ticket ->
                synchronized(recoveryLock) {
                    check(name == "agent-host.${ticket.profile}" && agentHostAdmissionCurrent(ticket))
                    if (ticket.idleGeneration != null) check(serverRunning)
                }
            } ?: error("idle_resume_stale")
        } else null
        removeService(name)
        services[name] = serviceStarted(name, process, port, "Agents are working on this phone")
        recordRunning()
        try {
            BuiltinServerService.start(context, currentNotice())
            if (hostTicket != null) synchronized(recoveryLock) {
                check(agentHostAdmissionCurrent(hostTicket))
                val state = idleState.snapshot()
                if (hostTicket.idleGeneration == null && state.generation > 0) {
                    check(state.owner != null && idleState.clearCompletedHelperGate(state.owner, state.generation))
                }
                agentHostAdmissions.remove(process)
            }
            agentWorkAdmissions.remove(process)?.let { workLeases.authorizeForegroundWork(it) }
        }
        catch (_: Throwable) {
            try { stopAgentProcess(process) } catch (_: Throwable) { }
            removeStoppedService(name)
            throw IllegalStateException("The agent host could not start.")
        }
        try {
            Thread({
                try { process.waitFor() }
                catch (_: Throwable) {
                    try { stopAgentProcess(process) } catch (_: Throwable) { }
                } finally {
                    try {
                        synchronized(this) {
                            if (services[name]?.process === process) {
                                val exited = services.getValue(name)
                                recordServiceExit(name, exited)
                                services.remove(name)
                                if (name == SERVER) {
                                    synchronized(recoveryLock) {
                                        val idle = idleState.snapshot()
                                        if (idle.helperStopped && idle.owner != null) idleState.markServerLost(idle.owner, idle.generation)
                                    }
                                    scheduleNativeRecovery(exited)
                                }
                                serviceSetChanged()
                            }
                        }
                    } catch (_: Throwable) { /* Supervision must not crash the app. */ }
                }
            }, "phone-agent-service").start()
        } catch (_: Throwable) {
            try { stopAgentProcess(process) } catch (_: Throwable) { }
            removeStoppedService(name)
            throw IllegalStateException("The agent host could not start.")
        }
    }

    /** A cancelled private flow cannot report drained while captured children survive. */
    fun stopAgentProcess(process: Process) {
        if (!process.isAlive) return
        synchronized(this) {
            services.values.filter { it.process === process }.forEach { it.stopRequested = true }
        }
        fun token(pid: Int): String? = try {
            File("/proc/$pid/stat").readText().substringAfterLast(") ").split(' ').getOrNull(19)
        } catch (_: Exception) { null }
        val root = pidOf(process)
        val children = if (root == null) emptyMap() else descendants(root).associateWith { token(it) }
        stopTree(process)
        children.filter { (pid, identity) -> identity != null && token(pid) == identity }
            .forEach { (pid, _) -> signal(pid, OsConstants.SIGKILL) }
        if (!process.waitFor(2, TimeUnit.SECONDS)) throw IllegalStateException("The agent did not stop.")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (children.any { (pid, identity) -> identity != null && token(pid) == identity } &&
            System.nanoTime() < deadline) Thread.sleep(20)
        if (children.any { (pid, identity) -> identity != null && token(pid) == identity })
            throw IllegalStateException("The agent did not stop.")
    }

    private val privateAgentAuth by lazy {
        PhoneAgentAuthRuntime(context,
            { profile, argv -> startAgentProcess(profile, argv, privateOutput = true) },
            { profile, quiet -> synchronized(this) {
                if (quiet()) PhoneAgentAuthLock.clear(context.filesDir, profile)
            } }, authOtherOwners::snapshot)
    }

    /** Private auth output bypasses neither the generic receipt filter nor owner checks. */
    fun agentAuthProbe(arguments: Map<*, *>): Map<String, Any?> = privateAgentAuth.run(arguments)

    val agentSignIn by lazy { PhoneAgentSignIn(this) }
    val agentHost by lazy { PhoneAgentHost(this) }

    fun blockAgentProfile(profileId: String) {
        val targets = synchronized(this) {
            check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profileId))
            blockedAgentProfiles.add(profileId)
            agentProcessProfiles.filterValues { it == profileId }.keys.toList()
        }
        check(privateAgentAuth.blockProfile(profileId)) { "The agent did not stop." }
        targets.forEach { stopAgentProcess(it) }
    }

    @Synchronized
    fun writeAgentConfig(profileId: String, config: String) {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profileId) && config.length < 65536 && profileId !in blockedAgentProfiles)
        val data = org.json.JSONObject(config)
        check(data.optInt("version") == 1)
        val dir = PhoneAgentPaths.prepare(context.filesDir, "linux/ubuntu/home/oc/.oc-profiles/$profileId/paseo")
        Os.chmod(dir.absolutePath, 448)
        val temp = File(dir, "config.json.tmp")
        check(temp.canonicalFile == temp.absoluteFile && !File(dir, "config.json").let { it.canonicalFile != it.absoluteFile })
        temp.writeText(config)
        Os.chmod(temp.absolutePath, 384)
        check(temp.renameTo(File(dir, "config.json")))
    }

    @Synchronized
    fun deleteAgentHome(profileId: String) {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profileId))
        val base = PhoneAgentPaths.resolve(context.filesDir, "linux/ubuntu/home/oc/.oc-profiles")
        val target = PhoneAgentPaths.resolve(base, profileId)
        fun erase(file: File) {
            val stat = try { Os.lstat(file.absolutePath) } catch (_: Exception) { return }
            if (OsConstants.S_ISDIR(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode))
                file.listFiles()?.forEach { erase(it) }
            check(file.delete()) { "The agent sign-in could not be removed." }
        }
        check(target.parentFile?.canonicalFile == base)
        val names = setOf("agent-auth.$profileId", "agent-host.$profileId")
        check(names.none { services[it]?.process?.isAlive == true }) {
            "Stop the agent before removing its sign-in."
        }
        erase(target)
        names.forEach { removeStoppedService(it) }
        val retained = diagnosticsPreferences.getStringSet("names", emptySet()).orEmpty() - names
        val edit = diagnosticsPreferences.edit().putStringSet("names", retained)
        names.forEach { name ->
            listOf("exit", "uptime", "restarts", "launched", "exitAt", "stopRequested").forEach { edit.remove("$name.$it") }
        }
        check(edit.commit()) { "The agent sign-in could not be removed." }
    }

    private val sharedProjectsFile = File(context.filesDir, "oc.sharedProjects")

    /** The shared-storage folders the person opened as projects (canonical). */
    @Synchronized
    fun sharedProjectRoots(): List<String> = try {
        if (sharedProjectsFile.isFile) {
            sharedProjectsFile.readLines().mapNotNull { SharedStorageBinds.canonicalRoot(it) }.distinct()
        } else emptyList()
    } catch (_: Exception) { emptyList() }

    /** Replaces the remembered folders (the union over all profiles, from Dart). */
    @Synchronized
    fun setSharedProjectRoots(roots: List<String>) {
        val clean = roots.mapNotNull { SharedStorageBinds.canonicalRoot(it) }.distinct().sorted()
        if (clean.isEmpty()) { sharedProjectsFile.delete(); return }
        val tmp = File(sharedProjectsFile.parentFile, "oc.sharedProjects.tmp")
        tmp.writeText(clean.joinToString("\n"))
        if (!tmp.renameTo(sharedProjectsFile)) { tmp.delete(); throw IllegalStateException("shared projects not saved") }
    }

    private fun sharedStorageBinds(): List<String> =
        SharedStorageBinds.binds(sharedProjectRoots(), prootIsConfined)

    /** The executable must match argv[0], including for the native PTY bridge. */
    val prootLaunchPath: String get() =
        if (prootIsConfined && protectionTier() != "proot") "$nativeDir/libaiteam_sandbox.so" else prootPath

    private fun protectedCommand(command: List<String>): List<String> {
        PhoneEngineNative.verifyBundle(context, "libaiteam_sandbox.so")
        val tmp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
        val policy = listOf(
            "$nativeDir/libaiteam_sandbox.so", "--read-only", nativeDir,
            "--read-write", home.absolutePath,
            "--read-write", projectStorage.projects.absolutePath,
            "--read-write", tmp.absolutePath,
        ) + sharedProjectRoots().filter { File(it).isDirectory }
            .flatMap { listOf("--read-write", it) } + listOf("/system", "/apex", "/vendor", "/proc", "/sys")
            .filter { File(it).exists() }.flatMap { listOf("--read-only", it) } +
            listOf("/dev/null", "/dev/zero", "/dev/random", "/dev/urandom")
                .filter { File(it).exists() }.flatMap { listOf("--device", it) }
        return policy + listOf("--") + command
    }

    /** The shipped helper isolates forbidden syscalls in a disposable child. */
    private fun requirePhoneBoundaryKernel() {
        PhoneEngineNative.verifyBundle(context, "libaiteam_sandbox.so")
        val launcher = File(nativeDir, "libaiteam_sandbox.so")
        if (!launcher.canExecute()) throw PhoneEngineNative.Failure("boundary_not_packaged")
        val child = ProcessBuilder(launcher.absolutePath, "--check-kernel").apply {
            environment().clear()
            redirectOutput(File("/dev/null"))
            redirectError(File("/dev/null"))
        }.start()
        child.outputStream.close()
        if (!child.waitFor(3, TimeUnit.SECONDS)) {
            stopTree(child)
            throw PhoneEngineNative.Failure("boundary_unavailable")
        }
        when (child.exitValue()) {
            0 -> Unit
            78 -> throw PhoneEngineNative.Failure("boundary_unsupported")
            else -> throw PhoneEngineNative.Failure("boundary_unavailable")
        }
    }

    /** Diagnostic candidate only: path emulation never signs an authority receipt. */
    @Synchronized
    fun runProotViewBoundaryProbe(): Map<String, Any?> {
        if (!installed || prootIsConfined || hasUnconfinedChildren())
            throw PhoneEngineNative.Failure("restart_required")
        projectStorage.prepare()
        val id = java.util.UUID.randomUUID().toString()
        val fixture = File(context.filesDir.canonicalFile, ".phone-engine-view-$id")
        val worker = File(projectStorage.projects, ".phone-engine-view-$id")
        check(fixture.mkdir() && worker.mkdir())
        val sentinel = File(fixture, "sentinel").apply { writeText("isolated-private-control") }
        Os.chmod(fixture.absolutePath, 448)
        Os.chmod(sentinel.absolutePath, 384)
        Os.symlink(sentinel.absolutePath, File(fixture, "link").absolutePath)
        Os.symlink(fixture.absolutePath, File(worker, "private-alias").absolutePath)
        val labels = listOf("positiveWrite", "positiveRead", "positiveGit", "positiveStat", "positiveReadlink", "positiveTracerIdentity",
            "directOpenDenied", "directStatDenied", "directReadlinkDenied", "workerAliasDenied",
            "procSelfRootDenied", "procParentRootDenied", "parentEnvironDenied", "parentCmdlineDenied",
            "fdHygiene", "tracerEscapeDenied")
        val controls = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        val ready = java.util.concurrent.CountDownLatch(1)
        val observedTracer = java.util.concurrent.atomic.AtomicInteger(0)
        val root = fixture.absolutePath.replace("'", "'\"'\"'")
        val parent = AndroidProcess.myPid()
        val script = """
            set -u
            cd '/root/projects/${worker.name}' || exit 78
            allow() { label=${'$'}1; shift; if "${'$'}@" >/dev/null 2>&1; then printf '%s=true\n' "${'$'}label"; else printf '%s=false\n' "${'$'}label"; fi; }
            deny() { label=${'$'}1; shift; if "${'$'}@" >/dev/null 2>&1; then printf '%s=false\n' "${'$'}label"; else printf '%s=true\n' "${'$'}label"; fi; }
            allow positiveWrite sh -c 'printf worker > positive'
            allow positiveRead cat positive
            allow positiveStat stat positive
            ln -s positive readable-link
            allow positiveReadlink readlink readable-link
            allow positiveGit sh -c 'git init -q repo && git -C repo -c user.name=proof -c user.email=proof@invalid.example commit -qm proof --allow-empty && git -C repo update-ref refs/heads/worker-proof HEAD && git -C repo show-ref --verify --quiet refs/heads/worker-proof'
            deny directOpenDenied cat '$root/sentinel'
            deny directStatDenied stat '$root/sentinel'
            deny directReadlinkDenied readlink '$root/link'
            deny workerAliasDenied cat private-alias/sentinel
            deny procSelfRootDenied cat '/proc/self/root$root/sentinel'
            deny procParentRootDenied cat '/proc/$parent/root$root/sentinel'
            deny parentEnvironDenied cat '/proc/$parent/environ'
            deny parentCmdlineDenied cat '/proc/$parent/cmdline'
            # No inherited descriptor may reveal the seeded private fixture.
            if (for fd in /proc/self/fd/*; do readlink "${'$'}fd"; done) 2>/dev/null | grep -F '$root' >/dev/null; then
                printf 'fdHygiene=false\n'
            else
                printf 'fdHygiene=true\n'
            fi
            # proot can have a launcher and a separate actual tracer. Target
            # only the kernel-reported tracer, verified by the native parent.
            while IFS=: read field value; do
                if [ "${'$'}field" = TracerPid ]; then printf 'TRACE_PID=%s\n' "${'$'}value"; break; fi
            done < /proc/${'$'}${'$'}/status
            printf 'READY_ESCAPE\n'
            read tracer_pid || exit 78
            # Exact isolated tracer only. If detached, shell builtins now use
            # real host paths. Never aim this probe at actual canonical data.
            if kill -KILL "${'$'}tracer_pid" 2>/dev/null; then
                # Let the owned parent reap the tracer; otherwise this write
                # can race its last pathname rewrite and give a false denial.
                n=0
                while kill -0 "${'$'}tracer_pid" 2>/dev/null && [ "${'$'}n" -lt 100000 ]; do
                    n=${'$'}((n + 1))
                done
                /system/bin/sh -c "printf escaped-control > '$root/sentinel'" 2>/dev/null || :
                printf 'tracerEscapeDenied=false\n'
            else
                printf 'tracerEscapeDenied=true\n'
            fi
        """.trimIndent()
        var child: Process? = null
        var owned = emptyList<Int>()
        var reader: Thread? = null
        try {
            child = ProcessBuilder(prootCommand(listOf("/bin/sh", "-c", script))).apply {
                environment().clear()
                environment().putAll(prootEnvironment())
                redirectError(File("/dev/null"))
            }.start()
            val running = child
            reader = Thread {
                running.inputStream.bufferedReader().useLines { lines -> lines.take(64).forEach { line ->
                    if (line == "READY_ESCAPE") ready.countDown()
                    else if (line.startsWith("TRACE_PID="))
                        observedTracer.set(line.substringAfter('=').trim().toIntOrNull() ?: 0)
                    else line.split('=', limit = 2).takeIf { it.size == 2 && it[0] in labels &&
                        it[1] in listOf("true", "false") }?.let { controls[it[0]] = it[1] == "true" }
                } }
            }.apply { isDaemon = true; start() }
            if (ready.await(30, TimeUnit.SECONDS)) {
                val pid = pidOf(running) ?: throw PhoneEngineNative.Failure("boundary_probe_unavailable")
                owned = descendants(pid) + pid
                val tracer = observedTracer.get().takeIf { it > 0 && it in owned }
                    ?: throw PhoneEngineNative.Failure("boundary_probe_unavailable")
                controls["positiveTracerIdentity"] = true
                running.outputStream.write("$tracer\n".toByteArray(Charsets.US_ASCII))
                running.outputStream.flush()
            }
            running.outputStream.close()
            if (!running.waitFor(5, TimeUnit.SECONDS)) stopTree(running)
            reader.join(3000)
            val unchanged = sentinel.readText() == "isolated-private-control"
            return labels.associateWith { controls[it] == true } + mapOf(
                "fixtureUnchanged" to unchanged,
                "complete" to (unchanged && labels.all { controls[it] == true }),
                "enablesExecution" to false,
                "boundaryReason" to "boundary_unsupported")
        } finally {
            // Detached tracees are still ours; stop only the exact recorded PIDs.
            owned.forEach { signal(it, OsConstants.SIGKILL) }
            child?.takeIf { it.isAlive }?.let { stopTree(it) }
            try { child?.inputStream?.close() } catch (_: Exception) { }
            reader?.join(1000)
            fun erase(file: File) {
                if (OsConstants.S_ISDIR(Os.lstat(file.absolutePath).st_mode))
                    file.listFiles()?.forEach { erase(it) }
                if (!file.delete()) throw PhoneEngineNative.Failure("proof_cleanup_failed")
            }
            erase(worker)
            erase(fixture)
        }
    }

    /** Isolated acceptance harness; it never grants execution authority. */
    @Synchronized
    fun runProotTierProof(canonicalRoot: File, daemonPid: Int? = null): Map<String, Any?> {
        check(installed && canonicalRoot.canonicalFile == canonicalRoot && canonicalRoot.isDirectory)
        val base = context.filesDir.canonicalFile
        check(canonicalRoot.parentFile == base && canonicalRoot.name.startsWith("oc.teamEngine."))
        val id = java.util.UUID.randomUUID().toString()
        val fixture = File(base, ".phone-engine-view-$id")
        val worker = File(projectStorage.projects, ".phone-engine-view-$id")
        projectStorage.prepare()
        check(fixture.mkdir() && worker.mkdir())
        val sentinel = File(fixture, "sentinel").apply { writeText("private-proot-control") }
        Os.chmod(fixture.absolutePath, 448)
        Os.chmod(sentinel.absolutePath, 384)
        Os.symlink(sentinel.absolutePath, File(fixture, "link").absolutePath)
        Os.symlink(fixture.absolutePath, File(worker, "private-alias").absolutePath)
        var subject: Process? = null
        var child: Process? = null
        var reader: Thread? = null
        var maskedSubject: Int? = null
        val controls = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        val labels = listOf("positiveWorkerClone", "positiveOpen", "positiveStat", "positiveReadlink", "positiveLs",
            "canonicalPathsDenied", "daemonProcDenied", "fdHygiene", "parentInspectionDenied",
            "canonicalOpenDenied", "canonicalStatDenied", "canonicalReadlinkDenied", "canonicalListDenied",
            "workerAliasOpenDenied", "workerAliasStatDenied", "workerAliasListDenied")
        fun quote(s: String) = "'" + s.replace("'", "'\"'\"'") + "'"
        try {
            var fd: Int? = null
            val pid = daemonPid ?: run {
                PhoneEngineNative.verifyBundle(context, "libaiteam_engine.so")
                val engine = File(nativeDir, "libaiteam_engine.so")
                val started = ProcessBuilder(engine.absolutePath, "--proot-proof-subject", fixture.absolutePath).apply {
                    environment().clear()
                    redirectError(File("/dev/null"))
                }.start()
                subject = started
                val ready = java.util.concurrent.FutureTask<String> {
                    val bytes = java.io.ByteArrayOutputStream()
                    while (bytes.size() < 128) {
                        val b = started.inputStream.read()
                        if (b < 0 || b == 10) break
                        bytes.write(b)
                    }
                    bytes.toString("US-ASCII")
                }
                Thread(ready, "proot-proof-subject").apply { isDaemon = true; start() }
                val frame = try { ready.get(5, TimeUnit.SECONDS) }
                    catch (_: Exception) { ready.cancel(true); throw PhoneEngineNative.Failure("boundary_probe_unavailable") }
                val match = Regex("READY_SUBJECT:([0-9]+):([0-9]+)").matchEntire(frame)
                    ?: throw PhoneEngineNative.Failure("boundary_probe_unavailable")
                fd = match.groupValues[2].toIntOrNull()?.takeIf { it >= 3 }
                    ?: throw PhoneEngineNative.Failure("boundary_probe_unavailable")
                match.groupValues[1].toIntOrNull()?.takeIf { it > 1 && started.isAlive }
                    ?: throw PhoneEngineNative.Failure("boundary_probe_unavailable")
            }
            check(pid > 1)
            prootMaskedPids.add(pid)
            maskedSubject = pid
            val parent = AndroidProcess.myPid()
            val paths = listOf(fixture.absolutePath, sentinel.absolutePath, File(fixture, "link").absolutePath,
                canonicalRoot.absolutePath)
            val procPaths = listOf("/proc/$pid/root", "/proc/$pid/cwd", "/proc/$pid/fd") +
                listOfNotNull(fd?.let { "/proc/$pid/fd/$it" })
            val script = """
                set -u
                cd '/root/projects/${worker.name}' || exit 78
                ok() { label=${'$'}1; shift; if "${'$'}@" >/dev/null 2>&1; then printf '%s=true\n' "${'$'}label"; else printf '%s=false\n' "${'$'}label"; fi; }
                ok positiveWorkerClone sh -c 'git init -q source && git -C source -c user.name=proof -c user.email=proof@invalid.example commit -qm proof --allow-empty && git clone -q source clone && git -C clone rev-parse --verify HEAD >/dev/null'
                printf worker > clone/positive
                ln -s positive clone/readable-link
                ok positiveOpen cat clone/positive
                ok positiveStat stat clone/positive
                ok positiveReadlink readlink clone/readable-link
                ok positiveLs ls clone
                denied=true
                open_denied=true; stat_denied=true; readlink_denied=true; list_denied=true
                for path in ${paths.joinToString(" ", transform = ::quote)}; do
                    if cat "${'$'}path" >/dev/null 2>&1; then open_denied=false; fi
                    if stat "${'$'}path" >/dev/null 2>&1; then stat_denied=false; fi
                    if readlink "${'$'}path" >/dev/null 2>&1; then readlink_denied=false; fi
                    if ls "${'$'}path" >/dev/null 2>&1; then list_denied=false; fi
                done
                printf 'canonicalOpenDenied=%s\ncanonicalStatDenied=%s\ncanonicalReadlinkDenied=%s\ncanonicalListDenied=%s\n' "${'$'}open_denied" "${'$'}stat_denied" "${'$'}readlink_denied" "${'$'}list_denied"
                alias_open=true; alias_stat=true; alias_list=true
                if cat private-alias/sentinel >/dev/null 2>&1; then alias_open=false; fi
                if stat -L private-alias/sentinel >/dev/null 2>&1; then alias_stat=false; fi
                if ls private-alias/sentinel >/dev/null 2>&1; then alias_list=false; fi
                printf 'workerAliasOpenDenied=%s\nworkerAliasStatDenied=%s\nworkerAliasListDenied=%s\n' "${'$'}alias_open" "${'$'}alias_stat" "${'$'}alias_list"
                for flag in "${'$'}open_denied" "${'$'}stat_denied" "${'$'}readlink_denied" "${'$'}list_denied" "${'$'}alias_open" "${'$'}alias_stat" "${'$'}alias_list"; do
                    [ "${'$'}flag" = true ] || denied=false
                done
                printf 'canonicalPathsDenied=%s\n' "${'$'}denied"
                denied=true
                for path in ${procPaths.joinToString(" ", transform = ::quote)}; do
                    for op in cat stat readlink ls; do
                        if "${'$'}op" "${'$'}path" >/dev/null 2>&1; then denied=false; fi
                    done
                done
                for path in '/proc/$pid/root${sentinel.absolutePath}' '/proc/$pid/cwd/sentinel' '/proc/self/root${sentinel.absolutePath}' '/proc/self/cwd${sentinel.absolutePath}'; do
                    if cat "${'$'}path" >/dev/null 2>&1; then denied=false; fi
                done
                printf 'daemonProcDenied=%s\n' "${'$'}denied"
                denied=true
                for path in '/proc/$pid/environ' '/proc/$pid/cmdline' '/proc/$parent/environ' '/proc/$parent/cmdline'; do
                    if cat "${'$'}path" >/dev/null 2>&1; then denied=false; fi
                done
                printf 'parentInspectionDenied=%s\n' "${'$'}denied"
                denied=true
                for f in /proc/self/fd/*; do
                    target=${'$'}(readlink "${'$'}f" 2>/dev/null || :)
                    case "${'$'}target" in *'oc.teamEngine.'*|*'.phone-engine-view-'*'/sentinel'*) denied=false;; esac
                done
                if env | grep -E 'OC_ENGINE_TOKEN|OC_PROMOTION|OC_BOUNDARY_PRIVATE_CANARY' >/dev/null; then denied=false; fi
                printf 'fdHygiene=%s\n' "${'$'}denied"
            """.trimIndent()
            child = ProcessBuilder(prootCommand(listOf("/bin/sh", "-c", script))).apply {
                environment().clear()
                environment().putAll(prootEnvironment())
                redirectError(File("/dev/null"))
            }.start()
            val running = child
            running.outputStream.close()
            reader = Thread {
                running.inputStream.bufferedReader().useLines { lines -> lines.take(32).forEach { line ->
                    line.split('=', limit = 2).takeIf { it.size == 2 && it[0] in labels &&
                        it[1] in listOf("true", "false") }?.let { controls[it[0]] = it[1] == "true" }
                } }
            }.apply { isDaemon = true; start() }
            val finished = running.waitFor(30, TimeUnit.SECONDS)
            if (!finished) stopTree(running)
            reader.join(2000)
            val unchanged = sentinel.readText() == "private-proot-control"
            val complete = finished && running.exitValue() == 0 && unchanged && labels.all { controls[it] == true }
            val report = labels.associateWith { controls[it] == true } + mapOf("tier" to "proot",
                "nativeAttacksDenied" to false, "prootGitCompatible" to (controls["positiveWorkerClone"] == true),
                "fixtureUnchanged" to unchanged, "complete" to complete)
            latestBoundaryControls = report
            return report
        } finally {
            if (daemonPid == null) maskedSubject?.let { prootMaskedPids.remove(it) }
            subject?.let { try { it.outputStream.close() } catch (_: Exception) { }; if (!it.waitFor(3, TimeUnit.SECONDS)) stopTree(it) }
            child?.takeIf { it.isAlive }?.let { stopTree(it) }
            try { child?.inputStream?.close() } catch (_: Exception) { }
            reader?.join(1000)
            fun erase(file: File) {
                if (OsConstants.S_ISDIR(Os.lstat(file.absolutePath).st_mode)) file.listFiles()?.forEach { erase(it) }
                if (!file.delete()) throw PhoneEngineNative.Failure("proof_cleanup_failed")
            }
            erase(worker)
            erase(fixture)
        }
    }

    /** Isolated acceptance harness; it never grants execution authority. */
    @Synchronized
    fun runPhoneEngineBoundaryProbe(): Map<String, Any?> {
        PhoneEngineNative.verifyBundle(context, "libaiteam_sandbox.so", "libaiteam_boundary_probe.so")
        if (!installed || hasUnconfinedChildren()) {
            throw PhoneEngineNative.Failure("restart_required")
        }
        requirePhoneBoundaryKernel()
        val probe = File(nativeDir, "libaiteam_boundary_probe.so")
        if (!probe.canExecute()) throw PhoneEngineNative.Failure("boundary_not_packaged")
        val id = java.util.UUID.randomUUID().toString()
        val fixture = File(context.filesDir, ".phone-engine-proof-$id")
        val protected = File(fixture, "protected")
        val worker = File(projectStorage.projects, ".phone-engine-proof-$id")
        projectStorage.prepare()
        check(protected.mkdirs() && worker.mkdir())
        Os.chmod(fixture.absolutePath, 448)
        Os.chmod(protected.absolutePath, 448)
        PhoneEngineAttestation.write(File(fixture, ".native-proof-fixture"), id.toByteArray(Charsets.US_ASCII))
        val sentinel = File(protected, "sentinel")
        sentinel.writeText("proof-only-canonical-state")
        Os.chmod(sentinel.absolutePath, 384)
        Os.symlink(protected.absolutePath, File(worker, "protected-alias").absolutePath)
        fun runProbe(argv: List<String>, environment: Map<String, String> = emptyMap()): Boolean {
            val process = ProcessBuilder(argv).apply {
                environment().clear()
                environment().putAll(environment)
                redirectOutput(File("/dev/null"))
                redirectError(File("/dev/null"))
            }.start()
            process.outputStream.close()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                stopTree(process)
                return false
            }
            return process.exitValue() == 0
        }
        return try {
            val preparation = ProcessBuilder(probe.absolutePath, "--prepare-git-fixture", protected.canonicalPath)
                .redirectError(File("/dev/null")).apply { environment().clear() }.start()
            preparation.outputStream.close()
            if (!preparation.waitFor(30, TimeUnit.SECONDS)) {
                stopTree(preparation)
                throw PhoneEngineNative.Failure("proof_fixture_unavailable")
            }
            val prepared = preparation.inputStream.use { input ->
                val bytes = ByteArray(128)
                val n = input.read(bytes)
                if (n < 0) "" else String(bytes, 0, n, Charsets.US_ASCII).trim()
            }
            if (preparation.exitValue() != 0 || !Regex("prepared-main:[0-9a-f]{40}").matches(prepared))
                throw PhoneEngineNative.Failure("proof_fixture_unavailable")
            val expectedMain = prepared.substringAfter(':')
            val mainRef = File(protected, ".git/refs/heads/main")
            val head = File(protected, ".git/HEAD")
            val config = File(protected, ".git/config")
            if (mainRef.readText().trim() != expectedMain || head.readText().trim() != "ref: refs/heads/main")
                throw PhoneEngineNative.Failure("proof_fixture_unavailable")
            val configHash = PhoneEngineAttestation.hash(config)
            val native = runProbe(protectedCommand(listOf(probe.absolutePath,
                protected.absolutePath, worker.absolutePath, AndroidProcess.myPid().toString())))
            val escapedRoot = protected.absolutePath.replace("'", "'\"'\"'")
            val guestWorker = "/root/projects/${worker.name}"
            val script = """
                set -eu
                cd '$guestWorker'
                # Positive controls cover writes, real Git refs and commits.
                printf 'worker\n' > positive-control
                git init -q positive-repo
                git -C positive-repo config user.name proof
                git -C positive-repo config user.email proof@invalid.example
                printf 'change\n' > positive-repo/file
                git -C positive-repo add file
                git -C positive-repo commit -q -m proof
                git -C positive-repo update-ref refs/heads/worker-proof HEAD
                git -C positive-repo show-ref --verify --quiet refs/heads/worker-proof
                # These are defence-in-depth view tests; the native raw probes
                # above establish OS denial independently of proot rewriting.
                if (printf 'attack' > '$escapedRoot/sentinel') 2>/dev/null; then exit 1; fi
                if (printf 'attack' > '/proc/${AndroidProcess.myPid()}/root$escapedRoot/sentinel') 2>/dev/null; then exit 1; fi
                if cat '/proc/${AndroidProcess.myPid()}/environ' >/dev/null 2>&1; then exit 1; fi
                if git -c core.hooksPath=/dev/null -C '$escapedRoot' update-ref -d refs/heads/main $expectedMain >/dev/null 2>&1; then exit 1; fi
                if git -c core.hooksPath=/dev/null --git-dir='$escapedRoot/.git' update-ref -d refs/heads/main $expectedMain >/dev/null 2>&1; then exit 1; fi
                if git --git-dir='$escapedRoot/.git' config core.hooksPath /dev/null >/dev/null 2>&1; then exit 1; fi
                if (printf 'attack' > '$escapedRoot/.git/refs/heads/main') 2>/dev/null; then exit 1; fi
            """.trimIndent()
            val wasProtected = protectedProot
            val proot = try {
                protectedProot = true
                runProbe(prootCommand(listOf("/bin/sh", "-c", script)), prootEnvironment())
            } finally { protectedProot = wasProtected }
            val unchanged = sentinel.readText() == "proof-only-canonical-state" &&
                (Os.lstat(sentinel.absolutePath).st_mode and 511) == 384 &&
                mainRef.readText().trim() == expectedMain && head.readText().trim() == "ref: refs/heads/main" &&
                PhoneEngineAttestation.hash(config) == configHash
            mapOf("schemaVersion" to 1, "tier" to "landlock", "nativeAttacksDenied" to native,
                "prootGitCompatible" to proot, "fixtureUnchanged" to unchanged,
                "complete" to (native && proot && unchanged),
                "enablesExecution" to false, "boundaryReason" to "boundary_unverified")
        } finally {
            // Fixture traversal never follows links into protected app state.
            fun erase(file: File) {
                if (OsConstants.S_ISDIR(Os.lstat(file.absolutePath).st_mode))
                    file.listFiles()?.forEach { erase(it) }
                if (!file.delete()) throw PhoneEngineNative.Failure("proof_cleanup_failed")
            }
            erase(worker)
            erase(fixture)
        }
    }

    @Synchronized
    fun startProtectedPhoneServer(profile: String, script: String, port: Int) {
        PhoneEngineNative.verifyBundle(context, "libaiteam_sandbox.so")
        phoneEngine.status(profile) // Validates the profile, without reading auth.
        if (serverRunning || services.any { it.key != PHONE_ENGINE && it.value.process.isAlive } ||
            processes.any { it.isAlive } || LocalTerminal.get(context).hasLiveSessions()) {
            throw PhoneEngineNative.Failure("restart_required")
        }
        if (port !in 1024..65535) throw PhoneEngineNative.Failure("invalid_port")
        if (phoneEngine.status(profile)["boundary"] != true) throw PhoneEngineNative.Failure("boundary_unverified")
        val tier = phoneEngine.status(profile)["boundaryTier"]
        if (tier !in listOf("landlock", "proot")) throw PhoneEngineNative.Failure("boundary_unverified")
        if (tier == "landlock") requirePhoneBoundaryKernel()
        // This affects every subsequent run, service and PTY launch. Kernel
        // support is a prerequisite, not proof; capabilities remain false.
        protectedProot = true
        protectedTier = tier as String
        try { startServer(script, port) } catch (error: Exception) {
            protectedProot = false
            throw error
        }
    }

    @Synchronized
    fun phoneEngineStatus(profile: String): Map<String, Any?> {
        val native = phoneEngine.status(profile)
        val unconfined = hasUnconfinedChildren()
        return native + mapOf("restartRequired" to unconfined,
            "unconfinedChildren" to unconfined, "protectionRequired" to markerPresent())
    }

    @Synchronized
    fun phoneEngineCredentials(profile: String): Map<String, String> = phoneEngine.credentials(profile)

    @Synchronized
    fun startPhoneEngine(profile: String, port: Int, notice: String?): Map<String, Any?> {
        try {
            val status = startPhoneEngineChecked(profile, port, notice)
            if (status["boundary"] != true) restoreStoppedPhoneServer()
            else stoppedPhoneServer = null
            return status
        } catch (error: Exception) {
            // Roll back only the server explicitly stopped in this runtime.
            // The UI may repeat its existing restart; startServer is idempotent.
            try { restoreStoppedPhoneServer() } catch (_: Exception) { }
            throw error
        }
    }

    private fun startPhoneEngineChecked(profile: String, port: Int, notice: String?): Map<String, Any?> {
        // Quiesce the tracked daemon before proof. Its stored receipt and
        // cached flags may belong to an older package/generation.
        phoneEngine.prepareFreshStart(profile)
        prootMaskedPids.clear()
        if (services[PHONE_ENGINE]?.process?.isAlive != true) removeStoppedService(PHONE_ENGINE)
        serviceSetChanged()
        // PRoot proc bindings are fixed at launch. Even a prior protected
        // generation must stop before its replacement daemon changes PID.
        val blocked = hasUnconfinedChildren() || serverRunning || processes.any { it.isAlive } ||
            services.any { it.key != PHONE_ENGINE && it.value.process.isAlive } ||
            LocalTerminal.get(context).hasLiveSessions()
        if (blocked) throw PhoneEngineNative.Failure("restart_required")
        val tier = try { requirePhoneBoundaryKernel(); "landlock" }
            catch (failure: PhoneEngineNative.Failure) {
                if (failure.code != "boundary_unsupported") throw failure
                "proot"
            }
        protectedTier = tier
        var reason = if (blocked) "restart_required" else "boundary_unverified"
        var controls: Map<String, Any?>? = if (tier == "landlock") try {
            runPhoneEngineBoundaryProbe().also {
                if (it["complete"] != true) reason = "boundary_proof_failed"
            }
        } catch (e: Exception) {
            reason = if (e is PhoneEngineNative.Failure) e.code else "boundary_proof_unavailable"
            null
        } else null
        val child = phoneEngine.start(profile, port, blocked, reason) { root ->
            if (tier == "proot") {
                protectedTier = "proot"
                controls = runProotTierProof(root)
            }
            val verified = controls
            if (verified?.get("complete") != true || hasUnconfinedChildren()) {
                throw PhoneEngineNative.Failure("boundary_proof_failed")
            } else {
                try {
                    commitProtectionAfterReceipt({
                        PhoneEngineAttestation(context).issue(profile, root, java.util.UUID.randomUUID().toString(),
                            if (tier == "landlock") protectedCommand(emptyList()).dropLast(1)
                                else listOf("tier=proot", prootPath, "--kill-on-exit", rootfs.canonicalPath,
                                    "private-store-unbound", "clean-environment", "proc-subject-denied"), verified)
                    }) {
                        // Signing must succeed first. This monitor fences all
                        // legacy launches until persistence and protected mode.
                        PhoneEngineAttestation.write(protectionMarker,
                            (if (tier == "proot") "required-proot-v1" else "required-v1").toByteArray(Charsets.US_ASCII))
                        protectedProot = true
                        protectedTier = tier
                    }
                } catch (_: Exception) { throw PhoneEngineNative.Failure("boundary_signer_unavailable") }
            }
        }
        if (phoneEngine.status(profile)["boundary"] != true) {
            val reasonCode = phoneEngine.status(profile)["boundaryReason"] as? String
            phoneEngine.stop(profile)
            throw PhoneEngineNative.Failure(reasonCode?.takeIf { it in listOf("boundary_signer_unavailable",
                "boundary_probe_unavailable", "attestation_rejected") } ?: "boundary_proof_failed")
        }
        if (tier == "proot") {
            val pid = pidOf(child) ?: throw PhoneEngineNative.Failure("boundary_probe_unavailable")
            prootMaskedPids.add(pid)
            val root = File(context.filesDir.canonicalFile, "oc.teamEngine.$profile")
            try {
                if (runProotTierProof(root, pid)["complete"] != true)
                    throw PhoneEngineNative.Failure("boundary_proof_failed")
            } catch (error: Exception) {
                prootMaskedPids.remove(pid)
                phoneEngine.stop(profile)
                throw error
            }
        }
        if (services[PHONE_ENGINE]?.process !== child) {
            services[PHONE_ENGINE] = serviceStarted(PHONE_ENGINE, child, port, notice)
            recordRunning()
            try { BuiltinServerService.start(context, currentNotice()) } catch (error: Exception) {
                phoneEngine.stop(profile)
                removeStoppedService(PHONE_ENGINE)
                serviceSetChanged()
                throw PhoneEngineNative.Failure("foreground_unavailable")
            }
            Thread {
                child.waitFor()
                synchronized(this) {
                    if (services[PHONE_ENGINE]?.process === child) {
                        recordServiceExit(PHONE_ENGINE, services.getValue(PHONE_ENGINE))
                        services.remove(PHONE_ENGINE)
                        serviceSetChanged()
                    }
                }
            }.start()
        }
        return phoneEngineStatus(profile)
    }

    @Synchronized
    fun stopPhoneEngine(profile: String): Map<String, Any?> {
        phoneEngine.stop(profile)
        prootMaskedPids.clear()
        if (services[PHONE_ENGINE]?.process?.isAlive != true) removeStoppedService(PHONE_ENGINE)
        serviceSetChanged()
        return phoneEngineStatus(profile)
    }

    @Synchronized
    fun deletePhoneEngine(profile: String) {
        phoneEngine.delete(profile)
        prootMaskedPids.clear()
        if (services[PHONE_ENGINE]?.process?.isAlive != true) removeStoppedService(PHONE_ENGINE)
        serviceSetChanged()
    }

    /**
     * Stand-ins for the /proc files Android keeps from apps (stat, loadavg,
     * uptime, version, vmstat: "Permission denied" on Android 8 and later),
     * bound over the real ones as proot-distro does. Without them `top` and
     * `htop` stop at "Cannot open /proc/stat". The numbers are fixed, so CPU
     * use and load read as idle; only files the app cannot read are
     * replaced.
     */
    private val fakeProcBinds: List<String> get() {
        val dir = File(home, "proc")
        return FAKE_PROC.mapNotNull { (name, content) ->
            val readable = try {
                File("/proc/$name").inputStream().use { it.read() }
                true
            } catch (_: Exception) {
                false
            }
            if (readable) return@mapNotNull null
            val fake = File(dir, name)
            try {
                dir.mkdirs()
                fake.writeText(content())
            } catch (error: Exception) {
                Log.w(TAG, "no stand-in for /proc/$name", error)
                return@mapNotNull null
            }
            "--bind=${fake.absolutePath}:/proc/$name"
        }
    }

    /** What proot itself needs in its environment, on top of the app's own. */
    fun prootEnvironment(): Map<String, String> {
        val tmp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
        return mapOf(
            "PROOT_LOADER" to "$nativeDir/libproot-loader.so",
            "PROOT_TMP_DIR" to tmp.absolutePath,
            "LD_LIBRARY_PATH" to nativeDir,
        )
    }

    // ---- long-running services ---------------------------------------------

    /**
     * A long-running program inside Ubuntu that the app owns: the OpenCode
     * server ([SERVER]) and, when it is on, the AI Team supervisor. Each runs
     * in its own proot, started and stopped here and nowhere else, because a
     * program a script leaves running in the background dies with that
     * script's proot (`--kill-on-exit`).
     */
    private class Service(val process: Process, val port: Int?, val notice: String?, val script: String? = null) {
        /** When this run began, on the clock that keeps counting in deep sleep. */
        val startedAt: Long = SystemClock.elapsedRealtime()
        var exitRecorded = false
        var stopRequested = false
    }

    private val services = LinkedHashMap<String, Service>()

    // Device-wide service health only: never scripts, argv, output or credentials.
    private val diagnosticsPreferences =
        context.getSharedPreferences("builtin_service_diagnostics", Context.MODE_PRIVATE)

    private fun diagnosticRecord(name: String): ServiceDiagnostics = ServiceDiagnostics(
        lastExitCode = if (diagnosticsPreferences.contains("$name.exit"))
            diagnosticsPreferences.getInt("$name.exit", 0) else null,
        lastUptimeMs = if (diagnosticsPreferences.contains("$name.uptime"))
            diagnosticsPreferences.getLong("$name.uptime", 0) else null,
        lastExitAtMs = if (diagnosticsPreferences.contains("$name.exitAt"))
            diagnosticsPreferences.getLong("$name.exitAt", 0) else null,
        lastStopRequested = if (diagnosticsPreferences.contains("$name.stopRequested"))
            diagnosticsPreferences.getBoolean("$name.stopRequested", false) else null,
        restartCount = diagnosticsPreferences.getInt("$name.restarts", 0),
        hasLaunched = diagnosticsPreferences.getBoolean("$name.launched", false),
    )

    private fun saveDiagnosticRecord(name: String, record: ServiceDiagnostics) {
        val names = diagnosticsPreferences.getStringSet("names", emptySet()).orEmpty().toMutableSet()
        names.add(name)
        val edit = diagnosticsPreferences.edit().putStringSet("names", names)
            .putInt("$name.restarts", record.restartCount)
            .putBoolean("$name.launched", record.hasLaunched)
        record.lastExitCode?.let { edit.putInt("$name.exit", it) } ?: edit.remove("$name.exit")
        record.lastUptimeMs?.let { edit.putLong("$name.uptime", it) } ?: edit.remove("$name.uptime")
        record.lastExitAtMs?.let { edit.putLong("$name.exitAt", it) } ?: edit.remove("$name.exitAt")
        record.lastStopRequested?.let { edit.putBoolean("$name.stopRequested", it) }
            ?: edit.remove("$name.stopRequested")
        if (!edit.commit()) Log.w(TAG, "Service diagnostics could not be saved")
    }

    private fun serviceStarted(name: String, process: Process, port: Int?, notice: String?,
        script: String? = null): Service {
        saveDiagnosticRecord(name, diagnosticRecord(name).launched())
        return Service(process, port, notice, script)
    }

    private fun recordServiceExit(name: String, service: Service) {
        if (service.exitRecorded || service.process.isAlive) return
        service.exitRecorded = true
        val code = try { service.process.exitValue() } catch (_: Throwable) { null }
        saveDiagnosticRecord(name, diagnosticRecord(name).exited(
            code, SystemClock.elapsedRealtime() - service.startedAt,
            System.currentTimeMillis(), service.stopRequested,
        ))
    }

    private fun removeStoppedService(name: String) {
        services[name]?.let { recordServiceExit(name, it) }
        services.remove(name)
    }

    @Synchronized
    fun privateAgentDiagnostics(name: String): Map<String, Any?> {
        check(name.startsWith("agent-host."))
        val service = services[name]
        if (service != null && !service.process.isAlive) recordServiceExit(name, service)
        val running = service?.process?.isAlive == true
        return diagnosticRecord(name).snapshot(running,
            if (running) SystemClock.elapsedRealtime() - service!!.startedAt else null)
    }

    private fun serviceDiagnostics(): Map<String, Map<String, Any?>> {
        val names = diagnosticsPreferences.getStringSet("names", emptySet()).orEmpty() + services.keys
        return names.associateWith { name ->
            val service = services[name]
            if (service != null && !service.process.isAlive) recordServiceExit(name, service)
            val running = service?.process?.isAlive == true
            diagnosticRecord(name).snapshot(running,
                if (running) SystemClock.elapsedRealtime() - service!!.startedAt else null)
        }
    }

    // Device-wide private state: the runtime is shared by local profiles.
    // Never infer intent from an old crash report or a missing preference.
    private val recoveryPreferences =
        context.getSharedPreferences("builtin_server_recovery", Context.MODE_PRIVATE)
    private val recoveryLock = Any()
    private var activityResumed = false
    private var recoveryGeneration = try { recoveryPreferences.getLong("runtimeGeneration", 0L) }
        catch (_: Throwable) { 0L }
    private val processBirthGeneration = recoveryGeneration
    private var activityEpoch = 0L
    @Volatile private var idleServerLive = false
    private var wantedRevision = 0L
    private var userStopped = recoveryPreferences.getBoolean("userStopped",
        !recoveryPreferences.getBoolean("wanted", false))
    private var restartWanted = recoveryPreferences.getBoolean("wanted", false) && !userStopped
    private val restartBackoff = RestartBackoff()
    private var supervisionProfile: String? = recoveryPreferences.getString("owner", null)
    private var supervisionEnabled = recoveryPreferences.getBoolean("enabled", false)
    private var supervisionGeneration = 0L
    private var scheduledRecovery = false
    private var recoveryScheduleId = 0L
    private var nativeRecoveryAttempt: RecoveryAttempt? = null
    private var manualStartGeneration: Long? = null
    // Pinned shared_preferences_android 2.4.27 legacy backend; READ ONLY.
    private val flutterPreferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    // Device-runtime intent only: no script, password, authentication or CPU lease is saved.
    private var captureIdleDrain = false
    private val idleState by lazy {
        val initial = try {
            recoveryPreferences.getString("idleState", null)?.let {
                NativeIdleState.Snapshot.read(jsonMap(it))
            } ?: NativeIdleState.Snapshot()
        } catch (_: Exception) { null }
        NativeIdleState(initial) { state ->
            val edit = recoveryPreferences.edit().putString("idleState", JSONObject(state.map()).toString())
            if (captureIdleDrain && state.stopped) capturePendingDrain(edit, state.owner, includeOther = true)
            edit.commit()
        }
    }
    private val idleHeartbeat = NativeIdleHeartbeat()
    private val idlePolicy by lazy {
        idleState.snapshot().let { IdleStopPolicy(it.enabled, it.idleMinutes) }
    }
    private val idleTimer by lazy { NativeIdleTimer(context) { checkIdleStop() } }
    private val idleReturnNotification by lazy {
        NativeIdleReturnNotification(
            { NativeIdleReturnNotificationHost.show(context) },
            { NativeIdleReturnNotificationHost.clear(context) },
        )
    }

    private fun showIdleReturnNotification() = synchronized(recoveryLock) {
        val state = idleState.snapshot()
        idleReturnNotification.update(NativeIdleReturnNotification.State(
            idleState.available, state.owner.takeIf { it == supervisionProfile && supervisionEnabled },
            state.generation, state.stopped,
            restartWanted, userStopped, activityResumed, idleServerLive,
        ))
    }

    private fun idleBlocksRestoration(): Boolean {
        val state = idleState.snapshot()
        return !idleState.available || state.stopped || state.helperStopped
    }

    private fun revokeIdleTransition() {
        idleReturnNotification.clear()
        idleTimer.cancel()
        check(idleState.revoke()) { "idle_policy_unavailable" }
        val state = idleState.snapshot()
        idlePolicy.configure(state.enabled, state.idleMinutes)
        idleHeartbeat.clear()
    }

    @Synchronized fun serverIdleStatus(): Map<String, Any?> {
        val state = idleState.snapshot()
        val available = idleState.available
        return mapOf(
            "installed" to installed, "phase" to phase,
            "serverRunning" to serverRunning, "serverRestartWanted" to serverRestartWanted,
            "serverRecoveryAuthority" to true, "serverRecoveryGeneration" to serverRecoveryGeneration,
            "serverRecoveryScheduled" to serverRecoveryScheduled,
            "serverIdlePolicySupported" to true, "serverIdleEnabled" to state.enabled,
            "serverIdleMinutes" to if (available) state.idleMinutes else null,
            "serverIdleStopped" to state.stopped, "serverIdleHelperStopped" to state.helperStopped,
            "serverIdleGeneration" to if (available) state.generation else null,
        )
    }

    @Synchronized fun setPhoneServerIdlePolicy(enabled: Boolean, minutes: Int): Map<String, Any?> {
        synchronized(recoveryLock) {
            check(activityResumed && idleState.configure(enabled, minutes)) { "idle_policy_unavailable" }
            idlePolicy.configure(enabled, minutes)
            idleTimer.cancel()
        }
        return serverIdleStatus()
    }

    @Synchronized fun observePhoneAgentWork(profile: String, busy: Boolean?) {
        if (!Regex("[A-Za-z0-9_-]{1,80}").matches(profile)) return
        // A late observation from a previous readable alias cannot replace the
        // current runtime owner's evidence or cancel its confirmed idle interval.
        if (synchronized(recoveryLock) { supervisionProfile != profile }) return
        idleHeartbeat.observe(profile, busy, SystemClock.elapsedRealtime())
        checkIdleStop()
    }

    private fun helperOwner(profile: String): String? {
        val value = flutterPreferences.getString("flutter.oc.phoneAgentOwner.$profile", null) ?: profile
        return value.takeIf { Regex("[A-Za-z0-9_-]{1,80}").matches(it) }
    }

    // Timer/receiver is stop-only. Every wake rechecks volatile busy proof and durable authority.
    @Synchronized fun checkIdleStop() {
        if (Thread.currentThread().isInterrupted) return
        val now = SystemClock.elapsedRealtime()
        if (synchronized(recoveryLock) { activityResumed || !restartWanted || idleBlocksRestoration() } || !serverRunning) {
            idlePolicy.observe(now, true, null)
            idleTimer.cancel()
            return
        }
        val owner = synchronized(recoveryLock) { supervisionProfile }
        val busy = if (owner != null && workLeases.logicalWorkBusy() == false)
            idleHeartbeat.busy(owner, now) else null
        val foreground = synchronized(recoveryLock) { activityResumed }
        val observation = idlePolicy.observe(now, foreground, busy)
        if (!observation.stopDue) {
            idleTimer.schedule(observation.deadlineMillis)
            return
        }
        try {
            val profile = owner ?: error("idle_resume_unavailable")
            val recipe = currentIdleRecipe(profile)
            val helper = helperOwner(profile) ?: error("idle_resume_unavailable")
            val helperName = "agent-host.$helper"
            val allowed = setOf(SERVER, helperName)
            check(services.filterValues { it.process.isAlive }.keys.all { it in allowed })
            val roots = services.filterValues { it.process.isAlive }.values.map { it.process }
            check(processes.filter { it.isAlive }.all { process -> roots.any { it === process } })
            recordRunning()
            val receipt = ownership(profile)
            NativeRuntimeOwnership.plan(receipt, bootIdentity(), sameUidInventory(), registeredRuntimePids(),
                requireCompleteInventory = true, nonceMatches = ::nonceMatches)
            synchronized(recoveryLock) {
                check(!Thread.currentThread().isInterrupted && !activityResumed && serverRunning &&
                    nativeAdmitted(profile, supervisionGeneration) &&
                    workLeases.logicalWorkBusy() == false && idleHeartbeat.busy(profile, now) == false)
                check(recipe.compatible(packageIdentity(), rootfsIdentity()))
                val helperWasLive = services[helperName]?.process?.isAlive == true
                captureIdleDrain = true
                try {
                    check(idleState.beginStop(profile, if (helperWasLive) helper else null, helperWasLive) != null)
                } finally { captureIdleDrain = false }
                wantedRevision++
                supervisionGeneration++
                recoveryScheduleId++
                scheduledRecovery = false
                recoveryGeneration++
                recoveryAttempt = null
                confirmedRecoveryAttempt = null
                nativeRecoveryAttempt = null
                manualStartGeneration = null
                restoreUnavailable("idleStopped")
            }
            idleTimer.cancel()
            drainPendingOwnedServer()
            // Already-drained exact tracked objects only; never clear wanted or recipe here.
            removeService(SERVER)
            if (services[helperName] != null) removeService(helperName)
            serviceSetChanged()
            // A successful exact drain precedes this plain return shortcut.
            showIdleReturnNotification()
        } catch (_: Exception) {
            // A failed proof leaves runtime intent intact. No uncertain PID is signalled.
            idleTimer.cancel()
        }
    }

    private fun currentIdleRecipe(profile: String): NativeServerRecipe {
        check(supervisionProfile == profile && migrationMarkerValid(profile) && policyPermits(profile))
        val recipe = NativeServerRecipe.read(jsonMap(recoveryPreferences.getString(recipeKey(profile), null)
            ?: error("idle_resume_unavailable")))
        check(recipe.profileId == profile && recipe.compatible(packageIdentity(), rootfsIdentity()) &&
            readBudget(profile).attempts < 3)
        return recipe
    }

    private fun idleResumeCurrent(profile: String, generation: Long, epoch: Long): Boolean =
        activityResumed && activityEpoch == epoch && restartWanted && !userStopped &&
            supervisionEnabled && supervisionProfile == profile && idleState.resumeAllowed(profile, generation) &&
            migrationMarkerValid(profile) && policyPermits(profile)

    @Synchronized fun resumeIdleStoppedPhoneServer(profile: String, generation: Long): Map<String, Any?> {
        val workGeneration = workLeases.foregroundGeneration()
        val epoch = synchronized(recoveryLock) {
            check(idleResumeCurrent(profile, generation, activityEpoch)) { "idle_resume_stale" }
            activityEpoch
        }
        val recipe = currentIdleRecipe(profile)
        val state = idleState.snapshot()
        if (state.stopped) {
            drainPendingOwnedServer()
            check(!serverRunning) { "idle_resume_stale" }
            BuiltinServerService.start(context, currentNotice())
            val deadline = SystemClock.elapsedRealtime() + 3000L
            while (!BuiltinServerService.isForegroundRunning && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(25L)
            }
            synchronized(recoveryLock) {
                check(idleResumeCurrent(profile, generation, epoch) && BuiltinServerService.isForegroundRunning)
            }
            serverRecipe = recipe
            val launch = ServiceLaunch(nativeOwned = true, idleOwner = profile, idleGeneration = generation,
                idleActivityEpoch = epoch)
            val process = launchService(SERVER, recipe.restorationScript(), SERVER_DEFAULT_PORT, null, launch)
            try {
                synchronized(recoveryLock) {
                    check(idleResumeCurrent(profile, generation, epoch) && process.isAlive)
                    check(idleState.markServerResumed(profile, generation))
                    manualStartGeneration = null
                    workLeases.authorizeForegroundWork(workGeneration)
                }
            } catch (error: Exception) {
                if (services[SERVER]?.process === process) {
                    synchronized(recoveryLock) {
                        if (!recoveryPreferences.contains("drainOwner")) {
                            val edit = recoveryPreferences.edit()
                            capturePendingDrain(edit, profile)
                            check(edit.commit())
                        }
                    }
                    removeService(SERVER)
                    drainPendingOwnedServer()
                }
                throw error
            }
        }
        return serverIdleStatus()
    }

    @Synchronized fun completePhoneServerIdleResume(profile: String, generation: Long): Map<String, Any?> {
        synchronized(recoveryLock) {
            val state = idleState.snapshot()
            check(activityResumed && restartWanted && !userStopped && state.owner == profile &&
                state.generation == generation && serverRunning && supervisionProfile == profile &&
                supervisionEnabled && migrationMarkerValid(profile) && policyPermits(profile))
            val helperRunning = state.helper?.let { services["agent-host.$it"]?.process?.isAlive == true } ?: false
            check(idleState.complete(profile, generation, helperRunning)) { "idle_resume_stale" }
        }
        return serverIdleStatus()
    }

    internal data class AgentHostTicket(val profile: String, val owner: String?, val revision: Long,
        val epoch: Long, val idleGeneration: Long?)
    private val agentHostTicket = ThreadLocal<AgentHostTicket?>()
    private val agentHostAdmissions = java.util.IdentityHashMap<Process, AgentHostTicket>()

    internal fun captureAgentHostStart(profile: String, idleResume: Boolean, generation: Long?): AgentHostTicket? =
        synchronized(recoveryLock) {
            if (!activityResumed || !Regex("[A-Za-z0-9_-]{1,80}").matches(profile)) null else {
                val owner = supervisionProfile
                val ticket = AgentHostTicket(profile, owner, wantedRevision, activityEpoch,
                    if (idleResume) generation else null)
                if (idleResume && (generation == null || generation <= 0)) null
                else ticket.takeIf { agentHostAdmissionCurrent(it) }
            }
        }

    private fun agentHostAdmissionCurrent(ticket: AgentHostTicket): Boolean {
        val state = idleState.snapshot()
        val authorityOwner = ticket.owner.takeIf { ticket.idleGeneration != null }
        val helper = if (ticket.idleGeneration == null && state.generation > 0 && ticket.owner != null)
            helperOwner(ticket.owner) else null
        return NativeAgentHostAdmission.admitted(
            NativeAgentHostAdmission.Ticket(ticket.profile, ticket.owner, ticket.revision,
                ticket.epoch, ticket.idleGeneration),
            NativeAgentHostAdmission.Snapshot(idleState.available, activityResumed, activityEpoch,
                wantedRevision, supervisionProfile, restartWanted, userStopped, supervisionEnabled,
                idleServerLive, authorityOwner?.let(::migrationMarkerValid) == true,
                authorityOwner?.let(::policyPermits) == true, helper, state))
    }

    internal fun <T> withAgentHostStart(ticket: AgentHostTicket, work: () -> T): T {
        check(agentHostTicket.get() == null)
        agentHostTicket.set(ticket)
        try {
            synchronized(recoveryLock) { check(agentHostAdmissionCurrent(ticket)) { "idle_resume_stale" } }
            return work()
        } finally { agentHostTicket.remove() }
    }

    private data class RecoveryAttempt(val process: Process, val generation: Long)
    private var recoveryAttempt: RecoveryAttempt? = null
    private var confirmedRecoveryAttempt: RecoveryAttempt? = null

    val serverRestartWanted: Boolean get() = synchronized(recoveryLock) { restartWanted }
    val serverRecoveryGeneration: Long get() = synchronized(recoveryLock) {
        nativeRecoveryAttempt?.takeIf { it.process.isAlive }?.generation ?: recoveryGeneration
    }
    val serverRecoveryScheduled: Boolean get() = synchronized(recoveryLock) { scheduledRecovery }

    private fun jsonMap(raw: String): Map<String, Any?> {
        fun objectMap(value: JSONObject): Map<String, Any?> = value.keys().asSequence().associateWith { key ->
            when (val item = value.get(key)) {
                JSONObject.NULL -> null
                is JSONObject -> objectMap(item)
                else -> item
            }
        }
        return objectMap(JSONObject(raw))
    }

    // Cold restoration metadata is isolated from scripts, account data and process output.
    @Volatile var restorePhase: String = if (recoveryPreferences.contains("restoreReason")) "unavailable" else "idle"
        private set
    @Volatile var restoreReason: String? = try { recoveryPreferences.getString("restoreReason", null) }
        catch (_: Throwable) { "storageUnavailable" }
        private set
    private var serverRecipe: NativeServerRecipe? = null
    private var coldRestoreStarted = false
    // These capabilities never leave this Android process or survive a package replacement.
    private var serverEventTicket: NativeServerRestoreTicket? = null
    private var serverEventWorkerStarted = false
    @Volatile internal var runtimeQaGateCheckpoint: ((String, NativeRuntimeReceipt) -> Unit)? = null
    private fun gateCheckpointForQa(stage: String, receipt: NativeRuntimeReceipt) {
        if (BuildConfig.BUILTIN_RUNTIME_QA) runtimeQaGateCheckpoint?.invoke(stage, receipt)
    }
    private fun recipeKey(profile: String) = "oc.builtinRuntimeRecipe.$profile"
    private fun ownershipKey(profile: String) = "oc.builtinRuntimeOwnership.$profile"
    private fun bootIdentity() = File("/proc/sys/kernel/random/boot_id").readText().trim()
    @Suppress("DEPRECATION")
    private fun packageIdentity(): Long = context.packageManager.getPackageInfo(context.packageName, 0).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
    }
    private fun rootfsIdentity() = ready.readText().trim()
    private fun restoreUnavailable(reason: String) { restorePhase = "unavailable"; restoreReason = reason }
    private fun persistRestartBackoff() {
        val state = restartBackoff.snapshot()
        check(recoveryPreferences.edit().putLong("backoffNext", state.first).putLong("backoffAt", state.second)
            .putString("backoffBoot", bootIdentity()).commit()) { "storageUnavailable" }
    }
    private fun restoredDelay(): Long {
        if (recoveryPreferences.getString("backoffBoot", null) != bootIdentity()) return MIN_RESTART_DELAY_MS
        restartBackoff.restore(recoveryPreferences.getLong("backoffNext", MIN_RESTART_DELAY_MS),
            recoveryPreferences.getLong("backoffAt", 0L))
        return restartBackoff.remainingMs(SystemClock.elapsedRealtime())
            .coerceIn(MIN_RESTART_DELAY_MS, MAX_RESTART_DELAY_MS)
    }

    private fun kernelIdentity(pid: Int): RuntimeProcessIdentity? = try {
        RuntimeProcessIdentity.stat(File("/proc/$pid/stat").readText())
    } catch (_: java.io.FileNotFoundException) { null }
    private fun processUid(pid: Int): Int? {
        val entry = File("/proc/$pid")
        return try {
            val uid = Os.stat(entry.absolutePath).st_uid
            if (uid != 0) uid else File(entry, "status").readLines()
                .first { it.startsWith("Uid:") }.substringAfter(':').trim().split(Regex("\\s+"))[0].toInt()
        } catch (error: ErrnoException) { if (error.errno == OsConstants.ENOENT) null else throw error }
        catch (error: java.io.FileNotFoundException) { if (!entry.exists()) null else throw error }
    }
    private fun sameUidInventory(): List<RuntimeProcessIdentity> {
        val entries = File("/proc").listFiles() ?: error("ownershipUnknown")
        return entries.mapNotNull { entry ->
            val pid = entry.name.toIntOrNull() ?: return@mapNotNull null
            if (processUid(pid) == AndroidProcess.myUid()) kernelIdentity(pid) else null
        }
    }
    /** Event admission cannot silently omit a live same-UID process whose stat was unreadable. */
    private fun serverEventInventory(): List<RuntimeProcessIdentity> {
        val entries = File("/proc").listFiles() ?: error("ownershipUnknown")
        return entries.mapNotNull { entry ->
            val pid = entry.name.toIntOrNull() ?: return@mapNotNull null
            if (processUid(pid) != AndroidProcess.myUid()) return@mapNotNull null
            val identity = kernelIdentity(pid)
            check(identity != null || processUid(pid) == null) { "ownershipUnknown" }
            if (identity != null) check(processUid(pid) == AndroidProcess.myUid() &&
                identity.sameProcess(kernelIdentity(pid))) { "ownershipUnknown" }
            identity
        }
    }
    private fun registeredRuntimePids(): Set<Int> = registeredAppProcessIds(
        context.getSystemService(ActivityManager::class.java)?.runningAppProcesses,
        context.packageName, AndroidProcess.myUid()).toSet() + AndroidProcess.myPid()
    private fun knownOtherRuntime(
        exclude: Process? = null, includeServer: Boolean = false,
    ): List<RuntimeProcessIdentity> {
        val roots = processes.filter { it.isAlive && it !== exclude && (includeServer || it !== services[SERVER]?.process) }.map {
            pidOf(it) ?: error("ownershipUnknown")
        } + services.filterKeys { includeServer || it != SERVER }.values
            .filter { it.process.isAlive && it.process !== exclude }
            .map {
            pidOf(it.process) ?: error("ownershipUnknown")
        } + LocalTerminal.get(context).list().filter { it.running }.map { it.pid }
        val ids = roots.flatMap { listOf(it) + descendants(it) }.distinct()
        check(ids.size <= 128) { "ownershipUnknown" }
        return ids.map { kernelIdentity(it) ?: error("ownershipUnknown") }
    }
    private fun nonceMatches(pid: Int, nonce: String): Boolean = try {
        // Inspect only the ownership key; never expose another environment value.
        val bytes = File("/proc/$pid/environ").inputStream().use { input ->
            val limit = ByteArray(MAX_ENV_BYTES + 1); val size = input.read(limit)
            if (size < 0 || size > MAX_ENV_BYTES) return false
            String(limit, 0, size, Charsets.UTF_8)
        }
        bytes.split('\u0000').any { it == "OC_RUNTIME_OWNER=$nonce" }
    } catch (_: Throwable) { false }
    private fun writeOwnership(profile: String, receipt: NativeRuntimeReceipt) {
        check(recoveryPreferences.edit()
            .putString(ownershipKey(profile), JSONObject(receipt.map()).toString()).commit()) {
            "storageUnavailable"
        }
    }
    private fun ownership(profile: String, key: String = ownershipKey(profile)): NativeRuntimeReceipt {
        val raw = recoveryPreferences.getString(key, null) ?: error("ownershipUnknown")
        val obj = JSONObject(raw)
        val value = jsonMap(raw).toMutableMap()
        value["other"] = (obj.getJSONArray("other")).let { array ->
            (0 until array.length()).map { jsonMap(array.getJSONObject(it).toString()) }
        }
        return NativeRuntimeReceipt.read(value)
    }
    private fun drainKey(profile: String) = "oc.builtinRuntimeDrain.$profile"
    private fun capturePendingDrain(
        edit: android.content.SharedPreferences.Editor, profile: String?, includeOther: Boolean = false,
    ) {
        if (profile != null) {
            val raw = recoveryPreferences.getString(ownershipKey(profile), null)
            if (raw != null) edit.putString(drainKey(profile), raw).putString("drainOwner", profile)
                .putBoolean("drainAll", includeOther)
        }
    }
    private fun drainPendingOwnedServer() {
        val profile = recoveryPreferences.getString("drainOwner", null) ?: return
        val receipt = ownership(profile, drainKey(profile))
        if (receipt.boot != bootIdentity()) {
            // Kernel process identity from another boot cannot be signalled.
            check(recoveryPreferences.edit().remove("drainOwner").remove("drainAll").remove(drainKey(profile)).commit())
            return
        }
        val includeOther = recoveryPreferences.getBoolean("drainAll", false)
        fun targets(): List<RuntimeProcessIdentity> {
            val current = sameUidInventory()
            val plan = NativeRuntimeOwnership.plan(receipt, bootIdentity(), current,
                registeredRuntimePids(), requireCompleteInventory = false, nonceMatches = ::nonceMatches)
            return (plan.server + if (includeOther) current.filter { it.pid in plan.other } else emptyList())
                .distinctBy { it.pid }
        }
        val deadline = SystemClock.elapsedRealtime() + OWNED_DRAIN_TIMEOUT_MS
        for (identity in targets()) if (identity.sameProcess(kernelIdentity(identity.pid))) {
            Os.kill(identity.pid, OsConstants.SIGTERM)
        }
        while (targets().isNotEmpty() &&
            SystemClock.elapsedRealtime() < deadline - OWNED_DRAIN_KILL_WINDOW_MS) Thread.sleep(PROCESS_POLL_MS)
        for (identity in targets()) if (identity.sameProcess(kernelIdentity(identity.pid))) {
            Os.kill(identity.pid, OsConstants.SIGKILL)
        }
        while (targets().isNotEmpty() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(PROCESS_POLL_MS)
        check(targets().isEmpty()) { "ownershipUnknown" }
        try {
            // A normal app start may create new tracked helpers after the old Stop
            // snapshot. They are peers of this live drain, never cold writer exemptions.
            // Only the original receipt above selects processes for signals.
            val current = sameUidInventory()
            val peers = knownOtherRuntime()
            val liveReceipt = NativeInstallerOwnership.withCurrentRuntimePeers(receipt, emptyList(), peers, current)
            NativeRuntimeOwnership.plan(liveReceipt, bootIdentity(), current, registeredRuntimePids(),
                nonceMatches = ::nonceMatches)
        } catch (error: Throwable) { restoreUnavailable("ownershipUnknown"); throw error }
        check(recoveryPreferences.edit().remove("drainOwner").remove("drainAll")
            .remove(drainKey(profile)).commit()) { "storageUnavailable" }
    }

    private fun nextRuntimeGeneration(): Long {
        val previous = recoveryPreferences.getLong("runtimeGeneration", 0)
        val next = maxOf(previous, recoveryGeneration) + 1
        check(next > 0 && recoveryPreferences.edit()
            .putLong("runtimeGeneration", next).commit()) { "storageUnavailable" }
        recoveryGeneration = next
        return next
    }
    private fun componentRecoveryPending(): Boolean {
        val targets = listOf("opt/opencode", "opt/opencode2", "opt/oc-claude/claude",
            "home/oc/.local/share/oc-paseo/0.9.2-82d16f9c432d", "home/oc/.local/share/oc-agents/claude/2.1.283")
        val paths = targets.map { "$it.oc-pending" } + listOf(
            "usr/local/bin/opencode.oc-pending", "usr/local/bin/opencode2.oc-pending",
            "usr/local/bin/claude.oc-pending",
            "home/oc/.local/bin/paseo.oc-pending", "home/oc/.local/bin/claude.oc-pending",
            "home/oc/.local/share/oc-agents/.lock-claude")
        return paths.any { path ->
            try { Os.lstat(File(rootfs, path).absolutePath); true }
            catch (e: ErrnoException) { e.errno != OsConstants.ENOENT }
        }
    }

    private data class ServiceLaunch(
        val expectedGeneration: Long? = null,
        val nativeOwned: Boolean = false,
        val nativeSupervisionGeneration: Long? = null,
        val nativeAttemptGeneration: Long? = null,
        val nativeScheduleId: Long? = null,
        val idleOwner: String? = null,
        val idleGeneration: Long? = null,
        val idleActivityEpoch: Long? = null,
        val systemEvent: NativeServerRestoreTicket? = null,
    )

    /** Workload cannot pass stdin gate until kernel/session identity has been durably committed. */
    private class GateUnavailable(val legacySafe: Boolean) :
        IllegalStateException("The phone server could not restart.")
    private fun startGatedServer(
        script: String, log: File, recipe: NativeServerRecipe, launch: ServiceLaunch = ServiceLaunch(),
    ): Process {
        val attemptGeneration = launch.nativeAttemptGeneration
        val supervisorGeneration = launch.nativeSupervisionGeneration
        val scheduleId = launch.nativeScheduleId
        val nonce = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val generation = attemptGeneration ?: nextRuntimeGeneration()
        check(generation == recoveryGeneration) { "storageUnavailable" }
        val prepared = NativeRuntimeReceipt(bootIdentity(), nonce, generation, null, null, knownOtherRuntime())
        writeOwnership(recipe.profileId, prepared)
        // Both values are generated fixed ASCII. The original launch input is never persisted.
        val gate = "printf 'OC-GATE-1 %s %s\\n' '$nonce' \"\$\$\"\n" +
            "IFS= read -r permit || exit 78\n[ \"\$permit\" = '$nonce' ] || exit 78\n" +
            "exec /bin/sh -c " + "'" + script.replace("'", "'\"'\"'") + "'"
        val process = ProcessBuilder(prootCommand(listOf("/usr/bin/env", "OC_RUNTIME_OWNER=$nonce",
            "/usr/bin/setsid", "/bin/sh", "-c", gate))).redirectErrorStream(true).apply {
            environment().clear(); environment().putAll(prootEnvironment())
            environment()["OC_RUNTIME_OWNER"] = nonce
        }.start()
        processes.add(process); processConfinement[process] = prootIsConfined
        val gateState = NativeRuntimeGate()
        try {
            val launchedIdentity = readServerIdentity(process, nonce, prepared)
            // Test-only checkpoint is outside admission lock: Stop can still revoke a blocked gate.
            gateCheckpointForQa("prepared", launchedIdentity)
            synchronized(recoveryLock) {
                check(gateAdmissionCurrent(recipe.profileId, generation, supervisorGeneration, scheduleId, launch)) {
                    "ownershipUnknown"
                }
                commitServerGate(recipe, launchedIdentity, gateState, process, nonce)
            }
            log.parentFile?.mkdirs()
            Thread({
                try { FileOutputStream(log, true).use { process.inputStream.copyTo(it) } }
                catch (_: Throwable) { }
            },
                "phone-runtime-output").start()
            gateCheckpointForQa("released", launchedIdentity)
            synchronized(recoveryLock) {
                if (gateGenerationCurrent(generation, supervisorGeneration, scheduleId)) {
                    restorePhase = "idle"; restoreReason = null
                }
            }
            return process
        } catch (_: Throwable) {
            try { process.outputStream.close() } catch (_: Throwable) { }
            try { stopTree(process) } catch (_: Throwable) { }
            synchronized(recoveryLock) {
                if (gateGenerationCurrent(generation, supervisorGeneration, scheduleId)) {
                    restoreUnavailable("ownershipUnknown")
                }
            }
            val rootPid = pidOf(process)
            val drained = !process.isAlive && (rootPid == null || descendants(rootPid).isEmpty())
            throw GateUnavailable(
                NativeRuntimeOwnership.manualFallbackAllowed(gateState.released, drained, serverRestartWanted))
        }
    }

    private fun commitServerGate(
        recipe: NativeServerRecipe, identity: NativeRuntimeReceipt, gateState: NativeRuntimeGate,
        process: Process, nonce: String,
    ) {
        writeOwnership(recipe.profileId, identity)
        // Persist only after actual setsid/session capability and current ownership are proved.
        check(recoveryPreferences.edit()
            .putString(recipeKey(recipe.profileId), JSONObject(recipe.map()).toString())
            .putString("restoreOwner", recipe.profileId)
                .remove("restoreReason").commit()) { "storageUnavailable" }
        gateState.identityCommitted()
        gateState.release {
            process.outputStream.write("$nonce\n".toByteArray(Charsets.US_ASCII)); process.outputStream.flush()
            process.outputStream.close()
        }
    }

    private fun gateGenerationCurrent(generation: Long, supervisorGeneration: Long?, scheduleId: Long?): Boolean =
        generation == recoveryGeneration && restartWanted && !userStopped &&
            (supervisorGeneration == null || supervisorGeneration == supervisionGeneration) &&
            (scheduleId == null || scheduleId == recoveryScheduleId)

    private fun gateAdmissionCurrent(
        profile: String, generation: Long, supervisorGeneration: Long?, scheduleId: Long?,
        launch: ServiceLaunch,
    ): Boolean = restartWanted && !userStopped && supervisionProfile == profile &&
        generation == recoveryGeneration &&
        (supervisorGeneration == null || nativeAdmitted(profile, supervisorGeneration)) &&
        (scheduleId == null || scheduleId == recoveryScheduleId) &&
        (launch.systemEvent == null || serverEventAuthorityCurrent(launch.systemEvent)) &&
        (if (launch.idleGeneration != null) idleResumeCurrent(profile, launch.idleGeneration,
            launch.idleActivityEpoch ?: -1L) else !idleBlocksRestoration())

    private fun readServerIdentity(
        process: Process, nonce: String, prepared: NativeRuntimeReceipt,
    ): NativeRuntimeReceipt {
        val read = java.util.concurrent.FutureTask<String> {
            val bytes = java.io.ByteArrayOutputStream()
            while (bytes.size() < 128) {
                val b = process.inputStream.read()
                check(b >= 0) { "ownershipUnknown" }
                if (b == 10) return@FutureTask bytes.toString("US-ASCII")
                bytes.write(b)
            }
            error("ownershipUnknown")
        }
        Thread(read, "phone-runtime-gate").start()
        val header = read.get(5, TimeUnit.SECONDS).split(' ')
        check(header.size == 3 && header[0] == "OC-GATE-1" && header[1] == nonce) { "ownershipUnknown" }
        val rootPid = pidOf(process) ?: error("ownershipUnknown")
        val root = kernelIdentity(rootPid) ?: error("ownershipUnknown")
        val leader = kernelIdentity(header[2].toInt()) ?: error("ownershipUnknown")
        check(leader.pid in descendants(rootPid) && processUid(leader.pid) == AndroidProcess.myUid() &&
            leader.session == leader.pid && leader.group == leader.pid && nonceMatches(leader.pid, nonce)) {
            "ownershipUnknown"
        }
        return prepared.copy(root = root, leader = leader)
    }

    private fun disarmRestoration(reason: String) {
        serverRecipe = null
        val owner = recoveryPreferences.getString("restoreOwner", null)
        val edit = recoveryPreferences.edit().remove("restoreOwner")
        if (owner != null) edit.remove(recipeKey(owner))
        check(edit.commit()) { "The phone server could not restart." }
        restoreUnavailable(reason)
    }
    private fun startManualServer(script: String, log: File): Process {
        val recipe = serverRecipe
        return if (recipe == null) start(script, log) else startManualWithRecipe(script, log, recipe)
    }

    private fun startManualWithRecipe(script: String, log: File, recipe: NativeServerRecipe): Process {
        // Lack of a readable boot/kernel capability disables restoration, not authored Start.
        val capable = try { bootIdentity(); knownOtherRuntime(); true }
        catch (_: Throwable) {
            disarmRestoration("ownershipUnknown")
            false
        }
        return if (!capable) start(script, log) else try { startGatedServer(script, log, recipe) }
        catch (error: GateUnavailable) {
            // A capability failure before release never executes the payload. Preserve authored Start.
            if (!error.legacySafe || !serverRestartWanted) throw error
            disarmRestoration("ownershipUnknown")
            start(script, log)
        }
    }

    val serverRestorationArmed: Boolean get() = try {
        val profile = recoveryPreferences.getString("restoreOwner", null)
        if (profile == null) false else {
            val recipe = NativeServerRecipe.read(jsonMap(recoveryPreferences.getString(recipeKey(profile), null)
                ?: error("storageUnavailable")))
            val budget = readBudget(profile)
            val receipt = ownership(profile)
            NativeRuntimeOwnership.stickyAllowed(
                recipe.profileId == profile && recipe.compatible(packageIdentity(), rootfsIdentity()),
                !receipt.prepared, nativeAdmitted(profile, supervisionGeneration), budget.attempts, serverRunning)
        }
    } catch (_: Throwable) { false }

    internal fun rejectServerRestoration() {
        var owesDrain = false
        synchronized(recoveryLock) {
            val profile = recoveryPreferences.getString("restoreOwner", null)
            val reason = restorationRejectionReason(profile)
            restoreUnavailable(reason)
            // Denial never reserves. Persist owed cleanup before Android removes foreground.
            // Only a previous-process receipt may be captured; a newer manual gate is untouched.
            if (profile != null) try {
                val receipt = ownership(profile)
                if (NativeRuntimeOwnership.denialMayDrain(receipt.generation, processBirthGeneration, serverRunning)) {
                    val edit = recoveryPreferences.edit()
                    capturePendingDrain(edit, profile)
                    check(edit.commit()) { "storageUnavailable" }
                    owesDrain = true
                }
            } catch (_: Throwable) { restoreUnavailable("storageUnavailable") }
            owesDrain = owesDrain || recoveryPreferences.contains("drainOwner")
        }
        if (owesDrain) Thread({
            try { synchronized(this) { drainPendingOwnedServer() } }
            catch (_: Throwable) { restoreUnavailable("ownershipUnknown") }
        }, "phone-runtime-revoked-drain").start()
    }

    private fun restorationRejectionReason(profile: String?): String = try {
        when {
            !restartWanted || userStopped -> recoveryPreferences.getString("restoreReason", null)
                .takeIf { it == "systemTimeout" } ?: "stopped"
            profile == null -> "ownershipUnknown"
            !nativeAdmitted(profile, supervisionGeneration) -> "policyDisabled"
            readBudget(profile).attempts >= 3 -> "budgetExhausted"
            else -> "storageUnavailable"
        }
    } catch (_: Throwable) { "storageUnavailable" }

    private class ColdRestoreRevoked : IllegalStateException()

    /** Bounded read-only admission plus an unspent, process-local dispatch reservation. */
    internal fun prepareServerEventRestore(
        event: NativeServerRestoreEvent,
    ): NativeServerRestoreTicket? = synchronized(recoveryLock) {
        // Receiver admission never waits for the Linux monitor held by a drain or installer.
        // This volatile liveness hint can deny conservatively; the worker later checks the service map.
        if (idleServerLive || coldRestoreStarted || scheduledRecovery || serverEventTicket != null) return null
        try {
            val profile = recoveryPreferences.getString("restoreOwner", null)
                ?: return null
            val original = NativeServerRecipe.read(jsonMap(recoveryPreferences.getString(recipeKey(profile), null)
                ?: error("storageUnavailable")))
            val budget = readBudget(profile)
            val idle = idleState.snapshot()
            if (!NativeServerEventPolicy.admitted(nativeAdmitted(profile, supervisionGeneration),
                    idleState.available, idle, supervisionProfile, profile, original.profileId, budget.attempts) ||
                !savedServerEventAuthority(profile)) {
                restoreUnavailable(if (budget.attempts >= 3) "budgetExhausted" else "policyDisabled")
                return null
            }
            check(!serverEventComponentPending()) { "componentRecoveryRequired" }
            val recipe = NativeServerEventPolicy.recipeForEvent(event, original, packageIdentity(), rootfsIdentity())
                ?: error("storageUnavailable")
            val receipt = ownership(profile)
            check(!receipt.prepared) { "ownershipUnknown" }
            check(serverEventDrainCompatible(profile, receipt)) { "ownershipUnknown" }
            val boot = bootIdentity()
            check(event != NativeServerRestoreEvent.BOOT_COMPLETED || receipt.boot != boot) { "ownershipUnknown" }
            val ticket = NativeServerRestoreTicket(event, original, recipe, receipt,
                wantedRevision, supervisionGeneration, ++recoveryScheduleId, boot, idle.counter, budget)
            serverEventTicket = ticket
            serverEventWorkerStarted = false
            scheduledRecovery = true
            restorePhase = "waiting"; restoreReason = null
            ticket
        } catch (error: Throwable) {
            restoreUnavailable(when (error.message) {
                "ownershipUnknown" -> "ownershipUnknown"
                "componentRecoveryRequired" -> "componentRecoveryRequired"
                else -> "storageUnavailable"
            })
            null
        }
    }

    private fun savedServerEventAuthority(profile: String): Boolean =
        recoveryPreferences.getBoolean("wanted", false) &&
            !recoveryPreferences.getBoolean("userStopped", true) &&
            recoveryPreferences.getBoolean("enabled", false) &&
            recoveryPreferences.getString("owner", null) == profile &&
            recoveryPreferences.getString("restoreOwner", null) == profile

    private fun serverEventDrainCompatible(profile: String, expected: NativeRuntimeReceipt): Boolean {
        val owner = recoveryPreferences.getString("drainOwner", null)
        return NativeServerEventPolicy.pendingDrainCompatible(owner, profile,
            recoveryPreferences.getBoolean("drainAll", false),
            owner?.let { ownership(it, drainKey(it)) }, expected)
    }

    private fun serverEventComponentPending(): Boolean = componentRecoveryPending() ||
        componentRecovery.hasPending() || installerTicket() != null || installerProcess != null

    // Call under recoveryLock. The budget is checked before reservation, not after spending attempt three.
    private fun serverEventAuthorityCurrent(ticket: NativeServerRestoreTicket): Boolean = try {
        val idle = idleState.snapshot()
        NativeServerEventPolicy.ticketCurrent(ticket, serverEventTicket, wantedRevision,
            supervisionGeneration, recoveryScheduleId, idle.counter, bootIdentity(), packageIdentity(), rootfsIdentity()) &&
            NativeServerEventPolicy.admitted(nativeAdmitted(ticket.profile, ticket.generation),
                idleState.available, idle, supervisionProfile,
                recoveryPreferences.getString("restoreOwner", null), ticket.profile, 0) &&
            (!serverEventWorkerStarted || BuiltinServerService.isForegroundRunning) &&
            savedServerEventAuthority(ticket.profile) &&
            serverEventDrainCompatible(ticket.profile, ticket.previousReceipt) && !serverEventComponentPending()
    } catch (_: Throwable) { false }

    /** Called after the service has shown its notification; never waits for a child or gate. */
    internal fun restoreServerAfterSystemEvent(ticket: NativeServerRestoreTicket): Boolean {
        val worker = synchronized(recoveryLock) {
            if (serverEventWorkerStarted || coldRestoreStarted || !BuiltinServerService.isForegroundRunning ||
                !serverEventAuthorityCurrent(ticket)) null
            else {
                serverEventWorkerStarted = true
                coldRestoreStarted = true
                NativeRuntimeOwnership.ColdWorker(ticket.generation, ticket.scheduleId)
            }
        }
        if (worker == null) {
            cancelServerEventRestore(ticket)
            return false
        }
        return try {
            Thread({ runColdRestore(worker, ticket.profile, ticket) }, "phone-runtime-system-restore").start()
            true
        } catch (_: Throwable) {
            synchronized(recoveryLock) {
                if (serverEventTicket === ticket) {
                    serverEventWorkerStarted = false
                    coldRestoreStarted = false
                }
            }
            cancelServerEventRestore(ticket)
            false
        }
    }

    /** Dispatch denial releases only this unspent reservation, never wanted intent or another worker. */
    internal fun cancelServerEventRestore(ticket: NativeServerRestoreTicket) {
        synchronized(recoveryLock) {
            if (serverEventTicket !== ticket || serverEventWorkerStarted) return
            serverEventTicket = null
            if (ticket.generation == supervisionGeneration && ticket.scheduleId == recoveryScheduleId) {
                scheduledRecovery = false
                recoveryScheduleId++
                if (restartWanted && !userStopped) restoreUnavailable("storageUnavailable")
            }
        }
    }

    /** Lifecycle callbacks do not wait for tree drain; live helpers and held work retain their service. */
    internal fun settleServerEventService() {
        try {
            Thread({
                try { synchronized(this) { serviceSetChanged() } } catch (_: Throwable) { }
            }, "phone-runtime-system-settle").start()
        } catch (_: Throwable) { /* Never block a lifecycle callback on runtime cleanup. */ }
    }

    // Call only with the BuiltinLinux monitor held when inspecting the service map.
    // Stop takes recoveryLock alone; never retain it across drain waits or gate I/O.
    private fun withColdRestoreAdmission(worker: NativeRuntimeOwnership.ColdWorker, profile: String,
        event: NativeServerRestoreTicket? = null,
        action: () -> Unit) = synchronized(recoveryLock) {
        if (!worker.runIfIdle(supervisionGeneration, recoveryScheduleId, serverRunning) {
            if (!nativeAdmitted(profile, worker.generation) ||
                (event != null && !serverEventAuthorityCurrent(event))) throw ColdRestoreRevoked()
            action()
        }) throw ColdRestoreRevoked()
    }

    /** Called only after Android has established the recreated service's foreground notification. */
    @Synchronized fun restoreServerAfterProcessReclaim() {
        if (!prepareColdRestore()) return
        val profile = recoveryPreferences.getString("restoreOwner", null)
        if (profile == null) {
            if (recoveryPreferences.contains("drainOwner")) Thread({
                try { synchronized(this) { drainPendingOwnedServer() } }
                catch (_: Throwable) { restoreUnavailable("ownershipUnknown") }
            }, "phone-runtime-revoked-drain").start()
        } else {
            val worker = createColdRestoreWorker(profile)
            if (worker != null) Thread({ runColdRestore(worker, profile) }, "phone-runtime-restore").start()
        }
    }

    private fun prepareColdRestore(): Boolean {
        if (coldRestoreStarted || serverRunning || serverEventTicket != null) return false
        return if (!serverRestorationArmed) {
            rejectServerRestoration()
            false
        } else {
            coldRestoreStarted = true
            true
        }
    }

    private fun createColdRestoreWorker(profile: String): NativeRuntimeOwnership.ColdWorker? =
        synchronized(recoveryLock) {
        // Stop may have revoked authority after the earlier durable eligibility read.
        if (!nativeAdmitted(profile, supervisionGeneration)) null else {
            NativeRuntimeOwnership.ColdWorker(supervisionGeneration, ++recoveryScheduleId).also {
                scheduledRecovery = true
                restorePhase = "waiting"; restoreReason = null
            }
        }
    }

    private fun runColdRestore(worker: NativeRuntimeOwnership.ColdWorker, profile: String,
        event: NativeServerRestoreTicket? = null) {
        try {
            awaitColdRestoreDelay(worker, profile, event)
            synchronized(this) {
                // Outside the short recovery admission lock: installer drain can wait.
                if (event == null) recoverColdComponentUpdates()
                val (recipe, oldReceipt) = readColdRestoreState(worker, profile, event)
                drainColdServer(worker, profile, oldReceipt, event)
                val reservedGeneration = reserveColdRestore(worker, profile, recipe, event)
                withColdRestoreAdmission(worker, profile, event) { }
                launchService(SERVER, recipe.restorationScript(), SERVER_DEFAULT_PORT, null,
                    ServiceLaunch(nativeOwned = true, nativeSupervisionGeneration = worker.generation,
                        nativeAttemptGeneration = reservedGeneration, nativeScheduleId = worker.scheduleId,
                        systemEvent = event))
            }
        } catch (_: ColdRestoreRevoked) {
            coldRestoreFailed(worker, profile, null, event)
        } catch (error: Throwable) {
            coldRestoreFailed(worker, profile, error, event)
        } finally {
            synchronized(recoveryLock) {
                worker.runIfCurrent(supervisionGeneration, recoveryScheduleId) { scheduledRecovery = false }
                if (event != null && serverEventTicket === event) {
                    serverEventTicket = null
                    serverEventWorkerStarted = false
                }
            }
            synchronized(this) { serviceSetChanged() }
        }
    }

    private fun awaitColdRestoreDelay(worker: NativeRuntimeOwnership.ColdWorker, profile: String,
        event: NativeServerRestoreTicket? = null) {
        val delayUntil = SystemClock.elapsedRealtime() + restoredDelay()
        while (SystemClock.elapsedRealtime() < delayUntil) {
            val current = synchronized(recoveryLock) {
                worker.runIfCurrent(supervisionGeneration, recoveryScheduleId) {
                    if (!nativeAdmitted(profile, worker.generation) ||
                        (event != null && !serverEventAuthorityCurrent(event))) throw ColdRestoreRevoked()
                }
            }
            if (!current) throw ColdRestoreRevoked()
            Thread.sleep((delayUntil - SystemClock.elapsedRealtime()).coerceIn(1L, OWNERSHIP_POLL_MS))
        }
    }

    private fun readColdRestoreState(
        worker: NativeRuntimeOwnership.ColdWorker, profile: String,
        event: NativeServerRestoreTicket? = null,
    ): Pair<NativeServerRecipe, NativeRuntimeReceipt> {
        var recipe: NativeServerRecipe? = null
        var receipt: NativeRuntimeReceipt? = null
        withColdRestoreAdmission(worker, profile, event) {
            if (readBudget(profile).attempts >= 3) {
                restoreUnavailable("budgetExhausted"); throw ColdRestoreRevoked()
            }
            if (componentRecoveryPending()) {
                restoreUnavailable("componentRecoveryRequired"); throw ColdRestoreRevoked()
            }
            recipe = NativeServerRecipe.read(jsonMap(recoveryPreferences.getString(recipeKey(profile), null)
                ?: error("storageUnavailable")))
            receipt = ownership(profile)
            if (event == null) {
                check(recipe!!.compatible(packageIdentity(), rootfsIdentity())) { "storageUnavailable" }
            } else {
                check(recipe == event.originalRecipe && receipt == event.previousReceipt &&
                    readBudget(profile) == event.budget) { "ownershipUnknown" }
                recipe = event.recipe
            }
        }
        return recipe!! to receipt!!
    }

    private fun drainColdServer(
        worker: NativeRuntimeOwnership.ColdWorker, profile: String, oldReceipt: NativeRuntimeReceipt,
        event: NativeServerRestoreTicket? = null,
    ) {
        if (event != null && oldReceipt.boot != bootIdentity()) {
            withColdRestoreAdmission(worker, profile, event) { requireRebootQuiescence(event) }
            return
        }
        val plan = NativeRuntimeOwnership.plan(oldReceipt, bootIdentity(),
            if (event == null) sameUidInventory() else serverEventInventory(),
            registeredRuntimePids(), nonceMatches = ::nonceMatches)
        // Admission and each exact signal share Stop's short lock; no newer owner is selected.
        for (identity in plan.server) withColdRestoreAdmission(worker, profile, event) {
            if (identity.sameProcess(kernelIdentity(identity.pid))) Os.kill(identity.pid, OsConstants.SIGTERM)
        }
        val deadline = SystemClock.elapsedRealtime() + 3000
        while (plan.server.any { it.sameProcess(kernelIdentity(it.pid)) } && SystemClock.elapsedRealtime() < deadline) {
            withColdRestoreAdmission(worker, profile, event) { }
            Thread.sleep(PROCESS_POLL_MS)
        }
        for (identity in plan.server) withColdRestoreAdmission(worker, profile, event) {
            if (identity.sameProcess(kernelIdentity(identity.pid))) Os.kill(identity.pid, OsConstants.SIGKILL)
        }
        val drainDeadline = SystemClock.elapsedRealtime() + 2000
        while (plan.server.any { it.sameProcess(kernelIdentity(it.pid)) } &&
            SystemClock.elapsedRealtime() < drainDeadline) {
            withColdRestoreAdmission(worker, profile, event) { }
            Thread.sleep(PROCESS_POLL_MS)
        }
        check(plan.server.none { it.sameProcess(kernelIdentity(it.pid)) }) { "ownershipUnknown" }
        NativeRuntimeOwnership.plan(oldReceipt, bootIdentity(),
            if (event == null) sameUidInventory() else serverEventInventory(), registeredRuntimePids(),
            nonceMatches = ::nonceMatches)
            .also { check(it.server.isEmpty()) { "ownershipUnknown" } }
    }

    private fun reserveColdRestore(
        worker: NativeRuntimeOwnership.ColdWorker, profile: String, recipe: NativeServerRecipe,
        event: NativeServerRestoreTicket? = null,
    ): Long? {
        var reservedGeneration: Long? = null
        withColdRestoreAdmission(worker, profile, event) {
            if (event != null) {
                check(readBudget(profile) == event.budget) { "storageUnavailable" }
                check(NativeServerRecipe.read(jsonMap(recoveryPreferences.getString(recipeKey(profile), null)
                    ?: error("storageUnavailable"))) == event.originalRecipe) { "storageUnavailable" }
                if (event.previousReceipt.boot != bootIdentity()) requireRebootQuiescence(event)
                else NativeRuntimeOwnership.plan(event.previousReceipt, bootIdentity(), serverEventInventory(),
                    registeredRuntimePids(), nonceMatches = ::nonceMatches)
                    .also { check(it.server.isEmpty()) { "ownershipUnknown" } }
                // A prior null-intent rejection may have queued the exact same server cleanup.
                // The worker just proved it complete; never clear a foreign, newer or helper drain.
                if (recoveryPreferences.contains("drainOwner")) {
                    check(serverEventDrainCompatible(profile, event.previousReceipt)) { "ownershipUnknown" }
                    check(recoveryPreferences.edit().remove("drainOwner").remove("drainAll")
                        .remove(drainKey(profile)).commit()) { "storageUnavailable" }
                }
                if (event.originalRecipe != recipe) check(recoveryPreferences.edit()
                    .putString(recipeKey(profile), JSONObject(recipe.map()).toString()).commit()) { "storageUnavailable" }
            }
            restorePhase = "restoring"
            serverRecipe = recipe
            // Runtime identity generation is independent of the immutable admission ticket.
            val runtimeGeneration = nextRuntimeGeneration()
            reservedGeneration = reserveNativeAttempt(profile, runtimeGeneration).recoveryGeneration
        }
        return reservedGeneration
    }

    private fun requireRebootQuiescence(ticket: NativeServerRestoreTicket) {
        check(NativeServerEventPolicy.crossBootQuiescent(ticket.previousReceipt, bootIdentity(),
            serverEventInventory(), registeredRuntimePids(), AndroidProcess.myPid())) { "ownershipUnknown" }
    }

    private fun coldRestoreFailed(worker: NativeRuntimeOwnership.ColdWorker, profile: String, error: Throwable?,
        event: NativeServerRestoreTicket? = null) {
        synchronized(recoveryLock) {
            worker.runIfCurrent(supervisionGeneration, recoveryScheduleId) {
                // Stop and system timeout own their published reason after revocation.
                if (restartWanted && !userStopped) {
                    if (!nativeAdmitted(profile, worker.generation) ||
                        (event != null && !serverEventAuthorityCurrent(event))) restoreUnavailable("policyDisabled")
                    else if (error != null) restoreUnavailable(
                        if (error.message == "ownershipUnknown") "ownershipUnknown" else "storageUnavailable")
                }
            }
        }
    }

    private fun budgetKey(profile: String) = "oc.builtinRecoveryBudget.$profile"
    private fun readBudget(profile: String): NativeRecoveryBudget = NativeRecoveryBudget.read(
        jsonMap(recoveryPreferences.getString(budgetKey(profile), null)
            ?: error("recovery_unavailable")))
    private fun writeBudget(profile: String, budget: NativeRecoveryBudget) {
        check(recoveryPreferences.edit().putString(budgetKey(profile), JSONObject(budget.map()).toString()).commit()) {
            "recovery_unavailable"
        }
    }

    @Synchronized
    fun stageServerRecovery(profile: String, legacy: Map<*, *>): Map<String, Any?> {
        require(profile.isNotEmpty() && profile.length <= 200 && !profile.contains('/'))
        if (!recoveryPreferences.contains(budgetKey(profile))) {
            writeBudget(profile, NativeRecoveryBudget.read(legacy))
        }
        return readBudget(profile).map()
    }

    @Synchronized
    fun bindServerRecovery(profile: String, legacy: Map<*, *>?, enabled: Boolean): Map<String, Any?> {
        require(profile.isNotEmpty() && profile.length <= 200 && !profile.contains('/'))
        check(migrationMarkerValid(profile)) { "recovery_unavailable" }
        // Bind cannot create a missing record; staging was disabled until marker acknowledgement.
        val budget = readBudget(profile)
        check(recoveryPreferences.edit().putString("owner", profile).putBoolean("enabled", enabled).commit())
        synchronized(recoveryLock) {
            if (supervisionProfile != profile || supervisionEnabled != enabled) {
                revokeIdleTransition()
                supervisionGeneration++
                scheduledRecovery = false
            }
            supervisionProfile = profile
            supervisionEnabled = enabled
        }
        return budget.map()
    }

    @Synchronized fun serverRecoveryBudget(profile: String): Map<String, Any?> = readBudget(profile).map()

    @Synchronized
    fun updateServerRecoveryReceipt(profile: String, value: Map<*, *>): Map<String, Any?> {
        val current = readBudget(profile)
        val next = NativeRecoveryBudget.read(value)
        check(current.revision == next.revision && current.attempts == next.attempts)
        // This consumer can update receipts, never reserve or reset the count.
        check(!next.pending || (current.pending && next.eventId == current.eventId &&
            next.recoveryGeneration == current.recoveryGeneration))
        check(next.pending || !current.pending || current.confirmedAt != null || !serverRunning)
        val updated = next.copy(revision = current.revision + 1)
        writeBudget(profile, updated)
        return updated.map()
    }

    @Synchronized
    fun confirmManualServerStart(profile: String): Map<String, Any?> {
        synchronized(recoveryLock) {
            check(NativeRecoveryBudget.manualResetAllowed(activityResumed, restartWanted, userStopped,
                supervisionProfile, profile, manualStartGeneration, recoveryGeneration,
                serverRunning, nativeRecoveryAttempt != null))
        }
        val current = readBudget(profile)
        val archived = NativeRecoveryBudget.archiveConfirmed(receipts(profile), current)
        val reset = NativeRecoveryBudget(revision = current.revision + 1)
        check(recoveryPreferences.edit()
            .putString(budgetKey(profile), JSONObject(reset.map()).toString())

                .putString("oc.builtinRecoveryReceipts.$profile",
                    JSONArray(archived.map { JSONObject(it.map()) }).toString()).commit())
        synchronized(recoveryLock) {
            if (manualStartGeneration == recoveryGeneration) manualStartGeneration = null
        }
        restartBackoff.reset()
        return reset.map()
    }

    /** Main-thread revocation does not wait for persistence or process shutdown. */
    fun unbindServerRecovery(profile: String) {
        synchronized(recoveryLock) {
            if (supervisionProfile == profile) {
                revokeIdleTransition()
                supervisionEnabled = false
                supervisionProfile = null
                supervisionGeneration++
                scheduledRecovery = false
            }
        }
    }

    @Synchronized fun persistServerRecoveryUnbind(profile: String) {
        if (recoveryPreferences.getString("restoreOwner", null) == profile) {
            check(recoveryPreferences.edit().remove("restoreOwner").remove(recipeKey(profile)).commit())
            serverRecipe = null
        }
        if (recoveryPreferences.getString("owner", null) == profile) {
            check(recoveryPreferences.edit().remove("owner").putBoolean("enabled", false).commit())
        }
    }

    @Synchronized fun deleteServerRecovery(profile: String) {
        unbindServerRecovery(profile)
        persistServerRecoveryUnbind(profile)
        val edit = recoveryPreferences.edit().remove(budgetKey(profile)).remove("oc.builtinRecoveryReceipts.$profile")
            .remove(recipeKey(profile)).remove(ownershipKey(profile)).remove(drainKey(profile))
        if (recoveryPreferences.getString("drainOwner", null) == profile) edit.remove("drainOwner")
        check(edit.commit())
    }

    private fun migrationMarkerValid(profile: String): Boolean {
        return try {
            val raw = flutterPreferences.getString("flutter.oc.builtinRecovery.$profile", null)
            raw != null && NativeRecoveryBudget.migrationAllows(jsonMap(raw))
        } catch (_: Throwable) { false }
    }

    private fun receipts(profile: String): List<NativeRecoveryBudget> {
        val raw = recoveryPreferences.getString("oc.builtinRecoveryReceipts.$profile", null) ?: return emptyList()
        val array = JSONArray(raw)
        check(array.length() <= 16) { "recovery_unavailable" }
        return (0 until array.length()).map { NativeRecoveryBudget.read(jsonMap(array.getJSONObject(it).toString())) }
    }

    @Synchronized fun serverRecoveryReceipts(profile: String): List<Map<String, Any?>> =
        receipts(profile).map { it.map() }

    @Synchronized fun ackServerRecoveryReceipt(profile: String, eventId: String) {
        val remaining = receipts(profile).filter { it.eventId != eventId }
        check(recoveryPreferences.edit().putString("oc.builtinRecoveryReceipts.$profile",
            JSONArray(remaining.map { JSONObject(it.map()) }).toString()).commit())
    }

    private fun policyPermits(profile: String): Boolean = try {
        val raw = flutterPreferences.getString("flutter.oc.automation.$profile", null)
        // Absence uses the explicitly bound default policy; malformed stored data never does.
        raw == null || NativeRecoveryBudget.policyAllows(jsonMap(raw))
    } catch (_: Throwable) { false }

    private fun nativeAdmitted(profile: String, generation: Long): Boolean = synchronized(recoveryLock) {
        NativeRecoveryBudget.admitted(!idleBlocksRestoration() && supervisionEnabled && supervisionProfile == profile,
            restartWanted, userStopped, supervisionGeneration, generation,
            migrationMarkerValid(profile), policyPermits(profile))
    }

    private fun reserveNativeAttempt(profile: String, generation: Long): NativeRecoveryBudget {
        var budget = readBudget(profile)
        val archived = NativeRecoveryBudget.archiveConfirmed(receipts(profile), budget)
        if (budget.pending) {
            // Preserve health proof even if the process crashes before Dart records its act.
            budget = NativeRecoveryBudget(attempts = budget.attempts, revision = budget.revision)
        }
        val reserved = budget.reserve(System.currentTimeMillis(), generation,
            "builtin-restart:${java.util.UUID.randomUUID()}")
        check(recoveryPreferences.edit()
            .putString(budgetKey(profile), JSONObject(reserved.map()).toString())

                .putString("oc.builtinRecoveryReceipts.$profile",
                    JSONArray(archived.map { JSONObject(it.map()) }).toString())
            .commit()) { "recovery_unavailable" }
        return reserved
    }

    private data class RecoverySchedule(val profile: String, val generation: Long, val id: Long)

    /** Exit worker owns timing and restart; no activity callback or Dart dispatch is needed. */
    private fun scheduleNativeRecovery(service: Service) {
        val script = service.script
        val port = service.port
        if (script != null && port != null) {
            val schedule = prepareNativeRecovery(service)
            if (schedule != null) {
                val worker = Thread({ runNativeRecovery(schedule, script, port) }, "phone-native-recovery")
                try { worker.start() } catch (_: Throwable) {
                    synchronized(recoveryLock) {
                        if (recoveryScheduleId == schedule.id) scheduledRecovery = false
                    }
                }
            }
        }
    }

    private fun recoveryScheduleAllowed(): Boolean =
        !idleBlocksRestoration() && supervisionEnabled && restartWanted && !userStopped

    private fun claimRecoverySchedule(): RecoverySchedule? = synchronized(recoveryLock) {
        val profile = supervisionProfile
        if (profile == null || !recoveryScheduleAllowed()) null else {
            val generation = supervisionGeneration
            val scheduleId = ++recoveryScheduleId
            scheduledRecovery = true
            RecoverySchedule(profile, generation, scheduleId)
        }
    }

    private fun prepareNativeRecovery(service: Service): RecoverySchedule? {
        val schedule = claimRecoverySchedule() ?: return null
        restartBackoff.exited(SystemClock.elapsedRealtime() - service.startedAt, SystemClock.elapsedRealtime())
        return try { persistRestartBackoff(); schedule } catch (_: Throwable) {
            restoreUnavailable("storageUnavailable")
            synchronized(recoveryLock) { scheduledRecovery = false }
            null
        }
    }

    private fun runNativeRecovery(schedule: RecoverySchedule, script: String, port: Int) {
        val profile = schedule.profile
        val generation = schedule.generation
        val scheduleId = schedule.id
        try {
            while (nativeAdmitted(profile, generation)) {
                val remaining = synchronized(this) { restartBackoff.remainingMs(SystemClock.elapsedRealtime()) }
                if (remaining <= 0L) break
                Thread.sleep(remaining.coerceAtMost(OWNERSHIP_POLL_MS))
            }
            synchronized(this) {
                launchNativeRecovery(profile, generation, script, port)
            }
        } catch (_: Throwable) { /* Fail closed: no launch without durable reservation. */ }
        finally {
            synchronized(recoveryLock) {
                if (supervisionGeneration == generation && recoveryScheduleId == scheduleId) scheduledRecovery = false
            }
            synchronized(this) { serviceSetChanged() }
        }
    }

    private fun launchNativeRecovery(profile: String, generation: Long, script: String, port: Int) {
        if (!nativeAdmitted(profile, generation) || serverRunning) return
        val budget = readBudget(profile)
        if (budget.attempts >= 3) return
        val reserved = reserveNativeAttempt(profile,
            if (serverRecipe != null) nextRuntimeGeneration() else recoveryGeneration)
        launchService(SERVER, script, port, null,
            ServiceLaunch(nativeOwned = true, nativeSupervisionGeneration = generation,
                nativeAttemptGeneration = reserved.recoveryGeneration))
    }

    /** Called on the main thread: invalidation never waits for runtime I/O. */
    fun setActivityResumed(resumed: Boolean) {
        synchronized(recoveryLock) {
            activityResumed = resumed
            activityEpoch++
            if (resumed) idleReturnNotification.clear()
        }
        idleTimer.schedule(SystemClock.elapsedRealtime())
        if (!resumed) cancelServerRecovery()
    }

    /** Revokes admission immediately, before an asynchronous explicit stop. */
    fun requestServerStop(reason: String = "stopped", includeOther: Boolean = false,
        onRevoked: ((Long) -> Unit)? = null): Long = synchronized(recoveryLock) {
        idleReturnNotification.clear()
        restartWanted = false
        serverEventTicket = null
        serverEventWorkerStarted = false
        userStopped = true
        supervisionGeneration++
        scheduledRecovery = false
        recoveryGeneration++
        recoveryAttempt = null
        confirmedRecoveryAttempt = null
        nativeRecoveryAttempt = null
        manualStartGeneration = null
        val stopRevision = ++wantedRevision
        onRevoked?.invoke(stopRevision)
        restoreUnavailable(reason)
        // The old identity snapshot and durable revocation share the short admission lock.
        // No newer manual Start can publish a replacement receipt between these operations.
        val profile = recoveryPreferences.getString("restoreOwner", null)
        val edit = recoveryPreferences.edit().putBoolean("wanted", false).putBoolean("userStopped", true)
            .remove("restoreOwner").putString("restoreReason", reason)
        capturePendingDrain(edit, profile, includeOther)
        if (profile != null) edit.remove(recipeKey(profile))
        check(NativeRuntimeOwnership.revocationMayCommit(stopRevision, wantedRevision) && edit.commit()) {
            "The phone server setting could not be saved."
        }
        revokeIdleTransition()
        stopRevision
    }

    /** Cancels only an unconfirmed automatic process, never a manual replacement. */
    fun cancelServerRecovery() {
        val attempt = synchronized(recoveryLock) {
            recoveryGeneration++
            confirmedRecoveryAttempt = null
            recoveryAttempt.also { recoveryAttempt = null }
        }
        if (attempt != null) {
            try {
                Thread({
                    try {
                        synchronized(this) {
                            if (services[SERVER]?.process === attempt.process) {
                                removeService(SERVER)
                            }
                        }
                    } catch (_: Throwable) { /* Lifecycle callbacks must not crash the app. */ }
                }, "phone-recovery-cancel").start()
            } catch (_: Throwable) {
                // Admission was revoked synchronously. A failed worker launch
                // cannot block or throw from Android's onPause callback.
            }
        }
    }

    fun confirmServerRecovery(expectedGeneration: Long) {
        synchronized(recoveryLock) {
            // Idempotence lets the controller retry durable act recording after
            // the starter already confirmed this exact automatic process.
            val attempt = nativeRecoveryAttempt ?: recoveryAttempt ?: confirmedRecoveryAttempt
            check(activityResumed && restartWanted && !userStopped &&
                (nativeRecoveryAttempt != null || recoveryGeneration == expectedGeneration) &&
                attempt != null && attempt.generation == expectedGeneration && attempt.process.isAlive) {
                "The phone server could not restart."
            }
            confirmedRecoveryAttempt = attempt
            recoveryAttempt = null
        }
    }

    private fun setServerWanted(wanted: Boolean) {
        val revision = synchronized(recoveryLock) {
            idleReturnNotification.clear()
            restartWanted = false
            serverEventTicket = null
            serverEventWorkerStarted = false
            wantedRevision++
            recoveryGeneration++
            recoveryAttempt = null
            confirmedRecoveryAttempt = null
            nativeRecoveryAttempt = null
            supervisionGeneration++
            scheduledRecovery = false
            wantedRevision
        }
        // Serialize the durable admission change with main-thread Stop. A stale
        // Start cannot overwrite its revocation after losing this revision.
        synchronized(recoveryLock) {
            check(NativeRuntimeOwnership.revocationMayCommit(revision, wantedRevision)) {
                "The phone server setting could not be saved."
            }
            val edit = recoveryPreferences.edit().putBoolean("wanted", wanted).putBoolean("userStopped", !wanted)
            val reason = if (wanted) null else recoveryPreferences.getString("restoreReason", null)
                .takeIf { it == "systemTimeout" } ?: "stopped"
            if (!wanted) {
                val owner = recoveryPreferences.getString("restoreOwner", null)
                edit.remove("restoreOwner")
                capturePendingDrain(edit, owner)
                if (owner != null) edit.remove(recipeKey(owner))
                edit.putString("restoreReason", reason)
            } else edit.remove("restoreReason")
            check(edit.commit()) { "The phone server setting could not be saved." }
            if (!wanted) { serverRecipe = null; restoreUnavailable(reason!!) }
            else { restorePhase = "idle"; restoreReason = null }
            revokeIdleTransition()
            restartWanted = wanted; userStopped = !wanted
        }
    }

    @Synchronized
    fun restartServer(script: String, port: Int, expectedGeneration: Long) {
        fun admitted() = activityResumed && restartWanted &&
            recoveryGeneration == expectedGeneration
        synchronized(recoveryLock) {
            check(admitted()) { "The phone server could not restart." }
        }
        check(!serverRunning) { "The phone server is already running." }
        val profile = synchronized(recoveryLock) { supervisionProfile?.takeIf { supervisionEnabled } }
        if (profile != null) {
            check(policyPermits(profile))
            reserveNativeAttempt(profile, expectedGeneration)
        }
        launchService(SERVER, script, port, null, ServiceLaunch(expectedGeneration = expectedGeneration))
        val accepted = synchronized(recoveryLock) { admitted() }
        if (!accepted) {
            removeService(SERVER)
            error("The phone server could not restart.")
        }
    }

    val serverLog = File(home, "server.log")

    val serverRunning: Boolean get() = serviceRunning(SERVER)

    val port: Int? get() = servicePort(SERVER)

    /**
     * How long the tracked OpenCode process has run, or null when none runs.
     * The app tells a server that is still booting from one that stopped
     * answering (stale) by this age, never by guessing from the outside.
     */
    val serverUptimeMs: Long?
        @Synchronized get() = services[SERVER]?.takeIf { it.process.isAlive }
            ?.let { SystemClock.elapsedRealtime() - it.startedAt }

    @Synchronized
    fun serviceRunning(name: String): Boolean = services[name]?.process?.isAlive == true

    @Synchronized
    fun servicePort(name: String): Int? =
        services[name]?.takeIf { it.process.isAlive }?.port

    /** The names of the services that run now. */
    @Synchronized
    fun runningServices(): List<String> =
        services.filterValues { it.process.isAlive }.keys.toList()

    @Synchronized
    fun startServer(script: String, port: Int, restoreRecipe: Map<*, *>? = null) {
        val requested = restoreRecipe?.let {
            check(port == SERVER_DEFAULT_PORT) { "The phone server could not restart." }
            NativeServerRecipe.fromRequest(it, it["profileId"] as? String, packageIdentity(), rootfsIdentity())
        }
        val eligible = NativeRuntimeOwnership.manualRecipeEligible(requested?.profileId, supervisionProfile,
            supervisionEnabled, requested?.let { migrationMarkerValid(it.profileId) } == true,
            requested?.let { policyPermits(it.profileId) } == true)
        val recipe = requested?.takeIf { eligible }
        serverRecipe = recipe
        if (recipe == null) {
            val old = recoveryPreferences.getString("restoreOwner", null)
            val edit = recoveryPreferences.edit().remove("restoreOwner")
            if (old != null) edit.remove(recipeKey(old)).remove(ownershipKey(old))
            check(edit.commit()) { "The phone server could not restart." }
        }
        startService(SERVER, script, port, null)
        if (requested != null && !eligible) restoreUnavailable("policyDisabled")
        else if (requested != null && !serverRestorationArmed && restoreReason == null) {
            restoreUnavailable("ownershipUnknown")
        }
    }

    private data class StoppedServer(val script: String, val port: Int)
    private var stoppedPhoneServer: StoppedServer? = null

    @Synchronized
    fun stopServer(forPhoneEngineSetup: Boolean = false) {
        stoppedPhoneServer = null
        if (forPhoneEngineSetup) services[SERVER]?.takeIf { it.process.isAlive }?.let { service ->
            service.script?.let { script -> service.port?.let { port ->
                stoppedPhoneServer = StoppedServer(script, port)
            } }
        }
        stopService(SERVER)
    }

    private fun restoreStoppedPhoneServer() {
        val stopped = stoppedPhoneServer ?: return
        if (!serverRunning) startServer(stopped.script, stopped.port)
        stoppedPhoneServer = null
    }

    /**
     * Starts [script] as the service [name], stopping an earlier run of the
     * same service first. [notice] is what the ongoing notification says
     * while this service runs (the app sends it in its own language); the
     * newest service with one wins, so "OpenCode and AI Team are running"
     * replaces "OpenCode is running" when the team starts.
     */
    @Synchronized
    fun startService(name: String, script: String, port: Int?, notice: String?) {
        val workGeneration = workLeases.foregroundGeneration()
        check(installed) { "Ubuntu is not installed in the app yet" }
        require(NAME.matches(name)) { "Invalid service name: $name" }
        require(name != PHONE_ENGINE) { "The native phone engine has a dedicated launcher." }
        if (name == SERVER) {
            stoppedPhoneServer = null
            drainPendingOwnedServer()
        }
        // Joining a setup rollback must not cut off the already restored server.
        if (name == SERVER && services[SERVER]?.let {
                it.process.isAlive && it.port == port && it.script == script
            } == true) return
        if (name == SERVER) {
            setServerWanted(true)
            restartBackoff.reset()
            if (serverRecipe != null) try { persistRestartBackoff() }
                catch (_: Throwable) { disarmRestoration("storageUnavailable") }
            synchronized(recoveryLock) { manualStartGeneration = recoveryGeneration }
        }
        launchService(name, script, port, notice)
        if (name == SERVER) {
            workLeases.authorizeForegroundWork(workGeneration)
            synchronized(recoveryLock) { manualStartGeneration = recoveryGeneration }
        }
    }

    private fun launchService(
        name: String,
        script: String,
        port: Int?,
        notice: String?,
        launch: ServiceLaunch = ServiceLaunch(),
    ): Process {
        val nativeOwned = launch.nativeOwned
        val nativeAttemptGeneration = launch.nativeAttemptGeneration
        // Exit monitor already removed an autonomous crash. Do not notify an
        // empty set after the final reservation: that would stop the retained
        // FGS between reserving attempt three and creating its child.
        if (NativeRecoveryBudget.shouldRemoveBeforeLaunch(nativeOwned, services.containsKey(name))) removeService(name)
        val log = serviceLogFile(name)
        // One log per run; the previous one stays for a look after a crash.
        if (log.isFile) log.renameTo(File(home, "$name.previous.log"))
        val process = launchServiceProcess(name, script, log, launch)
        process.outputStream.close()
        services[name] = serviceStarted(name, process, port, notice, script)
        if (name == SERVER && nativeOwned && launch.idleGeneration == null) synchronized(recoveryLock) {
            nativeRecoveryAttempt = RecoveryAttempt(process, nativeAttemptGeneration ?: error("recovery_unavailable"))
            recoveryAttempt = null
        }
        recordRunning()
        try {
            // Native crash recovery retains the existing FGS during its bounded delay.
            // Re-requesting a background FGS start would lose Android 12+ admission.
            if (!nativeOwned) BuiltinServerService.start(context, currentNotice())
        } catch (error: Exception) {
            // A refused foreground service must not leave an unsupervised child.
            removeService(name)
            throw error
        }
        monitorServiceExit(name, process)
        return process
    }

    private fun launchServiceProcess(name: String, script: String, log: File, launch: ServiceLaunch): Process {
        val nativeOwned = launch.nativeOwned
        val expectedGeneration = launch.expectedGeneration
        val nativeSupervisionGeneration = launch.nativeSupervisionGeneration
        return if (launch.idleGeneration != null) {
            val profile = launch.idleOwner ?: error("idle_resume_stale")
            val epoch = launch.idleActivityEpoch ?: error("idle_resume_stale")
            synchronized(recoveryLock) { check(idleResumeCurrent(profile, launch.idleGeneration, epoch)) }
            startGatedServer(script, log, serverRecipe ?: error("idle_resume_unavailable"), launch)
        } else if (nativeOwned) {
            val profile = synchronized(recoveryLock) { supervisionProfile } ?: error("recovery_unavailable")
            val supervisorGeneration = nativeSupervisionGeneration ?: error("recovery_unavailable")
            check(nativeAdmitted(profile, supervisorGeneration))
            if (name == SERVER && serverRecipe != null) startGatedServer(script, log, serverRecipe!!, launch)
            else synchronized(recoveryLock) {
                check(nativeAdmitted(profile, supervisorGeneration))
                start(script, log)
            }
        } else if (expectedGeneration == null) {
            if (name == SERVER) startManualServer(script, log) else start(script, log)
        } else synchronized(recoveryLock) {
            // The final admission check and process creation are one operation.
            // onPause never waits for tree shutdown, FGS work or a health probe.
            check(activityResumed && restartWanted &&
                recoveryGeneration == expectedGeneration) {
                "The phone server could not restart."
            }
            start(script, log).also {
                confirmedRecoveryAttempt = null
                recoveryAttempt = RecoveryAttempt(it, expectedGeneration)
            }
        }
    }

    private fun monitorServiceExit(name: String, process: Process) {
        // A service that exits on its own (a crash, a bad config) takes its
        // share of the "running" notification with it.
        try {
            Thread({
                try { process.waitFor() }
                catch (_: Throwable) { try { stopTree(process) } catch (_: Throwable) { } }
                finally {
                    try {
                        synchronized(this) {
                            if (services[name]?.process === process) {
                                val exited = services.getValue(name)
                                recordServiceExit(name, exited)
                                services.remove(name)
                                if (name == SERVER) {
                                    synchronized(recoveryLock) {
                                        val idle = idleState.snapshot()
                                        if (idle.helperStopped && idle.owner != null) idleState.markServerLost(idle.owner, idle.generation)
                                    }
                                    scheduleNativeRecovery(exited)
                                }
                                serviceSetChanged()
                            }
                        }
                    } catch (_: Throwable) { }
                }
            }, "phone-service").start()
        } catch (_: Throwable) {
            try { removeService(name) } catch (_: Throwable) { }
            throw IllegalStateException("The phone service could not start.")
        }
    }

    @Synchronized
    fun stopService(name: String) {
        try {
            if (name == SERVER) setServerWanted(false)
        } finally {
            removeService(name)
            if (name == SERVER) drainPendingOwnedServer()
        }
    }

    private fun removeService(name: String) {
        var failure: Exception? = null
        try {
            if (name == PHONE_ENGINE) try { phoneEngine.stopTracked() }
                catch (error: Exception) { failure = error }
            val service = services[name]
            if (service != null) {
                if (service.process.isAlive) service.stopRequested = true
                if (name.startsWith("agent-auth.") || name.startsWith("agent-host.")) stopAgentProcess(service.process)
                else stopTree(service.process)
                if (service.process.isAlive) throw PhoneEngineNative.Failure("engine_stop_failed")
                recordServiceExit(name, service)
                services.remove(name)
            }
        } finally { serviceSetChanged() }
        failure?.let { throw it }
    }

    /** Stops every service; Stop in the notification and uninstall use it. */
    @Synchronized
    internal fun drainRevokedRuntimeChildren() = drainPendingOwnedServer()

    @Synchronized
    fun stopAllServices(expectedStopRevision: Long? = null) {
        if (expectedStopRevision != null && synchronized(recoveryLock) {
                !NativeRuntimeOwnership.revocationMayCommit(expectedStopRevision, wantedRevision)
            }) {
            drainPendingOwnedServer()
            return
        }
        // Clear even if a crash already removed the server from the map.
        var failure: Exception? = null
        try { setServerWanted(false) } catch (error: Exception) { failure = error }
        try { stopEveryService(services.keys.toList()) { removeService(it) } }
        catch (error: Exception) { if (failure == null) failure = error }
        try { drainPendingOwnedServer() } catch (error: Exception) { if (failure == null) failure = error }
        failure?.let { throw it }
    }

    /** Keeps the foreground service exactly as long as any service runs. */
    private fun serviceSetChanged() {
        recordRunning()
        // A reply cannot run on a server that is gone: never keep the phone
        // awake for it.
        if (!serverRunning) workLeases.serverGone()
        workLeases.agentProfiles().forEach { profile ->
            if (services["agent-host.$profile"]?.process?.isAlive != true) workLeases.helperGone(profile)
        }
        val running = services.values.any { it.process.isAlive } || workLeases.foregroundHeld
        val holdsRecovery = try {
            synchronized(recoveryLock) {
                val eventHolds = scheduledRecovery && serverEventTicket?.let(::serverEventAuthorityCurrent) == true
                val profile = supervisionProfile
                eventHolds || (profile != null && NativeRecoveryBudget.keepsForeground(false,
                    serverRecoveryScheduled, nativeAdmitted(profile, supervisionGeneration),
                    readBudget(profile).attempts))
            }
        } catch (_: Throwable) { false }
        if (!running) {
            if (!holdsRecovery) try { BuiltinServerService.stop(context) } catch (_: Throwable) { }
            return
        }
        if (!synchronized(recoveryLock) { activityResumed }) return
        // Only the words change here. Android refuses to (re)start a
        // foreground service from the background, and a service can end
        // while the app is away; the notification then keeps its old text.
        try {
            BuiltinServerService.start(context, currentNotice())
        } catch (error: Exception) {
            Log.w(TAG, "notification not updated", error)
        }
    }

    /**
     * Keeps what runs on disk (AppLifecycle): a stop by the person clears
     * it, while a force stop or a kill runs nothing of ours and leaves it,
     * so the next start knows what to bring back.
     */
    private fun recordRunning() {
        idleServerLive = serverRunning
        try {
            val owner = recoveryPreferences.getString("restoreOwner", null)
            if (owner != null && serverRunning) {
                writeOwnership(owner, ownership(owner).copy(other = knownOtherRuntime()))
            }
        } catch (_: Throwable) { restoreUnavailable("storageUnavailable") }
        try {
            AppLifecycle.recordServices(
                context,
                services.filterValues { it.process.isAlive }.keys.toList(),
            )
        } catch (error: Exception) {
            Log.w(TAG, "running services not recorded", error)
        }
    }

    private fun currentNotice(): String? =
        services.values.lastOrNull { it.process.isAlive && it.notice != null }?.notice

    private fun serviceLogFile(name: String): File =
        if (name == SERVER) serverLog else File(home, "$name.log")

    fun serverLogTail(tailBytes: Int): String = serviceLogTail(SERVER, tailBytes)

    fun serviceLogTail(name: String, tailBytes: Int): String {
        if (!NAME.matches(name)) return ""
        val log = serviceLogFile(name)
        if (!log.isFile) return ""
        val skip = (log.length() - tailBytes).coerceAtLeast(0)
        log.inputStream().use { input ->
            input.skip(skip)
            return input.readBytes().toString(Charsets.UTF_8)
        }
    }

    // ---- bounded independent CPU work ownership -----------------------------

    private val workLeases by lazy {
        NativeWorkLeaseHost(context,
            terminalWork = { LocalTerminal.get(context).list().filter { it.running }
                .associate { it.id to (it.toMap()["signIn"] == true) } },
            protectTerminal = {
                if (synchronized(recoveryLock) { activityResumed }) {
                    BuiltinServerService.start(context, "Terminal is working on this phone")
                    true
                } else BuiltinServerService.isForegroundRunning
            },
            changed = { synchronized(this@BuiltinLinux) { serviceSetChanged() } })
    }
    val workHeld: Boolean get() = workLeases.held
    internal fun idleWorkBusy(): Boolean? = workLeases.logicalWorkBusy()
    fun setChatWorkLease(name: String, on: Boolean, forMs: Long): Map<String, Boolean> =
        workLeases.chat(name, on, forMs, serverRunning)
    /** A helper chat owns a different lease scope from OpenCode server replies. */
    fun setPhoneAgentChatWorkLease(profile: String, name: String, on: Boolean, forMs: Long): Map<String, Boolean> {
        return synchronized(this) {
            val admitted = profile !in blockedAgentProfiles &&
                services["agent-host.$profile"]?.process?.isAlive == true &&
                BuiltinServerService.isForegroundRunning
            workLeases.agentChat(profile, name, on, forMs, admitted)
        }
    }
    fun holdAwakeForWork(on: Boolean, forMs: Long): Boolean =
        setChatWorkLease("legacy.reply", on, forMs)["held"] == true
    internal fun revokeForegroundWork() {
        // Service destruction invalidates event release under the same short lock as the stdin gate.
        // It does not revoke ordinary BB3 wanted intent or touch already-running unrelated helpers.
        synchronized(recoveryLock) {
            serverEventTicket?.let { ticket ->
                serverEventTicket = null
                serverEventWorkerStarted = false
                if (ticket.generation == supervisionGeneration && ticket.scheduleId == recoveryScheduleId) {
                    scheduledRecovery = false
                    recoveryScheduleId++
                    if (restartWanted && !userStopped) restoreUnavailable("storageUnavailable")
                }
            }
        }
        workLeases.revokeForegroundWork()
    }
    internal fun revokeSetupWork() = workLeases.revokeSetupWork()
    private val setupWorkScopes by lazy {
        SetupWorkScopes({ owner, alive -> workLeases.adopt(owner, WorkLeases.Kind.SETUP, alive) }, workLeases::release)
    }
    internal fun prepareSetupWork(): SetupWorkScopes.Scope = setupWorkScopes.prepare()
    internal fun closeSetupWork(scope: SetupWorkScopes.Scope) = setupWorkScopes.close(scope)
    internal fun <T> withSetupWork(scope: SetupWorkScopes.Scope, work: () -> T): T = setupWorkScopes.run(scope, work)
    internal fun <T> withSetupWork(work: () -> T): T {
        return withSetupWork(prepareSetupWork(), work)
    }
    private fun trackWork(process: Process, kind: WorkLeases.Kind) {
        workLeases.adopt(process, kind) { process.isAlive }
    }

    /**
     * How proot runs the server now, for the Performance details: "seccomp"
     * when proot's seccomp filter is in place in the server (most system
     * calls then run without stopping in proot), "ptrace" when it is not
     * (every system call stops twice in proot: several times slower),
     * "unknown" when the kernel does not say or no server runs.
     *
     * Read from /proc: the server has one seccomp filter more than proot
     * itself (Android's own app filter is on both).
     */
    @Synchronized
    fun performance(): Map<String, Any?> {
        val service = services[SERVER]?.takeIf { it.process.isAlive }
        val root = service?.let { pidOf(it.process) }
        val prootFilters = root?.let { seccompFilters(it) }
        val serverFilters = root?.let { descendants(it) }
            ?.firstNotNullOfOrNull { seccompFilters(it) }
        val mode = when {
            prootFilters == null || serverFilters == null -> "unknown"
            serverFilters > prootFilters -> "seccomp"
            else -> "ptrace"
        }
        return mapOf(
            "serverRunning" to (service != null),
            "prootMode" to mode,
            "prootFilters" to prootFilters,
            "serverFilters" to serverFilters,
            "workHeld" to workHeld,
            "workLeases" to workLeases.diagnostics(),
            "services" to serviceDiagnostics(),
        )
    }

    /** The "Seccomp_filters:" count of [pid] (Linux 5.9+), or null. */
    private fun seccompFilters(pid: Int): Int? = try {
        File("/proc/$pid/status").readLines()
            .firstOrNull { it.startsWith("Seccomp_filters:") }
            ?.substringAfter(':')?.trim()?.toIntOrNull()
    } catch (_: Exception) {
        null
    }

    // ---- install state -----------------------------------------------------

    @Volatile var phase: String = if (ready.isFile) "ready" else "idle"
        private set

    @Volatile var message: String? = null
        private set

    /** Starts [install] on its own thread; [phase] says how it went. */
    @Synchronized
    fun installInBackground() {
        if (phase == "installing") return
        if (installed) {
            phase = "ready"
            return
        }
        phase = "installing"
        message = null
        Thread {
            try {
                install { step -> message = step }
                phase = "ready"
                message = null
            } catch (error: Throwable) {
                Log.e(TAG, "install failed", error)
                phase = "failed"
                message = error.message ?: error.javaClass.simpleName
            }
        }.start()
    }

    @Synchronized
    fun uninstall(alsoDeleteProjects: Boolean = false, confirmationName: String? = null) {
        require(!alsoDeleteProjects || confirmationName == "OpenCode") {
            "Project deletion needs the typed app name"
        }
        check(!installingRuntime && phase != "installing" && !SetupRunner.get(context).running) {
            "Finish or cancel setup before removing the runtime"
        }
        reclaimDeadInstaller()
        check(installerProcess == null) { "Finish or cancel setup before removing the runtime" }
        recoverColdComponentUpdates()
        // Every service first (OpenCode, AI Team with its store and agents):
        // deleting files under a running program leaves it spinning on
        // nothing.
        stopAllServices()
        LocalTerminal.get(context).let { terminals ->
            terminals.list().forEach {
                it.stop()
                check(!it.running) { "A terminal is still stopping" }
                terminals.remove(it.id)
            }
        }
        processes.filter { it.isAlive }.forEach { stopTree(it) }
        check(processes.none { it.isAlive }) { "A runtime process is still stopping" }
        processes.clear()
        check(sameUidInventory().all { it.pid in registeredRuntimePids() }) {
            "Another phone task is still running. Close it and try again."
        }
        // Stop removed the active copy; uninstall also sweeps inactive profile copies.
        phoneEngine.eraseAllCredentialCopies()
        // Migration must succeed before any runtime data is removed.
        projectStorage.removeRuntime(alsoDeleteProjects)
        measured = null
        phase = "idle"
        message = null
    }

    /** Fresh, no-follow logical sizes for the two removal choices. */
    @Synchronized
    fun projectStorage(): Map<String, Long> {
        check(!installingRuntime) { "Wait for runtime installation to finish" }
        val sizes = projectStorage.measure()
        return mapOf(
            "runtimeBytes" to sizes.runtimeBytes,
            "projectsBytes" to sizes.projectsBytes,
            "measuredAtMilliseconds" to System.currentTimeMillis(),
        )
    }

    /**
     * Disk used by Ubuntu and what is installed in it. Measuring walks every
     * file in Ubuntu, and the app reads status every few seconds, so it is
     * measured at most every ten minutes, at background priority, and not
     * while a reply runs (the walk would compete with it for the disk).
     */
    @Volatile private var measured: Pair<Long, Long>? = null

    fun bytesUsed(): Long? {
        val now = System.currentTimeMillis()
        val last = measured
        val due = last == null || now - last.first > MEASURE_EVERY_MS
        if (due && (last == null || !workHeld)) {
            measured = now to (last?.second ?: -1L)
            Thread {
                AndroidProcess.setThreadPriority(AndroidProcess.THREAD_PRIORITY_BACKGROUND)
                measured = System.currentTimeMillis() to sizeOf(rootfs)
            }.start()
        }
        return measured?.second?.takeIf { it >= 0 }
    }

    private fun sizeOf(file: File): Long {
        // lstat, not the link target: Ubuntu's links point at absolute paths
        // that resolve outside the tree from here.
        val stat = try {
            Os.lstat(file.absolutePath)
        } catch (_: Exception) {
            return 0
        }
        if (OsConstants.S_ISLNK(stat.st_mode)) return 0
        if (!file.isDirectory) return stat.st_size
        return file.listFiles()?.sumOf { sizeOf(it) } ?: 0
    }

    /**
     * Fetches [image] into [target], continuing a partial [target] with an
     * HTTP Range request. The server answers 206 (append), 200 (it ignores
     * ranges: start over) or 416 (the file is already whole: just check it).
     * The checksum covers the whole file, so a partial one is hashed first.
     */
    private fun download(image: Image, target: File, progress: InstallProgress) {
        val digest = MessageDigest.getInstance("SHA-256")
        var have = if (target.isFile) target.length() else 0L
        var url = URL(image.url)
        var connection: HttpURLConnection
        var redirects = 0
        var code: Int
        while (true) {
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            if (have > 0) connection.setRequestProperty("Range", "bytes=$have-")
            code = connection.responseCode
            if (code in 300..399 && redirects < 5) {
                url = URL(url, connection.getHeaderField("Location"))
                redirects++
                connection.disconnect()
                continue
            }
            if (code != 200 && code != 206 && !(code == 416 && have > 0)) {
                error("download failed: HTTP $code from ${url.host}")
            }
            break
        }
        val total: Long = when (code) {
            206 -> connection.getHeaderField("Content-Range")
                ?.substringAfterLast('/')?.toLongOrNull() ?: -1L
            416 -> have
            else -> connection.contentLengthLong
        }
        if (code == 200) have = 0
        if (have > 0) {
            progress.log("Resuming the download at $have bytes")
            target.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }
        progress.bytes(have, total.coerceAtLeast(0))
        if (code != 416) {
            connection.inputStream.use { input ->
                FileOutputStream(target, have > 0).use { out ->
                    val buffer = ByteArray(1 shl 16)
                    var done = have
                    var reported = 0L
                    while (true) {
                        if (progress.cancelled) throw Cancelled()
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (now - reported >= 250) {
                            reported = now
                            progress.bytes(done, total.coerceAtLeast(0))
                        }
                    }
                    progress.bytes(done, total.coerceAtLeast(done))
                }
            }
        }
        connection.disconnect()
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != image.sha256) {
            target.delete()
            error("checksum mismatch for ${image.url}")
        }
    }

    private fun unpack(archive: File, into: File, progress: InstallProgress) {
        // Hard links are made after everything else is in place: their targets
        // may come later in the archive.
        val hardLinks = mutableListOf<Pair<File, File>>()
        // Progress is the compressed bytes read so far against the archive's
        // size: the only total known before the end.
        val size = archive.length()
        var reported = 0L
        val counting = object : java.io.FilterInputStream(archive.inputStream()) {
            var count = 0L

            override fun read(): Int = super.read().also { if (it >= 0) count++ }

            override fun read(b: ByteArray, off: Int, len: Int): Int =
                super.read(b, off, len).also { if (it > 0) count += it }
        }
        TarArchiveInputStream(GZIPInputStream(BufferedInputStream(counting))).use { tar ->
            while (true) {
                if (progress.cancelled) throw Cancelled()
                val now = System.currentTimeMillis()
                if (now - reported >= 250) {
                    reported = now
                    progress.bytes(counting.count, size)
                }
                val entry: TarArchiveEntry = tar.nextEntry ?: break
                val name = entry.name.removePrefix("./").trimEnd('/')
                if (name.isEmpty() || name.split('/').contains("..")) continue
                val target = File(into, name)
                when {
                    entry.isDirectory -> {
                        target.mkdirs()
                        chmod(target, entry.mode or 0b111_000_000)
                    }
                    entry.isSymbolicLink -> {
                        target.parentFile?.mkdirs()
                        target.delete()
                        Os.symlink(entry.linkName, target.absolutePath)
                    }
                    entry.isLink -> hardLinks += target to File(into, entry.linkName.removePrefix("./"))
                    entry.isFile -> {
                        target.parentFile?.mkdirs()
                        target.delete()
                        FileOutputStream(target).use { tar.copyTo(it) }
                        chmod(target, entry.mode or 0b110_000_000)
                    }
                }
            }
        }
        for ((link, source) in hardLinks) {
            link.parentFile?.mkdirs()
            link.delete()
            source.copyTo(link)
            chmod(link, Os.stat(source.absolutePath).st_mode and 0xFFF)
        }
    }

    /**
     * Gives the app's Android groups (inet, everybody, its cache group…) a
     * name in Ubuntu's /etc/group. A login shell runs `groups`, which
     * otherwise prints "cannot find name for group ID 3003" once per group;
     * proot-distro adds the same lines. The group ids are per install, so
     * this is checked each time a terminal starts and costs one read.
     */
    fun nameAndroidGroups() {
        val file = File(rootfs, "etc/group")
        if (!file.isFile) return
        val gids = try {
            File("/proc/self/status").readLines()
                .firstOrNull { it.startsWith("Groups:") }
                ?.substringAfter(':')?.trim()?.split(Regex("\\s+"))
                ?.mapNotNull { it.toIntOrNull() }
                .orEmpty()
        } catch (_: Exception) {
            return
        }
        val existing = file.readLines().mapNotNull { it.split(':').getOrNull(2)?.toIntOrNull() }.toSet()
        val missing = gids.filter { it !in existing }.distinct()
        if (missing.isEmpty()) return
        file.appendText(missing.joinToString("") { "aid_$it:x:$it:\n" })
    }

    /** What proot-distro does after unpacking, trimmed to what Ubuntu needs. */
    private fun configure() {
        componentUpdatesRecovered = false
        check(installerProcess == null && !installerPreferences.contains("ticket")) { componentUpdateFailure }
        val generation = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        check(installerPreferences.edit().putString("rootfsGeneration", generation).commit()) { componentUpdateFailure }
        val etc = File(rootfs, "etc")
        File(etc, "resolv.conf").apply {
            delete()
            writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        }
        File(etc, "hosts").writeText(
            "127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n",
        )
        // apt drops to its own user to download; proot's fake root cannot
        // switch users, so apt downloads as root.
        File(etc, "apt/apt.conf.d/01-oc-sandbox").apply {
            parentFile?.mkdirs()
            writeText("APT::Sandbox::User \"root\";\n")
        }
        File(rootfs, "tmp").apply { mkdirs(); chmod(this, 0b111_111_111 or 0x200) }
        File(rootfs, "root").mkdirs()
    }

    private fun chmod(file: File, mode: Int) {
        try {
            Os.chmod(file.absolutePath, mode and 0xFFF)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val RUN_ADMISSION_WINDOW_MS = 10_000L
        private const val PROCESS_POLL_MS = 50L
        private const val OWNED_DRAIN_TIMEOUT_MS = 5_000L
        private const val OWNED_DRAIN_KILL_WINDOW_MS = 2_000L
        private const val OWNERSHIP_POLL_MS = 100L
        private const val MIN_RESTART_DELAY_MS = 1_000L
        private const val MAX_RESTART_DELAY_MS = 60_000L
        private const val MAX_RECEIPT_CHARS = 131_072
        private const val MAX_ENV_BYTES = 131_072
        private const val SERVER_DEFAULT_PORT = 4097

        internal fun registeredAppProcessIds(records: List<ActivityManager.RunningAppProcessInfo>?,
            packageName: String, uid: Int): Set<Int> = records.orEmpty().filter {
                it.pid > 0 && it.uid == uid && it.processName == "$packageName:local_pdf" &&
                    it.pkgList?.contains(packageName) == true
            }.map { it.pid }.toSet()

        internal fun <T> commitProtectionAfterReceipt(issue: () -> T, protect: () -> Unit): T {
            val receipt = issue()
            protect()
            return receipt
        }

        internal fun stopEveryService(names: List<String>, stop: (String) -> Unit) {
            var failure: Exception? = null
            for (name in names) try { stop(name) } catch (error: Exception) {
                if (failure == null) failure = error
            }
            failure?.let { throw it }
        }

        /**
         * Stops a proot [process] together with everything it started.
         *
         * Neither half works alone: proot ignores SIGTERM, and killing proot
         * with SIGKILL detaches its tracees, which then run on as orphans
         * (seen on the emulator: an OpenCode server kept port 4097 after its
         * proot was killed, so the next start failed "port in use"). So the
         * programs inside get SIGTERM first, to finish cleanly; whatever is
         * left after [graceMs] gets SIGKILL, proot last.
         */
        fun stopTree(process: Process, graceMs: Long = 3000) {
            val root = pidOf(process)
            if (root == null) {
                process.destroy()
                if (!process.waitFor(graceMs, TimeUnit.MILLISECONDS)) process.destroyForcibly()
                return
            }
            for (pid in descendants(root)) signal(pid, OsConstants.SIGTERM)
            if (process.waitFor(graceMs, TimeUnit.MILLISECONDS)) {
                // proot left when its command did; a straggler may remain.
                return
            }
            for (pid in descendants(root)) signal(pid, OsConstants.SIGKILL)
            process.destroyForcibly()
            process.waitFor(graceMs, TimeUnit.MILLISECONDS)
        }

        /**
         * [stopTree] for a process the app knows only by [root] pid (a
         * terminal session started through a PTY). [exited] waits up to the
         * given milliseconds for it to end and says whether it did.
         *
         * An interactive shell ignores SIGTERM, so the programs inside get
         * SIGHUP too, as when a terminal closes; SIGKILL follows after
         * [graceMs], proot last.
         */
        fun stopPidTree(root: Int, exited: (Long) -> Boolean, graceMs: Long = 2000) {
            for (pid in descendants(root)) {
                signal(pid, OsConstants.SIGHUP)
                signal(pid, OsConstants.SIGTERM)
            }
            if (exited(graceMs)) return
            for (pid in descendants(root)) signal(pid, OsConstants.SIGKILL)
            signal(root, OsConstants.SIGKILL)
            exited(graceMs)
        }

        /** How many processes run as this app's user now, the app itself included. */
        fun appProcessCount(): Int {
            val uid = android.os.Process.myUid()
            return File("/proc").listFiles()?.count { dir ->
                dir.name.toIntOrNull() != null &&
                    try {
                        Os.stat(dir.absolutePath).st_uid == uid
                    } catch (_: Exception) {
                        false
                    }
            } ?: 0
        }

        private fun signal(pid: Int, signal: Int) {
            try {
                Os.kill(pid, signal)
            } catch (_: Exception) {
                // Already gone.
            }
        }

        /** Android's ProcessImpl keeps the pid in a private field; no public API has it. */
        private fun pidOf(process: Process): Int? {
            return try {
                process.javaClass.getDeclaredField("pid").run {
                    isAccessible = true
                    getInt(process)
                }
            } catch (_: Throwable) {
                null
            }
        }

        /** Every process below [root], read from /proc (same app, same user). */
        private fun descendants(root: Int): List<Int> {
            val parents = HashMap<Int, Int>()
            File("/proc").listFiles()?.forEach { dir ->
                val pid = dir.name.toIntOrNull() ?: return@forEach
                val stat = try {
                    File(dir, "stat").readText()
                } catch (_: Exception) {
                    return@forEach
                }
                // "pid (comm) state ppid …"; comm may hold spaces and parens.
                val ppid = stat.substringAfterLast(')').trim().split(' ').getOrNull(1)?.toIntOrNull()
                if (ppid != null) parents[pid] = ppid
            }
            val found = mutableListOf<Int>()
            var frontier = listOf(root)
            while (frontier.isNotEmpty()) {
                val next = parents.filter { it.value in frontier }.keys.toList()
                found += next
                frontier = next
            }
            return found
        }

        /** The /proc stand-ins of [fakeProcBinds], by file name. */
        private val FAKE_PROC: List<Pair<String, () -> String>> = listOf(
            "stat" to {
                val cpus = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                buildString {
                    append("cpu  ${cpus * 100} 0 ${cpus * 100} ${cpus * 10000} 0 0 0 0 0 0\n")
                    for (i in 0 until cpus) append("cpu$i 100 0 100 10000 0 0 0 0 0 0\n")
                    append("intr 0\nctxt 0\nbtime ${System.currentTimeMillis() / 1000 - 7200}\n")
                    append("processes 1\nprocs_running 1\nprocs_blocked 0\n")
                    append("softirq 0 0 0 0 0 0 0 0 0 0 0\n")
                }
            },
            "loadavg" to { "0.12 0.07 0.02 1/100 100\n" },
            "uptime" to { "7200.00 7000.00\n" },
            "version" to {
                "Linux version ${System.getProperty("os.version") ?: "unknown"} (android) #1 SMP PREEMPT\n"
            },
            "vmstat" to {
                listOf(
                    "nr_free_pages", "nr_inactive_anon", "nr_active_anon", "nr_inactive_file",
                    "nr_active_file", "nr_dirty", "nr_writeback", "pgpgin", "pgpgout",
                    "pswpin", "pswpout", "pgfault", "pgmajfault",
                ).joinToString("") { "$it 0\n" }
            },
        )

        const val TAG = "OcLinux"

        /** The agent programs a sign-in terminal may run (the agent catalog's). */
        val SIGN_IN_PROGRAMS = setOf("claude", "codex", "gemini", "qwen", "goose", "omp", "fx")

        /** The longest one wake-lock hold for a reply lasts before renewal. */
        const val MAX_WORK_HOLD_MS = 15 * 60 * 1000L

        /** How often the Ubuntu tree's size is measured at most. */
        private const val MEASURE_EVERY_MS = 10 * 60 * 1000L
        private const val OUTPUT_CAP = 64 * 1024

        /** The OpenCode server's service name. */
        const val SERVER = "server"

        /** Service names double as log file names. */
        private val NAME = Regex("[a-z][a-z0-9-]{0,31}")

        @Volatile private var instance: BuiltinLinux? = null

        /** One Ubuntu per app: it owns the server process and the install state. */
        fun get(context: Context): BuiltinLinux =
            instance ?: synchronized(this) {
                instance ?: BuiltinLinux(context.applicationContext).also { instance = it }
            }

        // Ubuntu Base 24.04.5 (noble), from Canonical's SHA256SUMS.
        /** What the setup checklist shows for the Linux base once installed. */
        const val VERSION = "24.04.5"
        const val PHONE_ENGINE = "phone-engine"

        private const val BASE =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/"
        val arm64 = Image(
            BASE + "ubuntu-base-24.04.5-base-arm64.tar.gz",
            "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2",
        )
        val amd64 = Image(
            BASE + "ubuntu-base-24.04.5-base-amd64.tar.gz",
            "e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86",
        )

        fun imageForDevice(): Image =
            if (Build.SUPPORTED_ABIS.firstOrNull() == "x86_64") amd64 else arm64
    }
}

/**
 * Shared storage inside Ubuntu. Before AI Team is on, the whole /storage and
 * /sdcard are visible (Android still gates what the app may read). Once it is
 * on, one confined launcher covers every process, so shared storage is limited
 * to the exact folders the person opened as projects: each bound at its own
 * path and, on the primary volume, its /sdcard alias, and nothing wider.
 * Mirrors lib/domain/shared_storage_path.dart (sharedProjectRoot,
 * sharedStorageProotBinds): keep both in step.
 */
object SharedStorageBinds {
    private const val PRIMARY = "/storage/emulated/0"
    private val aliases = listOf(
        Regex("^/sdcard(?=/|$)"),
        Regex("^/mnt/sdcard(?=/|$)"),
        Regex("^/storage/self/primary(?=/|$)"),
        Regex("^/mnt/user/\\d+/(?:emulated/(\\d+)|primary)(?=/|$)"),
    )
    private val volume = Regex("^/storage/(?:emulated/\\d+|[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})(/.+)$")

    fun canonicalRoot(path: String): String? {
        val parts = ArrayList<String>()
        for (part in path.trim().replace('\\', '/').split('/')) {
            if (part.isEmpty() || part == ".") continue
            if (part == "..") { if (parts.isNotEmpty()) parts.removeAt(parts.size - 1); continue }
            parts.add(part)
        }
        var value = "/" + parts.joinToString("/")
        for (alias in aliases) {
            val match = alias.find(value) ?: continue
            val user = match.groupValues[1].ifEmpty { "0" }
            value = "/storage/emulated/$user" + value.substring(match.range.last + 1)
            break
        }
        return if (volume.matches(value)) value else null
    }

    fun binds(roots: List<String>, confined: Boolean): List<String> {
        if (!confined) {
            val out = mutableListOf<String>()
            if (File("/storage").isDirectory) out += "--bind=/storage"
            if (File(PRIMARY).isDirectory) out += "--bind=$PRIMARY:/sdcard"
            return out
        }
        val out = mutableListOf<String>()
        for (root in roots.mapNotNull { canonicalRoot(it) }.distinct()) {
            if (!File(root).isDirectory) continue
            out += "--bind=$root"
            if (root.startsWith("$PRIMARY/")) out += "--bind=$root:/sdcard${root.substring(PRIMARY.length)}"
        }
        return out
    }
}
