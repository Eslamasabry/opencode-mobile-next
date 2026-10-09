package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Private QA workload: real canonical OpenCode2 and an empty local helper, never agent auth.
 * The canonical recipe uses the person's existing /root/projects and .oc-opencode2 data/config;
 * only helper home/fixture metadata is isolated. No agent, session or config mutation is issued.
 */
internal class BuiltinIdleAcceptance(
    private val instrumentation: Instrumentation,
    private val arguments: Bundle,
) {
    class Refused(val safeCode: String) : Exception()
    private val context get() = instrumentation.targetContext
    private val native get() = context.getSharedPreferences("builtin_server_recovery", 0)
    private val flutter get() = context.getSharedPreferences("FlutterSharedPreferences", 0)
    private val fixture get() = File(context.filesDir, "bb5-runtime-qa.json")
    private val marker get() = "flutter.oc.builtinRecovery.$PROFILE"
    private val policy get() = "flutter.oc.automation.$PROFILE"
    private val helperName get() = "agent-host.$PROFILE"

    fun execute() {
        requireSafe(BuildConfig.BUILTIN_RUNTIME_QA, "bb5_qa_build_required")
        val linux = BuiltinLinux.get(context)
        when (arguments.getString("step")) {
            "bb5Idle" -> idle(linux)
            "bb5Cleanup" -> cleanup(linux)
            else -> throw Refused("bb5_step_invalid")
        }
    }

    private fun idle(linux: BuiltinLinux) {
        requireSafe(linux.installed && linux.serverRunning && linux.serverRestorationArmed,
            "bb5_canonical_baseline_required")
        requireSafe(!fixture.exists() && !File(fixture.path + ".tmp").exists() &&
            !home(linux).exists() && !flutter.contains(marker) && !flutter.contains(policy) &&
            !flutter.contains("flutter.oc.phoneAgentOwner.$PROFILE") &&
            native.all.keys.none { it.endsWith(".$PROFILE") }, "bb5_fixture_already_present")
        val initial = linux.serverIdleStatus()
        val enabled = initial["serverIdleEnabled"] as? Boolean
        val minutes = initial["serverIdleMinutes"] as? Int
        requireSafe(enabled != null && minutes != null && !idleMarked(initial) && initial["serverIdleHelperStopped"] == false, "bb5_idle_baseline_unavailable")
        val firstActivity = existingActivity() ?: throw Refused("bb5_real_activity_required")
        await(5_000L, "bb5_real_foreground_unavailable") { resumed(linux) }
        requireQuiescentServerOnly(linux)
        save(JSONObject().put("version", 1).put("owner", PROFILE)
            .put("priorIdleEnabled", enabled).put("priorIdleMinutes", minutes))
        var helper: Process? = null
        var replacement: Process? = null
        try {
            linux.requestServerStop()
            linux.stopServer()
            await(8_000L, "bb5_person_runtime_not_drained") { !linux.serverRunning }
            linux.stageServerRecovery(PROFILE, NativeRecoveryBudget(attempts = 1).map())
            requireSafe(flutter.edit().putString(marker, "{\"version\":2,\"nativeAuthority\":true}")
                .putString(policy, QA_POLICY).commit(), "bb5_fixture_save_failed")
            linux.bindServerRecovery(PROFILE, null, true)
            val recipe = NativeServerRecipe(PROFILE, "openCode2", 1L, "0".repeat(64))
            linux.startServer(recipe.restorationScript(), 4097,
                mapOf("version" to 1, "profileId" to PROFILE, "runtime" to "openCode2"))
            awaitHealth(linux)
            requireOwner()
            val firstHelper = startHelper(linux, false, null)
            helper = firstHelper
            linux.setPhoneServerIdlePolicy(true, 1)
            val budget = linux.serverRecoveryBudget(PROFILE)
            val old = runtimeMembers(linux)
            requireSafe(old.isNotEmpty() && firstHelper.isAlive, "bb5_original_runtime_unproven")
            save(JSONObject(fixture.readText()).put("members", org.json.JSONArray(old.map { JSONObject(it.map()) })))

            // Foreground and unknown work must not create an idle-stop token.
            linux.observePhoneAgentWork(PROFILE, false)
            requireSafe(!idleMarked(linux.serverIdleStatus()), "bb5_foreground_idle_stop")
            instrumentation.runOnMainSync {
                requireSafe(firstActivity.moveTaskToBack(true), "bb5_background_transition_refused")
            }
            await(5_000L, "bb5_background_transition_unproven") { !resumed(linux) }
            linux.observePhoneAgentWork(PROFILE, null)
            Thread.sleep(250L)
            requireOwner()
            requireSafe(!idleMarked(linux.serverIdleStatus()) && linux.serverRunning,
                "bb5_unknown_work_stopped_runtime")

            val started = SystemClock.elapsedRealtime()
            var nextHeartbeat = started
            await(75_000L, "bb5_real_idle_deadline_not_reached") {
                requireOwner()
                requireSafe(!resumed(linux), "bb5_background_lost")
                val now = SystemClock.elapsedRealtime()
                if (now >= nextHeartbeat) {
                    linux.observePhoneAgentWork(PROFILE, false)
                    nextHeartbeat = now + 10_000L
                }
                idleMarked(linux.serverIdleStatus())
            }
            requireSafe(SystemClock.elapsedRealtime() - started >= 60_000L, "bb5_idle_stopped_early")
            val paused = linux.serverIdleStatus()
            val generation = (paused["serverIdleGeneration"] as? Number)?.toLong()
                ?: throw Refused("bb5_idle_generation_missing")
            requireSafe(generation > 0 && paused["serverIdleHelperStopped"] == true &&
                linux.serverRestartWanted, "bb5_idle_intent_unproven")
            val persisted = JSONObject(native.getString("idleState", null)
                ?: throw Refused("bb5_idle_intent_unproven"))
            requireSafe(persisted.getBoolean("stopped") && persisted.getBoolean("helperStopped") &&
                persisted.getLong("generation") == generation && persisted.getString("owner") == PROFILE &&
                persisted.getString("helper") == PROFILE, "bb5_idle_intent_unproven")
            await(12_000L, "bb5_exact_idle_tree_not_drained") {
                !linux.serverRunning && !firstHelper.isAlive && old.all(::confirmedGone)
            }
            Thread.sleep(2_000L)
            requireSafe(idleMarked(linux.serverIdleStatus()) && !linux.serverRunning &&
                (linux.serverIdleStatus()["serverIdleGeneration"] as? Number)?.toLong() == generation &&
                linux.serverRecoveryBudget(PROFILE) == budget, "bb5_idle_resurrected_or_spent_budget")
            emit("bb5RealIdlePassed", "bb5ExactDrainPassed", "bb5StoppedIntentPassed")

            val notices = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notice = notices.activeNotifications.singleOrNull {
                it.id == NativeIdleReturnNotificationHost.ID
            }?.notification ?: throw Refused("bb5_idle_notification_missing")
            requireSafe(notice.contentIntent != null && notice.actions.isNullOrEmpty() &&
                notice.flags and Notification.FLAG_ONGOING_EVENT == 0 &&
                notice.flags and Notification.FLAG_FOREGROUND_SERVICE == 0 &&
                notice.flags and Notification.FLAG_AUTO_CANCEL != 0,
                "bb5_idle_notification_not_return_only")
            emit("bb5IdleNotificationPassed", "bb5AwaitNotificationTap")
            // Host taps the actual SystemUI notification. No synthetic PendingIntent or
            // instrumentation activity launch counts as notification-tap qualification.
            await(30_000L, "bb5_notification_tap_foreground_unproven") {
                existingActivity() != null && resumed(linux)
            }
            requireSafe(notices.activeNotifications.none {
                it.id == NativeIdleReturnNotificationHost.ID
            }, "bb5_foreground_idle_notification_retained")
            emit("bb5NotificationTapPassed")
            requireOwner() // Main/Dart owner rebinding is a refusal, never an acceptance bypass.
            linux.resumeIdleStoppedPhoneServer(PROFILE, generation)
            awaitHealth(linux)
            requireSafe(!idleMarked(linux.serverIdleStatus()) &&
                linux.serverIdleStatus()["serverIdleHelperStopped"] == true,
                "bb5_server_resume_unproven")
            requireSafe(runCatching { linux.completePhoneServerIdleResume(PROFILE, generation) }.isFailure,
                "bb5_missing_helper_ack_admitted")
            val nextHelper = startHelper(linux, true, generation)
            replacement = nextHelper
            val done = linux.completePhoneServerIdleResume(PROFILE, generation)
            requireSafe(!idleMarked(done) && done["serverIdleHelperStopped"] == false &&
                (done["serverIdleGeneration"] as? Number)?.toLong() == 0L && nextHelper.isAlive &&
                linux.serverRecoveryBudget(PROFILE) == budget, "bb5_helper_completion_unproven")
            emit("bb5ForegroundResumePassed", "bb5HelperAcknowledgementPassed", "bb5BudgetPreserved")

            linux.requestServerStop()
            requireSafe(runCatching { linux.resumeIdleStoppedPhoneServer(PROFILE, generation) }.isFailure &&
                linux.captureAgentHostStart(PROFILE, true, generation) == null,
                "bb5_explicit_stop_lost")
            linux.stopServer()
            linux.stopService(helperName)
            await(8_000L, "bb5_explicit_stop_not_drained") { !linux.serverRunning && !nextHelper.isAlive }
            requireSafe(!linux.serverRestartWanted && !idleMarked(linux.serverIdleStatus()) &&
                (linux.serverIdleStatus()["serverIdleGeneration"] as? Number)?.toLong() == 0L &&
                linux.serverRecoveryBudget(PROFILE) == budget, "bb5_explicit_stop_intent_unproven")
            emit("bb5ExplicitStopPassed", "bb5IdlePassed")
        } finally {
            // Exact Process objects we created; no PID selection, credential or helper lock operations.
            for (process in listOfNotNull(helper, replacement)) if (process.isAlive) linux.stopAgentProcess(process)
            cleanup(linux)
        }
    }

    private fun startHelper(linux: BuiltinLinux, resume: Boolean, generation: Long?): Process {
        val ticket = linux.captureAgentHostStart(PROFILE, resume, generation)
            ?: throw Refused("bb5_helper_admission_unavailable")
        val process = linux.withAgentHostStart(ticket) {
            linux.startAgentProcess(PROFILE, listOf("/bin/sh", "-c", "IFS= read -r unused"), privateOutput = true)
        }
        try { linux.trackPrivateAgentService(helperName, process, null) }
        catch (_: Throwable) { linux.stopAgentProcess(process); throw Refused("bb5_helper_tracking_refused") }
        return process
    }

    private fun cleanup(linux: BuiltinLinux) {
        if (!fixture.exists()) { emit("bb5CleanupComplete"); return }
        requireSafe(fixture.canonicalFile == fixture.absoluteFile && fixture.length() in 1..65_536L,
            "bb5_cleanup_fixture_invalid")
        val saved = JSONObject(fixture.readText())
        val keys = saved.keys().asSequence().toSet()
        requireSafe(keys == setOf("version", "owner", "priorIdleEnabled", "priorIdleMinutes") ||
            keys == setOf("version", "owner", "priorIdleEnabled", "priorIdleMinutes", "members"),
            "bb5_cleanup_fixture_invalid")
        requireSafe(saved.opt("version") == 1 && saved.opt("owner") == PROFILE &&
            saved.opt("priorIdleEnabled") is Boolean && saved.opt("priorIdleMinutes") is Int &&
            saved.getInt("priorIdleMinutes") in 1..60,
            "bb5_cleanup_fixture_invalid")
        val owner = native.getString("owner", null)
        requireSafe(owner == PROFILE || (owner != PROFILE && !native.contains("oc.builtinRecoveryBudget.$PROFILE")),
            "bb5_cleanup_owner_changed")
        if (owner == PROFILE) {
            // Native receipt is signal authority; saved QA members below are absence evidence only.
            linux.requestServerStop(includeOther = true)
            linux.stopServer()
            linux.stopService(helperName)
            await(8_000L, "bb5_cleanup_runtime_not_drained") { !linux.serverRunning }
        }
        val savedMembers = if (saved.has("members")) {
            val rows = saved.getJSONArray("members")
            requireSafe(rows.length() <= 128, "bb5_cleanup_fixture_invalid")
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val map = row.keys().asSequence().associateWith { row.get(it) }
                RuntimeProcessIdentity.read(map)
            }.also { rows ->
                requireSafe(rows.map { it.pid }.distinct().size == rows.size, "bb5_cleanup_fixture_invalid")
            }
        } else emptyList()
        await(8_000L, "bb5_cleanup_saved_members_not_gone") { savedMembers.all(::confirmedGone) }
        if (owner == PROFILE) linux.deleteServerRecovery(PROFILE)
        foreground(linux)
        linux.setPhoneServerIdlePolicy(saved.getBoolean("priorIdleEnabled"), saved.getInt("priorIdleMinutes"))
        requireSafe(flutter.edit().remove(marker).remove(policy).commit(), "bb5_cleanup_preferences_failed")
        linux.deleteAgentHome(PROFILE)
        requireSafe(!home(linux).exists(), "bb5_cleanup_helper_home_retained")
        requireSafe(fixture.delete(), "bb5_cleanup_fixture_retained")
        emit("bb5CleanupComplete")
    }

    private fun foreground(linux: BuiltinLinux) {
        instrumentation.runOnMainSync {
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        await(5_000L, "bb5_real_foreground_unavailable") { existingActivity() != null && resumed(linux) }
    }

    private fun existingActivity(): Activity? {
        var activity: Activity? = null
        instrumentation.runOnMainSync {
            val type = Class.forName("android.app.ActivityThread")
            val thread = type.getDeclaredMethod("currentActivityThread").invoke(null)
            val records = type.getDeclaredField("mActivities").apply { isAccessible = true }.get(thread) as Map<*, *>
            activity = records.values.mapNotNull { record -> record?.javaClass?.getDeclaredField("activity")
                ?.apply { isAccessible = true }?.get(record) as? MainActivity }.singleOrNull { !it.isDestroyed }
        }
        return activity
    }

    private fun resumed(linux: BuiltinLinux): Boolean {
        val lock = BuiltinLinux::class.java.getDeclaredField("recoveryLock")
            .apply { isAccessible = true }.get(linux)
        return synchronized(lock) {
            BuiltinLinux::class.java.getDeclaredField("activityResumed")
                .apply { isAccessible = true }.getBoolean(linux)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun requireQuiescentServerOnly(linux: BuiltinLinux) = synchronized(linux) {
        requireSafe(LocalTerminal.get(context).list().none { it.running }, "bb5_active_terminal_refused")
        requireSafe(linux.idleWorkBusy() == false,
            "bb5_active_or_unknown_work_refused")
        val services = BuiltinLinux::class.java.getDeclaredField("services")
            .apply { isAccessible = true }.get(linux) as Map<String, *>
        val server = services[BuiltinLinux.SERVER] ?: throw Refused("bb5_canonical_baseline_required")
        fun process(service: Any): Process = service.javaClass.getDeclaredField("process")
            .apply { isAccessible = true }.get(service) as Process
        val serverProcess = process(server)
        requireSafe(serverProcess.isAlive && services.filterValues { it != null && process(it).isAlive }
            .keys == setOf(BuiltinLinux.SERVER), "bb5_other_service_refused")
        val processes = BuiltinLinux::class.java.getDeclaredField("processes")
            .apply { isAccessible = true }.get(linux) as List<Process>
        requireSafe(processes.filter { it.isAlive }.all { it === serverProcess }, "bb5_other_process_refused")
        requireSafe(!context.getSharedPreferences("builtin_component_writer", 0).contains("ticket"),
            "bb5_component_writer_refused")
    }

    @Suppress("UNCHECKED_CAST")
    private fun runtimeMembers(linux: BuiltinLinux): List<RuntimeProcessIdentity> = synchronized(linux) {
        val all = BuiltinLinux::class.java.getDeclaredMethod("sameUidInventory").apply { isAccessible = true }
            .invoke(linux) as List<RuntimeProcessIdentity>
        val readers = BuiltinLinux::class.java.getDeclaredMethod("registeredRuntimePids").apply { isAccessible = true }
            .invoke(linux) as Set<Int>
        requireSafe(all.size <= 128, "bb5_original_inventory_overflow")
        all.filter { it.pid !in readers }
    }

    private fun confirmedGone(identity: RuntimeProcessIdentity): Boolean = try {
        Os.kill(identity.pid, 0)
        false // Includes PID reuse; a new process at a recorded PID cannot qualify absence.
    } catch (error: ErrnoException) { error.errno == OsConstants.ESRCH }

    private fun awaitHealth(linux: BuiltinLinux) {
        val file = File(linux.rootfs, "root/.oc-builtin/server.password")
        requireSafe(file.length() in 1..4096L, "bb5_private_health_unavailable")
        val authorization = "Basic " + Base64.encodeToString(
            "opencode:${file.readText().trim()}".toByteArray(), Base64.NO_WRAP)
        await(20_000L, "bb5_actual_opencode2_unhealthy") {
            listOf("/api/health", "/api/info").any { path ->
                var connection: HttpURLConnection? = null
                try {
                    connection = URL("http://127.0.0.1:4097$path").openConnection() as HttpURLConnection
                    connection.connectTimeout = 500; connection.readTimeout = 500
                    connection.setRequestProperty("Authorization", authorization)
                    connection.responseCode in 200..299 // No provider data or response body is read.
                } catch (_: Throwable) { false } finally { connection?.disconnect() }
            }
        }
    }

    private fun await(duration: Long, code: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + duration
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(100L)
        }
        throw Refused(code)
    }
    private fun requireOwner() = requireSafe(native.getString("owner", null) == PROFILE, "bb5_fixture_owner_changed")
    private fun idleMarked(status: Map<String, Any?>) = status["serverIdleStopped"] == true
    private fun home(linux: BuiltinLinux) = File(linux.rootfs, "home/oc/.oc-profiles/$PROFILE")
    private fun save(value: JSONObject) {
        val temp = File(fixture.path + ".tmp")
        FileOutputStream(temp).use { it.write(value.toString().toByteArray()); it.fd.sync() }
        requireSafe(temp.renameTo(fixture), "bb5_fixture_save_failed")
    }
    private fun emit(vararg names: String) = instrumentation.sendStatus(0, Bundle().apply {
        names.forEach { putBoolean(it, true) }
    })
    private fun requireSafe(value: Boolean, code: String) { if (!value) throw Refused(code) }
    private companion object {
        const val PROFILE = "qa_bb5_idle"
        const val QA_POLICY = "{\"version\":1,\"supervision\":\"high\",\"behaviors\":" +
            "{\"restartPhoneServer\":true,\"pollRestartHealth\":true}}"
    }
}
