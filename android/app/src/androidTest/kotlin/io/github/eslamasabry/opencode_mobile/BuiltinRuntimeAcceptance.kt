package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock

/** Test APK only. Exercises a dedicated fixture without replacing the person's services. */
class BuiltinRuntimeAcceptance : Instrumentation() {
    private class Refused(val safeCode: String) : Exception()

    private lateinit var arguments: Bundle
    private var activity: Activity? = null

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
                val linux = BuiltinLinux.get(targetContext)
                requireSafe(linux.installed, "ubuntu_not_initialized")
                when (arguments.getString("step")) {
                    "diagnostics" -> diagnostics(linux)
                    "persisted" -> persisted(linux)
                    else -> throw Refused("invalid_step")
                }
                passed = true
            } catch (failure: Refused) {
                failureCode = failure.safeCode
            } catch (_: Throwable) {
                // Runtime errors and child output may contain private data.
                failureCode = "acceptance_failed"
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
        val expected = arguments.getString("expectedRestarts")?.toLongOrNull()
            ?: throw Refused("expected_restarts_missing")
        requireSafe(expected >= 1L && number(snapshot, "restartCount") == expected,
            "persisted_restarts_invalid")
        val expectedUptime = arguments.getString("expectedUptimeMs")?.toLongOrNull()
            ?: throw Refused("expected_uptime_missing")
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
        const val FIXTURE = "qa-bb1"
    }
}
