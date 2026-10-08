package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Fixed program-update fixture only. The host owns normal startup and person-state restoration. */
internal class BuiltinComponentUpdateAcceptance(
    private val instrumentation: Instrumentation,
    private val arguments: Bundle,
) {
    class Refused(val safeCode: String) : Exception()
    private val context get() = instrumentation.targetContext
    private val evidence get() = File(context.filesDir, "bb9-runtime-qa.json")
    private val externalExport get() = File(context.filesDir, "bb9-external-qa.json")
    private val producerEvidence get() = File(context.filesDir, "bb9-producer-qa.json")
    private val writer get() = context.getSharedPreferences("builtin_component_writer", 0)
    private val backupName = "opencode2.oc-bb9-original-good"
    private val sentinelName = ".oc-bb9-qa-ready"
    private var externalStage = "admission"

    fun execute() {
        try {
            requireSafe(BuildConfig.BUILTIN_RUNTIME_QA, "bb9_qa_build_required")
            val linux = BuiltinLinux.get(context)
            requireSafe(linux.installed, "bb9_ubuntu_unavailable")
            when (arguments.getString("step")) {
                "bb9Preflight" -> preflight(linux)
                "bb9PrepareInterrupted" -> prepare(linux)
                "bb9PrepareExternal" -> prepare(linux, external = true)
                "bb9ExternalCommit" -> commitExternal(linux)
                "bb9ExternalArmed" -> armExternal(linux)
                "bb9Verify" -> verify(linux)
                "bb9Cleanup" -> cleanup(linux)
                "bb9SerializedChecks" -> concurrentChecks(linux, false)
                "bb9LivePeers" -> concurrentChecks(linux, true)
                "bb9FilesystemCases" -> BuiltinComponentFilesystemAcceptance(instrumentation).execute()
                "bb9FilesystemCleanup" -> BuiltinComponentFilesystemAcceptance(instrumentation).execute(cleanupOnly = true)
                else -> throw Refused("bb9_step_invalid")
            }
        } catch (error: Refused) { throw error }
        catch (error: Throwable) {
            if (arguments.getString("step") == "bb9ExternalCommit") {
                val cause = if (error is java.lang.reflect.InvocationTargetException) error.targetException else error
                val kind = when (cause) {
                    is NoSuchMethodException -> "method_unavailable"
                    is java.io.FileNotFoundException -> "file_unavailable"
                    is SecurityException -> "permission_denied"
                    is ErrnoException -> when (cause.errno) {
                        OsConstants.EACCES, OsConstants.EPERM -> "permission_denied"
                        OsConstants.ENOENT -> "file_unavailable"
                        else -> "kernel_unavailable"
                    }
                    is IllegalArgumentException, is IllegalStateException -> "ownership_unknown"
                    else -> "unavailable"
                }
                throw Refused("bb9_external_${externalStage}_$kind")
            }
            throw Refused("bb9_acceptance_unavailable")
        }
    }

    private fun active(linux: BuiltinLinux) = File(linux.rootfs, "opt/opencode2")
    private fun good(linux: BuiltinLinux) = File(linux.rootfs, "opt/opencode2.oc-good")
    private fun pending(linux: BuiltinLinux) = File(linux.rootfs, "opt/opencode2.oc-pending")
    private fun backup(linux: BuiltinLinux) = File(linux.rootfs, "opt/$backupName")
    private fun sentinel(linux: BuiltinLinux) = File(linux.rootfs, "opt/$sentinelName")
    private fun lock(linux: BuiltinLinux) = File(linux.rootfs, "home/oc/.local/share/oc-agents/.lock-claude")
    private fun executable(linux: BuiltinLinux) = File(active(linux), "bin/opencode2")

    private fun stat(file: File) = try { Os.lstat(file.absolutePath) }
        catch (error: ErrnoException) { if (error.errno == OsConstants.ENOENT) null else throw error }
    private fun directory(file: File) {
        val value = stat(file)
        requireSafe(value != null && OsConstants.S_ISDIR(value.st_mode), "bb9_path_unsafe")
    }
    private fun ancestry(linux: BuiltinLinux) {
        for (file in listOf(context.filesDir, linux.home, linux.rootfs, File(linux.rootfs, "opt"),
                File(linux.rootfs, "home"), File(linux.rootfs, "home/oc"), File(linux.rootfs, "home/oc/.local"),
                File(linux.rootfs, "home/oc/.local/share"), File(linux.rootfs, "home/oc/.local/share/oc-agents"))) directory(file)
    }
    private fun hash(file: File): String {
        val before = stat(file)
        requireSafe(before != null && OsConstants.S_ISREG(before.st_mode), "bb9_executable_unsafe")
        val descriptor = Os.open(file.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        val digest = MessageDigest.getInstance("SHA-256")
        java.io.FileInputStream(descriptor).use { stream ->
            val opened = Os.fstat(descriptor)
            requireSafe(opened.st_dev == before!!.st_dev && opened.st_ino == before.st_ino &&
                OsConstants.S_ISREG(opened.st_mode), "bb9_executable_changed")
            val bytes = ByteArray(65536)
            while (true) { val count = stream.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun preflight(linux: BuiltinLinux) {
        ancestry(linux); directory(active(linux)); directory(File(active(linux), "bin"))
        requireSafe(stat(evidence) == null && stat(File(evidence.parentFile, evidence.name + ".tmp")) == null &&
            stat(backup(linux)) == null && stat(sentinel(linux)) == null && stat(producerEvidence) == null,
            "bb9_existing_fixture_refused")
        requireSafe(stat(pending(linux)) == null && stat(File(pending(linux).path + ".new")) == null &&
            stat(File(active(linux).path + ".new")) == null, "bb9_existing_journal_refused")
        requireSafe(stat(lock(linux)) == null, "bb9_existing_lock_refused")
        good(linux).takeIf { stat(it) != null }?.let { directory(it) }
        requireSafe(!writer.contains("ticket"), "bb9_existing_writer_refused")
        hash(executable(linux))
        emit { putBoolean("bb9PreflightPassed", true); putBoolean("bb9OriginalGoodPresent", stat(good(linux)) != null) }
    }

    private fun finishActivities() = instrumentation.runOnMainSync {
        val type = Class.forName("android.app.ActivityThread")
        val thread = type.getDeclaredMethod("currentActivityThread").invoke(null)
        val records = type.getDeclaredField("mActivities").apply { isAccessible = true }.get(thread) as Map<*, *>
        for (record in records.values) if (record != null) {
            val activity = record.javaClass.getDeclaredField("activity").apply { isAccessible = true }.get(record) as? Activity
            if (activity?.packageName == context.packageName) activity.finish()
        }
    }
    private fun inventory(linux: BuiltinLinux): List<RuntimeProcessIdentity> {
        @Suppress("UNCHECKED_CAST")
        return BuiltinLinux::class.java.getDeclaredMethod("sameUidInventory").apply { isAccessible = true }
            .invoke(linux) as List<RuntimeProcessIdentity>
    }
    private fun quiescent(linux: BuiltinLinux): Boolean {
        val readers = BuiltinLinux.registeredAppProcessIds(
            context.getSystemService(ActivityManager::class.java)?.runningAppProcesses,
            context.packageName, Process.myUid()).toSet() + Process.myPid()
        return inventory(linux).all { it.pid in readers }
    }
    private fun stopServices(linux: BuiltinLinux) {
        linux.requestServerStop()
        linux.stopAllServices()
        val until = SystemClock.elapsedRealtime() + 7000
        while (!quiescent(linux) && SystemClock.elapsedRealtime() < until) Thread.sleep(100)
        requireSafe(quiescent(linux), "bb9_writer_quiescence_unknown")
    }
    private fun ticket(): InstallerTicket {
        val raw = writer.getString("ticket", null) ?: throw Refused("bb9_writer_ticket_missing")
        requireSafe(raw.length <= 131072, "bb9_writer_ticket_invalid")
        fun value(item: Any?): Any? = when (item) {
            JSONObject.NULL -> null
            is JSONObject -> item.keys().asSequence().associateWith { value(item.get(it)) }
            is JSONArray -> (0 until item.length()).map { value(item.get(it)) }
            else -> item
        }
        return InstallerTicket.read(value(JSONObject(raw)) as Map<*, *>)
    }

    /** Uses only run/start and the pre-existing installer stop/finish ABI, including on the red APK. */
    private fun concurrentChecks(linux: BuiltinLinux, withPeer: Boolean) {
        val temporary = File(linux.rootfs, "tmp")
        directory(temporary)
        val ready = File(temporary, ".oc-bb9-live-ready")
        val done = File(temporary, ".oc-bb9-live-done")
        val second = File(temporary, ".oc-bb9-live-second")
        requireSafe(listOf(ready, done, second).all { stat(it) == null }, "bb9_existing_live_fixture_refused")
        requireSafe(!writer.contains("ticket"), "bb9_existing_writer_refused")
        finishActivities()
        val activityDeadline = SystemClock.elapsedRealtime() + 5000
        while (!activityAbsent() && SystemClock.elapsedRealtime() < activityDeadline) Thread.sleep(100)
        requireSafe(activityAbsent(), "bb9_activity_present")
        stopServices(linux)
        var ownCheck: java.lang.Process? = null
        var launchId: String? = null
        var peer: java.lang.Process? = null
        var peerRoot: RuntimeProcessIdentity? = null
        var peerTree = emptyList<RuntimeProcessIdentity>()
        val firstElapsed = AtomicLong()
        val secondElapsed = AtomicLong()
        fun checked(script: String, elapsed: AtomicLong) = FutureTask {
            val began = SystemClock.elapsedRealtime()
            try { linux.run(script, timeoutSeconds = 10) }
            finally { elapsed.set(SystemClock.elapsedRealtime() - began) }
        }
        val first = checked("""
            set -eu
            [ ! -e /tmp/.oc-bb9-live-ready ] && [ ! -L /tmp/.oc-bb9-live-ready ]
            printf '%s' "${'$'}OC_RUNTIME_OWNER" > /tmp/.oc-bb9-live-ready
            /bin/sleep 2
            printf '%s' "${'$'}OC_RUNTIME_OWNER" > /tmp/.oc-bb9-live-done
            printf 'bb9-live-first\n'
        """.trimIndent(), firstElapsed)
        val next = checked("""
            set -eu
            [ -f /tmp/.oc-bb9-live-done ] && [ ! -L /tmp/.oc-bb9-live-done ]
            [ ! -e /tmp/.oc-bb9-live-second ] && [ ! -L /tmp/.oc-bb9-live-second ]
            cat /tmp/.oc-bb9-live-done > /tmp/.oc-bb9-live-second
            printf 'bb9-live-second\n'
        """.trimIndent(), secondElapsed)
        val firstThread = Thread(first, "bb9-first-check")
        val nextThread = Thread(next, "bb9-second-check")
        var failure: Refused? = null
        var serialized = false
        var survived = false
        var cleaned = false
        try {
            firstThread.start()
            val readyDeadline = SystemClock.elapsedRealtime() + 7000
            while (stat(ready)?.st_size != 64L && !first.isDone && SystemClock.elapsedRealtime() < readyDeadline) Thread.sleep(25)
            requireSafe(stat(ready)?.st_size == 64L && !first.isDone, "bb9_first_check_not_active")
            synchronized(linux) {
                val current = ticket()
                launchId = current.id
                requireSafe(readNoFollow(ready, 64) == current.id && current.operation == InstallerOperation.CHECK,
                    "bb9_first_check_owner_changed")
                ownCheck = currentInstaller(linux)
                requireSafe(ownCheck?.isAlive == true, "bb9_first_check_not_active")
            }
            if (withPeer) {
                peer = linux.start("exec /bin/sleep 30", null)
                val pid = peer!!.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(peer)
                peerRoot = identity(pid)
                requireSafe(peer!!.isAlive && processUid(linux, pid) == Process.myUid(), "bb9_peer_identity_unavailable")
                peerTree = listOf(peerRoot!!)
                Thread.sleep(100)
                peerTree = currentPeerTree(linux, peerRoot!!)
                requireSafe(peerTree.any { it.sameProcess(peerRoot) }, "bb9_peer_identity_unavailable")
            }
            requireSafe(stat(done) == null && !first.isDone, "bb9_checks_did_not_overlap")
            nextThread.start()
            val deadline = SystemClock.elapsedRealtime() + 10000
            fun result(task: FutureTask<BuiltinLinux.Result>): BuiltinLinux.Result? = try {
                task.get((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1), TimeUnit.MILLISECONDS)
            } catch (_: Throwable) { null }
            val firstResult = result(first)
            val secondResult = result(next)
            serialized = firstResult?.exitCode == 0 && secondResult?.exitCode == 0 &&
                firstElapsed.get() in 1..10000L && secondElapsed.get() in 1..10000L &&
                stat(second) != null && readNoFollow(second, 64) == launchId
            survived = !withPeer || (peer!!.isAlive && peerRoot!!.sameProcess(kernelIdentity(peerRoot!!.pid)) &&
                processUid(linux, peerRoot!!.pid) == Process.myUid())
            requireSafe(serialized && survived && !writer.contains("ticket"),
                if (withPeer) "bb9_live_peer_failed" else "bb9_serialized_checks_failed")
        } catch (error: Refused) { failure = error }
        catch (_: Throwable) { failure = Refused(if (withPeer) "bb9_live_peer_failed" else "bb9_serialized_checks_failed") }
        finally {
            try {
                // Drain only captured peer identities; never use a raw PID or select a later writer.
                peerRoot?.takeIf { it.sameProcess(kernelIdentity(it.pid)) }?.let { root ->
                    val captured = currentPeerTree(linux, root)
                    peerTree = (peerTree + captured).distinctBy { it.pid }
                        .sortedBy { if (it.pid == root.pid) 1 else 0 }
                }
                for (identity in peerTree) exactSignal(linux, identity, OsConstants.SIGTERM)
                val peerDeadline = SystemClock.elapsedRealtime() + 1500
                while (peerTree.any { it.sameProcess(kernelIdentity(it.pid)) } && SystemClock.elapsedRealtime() < peerDeadline) Thread.sleep(50)
                for (identity in peerTree) exactSignal(linux, identity, OsConstants.SIGKILL)
                val killedDeadline = SystemClock.elapsedRealtime() + 2000
                while (peerTree.any { it.sameProcess(kernelIdentity(it.pid)) } && SystemClock.elapsedRealtime() < killedDeadline) Thread.sleep(50)
                requireSafe(peerTree.none { it.sameProcess(kernelIdentity(it.pid)) }, "bb9_live_peer_cleanup_failed")
                if (firstThread.isAlive) firstThread.interrupt()
                if (nextThread.isAlive) nextThread.interrupt()
                firstThread.join(4000); nextThread.join(4000)
                requireSafe(!firstThread.isAlive && !nextThread.isAlive, "bb9_live_worker_cleanup_failed")
                synchronized(linux) {
                    val current = currentInstaller(linux)
                    if (current != null) {
                        requireSafe(current === ownCheck && launchId != null && ticket().id == launchId,
                            "bb9_live_cleanup_writer_changed")
                        linux.stopInstaller(current); linux.finishInstaller(current)
                    }
                }
                requireSafe(quiescent(linux) && !writer.contains("ticket"), "bb9_live_cleanup_not_quiescent")
                for (marker in listOf(ready, done, second)) if (stat(marker) != null) {
                    requireSafe(launchId != null && readNoFollow(marker, 64) == launchId, "bb9_live_marker_changed")
                    requireSafe(quiescent(linux), "bb9_live_cleanup_not_quiescent")
                    Os.remove(marker.absolutePath)
                }
                sync(temporary)
                cleaned = true
            } catch (_: Throwable) { if (failure == null) failure = Refused("bb9_live_cleanup_failed") }
            emit {
                putBoolean("bb9SerializedChecksPassed", serialized && cleaned)
                putBoolean("bb9PeerSurvivedCompletion", withPeer && survived)
                putBoolean("bb9LivePeersPassed", withPeer && serialized && survived && cleaned && failure == null)
            }
        }
        failure?.let { throw it }
    }
    private fun currentInstaller(linux: BuiltinLinux) = BuiltinLinux::class.java.getDeclaredField("installerProcess")
        .apply { isAccessible = true }.get(linux) as? java.lang.Process
    private fun currentPeerTree(linux: BuiltinLinux, root: RuntimeProcessIdentity): List<RuntimeProcessIdentity> {
        requireSafe(root.sameProcess(kernelIdentity(root.pid)) && processUid(linux, root.pid) == Process.myUid(),
            "bb9_peer_identity_unavailable")
        val current = inventory(linux)
        val members = mutableSetOf(root.pid)
        var changed: Boolean
        do {
            changed = false
            for (child in current) if (child.parent in members && members.add(child.pid)) changed = true
        } while (changed)
        return current.filter { it.pid in members }.sortedBy { if (it.pid == root.pid) 1 else 0 }
    }
    private fun kernelIdentity(pid: Int): RuntimeProcessIdentity? = try { identity(pid) }
        catch (_: java.io.FileNotFoundException) { null }
    private fun processUid(linux: BuiltinLinux, pid: Int): Int? = BuiltinLinux::class.java
        .getDeclaredMethod("processUid", Integer.TYPE).apply { isAccessible = true }.invoke(linux, pid) as? Int
    private fun exactSignal(linux: BuiltinLinux, identity: RuntimeProcessIdentity, signal: Int) {
        if (identity.sameProcess(kernelIdentity(identity.pid))) {
            requireSafe(processUid(linux, identity.pid) == Process.myUid(), "bb9_live_identity_uid_changed")
            if (identity.sameProcess(kernelIdentity(identity.pid))) Os.kill(identity.pid, signal)
        }
    }
    private fun prepare(linux: BuiltinLinux, external: Boolean = false) {
        preflight(linux)
        finishActivities()
        val activityDeadline = SystemClock.elapsedRealtime() + 5000
        while (!activityAbsent() && SystemClock.elapsedRealtime() < activityDeadline) Thread.sleep(100)
        requireSafe(activityAbsent(), "bb9_activity_present")
        stopServices(linux)
        val originalHash = hash(executable(linux))
        val previousGood = stat(good(linux)) != null
        val state = JSONObject().put("version", 1).put("stage", "preparing")
            .put("app", JSONObject(identity(Process.myPid()).map())).put("originalExecutableSha256", originalHash)
            .put("originalGoodPresent", previousGood).put("originalLockPresent", false)
        if (previousGood) {
            val before = stat(good(linux))!!
            state.put("originalGoodDevice", before.st_dev).put("originalGoodInode", before.st_ino)
        }
        save(state)
        if (external) {
            requireSafe(stat(externalExport) == null, "bb9_external_export_exists")
            val nonce = java.util.UUID.randomUUID().toString().replace("-", "") +
                java.util.UUID.randomUUID().toString().replace("-", "")
            val gate = "printf 'OC-INSTALL-1 %s %s\\n' '$nonce' \"\$\$\"\n" +
                "IFS= read -r permit || exit 78\n[ \"\$permit\" = '$nonce' ] || exit 78\n" +
                "exec /bin/sh -c '" + faultScript(previousGood).replace("'", "'\"'\"'") + "'"
            // Build the protected command before publishing the prepared ticket.
            val command = linux.prootCommand(listOf("/usr/bin/env", "OC_RUNTIME_OWNER=$nonce",
                "/usr/bin/setsid", "/bin/sh", "-c", gate))
            val rootfs = BuiltinLinux::class.java.getDeclaredMethod("installerRootfsGeneration")
                .apply { isAccessible = true }.invoke(linux) as String
            val boot = BuiltinLinux::class.java.getDeclaredMethod("bootIdentity")
                .apply { isAccessible = true }.invoke(linux) as String
            val generation = writer.getLong("generation", 0L) + 1L
            requireSafe(generation > 0, "bb9_external_generation_invalid")
            val prepared = InstallerTicket(nonce, rootfs, InstallerTarget.entries.toSet(), InstallerOperation.INSTALL,
                NativeRuntimeReceipt(boot, nonce, generation, null, null, emptyList()))
            state.put("ticket", JSONObject(prepared.map())); save(state)
            saveExternalTicket(linux, prepared)
            val export = JSONObject().put("version", 1).put("uid", Process.myUid())
                .put("app", JSONObject(identity(Process.myPid()).map())).put("ticketId", nonce)
                .put("command", JSONArray(command)).put("environment", JSONObject(linux.prootEnvironment() + ("OC_RUNTIME_OWNER" to nonce)))
            val descriptor = Os.open(externalExport.absolutePath,
                OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, 384)
            FileOutputStream(descriptor).use { stream -> stream.write(export.toString().toByteArray()); stream.fd.sync() }
            sync(externalExport.parentFile!!)
            emit { putBoolean("bb9ExternalPrepared", true) }
            return
        }
        val process = linux.startInstaller(faultScript(previousGood), InstallerTarget.entries.toSet(), InstallerOperation.INSTALL)
        state.put("ticket", JSONObject(ticket().map())); save(state)
        val until = SystemClock.elapsedRealtime() + 20000
        while (stat(sentinel(linux)) == null && process.isAlive && SystemClock.elapsedRealtime() < until) Thread.sleep(100)
        requireSafe(process.isAlive && stat(sentinel(linux)) != null, "bb9_fixture_not_ready")
        requireSafe(hash(File(good(linux), "bin/opencode2")) == originalHash, "bb9_original_not_retained")
        requireSafe(hash(executable(linux)) != originalHash && stat(pending(linux)) != null &&
            stat(lock(linux)) != null, "bb9_fault_not_armed")
        // Give the production watcher one bounded snapshot opportunity; never infer orphan survival.
        Thread.sleep(300)
        val current = ticket()
        requireSafe(!current.ownership.prepared && current.observed.isNotEmpty(), "bb9_writer_not_observed")
        state.put("stage", "armed").put("ticket", JSONObject(current.map()))
            .put("activityAbsent", activityAbsent())
        requireSafe(state.getBoolean("activityAbsent"), "bb9_activity_present")
        save(state)
        emit { putBoolean("bb9InterruptedReady", true) }
        // Deliberately do not finish/stop the installer. Instrumentation must detach before host SIGKILL.
    }

    private fun saveExternalTicket(linux: BuiltinLinux, value: InstallerTicket) {
        BuiltinLinux::class.java.getDeclaredMethod("saveInstaller", InstallerTicket::class.java)
            .apply { isAccessible = true }.invoke(linux, value)
    }

    /** No workload permit or filesystem fault precedes the app's own kernel/nonce proof. */
    private fun commitExternal(linux: BuiltinLinux) = synchronized(linux) {
        val state = read()
        requireSafe(state.getString("stage") == "preparing" && activityAbsent(), "bb9_external_stage_invalid")
        requireSafe(state.getJSONObject("app").let { app -> RuntimeProcessIdentity.read(
            app.keys().asSequence().associateWith { app.get(it) }) } == identity(Process.myPid()),
            "bb9_external_app_changed")
        val prepared = ticketFrom(state)
        requireSafe(prepared.ownership.prepared && ticket() == prepared &&
            arguments.getString("ticketId") == prepared.id, "bb9_external_ticket_changed")
        fun supplied(name: String): Long {
            val value = arguments.getString(name)
            requireSafe(value != null && Regex("[0-9]{1,19}").matches(value), "bb9_external_identity_invalid")
            return value!!.toLongOrNull()?.takeIf { it > 1L } ?: throw Refused("bb9_external_identity_invalid")
        }
        val rootPid = supplied("rootPid"); val leaderPid = supplied("leaderPid")
        requireSafe(rootPid <= Int.MAX_VALUE && leaderPid <= Int.MAX_VALUE && rootPid != Process.myPid().toLong(),
            "bb9_external_identity_invalid")
        externalStage = "identity"
        val root = identity(rootPid.toInt()); val leader = identity(leaderPid.toInt())
        requireSafe(root.startTicks == supplied("rootStartTicks") && leader.startTicks == supplied("leaderStartTicks") &&
            processUid(linux, root.pid) == Process.myUid() && processUid(linux, leader.pid) == Process.myUid(),
            "bb9_external_identity_invalid")
        fun nonceMatches(pid: Int): Boolean = BuiltinLinux::class.java
            .getDeclaredMethod("nonceMatches", Integer.TYPE, String::class.java).apply { isAccessible = true }
            .invoke(linux, pid, prepared.id) as Boolean
        externalStage = "nonce"
        val descendants = currentPeerTree(linux, root)
        requireSafe(nonceMatches(root.pid) && nonceMatches(leader.pid) && descendants.any { it.sameProcess(leader) } &&
            leader.session == leader.pid && leader.group == leader.pid, "bb9_external_ownership_unavailable")
        fun cgroup(pid: Int): String {
            val bytes = File("/proc/$pid/cgroup").inputStream().use { it.readNBytes(8193) }
            requireSafe(bytes.size in 1..8192, "bb9_external_cgroup_unavailable")
            return bytes.toString(Charsets.US_ASCII)
        }
        externalStage = "cgroup"
        val appGroup = "pid_${Process.myPid()}"
        fun belongs(value: String) = value.lineSequence().any { line -> line.substringAfterLast(':').split('/').contains(appGroup) }
        requireSafe(belongs(cgroup(Process.myPid())) && !belongs(cgroup(root.pid)) && !belongs(cgroup(leader.pid)),
            "bb9_external_cgroup_unavailable")
        // Probe actual native signal permission while the fixed gate is waiting.
        // SIGCONT neither releases stdin nor changes a running workload's files.
        externalStage = "signal"
        for (owned in listOf(root, leader)) {
            requireSafe(owned.sameProcess(kernelIdentity(owned.pid)), "bb9_external_identity_invalid")
            exactSignal(linux, owned, OsConstants.SIGCONT)
            requireSafe(owned.sameProcess(kernelIdentity(owned.pid)), "bb9_external_identity_invalid")
        }
        externalStage = "plan"
        val committed = NativeInstallerOwnership.committed(prepared, root, leader)
        val plan = BuiltinLinux::class.java.getDeclaredMethod("installerPlan", InstallerTicket::class.java)
            .apply { isAccessible = true }.invoke(linux, committed) as NativeRuntimeOwnership.Drain
        val observed = committed.copy(observed = plan.server)
        requireSafe(observed.observed.isNotEmpty(), "bb9_external_ownership_unavailable")
        externalStage = "persist"
        saveExternalTicket(linux, observed)
        state.put("stage", "committed").put("ticket", JSONObject(observed.map())); save(state)
        emit { putBoolean("bb9ExternalCommitted", true) }
    }

    private fun armExternal(linux: BuiltinLinux) {
        val state = read()
        requireSafe(state.getString("stage") == "committed" && ticket() == ticketFrom(state), "bb9_external_ticket_changed")
        requireSafe(activityAbsent() && stat(sentinel(linux)) != null &&
            hash(File(good(linux), "bin/opencode2")) == state.getString("originalExecutableSha256") &&
            hash(executable(linux)) != state.getString("originalExecutableSha256") &&
            stat(pending(linux)) != null && stat(lock(linux)) != null, "bb9_fault_not_armed")
        val current = ticket()
        val plan = BuiltinLinux::class.java.getDeclaredMethod("installerPlan", InstallerTicket::class.java)
            .apply { isAccessible = true }.invoke(linux, current) as NativeRuntimeOwnership.Drain
        requireSafe(current.ownership.root?.let { fixed -> plan.server.any { it.sameProcess(fixed) } } == true,
            "bb9_external_writer_not_live")
        val observed = current.copy(observed = plan.server)
        saveExternalTicket(linux, observed)
        state.put("stage", "armed").put("ticket", JSONObject(observed.map())).put("activityAbsent", true); save(state)
        emit { putBoolean("bb9ExternalArmed", true) }
    }

    private fun faultScript(previousGood: Boolean): String = """
        set -eu
        /usr/bin/python3 - <<'OC_BB9'
        import os, stat
        root='/opt/opencode2'
        good=root+'.oc-good'
        backup='/opt/$backupName'
        pending=root+'.oc-pending'
        lock='/home/oc/.local/share/oc-agents/.lock-claude'
        ready='/opt/$sentinelName'
        def directory(path):
            assert stat.S_ISDIR(os.lstat(path).st_mode)
        def absent(path):
            try: os.lstat(path)
            except FileNotFoundError: return
            raise RuntimeError('refused')
        def sync(path):
            fd=os.open(path,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
            try: os.fsync(fd)
            finally: os.close(fd)
        for path in ('/opt',root,root+'/bin','/home','/home/oc','/home/oc/.local','/home/oc/.local/share','/home/oc/.local/share/oc-agents'):
            directory(path)
        assert stat.S_ISREG(os.lstat(root+'/bin/opencode2').st_mode)
        for path in (backup,pending,pending+'.new',root+'.new',lock,ready): absent(path)
        if ${if (previousGood) "True" else "False"}:
            directory(good)
            os.rename(good,backup)
            sync('/opt')
        else: absent(good)
        fd=os.open(pending,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
        with os.fdopen(fd,'wb') as stream:
            stream.write(b'existing\n'); stream.flush(); os.fsync(stream.fileno())
        sync('/opt')
        os.rename(root,good)
        sync('/opt')
        os.mkdir(root,0o700); os.mkdir(root+'/bin',0o700)
        fd=os.open(root+'/bin/opencode2',os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o700)
        with os.fdopen(fd,'wb') as stream:
            stream.write(b'#!/bin/sh\nexit 74\n'); stream.flush(); os.fsync(stream.fileno())
        sync(root+'/bin'); sync(root); sync('/opt')
        os.mkdir(lock,0o700); sync(os.path.dirname(lock))
        fd=os.open(ready,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
        with os.fdopen(fd,'wb') as stream:
            stream.write(b'bb9-owned\n'); stream.flush(); os.fsync(stream.fileno())
        sync('/opt')
        OC_BB9
        exec /bin/sleep 180
    """.trimIndent()

    /** Read-only: only the host's real next normal startup is allowed to cause recovery. */
    private fun verify(linux: BuiltinLinux) {
        val state = read()
        requireSafe(state.getString("stage") == "armed", "bb9_fixture_stage_invalid")
        ancestry(linux); directory(active(linux)); directory(File(active(linux), "bin"))
        requireSafe(hash(executable(linux)) == state.getString("originalExecutableSha256"), "bb9_original_not_restored")
        requireSafe(stat(pending(linux)) == null && stat(lock(linux)) == null && !writer.contains("ticket"), "bb9_recovery_incomplete")
        val old = ticketFrom(state)
        val current = inventory(linux).associateBy { it.pid }
        val owned = (listOfNotNull(old.ownership.root, old.ownership.leader) + old.observed).distinctBy { it.pid }
        requireSafe(owned.none { it.sameProcess(current[it.pid]) }, "bb9_owned_writer_survived")
        emit {
            putBoolean("bb9Verified", true); putBoolean("bb9OwnedWriterGone", true)
            putInt("bb9VerifierAppPid", Process.myPid())
            putLong("bb9VerifierAppStartTicks", identity(Process.myPid()).startTicks)
        }
    }

    private fun cleanup(linux: BuiltinLinux) = synchronized(linux) {
        val state = read()
        val saved = state.optJSONObject("ticket")?.let { ticketFrom(state) }
        if (saved != null) {
            fun lifecycle(name: String) = BuiltinLinux::class.java.getDeclaredMethod(name)
                .apply { isAccessible = true }.invoke(linux) as String
            requireSafe(saved.rootfsGeneration == lifecycle("installerRootfsGeneration") &&
                saved.ownership.boot == lifecycle("bootIdentity"), "bb9_cleanup_lifecycle_changed")
        }
        val producerRaw = if (stat(producerEvidence) == null) null else readNoFollow(producerEvidence, 131072)
        val producerKnown = producerRaw?.let { raw ->
            val producer = JSONObject(raw)
            val keys = producer.keys().asSequence().toSet()
            val required = setOf("version", "ticketId", "uid", "producer", "root", "leader", "state")
            requireSafe((keys == required || keys == required + "error") && saved != null && producer.get("version") == 1 &&
                producer.getString("ticketId") == saved.id && producer.get("uid") == Process.myUid() &&
                producer.getString("state") in setOf("waiting", "running", "stopped", "failed"),
                "bb9_producer_cleanup_unavailable")
            fun metadataIdentity(name: String): RuntimeProcessIdentity? = producer.optJSONObject(name)?.let { value ->
                RuntimeProcessIdentity.read(value.keys().asSequence().associateWith { value.get(it) })
            }
            val actor = metadataIdentity("producer") ?: throw Refused("bb9_producer_cleanup_unavailable")
            val root = metadataIdentity("root"); val leader = metadataIdentity("leader")
            for (name in listOf("root", "leader")) requireSafe(producer.isNull(name) || producer.optJSONObject(name) != null,
                "bb9_producer_cleanup_unavailable")
            if (!saved!!.ownership.prepared) requireSafe(root == saved.ownership.root && leader == saved.ownership.leader,
                "bb9_producer_cleanup_unavailable")
            listOfNotNull(actor, root, leader)
        } ?: emptyList()
        val savedKnown = listOfNotNull(saved?.ownership?.root, saved?.ownership?.leader) + (saved?.observed ?: emptyList())
        fun producerGone() {
            val current = inventory(linux)
            fun confirmedGone(pid: Int): Boolean = try { Os.kill(pid, 0); false }
                catch (error: ErrnoException) { error.errno == OsConstants.ESRCH }
                catch (_: Throwable) { false }
            NativeInstallerVisibility.requireMissingGone(producerKnown, current, ::confirmedGone)
            NativeInstallerVisibility.requireMissingGone(savedKnown, current, ::confirmedGone)
            requireSafe((producerKnown + savedKnown).none { known -> current.any { known.sameProcess(it) } },
                "bb9_producer_cleanup_unavailable")
            requireSafe(quiescent(linux), "bb9_cleanup_not_quiescent")
            requireSafe(if (producerRaw == null) stat(producerEvidence) == null else
                readNoFollow(producerEvidence, 131072) == producerRaw, "bb9_producer_cleanup_unavailable")
        }
        val currentProcess = BuiltinLinux::class.java.getDeclaredField("installerProcess").apply { isAccessible = true }
            .get(linux) as? java.lang.Process
        if (currentProcess != null) {
            requireSafe(saved != null && ticket().id == saved.id, "bb9_cleanup_writer_changed")
            linux.stopInstaller(currentProcess); linux.finishInstaller(currentProcess)
        } else if (writer.contains("ticket")) {
            val durable = ticket()
            requireSafe(saved != null && durable.id == saved.id, "bb9_cleanup_writer_changed")
            // Use the existing native exact-identity/UID drain, never a generic PID tree operation.
            synchronized(linux) {
                BuiltinLinux::class.java.getDeclaredMethod("drainInstaller", InstallerTicket::class.java,
                    java.lang.Process::class.java, String::class.java)
                    .apply { isAccessible = true }.invoke(linux, durable, null, null)
            }
        }
        stopServices(linux)
        requireSafe(quiescent(linux), "bb9_cleanup_not_quiescent")
        ancestry(linux)
        // Metadata never grants signal authority; prove every old actor gone before filesystem recovery.
        producerGone()
        // Recovery here is cleanup only; verify above never invokes it.
        NativeComponentUpdateRecovery(context.filesDir, linux.home, linux.rootfs).recoverAfterQuiescence {
            producerGone(); quiescent(linux)
        }
        requireSafe(hash(executable(linux)) == state.getString("originalExecutableSha256"), "bb9_cleanup_original_missing")
        if (state.getBoolean("originalGoodPresent")) {
            val retained = if (stat(backup(linux)) != null) backup(linux) else good(linux)
            directory(retained)
            val before = stat(retained)!!
            requireSafe(before.st_dev == state.getLong("originalGoodDevice") &&
                before.st_ino == state.getLong("originalGoodInode"), "bb9_cleanup_backup_changed")
            if (retained == backup(linux)) {
                requireSafe(stat(good(linux)) == null, "bb9_cleanup_good_changed")
                producerGone()
                Os.rename(retained.absolutePath, good(linux).absolutePath); sync(File(linux.rootfs, "opt"))
            }
        } else requireSafe(stat(backup(linux)) == null && stat(good(linux)) == null, "bb9_cleanup_backup_changed")
        if (stat(sentinel(linux)) != null) {
            val value = stat(sentinel(linux))!!
            requireSafe(OsConstants.S_ISREG(value.st_mode) && value.st_size == 10L, "bb9_cleanup_sentinel_changed")
            requireSafe(readNoFollow(sentinel(linux), 10) == "bb9-owned\n", "bb9_cleanup_sentinel_changed")
            producerGone()
            Os.remove(sentinel(linux).absolutePath); sync(File(linux.rootfs, "opt"))
        }
        requireSafe(quiescent(linux), "bb9_cleanup_not_quiescent")
        producerGone()
        if (writer.contains("ticket")) {
            requireSafe(saved != null && ticket().id == saved.id, "bb9_cleanup_writer_changed")
            requireSafe(writer.edit().remove("ticket").commit(), "bb9_cleanup_ticket_failed")
        }
        producerGone()
        if (stat(externalExport) != null) {
            val exported = JSONObject(readNoFollow(externalExport, 32768))
            requireSafe(saved != null && exported.getInt("version") == 1 &&
                exported.getString("ticketId") == saved.id && exported.getInt("uid") == Process.myUid(),
                "bb9_external_export_changed")
            Os.remove(externalExport.absolutePath); sync(externalExport.parentFile!!)
        }
        if (producerRaw != null) {
            producerGone()
            Os.remove(producerEvidence.absolutePath); sync(producerEvidence.parentFile!!)
        }
        requireSafe(stat(evidence)?.let { OsConstants.S_ISREG(it.st_mode) } == true, "bb9_evidence_invalid")
        Os.remove(evidence.absolutePath); sync(evidence.parentFile!!)
        emit { putBoolean("bb9CleanupComplete", true) }
    }
    private fun ticketFrom(state: JSONObject): InstallerTicket {
        fun value(item: Any?): Any? = when (item) {
            JSONObject.NULL -> null
            is JSONObject -> item.keys().asSequence().associateWith { value(item.get(it)) }
            is JSONArray -> (0 until item.length()).map { value(item.get(it)) }
            else -> item
        }
        return InstallerTicket.read(value(state.getJSONObject("ticket")) as Map<*, *>)
    }
    private fun identity(pid: Int) = RuntimeProcessIdentity.stat(File("/proc/$pid/stat").readText())
    private fun activityAbsent() = context.getSystemService(ActivityManager::class.java)
        .appTasks.none { it.taskInfo?.topActivity?.packageName == context.packageName }
    private fun read(): JSONObject {
        val value = stat(evidence)
        requireSafe(value != null && OsConstants.S_ISREG(value.st_mode) && value.st_size in 1..131072L, "bb9_evidence_invalid")
        return JSONObject(readNoFollow(evidence, 131072)).also { requireSafe(it.getInt("version") == 1, "bb9_evidence_invalid") }
    }
    private fun save(state: JSONObject) {
        val temporary = File(evidence.parentFile, evidence.name + ".tmp")
        requireSafe(stat(temporary) == null, "bb9_evidence_temporary_exists")
        val descriptor = Os.open(temporary.absolutePath,
            OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, 384)
        FileOutputStream(descriptor).use { stream -> stream.write(state.toString().toByteArray()); stream.fd.sync() }
        val app = JSONObject(readNoFollow(temporary, 131072)).optJSONObject("app")
        val current = identity(Process.myPid())
        requireSafe(app != null && app.getInt("pid") == current.pid && app.getLong("startTicks") == current.startTicks,
            "bb9_app_identity_not_object")
        Os.rename(temporary.absolutePath, evidence.absolutePath); sync(evidence.parentFile!!)
    }
    private fun readNoFollow(file: File, maximum: Int): String {
        val before = stat(file)
        requireSafe(before != null && OsConstants.S_ISREG(before.st_mode) && before.st_uid == Process.myUid() &&
            before.st_size in 1..maximum.toLong(), "bb9_evidence_invalid")
        fun unchanged(first: android.system.StructStat, second: android.system.StructStat?) = second != null &&
            first.st_dev == second.st_dev && first.st_ino == second.st_ino && first.st_size == second.st_size &&
            first.st_uid == second.st_uid && first.st_mode == second.st_mode &&
            first.st_mtime == second.st_mtime && first.st_ctime == second.st_ctime
        val descriptor = Os.open(file.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        return java.io.FileInputStream(descriptor).use { stream ->
            val opened = Os.fstat(descriptor)
            requireSafe(unchanged(before!!, opened), "bb9_evidence_invalid")
            val bytes = ByteArray(maximum + 1)
            var used = 0
            while (used < bytes.size) { val count = stream.read(bytes, used, bytes.size - used); if (count < 0) break; used += count }
            requireSafe(used.toLong() == opened.st_size && used in 1..maximum &&
                unchanged(opened, Os.fstat(descriptor)) && unchanged(opened, stat(file)), "bb9_evidence_invalid")
            String(bytes, 0, used, Charsets.UTF_8)
        }
    }
    private fun sync(directory: File) {
        val before = stat(directory)
        requireSafe(before != null && OsConstants.S_ISDIR(before.st_mode), "bb9_path_unsafe")
        val descriptor = Os.open(directory.absolutePath,
            OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK, 0)
        try {
            val opened = Os.fstat(descriptor)
            requireSafe(OsConstants.S_ISDIR(opened.st_mode) && opened.st_dev == before!!.st_dev &&
                opened.st_ino == before.st_ino, "bb9_path_unsafe")
            Os.fsync(descriptor)
        } finally { Os.close(descriptor) }
    }
    private fun emit(write: Bundle.() -> Unit) { instrumentation.sendStatus(0, Bundle().apply(write)) }
    private fun requireSafe(value: Boolean, code: String) { if (!value) throw Refused(code) }
}
