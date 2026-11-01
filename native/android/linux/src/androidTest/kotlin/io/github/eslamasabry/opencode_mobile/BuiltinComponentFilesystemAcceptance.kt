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
import android.system.StructStat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom

/** Real AndroidFs negatives on an absent fixed legacy target; never probes an account. */
internal class BuiltinComponentFilesystemAcceptance(private val instrumentation: Instrumentation) {
    private val context get() = instrumentation.targetContext
    private val metadata get() = File(context.filesDir, METADATA_NAME)
    private val writer get() = context.getSharedPreferences("builtin_component_writer", 0)
    private lateinit var state: JSONObject
    private var metadataIdentity: StructStat? = null
    private var metadataDigest: String? = null

    fun execute(cleanupOnly: Boolean = false) {
        try {
            requireSafe(BuildConfig.BUILTIN_RUNTIME_QA, "bb9_qa_build_required")
            val linux = BuiltinLinux.get(context)
            requireSafe(linux.installed, "bb9_ubuntu_unavailable")
            requireSafe(!writer.contains("ticket"), "bb9_existing_writer_refused")
            if (cleanupOnly) {
                cleanupRecordedFixture(linux)
                return
            }
            executeCases(linux)
        } catch (error: BuiltinComponentUpdateAcceptance.Refused) { throw error }
        catch (_: Throwable) { throw BuiltinComponentUpdateAcceptance.Refused("bb9_filesystem_acceptance_unavailable") }
    }

    private fun cleanupRecordedFixture(linux: BuiltinLinux) {
        finishAndStop(linux)
        if (stat(metadata) == null) {
            ancestry(linux, File(linux.rootfs, "opt")); ancestry(linux, lock(linux).parentFile!!)
            stat(parent(linux))?.let { requireSafe(OsConstants.S_ISDIR(it.st_mode), "bb9_path_unsafe") }
            requireSafe(stat(temporary()) == null && stat(active(linux)) == null &&
                stat(pending(linux)) == null && stat(lock(linux)) == null,
                "bb9_filesystem_unrecorded_fixture_refused")
        } else {
            load(linux)
            cleanup(linux)
        }
        emit { putBoolean("bb9FilesystemCleanupComplete", true) }
    }

    private fun executeCases(linux: BuiltinLinux) {
        preflight(linux)
        finishAndStop(linux)
        preflight(linux)
        val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        state = JSONObject().put("version", 1).put("fixture", "bb9FilesystemCases")
            .put("token", token).put("stage", "preparing")
            .put("app", JSONObject(identity(Process.myPid()).map()))
            .put("rootfs", stamp(stat(linux.rootfs)!!)).put("opt", stamp(stat(File(linux.rootfs, "opt"))!!))
            .put("agents", stamp(stat(lock(linux).parentFile!!)!!))
            .put("parentOriginallyPresent", stat(parent(linux)) != null)
            .put("parentCreated", false).put("artifacts", JSONArray())
        stat(parent(linux))?.let { state.put("parentIdentity", stamp(it)) }
        save(linux)
        var failure: Throwable? = null
        var passed = false
        try {
            createParentIfAbsent(linux)
            runCases(linux)
            passed = true
        } catch (error: Throwable) { failure = error }
        finally {
            try { cleanup(linux) }
            catch (error: Throwable) {
                if (failure == null) failure = error
                passed = false
            }
        }
        failure?.let { throw it }
        requireSafe(passed, "bb9_filesystem_cases_failed")
        emit {
            putBoolean("bb9FilesystemCasesPassed", true)
            putBoolean("bb9FirstInstallAbsentPassed", true)
            putBoolean("bb9UnsafeReceiptRefused", true)
            putBoolean("bb9UnknownQuiescenceRefused", true)
            putBoolean("bb9LegacyLockRefused", true)
        }
    }

    private fun createParentIfAbsent(linux: BuiltinLinux) {
        if (stat(parent(linux)) == null) {
            state.put("parentCreated", true); save(linux)
            requireQuiescence(linux); ancestry(linux, parent(linux).parentFile!!)
            Os.mkdir(parent(linux).absolutePath, 448)
            val created = stat(parent(linux))!!
            requireSafe(OsConstants.S_ISDIR(created.st_mode), "bb9_filesystem_parent_changed")
            state.put("parentIdentity", stamp(created)); sync(parent(linux).parentFile!!); save(linux)
        }
    }

    private fun runCases(linux: BuiltinLinux) {
        val recovery = NativeComponentUpdateRecovery(context.filesDir, linux.home, linux.rootfs)
        startCase(linux, "firstInstall", "new\n")
        recovery.recoverAfterQuiescence { quiescent(linux) }
        requireSafe(stat(active(linux)) == null && stat(pending(linux)) == null,
            "bb9_filesystem_first_install_not_absent")
        clearCase(linux)

        startCase(linux, "bogusReceipt", "bogus\n")
        refused { recovery.recoverAfterQuiescence { quiescent(linux) } }
        unchanged(linux); clearCase(linux)

        startCase(linux, "symlinkReceipt", null)
        refused { recovery.recoverAfterQuiescence { quiescent(linux) } }
        unchanged(linux); clearCase(linux)

        startCase(linux, "falseQuiescence", "new\n")
        // This proves the filesystem callback refuses false authority, not an orphan-kernel proof.
        refused { recovery.recoverAfterQuiescence { false } }
        unchanged(linux); clearCase(linux)

        startCase(linux, "nonemptyLegacyLock", "new\n")
        val directory = plan(linux, "lock", "directory")
        requireQuiescence(linux); ancestry(linux, lock(linux).parentFile!!)
        requireSafe(stat(lock(linux)) == null, "bb9_existing_lock_refused")
        Os.mkdir(lock(linux).absolutePath, 448)
        directory.put("identity", stamp(stat(lock(linux))!!)); sync(lock(linux).parentFile!!); save(linux)
        createRegular(linux, "lockMarker", payload("lock"))
        refused { recovery.recoverAfterQuiescence { quiescent(linux) } }
        unchanged(linux)
        verifyArtifact(linux, directory)
        clearCase(linux)
        state.put("stage", "complete"); save(linux)
    }

    private fun startCase(linux: BuiltinLinux, stage: String, receipt: String?) {
        requireSafe(state.getJSONArray("artifacts").length() == 0 && stat(active(linux)) == null &&
            stat(pending(linux)) == null && stat(lock(linux)) == null, "bb9_filesystem_fixture_changed")
        state.put("stage", stage); save(linux)
        createRegular(linux, "active", payload(stage))
        if (receipt != null) createRegular(linux, "pending", receipt.toByteArray(Charsets.US_ASCII))
        else {
            val record = plan(linux, "pending", "symlink", link = "claude")
            requireQuiescence(linux); ancestry(linux, parent(linux))
            requireSafe(stat(pending(linux)) == null, "bb9_filesystem_fixture_changed")
            Os.symlink("claude", pending(linux).absolutePath)
            record.put("identity", stamp(stat(pending(linux))!!)); sync(parent(linux)); save(linux)
        }
    }
    private fun payload(kind: String) =
        "bb9-filesystem-v1:$kind:${state.getString("token")}\n".toByteArray(Charsets.US_ASCII)

    private fun plan(linux: BuiltinLinux, role: String, type: String, bytes: ByteArray? = null,
        link: String? = null): JSONObject {
        val record = JSONObject().put("role", role).put("type", type)
        if (bytes != null) record.put("sha256", digest(bytes))
        if (link != null) record.put("link", link)
        state.getJSONArray("artifacts").put(record)
        // Persist intent before a create. A created regular object can be repaired by its unique hash.
        save(linux)
        return record
    }
    private fun createRegular(linux: BuiltinLinux, role: String, bytes: ByteArray) {
        val record = plan(linux, role, "regular", bytes)
        val file = artifactFile(linux, role)
        requireQuiescence(linux); ancestry(linux, file.parentFile!!)
        requireSafe(stat(file) == null, "bb9_filesystem_fixture_changed")
        val descriptor = Os.open(file.absolutePath,
            OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, 384)
        FileOutputStream(descriptor).use { stream ->
            stream.write(bytes); stream.fd.sync()
            record.put("identity", stamp(Os.fstat(descriptor)))
        }
        sync(file.parentFile!!); save(linux)
    }
    private fun refused(action: () -> Unit) {
        val failure = try { action(); null } catch (error: IllegalStateException) { error }
        requireSafe(failure != null && failure.message == NativeComponentUpdateRecovery.FAILURE &&
            failure.cause == null,
            "bb9_filesystem_refusal_missing")
    }
    private fun unchanged(linux: BuiltinLinux) {
        requireQuiescence(linux)
        val records = state.getJSONArray("artifacts")
        for (index in 0 until records.length()) verifyArtifact(linux, records.getJSONObject(index), mustExist = true)
    }
    private fun clearCase(linux: BuiltinLinux) {
        removeArtifacts(linux)
        state.put("artifacts", JSONArray()); save(linux)
    }

    private fun preflight(linux: BuiltinLinux) {
        ancestry(linux, File(linux.rootfs, "opt")); ancestry(linux, lock(linux).parentFile!!)
        stat(parent(linux))?.let { requireSafe(OsConstants.S_ISDIR(it.st_mode), "bb9_path_unsafe") }
        requireSafe(stat(metadata) == null && stat(temporary()) == null, "bb9_existing_fixture_refused")
        requireSafe(!writer.contains("ticket"), "bb9_existing_writer_refused")
        for (suffix in listOf("", ".oc-good", ".oc-pending", ".oc-pending.new", ".new"))
        requireSafe(stat(File(active(linux).path + suffix)) == null, "bb9_existing_journal_refused")
        requireSafe(stat(lock(linux)) == null, "bb9_existing_lock_refused")
        requireSafe(!NativeComponentUpdateRecovery(context.filesDir, linux.home, linux.rootfs).hasPending(),
            "bb9_existing_journal_refused")
        requireNoForeignJournal(linux)
        requireSafe(stat(File(context.filesDir, "bb9-runtime-qa.json")) == null,
            "bb9_existing_fixture_refused")
        for (fixture in listOf("opt/opencode2.oc-bb9-original-good", "opt/.oc-bb9-qa-ready",
            "tmp/.oc-bb9-live-ready", "tmp/.oc-bb9-live-done", "tmp/.oc-bb9-live-second"))
        requireSafe(stat(File(linux.rootfs, fixture)) == null, "bb9_existing_fixture_refused")
    }
    private fun finishAndStop(linux: BuiltinLinux) {
        instrumentation.runOnMainSync {
            val type = Class.forName("android.app.ActivityThread")
            val thread = type.getDeclaredMethod("currentActivityThread").invoke(null)
            val records = type.getDeclaredField("mActivities").apply { isAccessible = true }.get(thread) as Map<*, *>
            for (record in records.values) if (record != null) {
                val activity = record.javaClass.getDeclaredField("activity").apply { isAccessible = true }
                    .get(record) as? Activity
                if (activity?.packageName == context.packageName) activity.finish()
            }
        }
        val activityDeadline = SystemClock.elapsedRealtime() + 5000
        while (!activityAbsent() && SystemClock.elapsedRealtime() < activityDeadline) Thread.sleep(100)
        requireSafe(activityAbsent(), "bb9_activity_present")
        requireSafe(!writer.contains("ticket"), "bb9_existing_writer_refused")
        linux.requestServerStop(); linux.stopAllServices()
        val deadline = SystemClock.elapsedRealtime() + 7000
        while (!quiescent(linux) && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        requireQuiescence(linux)
    }
    private fun quiescent(linux: BuiltinLinux): Boolean {
        if (writer.contains("ticket")) return false
        val readers = BuiltinLinux.registeredAppProcessIds(
            context.getSystemService(ActivityManager::class.java)?.runningAppProcesses,
            context.packageName, Process.myUid()).toSet() + Process.myPid()
        @Suppress("UNCHECKED_CAST")
        val inventory = BuiltinLinux::class.java.getDeclaredMethod("sameUidInventory").apply { isAccessible = true }
            .invoke(linux) as List<RuntimeProcessIdentity>
        return inventory.all { it.pid in readers }
    }
    private fun requireQuiescence(linux: BuiltinLinux) = requireSafe(quiescent(linux), "bb9_writer_quiescence_unknown")
    private fun activityAbsent() = context.getSystemService(ActivityManager::class.java)
        .appTasks.none { it.taskInfo?.topActivity?.packageName == context.packageName }

    private fun removeArtifacts(linux: BuiltinLinux) {
        requireQuiescence(linux); checkAnchors(linux); validateRecords(); requireNoForeignJournal(linux)
        val records = state.getJSONArray("artifacts")
        val byRole = (0 until records.length()).map { records.getJSONObject(it) }.associateBy { it.getString("role") }
        requireSafe(byRole.size == records.length(), "bb9_filesystem_metadata_invalid")
        // A nonempty lock must contain our exact marker, never an unknown install object.
        if (stat(lock(linux)) != null) {
            val ownLock = byRole["lock"] ?: throw BuiltinComponentUpdateAcceptance.Refused("bb9_existing_lock_refused")
            verifyArtifact(linux, ownLock)
            val names = lock(linux).list()?.toSet() ?: throw BuiltinComponentUpdateAcceptance.Refused("bb9_path_unsafe")
            requireSafe(names.isEmpty() || names == setOf(LOCK_MARKER) && byRole.containsKey("lockMarker"),
                "bb9_filesystem_cleanup_unknown_lock")
        }
        for (role in listOf("lockMarker", "lock", "active", "pending")) {
            byRole[role]?.let { removeArtifact(linux, role, it) }
        }
    }
    private fun removeArtifact(linux: BuiltinLinux, role: String, record: JSONObject) {
        val file = artifactFile(linux, role)
        val before = stat(file) ?: return
        verifyArtifact(linux, record)
        requireQuiescence(linux); ancestry(linux, file.parentFile!!)
        requireSafe(same(before, stat(file)), "bb9_filesystem_fixture_changed")
        if (record.getString("type") == "directory") requireSafe(file.list()?.isEmpty() == true,
            "bb9_filesystem_cleanup_unknown_lock")
        Os.remove(file.absolutePath); sync(file.parentFile!!)
    }
    private fun verifyArtifact(linux: BuiltinLinux, record: JSONObject, mustExist: Boolean = false) {
        val file = artifactFile(linux, record.getString("role"))
        ancestry(linux, file.parentFile!!)
        val current = stat(file)
        if (current == null) { requireSafe(!mustExist, "bb9_filesystem_fixture_changed"); return }
        val saved = record.optJSONObject("identity")
        if (saved != null) requireSafe(matches(saved, current), "bb9_filesystem_fixture_changed")
        when (record.getString("type")) {
            "regular" -> requireSafe(OsConstants.S_ISREG(current.st_mode) &&
                digest(readNoFollow(file, 1024)) == record.getString("sha256"), "bb9_filesystem_fixture_changed")
            "symlink" -> requireSafe(OsConstants.S_ISLNK(current.st_mode) && record.getString("link") == "claude" &&
                Os.readlink(file.absolutePath) == "claude", "bb9_filesystem_fixture_changed")
            "directory" -> requireSafe(saved != null && OsConstants.S_ISDIR(current.st_mode),
                "bb9_filesystem_cleanup_unknown_lock")
            else -> throw BuiltinComponentUpdateAcceptance.Refused("bb9_filesystem_metadata_invalid")
        }
        requireSafe(same(current, stat(file)), "bb9_filesystem_fixture_changed")
    }
    private fun cleanup(linux: BuiltinLinux) {
        removeArtifacts(linux)
        requireSafe(stat(active(linux)) == null && stat(pending(linux)) == null && stat(lock(linux)) == null,
            "bb9_filesystem_cleanup_failed")
        if (state.getBoolean("parentCreated") && stat(parent(linux)) != null) {
            ancestry(linux, parent(linux)); requireQuiescence(linux)
            requireSafe(state.optJSONObject("parentIdentity")?.let { matches(it, stat(parent(linux))) } == true &&
                parent(linux).list()?.isEmpty() == true, "bb9_filesystem_parent_changed")
            Os.remove(parent(linux).absolutePath); sync(parent(linux).parentFile!!)
        }
        requireQuiescence(linux); checkMetadataIdentity()
        Os.remove(metadata.absolutePath); sync(context.filesDir)
    }
    private fun load(linux: BuiltinLinux) {
        val bytes = readNoFollow(metadata, 65536)
        state = JSONObject(bytes.toString(Charsets.UTF_8))
        requireSafe(state.keys().asSequence().all { it in setOf("version", "fixture", "token", "stage", "app",
                "rootfs", "opt",
                "agents", "parentOriginallyPresent", "parentCreated", "parentIdentity", "artifacts") } &&
            state.getInt("version") == 1 && state.getString("fixture") == "bb9FilesystemCases" &&
            Regex("[0-9a-f]{32}").matches(state.getString("token")) && state.getJSONArray("artifacts").length() <= 4,
            "bb9_filesystem_metadata_invalid")
        requireSafe(state.opt("parentOriginallyPresent") is Boolean && state.opt("parentCreated") is Boolean &&
            !(state.getBoolean("parentOriginallyPresent") && state.getBoolean("parentCreated")),
            "bb9_filesystem_metadata_invalid")
        validateRecords()
        metadataIdentity = stat(metadata); metadataDigest = digest(bytes)
        requireSafe(stat(temporary()) == null, "bb9_filesystem_temporary_refused")
        checkAnchors(linux)
    }
    private fun checkAnchors(linux: BuiltinLinux) {
        ancestry(linux, File(linux.rootfs, "opt")); ancestry(linux, lock(linux).parentFile!!)
        requireSafe(matches(state.getJSONObject("rootfs"), stat(linux.rootfs)) &&
            matches(state.getJSONObject("opt"), stat(File(linux.rootfs, "opt"))) &&
            matches(state.getJSONObject("agents"), stat(lock(linux).parentFile!!)), "bb9_filesystem_root_changed")
        if (state.getBoolean("parentOriginallyPresent")) requireSafe(state.optJSONObject("parentIdentity")?.let {
            matches(it, stat(parent(linux))) } == true, "bb9_filesystem_parent_changed")
        if (state.getBoolean("parentCreated") && stat(parent(linux)) != null && state.has("parentIdentity"))
        requireSafe(matches(state.getJSONObject("parentIdentity"), stat(parent(linux))),
            "bb9_filesystem_parent_changed")
    }

    private fun validateRecords() {
        val stage = state.getString("stage")
        requireSafe(stage in setOf("preparing", "firstInstall", "bogusReceipt", "symlinkReceipt", "falseQuiescence",
            "nonemptyLegacyLock", "complete"), "bb9_filesystem_metadata_invalid")
        val artifacts = state.getJSONArray("artifacts")
        requireSafe(artifacts.length() <= 4 && (stage !in setOf("preparing", "complete") || artifacts.length() == 0),
            "bb9_filesystem_metadata_invalid")
        for (index in 0 until artifacts.length()) {
            val record = artifacts.getJSONObject(index)
            requireSafe(record.keys().asSequence().all { it in setOf("role", "type", "sha256", "link", "identity") },
                "bb9_filesystem_metadata_invalid")
            val role = record.getString("role")
            val expected = expectedPayload(role, stage, record)
            if (expected != null) requireSafe(record.getString("type") == "regular" &&
                record.getString("sha256") == digest(expected), "bb9_filesystem_metadata_invalid")
            else if (role == "pending") requireSafe(record.getString("type") == "symlink" &&
                record.getString("link") == "claude",
                "bb9_filesystem_metadata_invalid")
        }
    }

    private fun expectedPayload(role: String, stage: String, record: JSONObject): ByteArray? {
        return when (role) {
            "active" -> payload(stage)
            "pending" -> when (stage) {
                "bogusReceipt" -> "bogus\n".toByteArray(Charsets.US_ASCII)
                "symlinkReceipt" -> null
                "firstInstall", "falseQuiescence", "nonemptyLegacyLock" -> "new\n".toByteArray(Charsets.US_ASCII)
                else -> throw BuiltinComponentUpdateAcceptance.Refused("bb9_filesystem_metadata_invalid")
            }
            "lockMarker" -> {
                requireSafe(stage == "nonemptyLegacyLock", "bb9_filesystem_metadata_invalid"); payload("lock")
            }
            "lock" -> {
                requireSafe(stage == "nonemptyLegacyLock" && record.getString("type") == "directory",
                    "bb9_filesystem_metadata_invalid"); null
            }
            else -> throw BuiltinComponentUpdateAcceptance.Refused("bb9_filesystem_metadata_invalid")
        }
    }

    private fun requireNoForeignJournal(linux: BuiltinLinux) {
        val otherTargets = listOf("opt/opencode", "opt/opencode2", "home/oc/.local/share/oc-paseo/0.9.2-82d16f9c432d",
            "home/oc/.local/share/oc-agents/claude/2.1.283", "usr/local/bin/opencode", "usr/local/bin/opencode2",
            "usr/local/bin/claude", "home/oc/.local/bin/paseo", "home/oc/.local/bin/claude")
        for (target in otherTargets) for (suffix in listOf(".oc-pending", ".oc-pending.new")) {
            val file = File(linux.rootfs, target + suffix)
            // Missing fixed directories cannot contain a receipt. Existing parents must be safe.
            var current = file.parentFile
            while (current != null && stat(current) == null) current = current.parentFile
            requireSafe(current != null, "bb9_path_unsafe")
            ancestry(linux, current!!)
            requireSafe(stat(file) == null, "bb9_existing_journal_refused")
        }
        requireSafe(stat(File(active(linux).path + ".oc-good")) == null &&
            stat(File(active(linux).path + ".new")) == null && stat(File(pending(linux).path + ".new")) == null,
            "bb9_existing_journal_refused")
    }
    private fun save(linux: BuiltinLinux) {
        requireQuiescence(linux); checkAnchors(linux)
        if (stat(metadata) != null) checkMetadataIdentity()
        else requireSafe(metadataIdentity == null, "bb9_filesystem_metadata_changed")
        requireSafe(stat(temporary()) == null, "bb9_filesystem_temporary_refused")
        state.put("app", JSONObject(identity(Process.myPid()).map()))
        val bytes = state.toString().toByteArray(Charsets.UTF_8)
        requireSafe(bytes.size in 1..65536, "bb9_filesystem_metadata_invalid")
        val descriptor = Os.open(temporary().absolutePath,
            OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, 384)
        FileOutputStream(descriptor).use { stream -> stream.write(bytes); stream.fd.sync() }
        requireQuiescence(linux)
        Os.rename(temporary().absolutePath, metadata.absolutePath); sync(context.filesDir)
        metadataIdentity = stat(metadata); metadataDigest = digest(bytes)
        emit { putString("bb9FilesystemMetadataSha256", metadataDigest) }
    }
    private fun checkMetadataIdentity() {
        requireSafe(metadataIdentity?.let { same(it, stat(metadata)) } == true &&
            digest(readNoFollow(metadata, 65536)) == metadataDigest, "bb9_filesystem_metadata_changed")
    }

    private fun ancestry(linux: BuiltinLinux, directory: File) {
        val base = context.filesDir.absoluteFile
        val destination = directory.absoluteFile
        requireSafe(destination.toPath().normalize().toFile() == destination &&
            (destination == base || destination.path.startsWith(base.path + "/")), "bb9_path_unsafe")
        var current = base
        requireDirectory(current)
        if (destination != base) for (segment in destination.path.removePrefix(base.path + "/").split('/')) {
            current = File(current, segment); requireDirectory(current)
        }
        requireSafe(linux.rootfs.absolutePath.startsWith(linux.home.absolutePath + "/"), "bb9_path_unsafe")
    }
    private fun requireDirectory(file: File) = requireSafe(stat(file)?.let { OsConstants.S_ISDIR(it.st_mode) } == true,
        "bb9_path_unsafe")
    private fun stat(file: File): StructStat? = try { Os.lstat(file.absolutePath) }
    catch (error: ErrnoException) { if (error.errno == OsConstants.ENOENT) null else throw error }
    private fun same(before: StructStat, after: StructStat?) = after != null && before.st_dev == after.st_dev &&
        before.st_ino == after.st_ino && before.st_mode == after.st_mode
    private fun stamp(value: StructStat) = JSONObject().put("device", value.st_dev).put("inode",
        value.st_ino).put("mode", value.st_mode)
    private fun matches(value: JSONObject, actual: StructStat?) = actual != null &&
        value.getLong("device") == actual.st_dev &&
        value.getLong("inode") == actual.st_ino && value.getInt("mode") == actual.st_mode
    private fun readNoFollow(file: File, maximum: Int): ByteArray {
        val before = stat(file)
        requireSafe(before != null && OsConstants.S_ISREG(before.st_mode) && before.st_size in 1..maximum.toLong(),
            "bb9_filesystem_fixture_changed")
        val descriptor = Os.open(file.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        val bytes = FileInputStream(descriptor).use { stream ->
            requireSafe(same(before!!, Os.fstat(descriptor)), "bb9_filesystem_fixture_changed")
            val buffer = ByteArray(maximum + 1)
            var count = 0
            while (count < buffer.size) {
                val read = stream.read(buffer, count, buffer.size - count)
                if (read < 0) break
                requireSafe(read > 0, "bb9_filesystem_fixture_changed"); count += read
            }
            requireSafe(count in 1..maximum, "bb9_filesystem_fixture_changed")
            buffer.copyOf(count)
        }
        requireSafe(same(before!!, stat(file)), "bb9_filesystem_fixture_changed")
        return bytes
    }
    private fun sync(directory: File) {
        val before = stat(directory)!!
        requireSafe(OsConstants.S_ISDIR(before.st_mode), "bb9_path_unsafe")
        val descriptor = Os.open(directory.absolutePath,
            OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK, 0)
        try { requireSafe(same(before, Os.fstat(descriptor)), "bb9_path_unsafe"); Os.fsync(descriptor) }
        finally { Os.close(descriptor) }
    }
    private fun digest(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun identity(pid: Int) = RuntimeProcessIdentity.stat(File("/proc/$pid/stat").readText())
    private fun temporary() = File(metadata.parentFile, metadata.name + ".tmp")
    private fun parent(linux: BuiltinLinux) = File(linux.rootfs, "opt/oc-claude")
    private fun active(linux: BuiltinLinux) = File(parent(linux), "claude")
    private fun pending(linux: BuiltinLinux) = File(parent(linux), "claude.oc-pending")
    private fun lock(linux: BuiltinLinux) = File(linux.rootfs, "home/oc/.local/share/oc-agents/.lock-claude")
    private fun artifactFile(linux: BuiltinLinux, role: String): File = when (role) {
        "active" -> active(linux)
        "pending" -> pending(linux)
        "lock" -> lock(linux)
        "lockMarker" -> File(lock(linux), LOCK_MARKER)
        else -> throw BuiltinComponentUpdateAcceptance.Refused("bb9_filesystem_metadata_invalid")
    }
    private fun emit(write: Bundle.() -> Unit) { instrumentation.sendStatus(0, Bundle().apply(write)) }
    private fun requireSafe(value: Boolean,
        code: String) { if (!value) throw BuiltinComponentUpdateAcceptance.Refused(code) }
    companion object {
        const val METADATA_NAME = "bb9-filesystem-qa.json"
        private const val LOCK_MARKER = ".oc-bb9-filesystem-owner"
    }
}
