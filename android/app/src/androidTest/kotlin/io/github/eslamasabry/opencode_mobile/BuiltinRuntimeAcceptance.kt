package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64
import java.nio.file.Files
import java.nio.file.LinkOption
import org.json.JSONObject

/** Test APK only. Exercises a dedicated fixture without replacing the person's services. */
class BuiltinRuntimeAcceptance : Instrumentation() {
    private class Refused(val safeCode: String) : Exception()

    private lateinit var arguments: Bundle
    private var activity: Activity? = null
    private var phase = "setup"
    private var opencodeHostPath: String? = null

    override fun onCreate(arguments: Bundle?) {
        this.arguments = arguments ?: Bundle()
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        Thread({
            var passed = false
            var failureCode: String? = null
            try {
                executeStep()
                passed = true
            } catch (failure: BuiltinComponentUpdateAcceptance.Refused) {
                failureCode = failure.safeCode
            } catch (failure: BuiltinRuntimeReclaimAcceptance.Refused) {
                failureCode = failure.safeCode
            } catch (failure: Refused) {
                failureCode = failure.safeCode
            } catch (error: Throwable) {
                // Only fixed phase + exception class: no message or child output.
                failureCode = if (arguments.getString("step")?.startsWith("bb3") == true) "bb3_acceptance_failed"
                else "acceptance_failed_${phase}_${error.javaClass.simpleName}"
            } finally {
                activity?.let { current ->
                    try { runOnMainSync { current.finish() } } catch (_: Throwable) { }
                }
                finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                    Bundle().apply {
                    putString("builtinRuntimeResult", if (passed) "PASS" else "FAIL")
                    failureCode?.let { putString("builtinRuntimeFailure", it) }
                })
            }
        }, "builtin-runtime-acceptance").start()
    }

    private fun supervision(linux: BuiltinLinux) {
        requireSafe(!linux.serverRunning && !linux.serverRestartWanted, "person_server_intent_present")
        val profile = "qa_bb2_supervision"
        val fixtureRoot = File(linux.rootfs, "root/.oc-bb2-qa")
        requireSafe(!Files.exists(fixtureRoot.toPath(), LinkOption.NOFOLLOW_LINKS), "qa_fixture_directory_exists")
        val flutterPrefs = targetContext.getSharedPreferences("FlutterSharedPreferences", 0)
        val marker = "flutter.oc.builtinRecovery.$profile"
        val policy = "flutter.oc.automation.$profile"
        requireSafe(!flutterPrefs.contains(marker) && !flutterPrefs.contains(policy), "qa_profile_exists")
        val nativePrefs = targetContext.getSharedPreferences("builtin_server_recovery", 0)
        requireSafe(!nativePrefs.contains("oc.builtinRecoveryBudget.$profile"), "qa_native_record_exists")
        val probe = linux.run("command -v opencode >/dev/null 2>&1 && test -s /root/.oc-builtin/server.password", 10L)
        requireSafe(probe.exitCode == 0, "actual_opencode_prerequisite_missing")
        val binary = linux.run("readlink -f \"${'$'}(command -v opencode)\"", 10L)
        val guestBinary = binary.output.trim()
        requireSafe(binary.exitCode == 0 && guestBinary.startsWith("/") && !guestBinary.contains('\n'),
            "actual_opencode_path_unavailable")
        opencodeHostPath = File(linux.rootfs, guestBinary.removePrefix("/")).canonicalPath
        val passwordFile = File(linux.rootfs, "root/.oc-builtin/server.password")
        val password = passwordFile.readText().trim()
        requireSafe(password.isNotEmpty(), "private_password_missing")
        // Fixtures have isolated config/data/projects. No prompt or account-auth operations.
        val script = supervisionScript()
        val bootstrap = "qa-bb2-bootstrap"
        requireSafe(bootstrap !in linux.runningServices(), "qa_bootstrap_already_running")
        try {
            phase = "stage_qa"
            linux.stageServerRecovery(profile, NativeRecoveryBudget().map())
            requireSafe(flutterPrefs.edit().putString(marker, "{\"version\":2,\"nativeAuthority\":true}")
                .putString(policy,
                QA_POLICY_ENABLED).commit(), "qa_marker_write_failed")
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            phase = "bootstrap_launch"
            // Establish our temporary FGS child while foreground. The Flutter
            // owner must finish before the isolated server binding is installed.
            linux.startService(bootstrap, "sleep 60", null, null)
            // Leave no resumed Activity/Dart health owner before initial health.
            drainSupervisionActivity(linux)
            phase = "bind_qa"
            linux.bindServerRecovery(profile, null, false)
            requireSafe(nativePrefs.getString("owner", null) == profile &&
                !nativePrefs.getBoolean("enabled", true), "qa_initial_binding_not_disabled")
            phase = "actual_server_launch"
            linux.startServer(script, 4097)
            requireSafe(linux.serverRunning, "qa_initial_server_not_tracked")
            phase = "bootstrap_remove"
            linux.stopService(bootstrap)
            requireSafe(nativePrefs.getString("owner", null) == profile &&
                !nativePrefs.getBoolean("enabled", true), "qa_initial_binding_changed")
            phase = "initial_health"
            awaitHealth(linux, password)
            phase = "initial_pid"
            val firstPid = serverPid(linux)
            linux.bindServerRecovery(profile, null, true)
            val pid = restartAttempts(linux, profile, nativePrefs, password, firstPid)
            exhaustBudget(linux, profile, pid)
            resetManualStart(linux, profile, flutterPrefs, script, password)
        } finally {
            cleanupSupervision(linux, profile, nativePrefs, flutterPrefs)
        }
    }

    private fun awaitHealth(linux: BuiltinLinux, password: String) {
        val deadline = SystemClock.elapsedRealtime() + 60000L
        val auth = Base64.encodeToString("opencode:$password".toByteArray(), Base64.NO_WRAP)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (linux.serverRunning) {
                val healthy = healthProbe(auth)
                if (healthy) return
            }
            Thread.sleep(100L)
        }
        throw Refused("actual_opencode_health_timeout")
    }

    /** Select only descendants of this exact tracked service, then kill its exact executable PID. */
    private fun trackedServerPids(linux: BuiltinLinux): Set<Int> {
        val servicesField = BuiltinLinux::class.java.getDeclaredField("services").apply { isAccessible = true }
        val service = (servicesField.get(linux) as Map<*, *>)[BuiltinLinux.SERVER]
        ?: throw Refused("tracked_server_missing")
        val processField = service.javaClass.getDeclaredField("process").apply { isAccessible = true }
        val process = processField.get(service) as Process
        val pidField = process.javaClass.getDeclaredField("pid").apply { isAccessible = true }
        val root = (pidField.get(process) as Number).toInt()
        val parents = File("/proc").listFiles().orEmpty().mapNotNull { dir ->
            val pid = dir.name.toIntOrNull() ?: return@mapNotNull null
            val parent = try { File(dir, "stat").readText().substringAfterLast(')').trim()
                    .split(' ').getOrNull(1)?.toIntOrNull() } catch (_: Throwable) { null }
            parent?.let { pid to it }
        }.toMap()
        val descendants = mutableSetOf(root)
        repeat(parents.size) { parents.forEach { (pid, parent) -> if (parent in descendants) descendants.add(pid) } }
        return descendants
    }

    private fun serverPid(linux: BuiltinLinux): Int {
        val expected = opencodeHostPath ?: throw Refused("actual_opencode_path_unavailable")
        val candidates = trackedServerPids(linux)
        val matches = candidates.filter { pid ->
            // PRoot can expose linker64 as /proc/exe for a guest ELF. Require
            // the real executable mapping and exact tracked descendant owner.
            try { File("/proc/$pid/maps").useLines { lines -> lines.any { line ->
                        val fields = line.split(Regex("\\s+"), limit = 6)
                        fields.size == 6 && fields[1].contains('x') &&
                            File(fields[5].removeSuffix(" (deleted)")).canonicalPath == expected
                    } } } catch (_: Throwable) { false }
        }
        if (matches.size != 1) {
            candidates.forEach { pid ->
                val executable = try { File(Os.readlink("/proc/$pid/exe"))
                        .name } catch (_: Throwable) { "unavailable" }
                val safe = executable.takeIf { it in setOf("opencode", "linker64", "proot", "sh", "bash",
                        "unavailable") } ?: "other"
                sendStatus(0, Bundle().apply { putString("trackedExecutableClass", safe) })
            }
            throw Refused("actual_opencode_pid_missing")
        }
        return matches.single()
    }

    private fun diagnostics(linux: BuiltinLinux) {
        requireSafe(FIXTURE !in linux.runningServices(), "fixture_already_running")
        // The real activity must be visible before Android admits foreground work.
        activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        linux.startService(FIXTURE, "sleep 2; exit 137", null, null)
        val first = awaitStopped(linux, 137)
        requireSafe(first["exitReason"] == "memory_or_phantom_kill", "exit_reason_invalid")
        val firstRestarts = number(first, "restartCount")
        requireSafe(firstRestarts >= 0L, "restart_count_invalid")
        emit(first)

        linux.startService(FIXTURE, "sleep 2; exit 7", null, null)
        val second = awaitStopped(linux, 7)
        requireSafe(number(second, "restartCount") == firstRestarts + 1L,
            "restart_count_not_incremented")
        emit(second)
        deletion(linux)
    }

    private fun deletion(linux: BuiltinLinux) {
        val profile = "qa_bb1_profile"
        val names = setOf("agent-auth.$profile", "agent-host.$profile")
        val preferences = targetContext.getSharedPreferences("builtin_service_diagnostics", 0)
        val known = preferences.getStringSet("names", emptySet()).orEmpty()
        requireSafe(names.none { it in known }, "deletion_fixture_exists")
        val edit = preferences.edit().putStringSet("names", known + names)
        names.forEach { name ->
            edit.putInt("$name.exit", 137).putLong("$name.uptime", 1234L)
                .putInt("$name.restarts", 2).putBoolean("$name.launched", true)
        }
        requireSafe(edit.commit(), "deletion_fixture_failed")
        linux.deleteAgentHome(profile)
        requireSafe(names.none { (linux.performance()["services"] as? Map<*, *>)?.containsKey(it) == true },
            "profile_diagnostics_retained")
        requireSafe(names.none { name -> listOf("exit", "uptime", "restarts", "launched")
                .any { preferences.contains("$name.$it") } }, "profile_diagnostic_keys_retained")
        sendStatus(0, Bundle().apply { putBoolean("profileDiagnosticsDeleted", true) })
    }

    private fun persisted(linux: BuiltinLinux) {
        val snapshot = service(linux) ?: throw Refused("persisted_service_missing")
        requireSafe(snapshot["running"] == false, "persisted_service_running")
        requireSafe(number(snapshot, "lastExitCode") == 7L, "persisted_exit_invalid")
        requireSafe(number(snapshot, "lastUptimeMs") >= 1_000L, "persisted_uptime_invalid")
        val expected = expectedLong("expectedRestarts", "expected_restarts_missing")
        requireSafe(expected >= 1L && number(snapshot, "restartCount") == expected,
            "persisted_restarts_invalid")
        val expectedUptime = expectedLong("expectedUptimeMs", "expected_uptime_missing")
        requireSafe(number(snapshot, "lastUptimeMs") == expectedUptime, "persisted_uptime_changed")
        requireSafe(snapshot["uptimeMs"] == null, "stopped_uptime_present")
        emit(snapshot)
    }

    private fun awaitStopped(linux: BuiltinLinux, expectedExit: Long): Map<*, *> {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val snapshot = service(linux)
            if (snapshot != null && snapshot["running"] == false &&
                (snapshot["lastExitCode"] as? Number)?.toLong() == expectedExit) {
                requireSafe(number(snapshot, "lastUptimeMs") >= 1_000L,
                    "service_uptime_invalid")
                requireSafe(snapshot["uptimeMs"] == null, "stopped_uptime_present")
                return snapshot
            }
            Thread.sleep(100L)
        }
        throw Refused("service_exit_timeout")
    }

    private fun service(linux: BuiltinLinux): Map<*, *>? =
        (linux.performance()["services"] as? Map<*, *>)?.get(FIXTURE) as? Map<*, *>

    private fun number(snapshot: Map<*, *>, key: String): Long =
        (snapshot[key] as? Number)?.toLong() ?: throw Refused("diagnostic_field_missing")

    private fun emit(snapshot: Map<*, *>) {
        sendStatus(0, Bundle().apply {
            putLong("lastExitCode", number(snapshot, "lastExitCode"))
            putLong("lastUptimeMs", number(snapshot, "lastUptimeMs"))
            putLong("restartCount", number(snapshot, "restartCount"))
            putBoolean("running", snapshot["running"] == true)
            // Admit one known enum; never pass a runtime string through unchecked.
            if (snapshot["exitReason"] == "memory_or_phantom_kill") {
                putString("exitReason", "memory_or_phantom_kill")
            }
        })
    }

    private fun requireSafe(condition: Boolean, safeCode: String) {
        if (!condition) throw Refused(safeCode)
    }

    private companion object {
        private const val QA_POLICY_DISABLED =
            "{\"version\":1,\"supervision\":\"high\",\"behaviors\":" +
        "{\"restartPhoneServer\":false,\"pollRestartHealth\":false}}"

        private const val QA_POLICY_ENABLED =
            "{\"version\":1,\"supervision\":\"high\",\"behaviors\":" +
        "{\"restartPhoneServer\":true,\"pollRestartHealth\":true}}"

        const val FIXTURE = "qa-bb1"
    }
    private fun executeStep() {
        if (arguments.getString("step")?.startsWith("bb9") == true) {
            BuiltinComponentUpdateAcceptance(this, arguments).execute()
            return
        }
        if (arguments.getString("step")?.startsWith("bb3") == true) {
            BuiltinRuntimeReclaimAcceptance(this, arguments).execute()
            return
        }
        dispatchStep()
    }

    private fun dispatchStep() {
        val linux = BuiltinLinux.get(targetContext)
        requireSafe(linux.installed, "ubuntu_not_initialized")
        when (arguments.getString("step")) {
            "diagnostics" -> diagnostics(linux)
            "persisted" -> persisted(linux)
            "supervision" -> supervision(linux)
            "stopForQa" -> stopForQa(linux)
            "stopPersisted" -> {
                requireSafe(!linux.serverRestartWanted && !linux.serverRunning, "explicit_stop_not_persisted")
                sendStatus(0, Bundle().apply { putBoolean("userStopPersisted", true) })
            }
            else -> throw Refused("invalid_step")
        }
    }

    private fun stopForQa(linux: BuiltinLinux) {

        val owned = if (linux.serverRunning) trackedServerPids(linux) else emptySet()
        linux.requestServerStop()
        linux.stopServer()
        val deadline = SystemClock.elapsedRealtime() + 10000L
        fun drained() = owned.none { pid ->
            try { File("/proc/$pid/stat").readText().substringAfterLast(')').trim()
                    .substringBefore(' ') !in setOf("Z", "X") } catch (_: Throwable) { false }
        }
        while (!drained() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100L)
        requireSafe(drained() && !linux.serverRunning && !linux.serverRestartWanted,
            "qa_initial_stop_not_drained")
        sendStatus(0, Bundle().apply { putBoolean("qaInitialStopDrained", true) })
    }

    private fun restartAttempts(linux: BuiltinLinux, profile: String,
        nativePrefs: android.content.SharedPreferences, password: String, firstPid: Int): Int {
        var pid = firstPid
        for (attempt in 1..3) {
            phase = "crash_$attempt"
            requireSafe(nativePrefs.getString("owner", null) == profile, "qa_supervision_owner_changed")
            val killedAt = SystemClock.elapsedRealtime()
            Os.kill(pid, OsConstants.SIGKILL)
            val observation = observeReplacement(linux, pid, killedAt)
            val replacement = observation.pid
            val retainedForeground = observation.retainedForeground
            val observedMs = observation.observedMs
            pid = replacement
            requireSafe((linux.serverRecoveryBudget(profile)["attempts"] as? Number)?.toInt() == attempt,
                "native_budget_not_consumed")
            awaitHealth(linux, password)
            @Suppress("DEPRECATION")
            val stillForeground = targetContext.getSystemService(ActivityManager::class.java)
                .getRunningServices(100).any { it.service.className == BuiltinServerService::class.java.name &&
                    it.foreground }
            requireSafe(stillForeground, "reserved_attempt_foreground_lost")
            sendStatus(0, Bundle().apply {
                putInt("nativeAttempts", attempt)
                putLong("restartObservedMs", observedMs)
                putBoolean("actualOpenCodeHealthy", true)
                putBoolean("activityAbsent", true)
                putBoolean("restartForegroundRetained", retainedForeground)
            })
        }
        return pid
    }

    private fun exhaustBudget(linux: BuiltinLinux, profile: String, pid: Int) {
        Os.kill(pid, OsConstants.SIGKILL)
        val exhaustedDeadline = SystemClock.elapsedRealtime() + 65000L
        var quiescent = false
        while (SystemClock.elapsedRealtime() < exhaustedDeadline) {
            val candidate = if (linux.serverRunning) try { serverPid(linux) } catch (_: Throwable) { null } else null
            requireSafe(candidate == null || candidate == pid, "fourth_restart_bypassed_budget")
            val diagnostic = (linux.performance()["services"] as? Map<*, *>)?.get(BuiltinLinux.SERVER) as? Map<*, *>
            @Suppress("DEPRECATION")
            val foreground = targetContext.getSystemService(ActivityManager::class.java)
                .getRunningServices(100).any { it.service.className == BuiltinServerService::class.java.name &&
                    it.foreground }
            if (isQuiescent(linux, foreground, diagnostic)) {
                quiescent = true
                break
            }
            Thread.sleep(100L)
        }
        requireSafe(quiescent && !linux.serverRunning, "exhausted_runtime_not_quiescent")
        requireSafe((linux.serverRecoveryBudget(profile)["attempts"] as? Number)?.toInt() == 3,
            "exhausted_budget_not_preserved")
    }

    private fun resetManualStart(linux: BuiltinLinux, profile: String,
        flutterPrefs: android.content.SharedPreferences, script: String, password: String) {
        val policy = "flutter.oc.automation.$profile"
        // A person's healthy Start resets the count even with automation disabled.
        requireSafe(flutterPrefs.edit().putString(policy,
            QA_POLICY_DISABLED).commit(), "qa_policy_disable_failed")
        activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        linux.setActivityResumed(true)
        linux.startServer(script, 4097)
        awaitHealth(linux, password)
        synchronized(linux) {
            linux.bindServerRecovery(profile, null, false)
            requireSafe((linux.confirmManualServerStart(profile)["attempts"] as? Number)?.toInt() == 0,
                "policy_off_manual_reset_failed")
        }
        sendStatus(0, Bundle().apply { putBoolean("policyOffManualReset", true) })
        linux.stopServer()
        requireSafe(!linux.serverRestartWanted, "explicit_stop_intent_not_cleared")
        sendStatus(0, Bundle().apply { putBoolean("nativeBudgetExhausted", true) })
    }

    private fun supervisionScript(): String {
        return """
            set -eu
            mkdir -p /root/.oc-bb2-qa/projects /root/.oc-bb2-qa/config
            cd /root/.oc-bb2-qa/projects
            export XDG_DATA_HOME=/root/.oc-bb2-qa/data XDG_CACHE_HOME=/root/.oc-bb2-qa/cache
            export XDG_STATE_HOME=/root/.oc-bb2-qa/state XDG_CONFIG_HOME=/root/.oc-bb2-qa/config
            export OPENCODE_CONFIG_DIR=/root/.oc-bb2-qa/config
            unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT
            export OPENCODE_SERVER_USERNAME=opencode
            export OPENCODE_SERVER_PASSWORD="${'$'}(cat /root/.oc-builtin/server.password)"
            export OPENCODE_PASSWORD="${'$'}OPENCODE_SERVER_PASSWORD"
            exec opencode serve --hostname 127.0.0.1 --port 4097
        """.trimIndent()
    }

    private fun isQuiescent(linux: BuiltinLinux, foreground: Boolean, diagnostic: Map<*, *>?) =
        !linux.serverRunning && !linux.serverRecoveryScheduled && !foreground &&
        diagnostic?.get("lastExitCode") is Number && diagnostic["running"] == false

    private fun healthProbe(auth: String): Boolean {
        return try {
            val connection = URL("http://127.0.0.1:4097/global/health").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 500
                connection.readTimeout = 500
                connection.setRequestProperty("Authorization", "Basic $auth")
                if (connection.responseCode != 200) false else {
                    val body = readHealthBody(connection)
                    val health = JSONObject(body)
                    health.optBoolean("healthy", false) && health.optString("version").isNotEmpty()
                }
            } finally { connection.disconnect() }
        } catch (_: Throwable) { false }
    }

    private fun expectedLong(name: String, code: String): Long =
        arguments.getString(name)?.toLongOrNull() ?: throw Refused(code)

    private fun readHealthBody(connection: HttpURLConnection): String {
        return connection.inputStream.bufferedReader().use { reader ->
            val buffer = CharArray(4096)
            val count = reader.read(buffer)
            if (count <= 0) "" else String(buffer, 0, count)
        }
    }

    private fun cleanupSupervision(linux: BuiltinLinux, profile: String,
        nativePrefs: android.content.SharedPreferences, flutterPrefs: android.content.SharedPreferences) {
        val marker = "flutter.oc.builtinRecovery.$profile"
        val policy = "flutter.oc.automation.$profile"

        linux.stopService("qa-bb2-bootstrap")
        linux.unbindServerRecovery(profile)
        try { linux.requestServerStop() } catch (_: Throwable) { }
        try { linux.stopServer() } catch (_: Throwable) { }
        requireSafe(!linux.serverRunning, "qa_cleanup_server_not_drained")
        linux.deleteServerRecovery(profile)
        requireSafe(!nativePrefs.contains("oc.builtinRecoveryBudget.$profile") &&
            !nativePrefs.contains("oc.builtinRecoveryReceipts.$profile"), "qa_scoped_keys_retained")
        requireSafe(flutterPrefs.edit().remove(marker).remove(policy).commit(), "qa_marker_cleanup_failed")
        // Exact fixture directory only, after the child drains.
        val fixture = File(linux.rootfs, "root/.oc-bb2-qa")
        requireSafe(fixture.canonicalPath.startsWith(linux.rootfs.canonicalPath + File.separator),
            "qa_fixture_path_invalid")
        if (fixture.exists()) requireSafe(fixture.deleteRecursively(), "qa_fixture_cleanup_failed")    }

    private data class RestartObservation(val pid: Int, val retainedForeground: Boolean, val observedMs: Long)

    private fun observeReplacement(linux: BuiltinLinux, pid: Int, killedAt: Long): RestartObservation {
        val deadline = killedAt + 60000L
        var replacement: Int? = null
        var retainedForeground = false
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!linux.serverRunning) {
                @Suppress("DEPRECATION")
                val foreground = targetContext.getSystemService(ActivityManager::class.java)
                    .getRunningServices(100).any { it.service.className == BuiltinServerService::class.java
                        .name && it.foreground }
                requireSafe(foreground, "restart_foreground_not_retained")
                retainedForeground = true
            }
            if (linux.serverRunning) {
                val candidate = try { serverPid(linux) } catch (_: Throwable) { null }
                if (candidate != null && candidate != pid) { replacement = candidate; break }
            }
            Thread.sleep(100L)
        }
        requireSafe(replacement != null, "native_restart_timeout")
        requireSafe(retainedForeground, "restart_foreground_window_not_observed")
        val observedMs = SystemClock.elapsedRealtime() - killedAt
        requireSafe(observedMs in 1000L..60000L, "native_restart_delay_out_of_bounds")
        return RestartObservation(replacement!!, retainedForeground, observedMs)
    }

    private fun drainSupervisionActivity(linux: BuiltinLinux) {
        val initialActivity = activity!!
        runOnMainSync { initialActivity.finish() }
        val destroyDeadline = SystemClock.elapsedRealtime() + 10000L
        while (!initialActivity.isDestroyed && SystemClock.elapsedRealtime() < destroyDeadline) Thread.sleep(100L)
        requireSafe(initialActivity.isDestroyed, "qa_activity_not_destroyed")
        activity = null
        phase = "activity_drain"
        linux.setActivityResumed(false)
        // Let already dispatched activity callbacks finish; after engine
        // destruction there is no Dart consumer issuing another binding.
        Thread.sleep(2000L)
    }

}
