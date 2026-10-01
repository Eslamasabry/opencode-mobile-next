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
    fun run(script: String, timeoutSeconds: Long = 600): Result {
        val process = start(script, null)
        process.outputStream.close()
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().forEachLine { line ->
                Log.i(TAG, line)
                synchronized(output) {
                    output.appendLine(line)
                    // Keep the tail: that is where a failing command says why.
                    if (output.length > OUTPUT_CAP * 2) {
                        output.delete(0, output.length - OUTPUT_CAP)
                    }
                }
            }
        }.apply { start() }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) stopTree(process)
        reader.join(2000)
        val text = synchronized(output) { output.takeLast(OUTPUT_CAP).toString() }
        return Result(if (finished) process.exitValue() else -1, text)
    }

    /**
     * Starts [script] inside Ubuntu with its output appended to [log], or piped
     * back when [log] is null. proot is given --kill-on-exit, so stopping it
     * stops everything the script started.
     */
    @Synchronized
    fun start(script: String, log: File?): Process {
        check(installed) { "Ubuntu is not installed in the app yet" }
        processes.removeAll { !it.isAlive }
        return ProcessBuilder(prootCommand(listOf("/bin/sh", "-c", script)))
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

    /** proot's own path: the program a terminal session (LocalTerminal.kt) starts. */
    val prootPath: String get() = "$nativeDir/libproot.so"

    /**
     * The proot command line that runs [program] inside Ubuntu as root, with
     * a clean environment. [start] and the local terminal (LocalTerminal.kt)
     * both use it, so a shell sees exactly what the app's scripts see.
     */
    @Synchronized
    fun prootCommand(program: List<String>): List<String> {
        check(!installingRuntime) { "Runtime installation is still running" }
        projectStorage.prepare()
        val command = listOf(
            prootPath,
            "--root-id",
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
            "--bind=${projectStorage.projects.absolutePath}:/root/projects",
        ) + sharedStorageBinds() + fakeProcBinds + (if (protectionTier() == "proot") {
            // PRoot exposes host proc by default. Hide native app/daemon entries
            // rather than depending on Linux cmdline permissions alone.
            val mask = File(home.canonicalFile, "proc/phone-engine-hidden").apply { mkdirs() }
            check(mask.isDirectory && mask.canonicalFile == mask.absoluteFile && mask.listFiles()?.isEmpty() == true)
            (prootMaskedPids + AndroidProcess.myPid()).sorted().flatMap {
                listOf("--bind=${mask.absolutePath}:/proc/$it")
            }
        } else emptyList()) + listOf(
            "--cwd=/root",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "TMPDIR=/tmp",
        ) + program
        return if (prootIsConfined && protectionTier() != "proot") protectedCommand(command) else command
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
        if (services[PHONE_ENGINE]?.process?.isAlive != true) services.remove(PHONE_ENGINE)
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
            services[PHONE_ENGINE] = Service(child, port, notice)
            recordRunning()
            try { BuiltinServerService.start(context, currentNotice()) } catch (error: Exception) {
                phoneEngine.stop(profile)
                services.remove(PHONE_ENGINE)
                serviceSetChanged()
                throw PhoneEngineNative.Failure("foreground_unavailable")
            }
            Thread {
                child.waitFor()
                synchronized(this) {
                    if (services[PHONE_ENGINE]?.process === child) {
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
        if (services[PHONE_ENGINE]?.process?.isAlive != true) services.remove(PHONE_ENGINE)
        serviceSetChanged()
        return phoneEngineStatus(profile)
    }

    @Synchronized
    fun deletePhoneEngine(profile: String) {
        phoneEngine.delete(profile)
        prootMaskedPids.clear()
        if (services[PHONE_ENGINE]?.process?.isAlive != true) services.remove(PHONE_ENGINE)
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
    }

    private val services = LinkedHashMap<String, Service>()

    // Device-wide private state: the runtime is shared by local profiles.
    // Never infer intent from an old crash report or a missing preference.
    private val recoveryPreferences =
        context.getSharedPreferences("builtin_server_recovery", Context.MODE_PRIVATE)
    private val recoveryLock = Any()
    private var activityResumed = false
    private var recoveryGeneration = 0L
    private var wantedRevision = 0L
    private var restartWanted = recoveryPreferences.getBoolean("wanted", false)
    private data class RecoveryAttempt(val process: Process, val generation: Long)
    private var recoveryAttempt: RecoveryAttempt? = null
    private var confirmedRecoveryAttempt: RecoveryAttempt? = null

    val serverRestartWanted: Boolean get() = synchronized(recoveryLock) { restartWanted }
    val serverRecoveryGeneration: Long get() = synchronized(recoveryLock) { recoveryGeneration }

    /** Called on the main thread: invalidation never waits for runtime I/O. */
    fun setActivityResumed(resumed: Boolean) {
        synchronized(recoveryLock) { activityResumed = resumed }
        if (!resumed) cancelServerRecovery()
    }

    /** Revokes admission immediately, before an asynchronous explicit stop. */
    fun requestServerStop() {
        synchronized(recoveryLock) {
            restartWanted = false
            wantedRevision++
        }
        cancelServerRecovery()
    }

    /** Cancels only an unconfirmed automatic process, never a manual replacement. */
    fun cancelServerRecovery() {
        val attempt = synchronized(recoveryLock) {
            recoveryGeneration++
            confirmedRecoveryAttempt = null
            recoveryAttempt.also { recoveryAttempt = null }
        }
        if (attempt != null) Thread {
            synchronized(this) {
                if (services[SERVER]?.process === attempt.process) {
                    removeService(SERVER)
                }
            }
        }.start()
    }

    fun confirmServerRecovery(expectedGeneration: Long) {
        synchronized(recoveryLock) {
            // Idempotence lets the controller retry durable act recording after
            // the starter already confirmed this exact automatic process.
            val attempt = recoveryAttempt ?: confirmedRecoveryAttempt
            check(activityResumed && restartWanted &&
                recoveryGeneration == expectedGeneration &&
                attempt != null && attempt.generation == expectedGeneration && attempt.process.isAlive) {
                "The phone server could not restart."
            }
            confirmedRecoveryAttempt = attempt
            recoveryAttempt = null
        }
    }

    private fun setServerWanted(wanted: Boolean) {
        val revision = synchronized(recoveryLock) {
            restartWanted = false
            wantedRevision++
            recoveryGeneration++
            recoveryAttempt = null
            confirmedRecoveryAttempt = null
            wantedRevision
        }
        // Persist before launch, without making main-thread invalidation wait
        // for storage. Service mutations are serialized by the runtime lock.
        check(recoveryPreferences.edit().putBoolean("wanted", wanted).commit()) {
            "The phone server setting could not be saved."
        }
        synchronized(recoveryLock) {
            if (wantedRevision == revision) restartWanted = wanted
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
        launchService(SERVER, script, port, null, expectedGeneration)
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

    fun startServer(script: String, port: Int) = startService(SERVER, script, port, null)

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
        check(installed) { "Ubuntu is not installed in the app yet" }
        require(NAME.matches(name)) { "Invalid service name: $name" }
        require(name != PHONE_ENGINE) { "The native phone engine has a dedicated launcher." }
        if (name == SERVER) stoppedPhoneServer = null
        // Joining a setup rollback must not cut off the already restored server.
        if (name == SERVER && services[SERVER]?.let {
                it.process.isAlive && it.port == port && it.script == script
            } == true) return
        if (name == SERVER) setServerWanted(true)
        launchService(name, script, port, notice)
    }

    private fun launchService(
        name: String,
        script: String,
        port: Int?,
        notice: String?,
        expectedGeneration: Long? = null,
    ): Process {
        removeService(name)
        val log = serviceLogFile(name)
        // One log per run; the previous one stays for a look after a crash.
        if (log.isFile) log.renameTo(File(home, "$name.previous.log"))
        val process = if (expectedGeneration == null) {
            start(script, log)
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
        process.outputStream.close()
        services[name] = Service(process, port, notice, script)
        recordRunning()
        try {
            BuiltinServerService.start(context, currentNotice())
        } catch (error: Exception) {
            // A refused foreground service must not leave an unsupervised child.
            removeService(name)
            throw error
        }
        // A service that exits on its own (a crash, a bad config) takes its
        // share of the "running" notification with it.
        Thread {
            process.waitFor()
            synchronized(this) {
                if (services[name]?.process === process) {
                    services.remove(name)
                    serviceSetChanged()
                }
            }
        }.start()
        return process
    }

    @Synchronized
    fun stopService(name: String) {
        try {
            if (name == SERVER) setServerWanted(false)
        } finally {
            removeService(name)
        }
    }

    private fun removeService(name: String) {
        var failure: Exception? = null
        try {
            if (name == PHONE_ENGINE) try { phoneEngine.stopTracked() }
                catch (error: Exception) { failure = error }
            val service = services[name]
            if (service != null) {
                stopTree(service.process)
                if (service.process.isAlive) throw PhoneEngineNative.Failure("engine_stop_failed")
                services.remove(name)
            }
        } finally { serviceSetChanged() }
        failure?.let { throw it }
    }

    /** Stops every service; Stop in the notification and uninstall use it. */
    @Synchronized
    fun stopAllServices() {
        // Clear even if a crash already removed the server from the map.
        var failure: Exception? = null
        try { setServerWanted(false) } catch (error: Exception) { failure = error }
        try { stopEveryService(services.keys.toList()) { removeService(it) } }
        catch (error: Exception) { if (failure == null) failure = error }
        failure?.let { throw it }
    }

    /** Keeps the foreground service exactly as long as any service runs. */
    private fun serviceSetChanged() {
        recordRunning()
        // A reply cannot run on a server that is gone: never keep the phone
        // awake for it.
        if (!serverRunning) releaseWork()
        if (services.values.none { it.process.isAlive }) {
            BuiltinServerService.stop(context)
            return
        }
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

    // ---- a reply in flight ---------------------------------------------------

    /**
     * Keeps the CPU running while the in-app server works on a reply, as
     * Termux's wake lock does for a server there. The foreground service
     * keeps the process alive, but without a wake lock the phone still
     * sleeps with the screen off, and a reply (or a tool it runs) stalls
     * until something wakes it.
     *
     * Never unbounded: every hold ends by itself after at most
     * [MAX_WORK_HOLD_MS]; the app renews it while a reply is still running
     * and releases it as soon as none is. It is held only while the server
     * runs, and a server that stops releases it (serviceSetChanged).
     */
    private val workLock: PowerManager.WakeLock? by lazy {
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenCode:reply")
            ?.apply { setReferenceCounted(false) }
    }

    /** Whether the phone is kept awake for a reply now. */
    val workHeld: Boolean get() = synchronized(workLockGuard) { workLock?.isHeld == true }

    private val workLockGuard = Any()

    /**
     * [on]: hold (or renew) the wake lock for [forMs], capped; off releases
     * it. Returns whether it is held afterwards.
     */
    fun holdAwakeForWork(on: Boolean, forMs: Long): Boolean {
        if (!on || !serverRunning) {
            releaseWork()
            return false
        }
        val lock = workLock ?: return false
        synchronized(workLockGuard) {
            // Not reference-counted: acquiring again only moves the timeout.
            lock.acquire(forMs.coerceIn(1_000L, MAX_WORK_HOLD_MS))
        }
        return true
    }

    private fun releaseWork() {
        synchronized(workLockGuard) {
            val lock = workLock ?: return
            if (lock.isHeld) {
                try {
                    lock.release()
                } catch (_: RuntimeException) {
                    // Its timeout released it a moment ago.
                }
            }
        }
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
