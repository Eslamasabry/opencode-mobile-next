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
 * Private QA workload retaining the real saved server owner, recipe, and canonical helper.
 * A stand-in runs only a read on stdin in the existing helper home; no auth or agent command.
 * No person preferences, recovery records, credentials, or helper home are removed.
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
    private lateinit var ownerProfile: String
    private lateinit var helperProfile: String
    private val helperName get() = "agent-host.$helperProfile"

    fun execute() {
        requireSafe(BuildConfig.BUILTIN_RUNTIME_QA, "bb5_qa_build_required")
        val linux = BuiltinLinux.get(context)
        when (arguments.getString("step")) {
            "bb5Idle" -> idle(linux)
            "bb5Cleanup" -> cleanup(linux)
            else -> throw Refused("bb5_step_invalid")
        }
    }

    // A scripted device scenario: its steps must stay in one readable, ordered sequence.
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount")
    private fun idle(linux: BuiltinLinux) {
        requireSafe(linux.installed && linux.serverRunning && linux.serverRestorationArmed,
            "bb5_canonical_baseline_required")
        requireSafe(!fixture.exists() && !File(fixture.path + ".tmp").exists(),
            "bb5_fixture_already_present")
        ownerProfile = native.getString("owner", null) ?: throw Refused("bb5_saved_owner_missing")
        helperProfile = mappedHelper(ownerProfile)
        requireOwner()
        requireSafe(RuntimeQaGuard.retainedHomeSafe(linux.rootfs, helperProfile),
            "bb5_existing_helper_home_required")
        val recipe = runCatching {
            BuiltinLinux::class.java.getDeclaredMethod("currentIdleRecipe", String::class.java)
                .apply { isAccessible = true }.invoke(linux, ownerProfile) as NativeServerRecipe
        }.getOrElse { throw Refused("bb5_existing_recipe_unavailable") }
        requireSafe(recipe.profileId == ownerProfile && recipe.runtime == "openCode2",
            "bb5_canonical_recipe_required")
        val budget = linux.serverRecoveryBudget(ownerProfile)
        requireSafe(budget["pending"] == false, "bb5_pending_budget_refused")
        val initial = linux.serverIdleStatus()
        val enabled = initial["serverIdleEnabled"] as? Boolean
        val minutes = initial["serverIdleMinutes"] as? Int
        requireSafe(enabled != null && minutes != null && !idleMarked(initial) &&
            initial["serverIdleHelperStopped"] == false, "bb5_idle_baseline_unavailable")
        val firstActivity = existingActivity() ?: throw Refused("bb5_real_activity_required")
        await(5_000L, "bb5_real_foreground_unavailable") { resumed(linux) }
        // Read-only app startup probes may still be draining. Wait for exactly the
        // same full quiescence proof; no fixture or runtime mutation occurs yet.
        RuntimeQaGuard.awaitStartupSettlement(
            { SystemClock.elapsedRealtime() },
            {
                requireOwner()
                requireSafe(resumed(linux), "bb5_real_foreground_unavailable")
                requireSafe(linux.serverRunning && linux.serverRestorationArmed && linux.serverRestartWanted,
                    "bb5_canonical_baseline_required")
            },
            { synchronized(linux) { requireQuiescentServerOnly(linux) } },
            { Thread.sleep(100L) },
        )
        save(JSONObject().put("version", 2).put("owner", ownerProfile).put("helper", helperProfile)
            .put("priorIdleEnabled", enabled).put("priorIdleMinutes", minutes))
        var helper: Process? = null
        var replacement: Process? = null
        var phase = "bb5_saved_owner_validation_failed"
        var observedGeneration: Long? = null
        var returnObservationStarted = false
        RuntimeQaGuard.runWithCleanup({ try {
            // The live Dart controller retains its real saved owner throughout this fixture.
            // Existing recipe, policy, migration marker, and restart budget are authoritative.
            requireOwner()
            phase = "bb5_helper_start_failed"
            val firstHelper = startHelper(linux, false, null)
            helper = firstHelper
            phase = "bb5_idle_policy_failed"
            linux.setPhoneServerIdlePolicy(true, 1)
            val old = runtimeMembers(linux)
            requireSafe(old.isNotEmpty() && firstHelper.isAlive, "bb5_original_runtime_unproven")
            save(JSONObject(fixture.readText()).put("members", org.json.JSONArray(old.map { JSONObject(it.map()) })))
            emit("bb5RuntimePrepared")
            phase = "bb5_background_setup_failed"

            // Foreground and unknown work must not create an idle-stop token.
            linux.observePhoneAgentWork(ownerProfile, false)
            requireSafe(!idleMarked(linux.serverIdleStatus()), "bb5_foreground_idle_stop")
            instrumentation.runOnMainSync {
                requireSafe(firstActivity.moveTaskToBack(true), "bb5_background_transition_refused")
            }
            await(5_000L, "bb5_background_transition_unproven") { !resumed(linux) }
            linux.observePhoneAgentWork(ownerProfile, null)
            Thread.sleep(250L)
            requireOwner()
            requireSafe(!idleMarked(linux.serverIdleStatus()) && linux.serverRunning,
                "bb5_unknown_work_stopped_runtime")

            val started = SystemClock.elapsedRealtime()
            var nextHeartbeat = started
            emit("bb5IdleWaitEntered")
            phase = "bb5_idle_wait_failed"
            await(75_000L, "bb5_real_idle_deadline_not_reached") {
                requireOwner()
                requireSafe(!resumed(linux), "bb5_background_lost")
                val now = SystemClock.elapsedRealtime()
                if (now >= nextHeartbeat) {
                    linux.observePhoneAgentWork(ownerProfile, false)
                    nextHeartbeat = now + 10_000L
                }
                idleMarked(linux.serverIdleStatus())
            }
            requireSafe(SystemClock.elapsedRealtime() - started >= 60_000L, "bb5_idle_stopped_early")
            val paused = linux.serverIdleStatus()
            val generation = (paused["serverIdleGeneration"] as? Number)?.toLong()
                ?: throw Refused("bb5_idle_generation_missing")
            observedGeneration = generation
            requireSafe(generation > 0 && paused["serverIdleHelperStopped"] == true &&
                linux.serverRestartWanted, "bb5_idle_intent_unproven")
            val persisted = JSONObject(native.getString("idleState", null)
                ?: throw Refused("bb5_idle_intent_unproven"))
            requireSafe(persisted.getBoolean("stopped") && persisted.getBoolean("helperStopped") &&
                persisted.getLong("generation") == generation && persisted.getString("owner") == ownerProfile &&
                persisted.getString("helper") == helperProfile, "bb5_idle_intent_unproven")
            await(12_000L, "bb5_exact_idle_tree_not_drained") {
                !linux.serverRunning && !firstHelper.isAlive && old.all(::confirmedGone)
            }
            Thread.sleep(2_000L)
            requireSafe(idleMarked(linux.serverIdleStatus()) && !linux.serverRunning &&
                (linux.serverIdleStatus()["serverIdleGeneration"] as? Number)?.toLong() == generation &&
                linux.serverRecoveryBudget(ownerProfile) == budget, "bb5_idle_resurrected_or_spent_budget")
            emit("bb5RealIdlePassed", "bb5ExactDrainPassed", "bb5StoppedIntentPassed")
            phase = "bb5_notification_validation_failed"

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
            phase = "bb5_foreground_resume_failed"
            requireOwner()
            // Give the real Dart lifecycle path the first opportunity. Never manufacture
            // an automatic-resume claim when this runner must finish the native token.
            returnObservationStarted = true
            val observationStarted = SystemClock.elapsedRealtime()
            while (RuntimeQaGuard.idleObservationPending(observationStarted, SystemClock.elapsedRealtime()) &&
                !resumeComplete(linux)) {
                requireOwner()
                Thread.sleep(100L)
            }
            if (!resumeComplete(linux) && hasInFlightHelper(linux)) {
                // A live/in-flight helper still owns its callback. Never complete its
                // token on its behalf just because the observer budget expired.
                throw Refused("bb5_controller_helper_completion_unproven")
            }
            var usedFallback = false
            if (!resumeComplete(linux)) {
                synchronized(linux) {
                    requireOwner()
                    if (!resumeComplete(linux)) {
                        requireSameToken(linux, generation)
                        // Native calls and helper creation share this monitor. An already
                        // admitted but untracked process makes this path refuse as well.
                        requireQuiescentServerOnly(linux, requireServer = false)
                        if (!linux.serverRunning) linux.resumeIdleStoppedPhoneServer(ownerProfile, generation)
                        requireSameToken(linux, generation)
                        requireSafe(runCatching {
                            linux.completePhoneServerIdleResume(ownerProfile, generation)
                        }.isFailure, "bb5_missing_helper_ack_admitted")
                        replacement = startHelper(linux, true, generation)
                        linux.completePhoneServerIdleResume(ownerProfile, generation)
                        usedFallback = true
                    }
                }
            }
            awaitHealth(linux)
            requireSafe(resumeComplete(linux) && linux.serverRecoveryBudget(ownerProfile) == budget,
                "bb5_helper_completion_unproven")
            val nextHelper = trackedHelper(linux) ?: throw Refused("bb5_resumed_helper_missing")
            // Detect a late competing start rather than silently replacing or stopping it.
            val settledUntil = SystemClock.elapsedRealtime() + 3_000L
            while (SystemClock.elapsedRealtime() < settledUntil) {
                requireOwner()
                requireSafe(trackedHelper(linux) === nextHelper && nextHelper.isAlive,
                    "bb5_late_helper_replacement")
                requireRuntimeShape(linux, allowHelper = true, requireServer = true)
                requireKernelOwnership(linux, allowHelper = true)
                Thread.sleep(100L)
            }
            emit(if (usedFallback) "bb5NativeFallbackResume" else "bb5ObservedDartResume")
            emit("bb5ForegroundResumePassed", "bb5HelperAcknowledgementPassed", "bb5BudgetPreserved")

            phase = "bb5_explicit_stop_failed"
            linux.requestServerStop()
            requireSafe(runCatching { linux.resumeIdleStoppedPhoneServer(ownerProfile, generation) }.isFailure &&
                linux.captureAgentHostStart(helperProfile, true, generation) == null,
                "bb5_explicit_stop_lost")
            linux.stopServer()
            linux.stopService(helperName)
            await(8_000L, "bb5_explicit_stop_not_drained") { !linux.serverRunning && !nextHelper.isAlive }
            requireSafe(!linux.serverRestartWanted && !idleMarked(linux.serverIdleStatus()) &&
                (linux.serverIdleStatus()["serverIdleGeneration"] as? Number)?.toLong() == 0L &&
                linux.serverRecoveryBudget(ownerProfile) == budget, "bb5_explicit_stop_intent_unproven")
            emit("bb5ExplicitStopPassed", "bb5IdlePassed")
        } catch (failure: Refused) {
            if (returnObservationStarted) emitReturnObservation(linux, observedGeneration)
            throw failure
        } catch (_: Throwable) {
            if (returnObservationStarted) emitReturnObservation(linux, observedGeneration)
            // Fixed phase only; neither exception messages nor process output leave the runner.
            throw Refused(phase)
        } }, {
            // Exact Process objects we created; no PID selection, credential or helper lock operations.
            for (process in listOfNotNull(helper, replacement)) if (process.isAlive) linux.stopAgentProcess(process)
            cleanup(linux)
        }, { failure -> instrumentation.sendStatus(0, Bundle().apply {
            putString("bb5CleanupFailure", (failure as? Refused)?.safeCode ?: "bb5_cleanup_failed")
        }) })
    }

    private fun startHelper(linux: BuiltinLinux, resume: Boolean, generation: Long?): Process = synchronized(linux) {
        requireOwner()
        requireSafe(RuntimeQaGuard.retainedHomeSafe(linux.rootfs, helperProfile), "bb5_existing_helper_home_required")
        requireRuntimeShape(linux, allowHelper = false, requireServer = true)
        requireKernelOwnership(linux, allowHelper = false)
        val status = linux.serverIdleStatus()
        val expected = if (resume) generation ?: throw Refused("bb5_helper_token_missing") else 0L
        requireSafe(RuntimeQaGuard.helperStartAllowed(expected,
            (status["serverIdleGeneration"] as? Number)?.toLong() ?: -1L,
            linux.serverRunning, idleMarked(status), status["serverIdleHelperStopped"] == true,
            0, trackedHelper(linux) != null), "bb5_helper_duplicate_or_stale")
        val ticket = linux.captureAgentHostStart(helperProfile, resume, generation)
            ?: throw Refused("bb5_helper_admission_unavailable")
        val process = linux.withAgentHostStart(ticket) {
            linux.startAgentProcess(helperProfile, listOf("/bin/sh", "-c", "IFS= read -r unused"), privateOutput = true)
        }
        try { linux.trackPrivateAgentService(helperName, process, null) }
        catch (_: Throwable) { linux.stopAgentProcess(process); throw Refused("bb5_helper_tracking_refused") }
        process
    }

    private fun cleanup(linux: BuiltinLinux) {
        if (!fixture.exists()) { emit("bb5CleanupComplete"); return }
        requireSafe(RuntimeQaGuard.fixturePathSafe(context.filesDir, fixture, "bb5-runtime-qa.json"),
            "bb5_cleanup_fixture_path_invalid")
        requireSafe(fixture.length() in 1..65_536L, "bb5_cleanup_fixture_size_invalid")
        val saved = JSONObject(fixture.readText())
        val baseKeys = setOf("version", "owner", "helper", "priorIdleEnabled", "priorIdleMinutes")
        val keys = saved.keys().asSequence().toSet()
        requireSafe(keys == baseKeys || keys == baseKeys + "members", "bb5_cleanup_fixture_keys_invalid")
        requireSafe(saved.opt("version") == 2 && saved.opt("owner") is String && saved.opt("helper") is String &&
            saved.opt("priorIdleEnabled") is Boolean && saved.opt("priorIdleMinutes") is Int &&
            saved.getInt("priorIdleMinutes") in 1..60, "bb5_cleanup_fixture_values_invalid")
        ownerProfile = saved.getString("owner")
        helperProfile = saved.getString("helper")
        requireOwner()
        requireRuntimeShape(linux, allowHelper = true, requireServer = false)
        requireKernelOwnership(linux, allowHelper = true)
        // Native ownership receipts remain the only signal authority.
        linux.requestServerStop(includeOther = true)
        linux.stopServer()
        linux.stopService(helperName)
        await(8_000L, "bb5_cleanup_runtime_not_drained") { !linux.serverRunning && trackedHelper(linux) == null }
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
        foreground(linux)
        linux.setPhoneServerIdlePolicy(saved.getBoolean("priorIdleEnabled"), saved.getInt("priorIdleMinutes"))
        // Owner, marker, policy, recipe, budget, and helper home are person data.
        // Host restoration compares/merges those saved records after this exact drain.
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

    private fun requireQuiescentServerOnly(linux: BuiltinLinux, requireServer: Boolean = true) {
        requireSafe(LocalTerminal.get(context).list().none { it.running }, "bb5_active_terminal_refused")
        requireSafe(linux.idleWorkBusy() == false, "bb5_active_or_unknown_work_refused")
        requireRuntimeShape(linux, allowHelper = false, requireServer = requireServer)
        requireKernelOwnership(linux, allowHelper = false)
        requireSafe(!context.getSharedPreferences("builtin_component_writer", 0).contains("ticket"),
            "bb5_component_writer_refused")
    }

    @Suppress("UNCHECKED_CAST")
    private fun liveServices(linux: BuiltinLinux): Map<String, Process> {
        val services = BuiltinLinux::class.java.getDeclaredField("services")
            .apply { isAccessible = true }.get(linux) as Map<String, *>
        return services.mapValues { (_, service) ->
            service!!.javaClass.getDeclaredField("process").apply { isAccessible = true }.get(service) as Process
        }.filterValues { it.isAlive }
    }

    private fun trackedHelper(linux: BuiltinLinux): Process? = synchronized(linux) { liveServices(linux)[helperName] }

    @Suppress("UNCHECKED_CAST")
    private fun requireRuntimeShape(
        linux: BuiltinLinux, allowHelper: Boolean, requireServer: Boolean,
    ) = synchronized(linux) {
        val services = liveServices(linux)
        val allowed = if (allowHelper) setOf(BuiltinLinux.SERVER, helperName) else setOf(BuiltinLinux.SERVER)
        requireSafe(services.keys.all { it in allowed } && (!requireServer || BuiltinLinux.SERVER in services),
            "bb5_other_service_refused")
        val processes = BuiltinLinux::class.java.getDeclaredField("processes")
            .apply { isAccessible = true }.get(linux) as List<Process>
        requireSafe(processes.filter { it.isAlive }.all { process -> services.values.any { it === process } },
            "bb5_other_process_refused")
    }

    @Suppress("UNCHECKED_CAST")
    private fun hasInFlightHelper(linux: BuiltinLinux): Boolean = synchronized(linux) {
        val services = liveServices(linux)
        val server = services[BuiltinLinux.SERVER]
        val processes = BuiltinLinux::class.java.getDeclaredField("processes")
            .apply { isAccessible = true }.get(linux) as List<Process>
        services.keys.any { it != BuiltinLinux.SERVER } || processes.any { it.isAlive && it !== server }
    }

    @Suppress("UNCHECKED_CAST")
    private fun requireKernelOwnership(linux: BuiltinLinux, allowHelper: Boolean) = synchronized(linux) {
        try {
            val type = BuiltinLinux::class.java
            val receipt = type.getDeclaredMethod("ownership", String::class.java, String::class.java)
                .apply { isAccessible = true }.invoke(linux, ownerProfile,
                    "oc.builtinRuntimeOwnership.$ownerProfile") as NativeRuntimeReceipt
            val current = type.getDeclaredMethod("sameUidInventory").apply { isAccessible = true }
                .invoke(linux) as List<RuntimeProcessIdentity>
            val registered = type.getDeclaredMethod("registeredRuntimePids").apply { isAccessible = true }
                .invoke(linux) as Set<Int>
            val nonce = type.getDeclaredMethod("nonceMatches", Int::class.javaPrimitiveType, String::class.java)
                .apply { isAccessible = true }
            val plan = NativeRuntimeOwnership.plan(receipt,
                File("/proc/sys/kernel/random/boot_id").readText().trim(), current, registered,
                requireCompleteInventory = true,
                nonceMatches = { pid, value -> nonce.invoke(linux, pid, value) as Boolean })
            requireSafe(plan.other.isEmpty() || (allowHelper && trackedHelper(linux) != null),
                "bb5_inflight_or_unknown_helper")
        } catch (failure: Refused) { throw failure }
        catch (_: Throwable) { throw Refused("bb5_kernel_ownership_unproven") }
    }

    private fun emitReturnObservation(linux: BuiltinLinux, generation: Long?) {
        // Independent guarded probes run before cleanup. False includes unavailable;
        // these fixed booleans are diagnostics, never extra acceptance proof.
        val serverRunning = runCatching { linux.serverRunning }.getOrDefault(false)
        val helperTracked = runCatching { trackedHelper(linux) != null }.getOrDefault(false)
        val tokenPending = runCatching {
            val state = linux.serverIdleStatus()
            RuntimeQaGuard.idleTokenPending(generation ?: 0L,
                (state["serverIdleGeneration"] as? Number)?.toLong() ?: -1L,
                state["serverIdleStopped"] == true, state["serverIdleHelperStopped"] == true)
        }.getOrDefault(false)
        val ownerCurrent = runCatching { requireOwner(); true }.getOrDefault(false)
        runCatching {
            instrumentation.sendStatus(0, Bundle().apply {
                putBoolean("bb5ReturnServerRunning", serverRunning)
                putBoolean("bb5ReturnHelperTracked", helperTracked)
                putBoolean("bb5ReturnTokenPending", tokenPending)
                putBoolean("bb5ReturnOwnerCurrent", ownerCurrent)
            })
        }
    }

    private fun resumeComplete(linux: BuiltinLinux): Boolean {
        val state = linux.serverIdleStatus()
        return linux.serverRunning && !idleMarked(state) && state["serverIdleHelperStopped"] == false &&
            (state["serverIdleGeneration"] as? Number)?.toLong() == 0L && trackedHelper(linux) != null
    }

    private fun requireSameToken(linux: BuiltinLinux, generation: Long) {
        val state = linux.serverIdleStatus()
        requireSafe((state["serverIdleGeneration"] as? Number)?.toLong() == generation &&
            state["serverIdleHelperStopped"] == true && linux.serverRestartWanted,
            "bb5_idle_token_changed")
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
    private fun mappedHelper(owner: String): String =
        flutter.getString("flutter.oc.phoneAgentOwner.$owner", null) ?: owner

    private fun requireOwner() = requireSafe(RuntimeQaGuard.fixtureBindingValid(ownerProfile,
        native.getString("owner", null), flutter.getString("flutter.oc.builtinServerOwner", null),
        helperProfile, mappedHelper(ownerProfile)), "bb5_fixture_owner_changed")
    private fun idleMarked(status: Map<String, Any?>) = status["serverIdleStopped"] == true
    private fun save(value: JSONObject) {
        val temp = File(fixture.path + ".tmp")
        FileOutputStream(temp).use { it.write(value.toString().toByteArray()); it.fd.sync() }
        requireSafe(temp.renameTo(fixture), "bb5_fixture_save_failed")
    }
    private fun emit(vararg names: String) = instrumentation.sendStatus(0, Bundle().apply {
        names.forEach { putBoolean(it, true) }
    })
    private fun requireSafe(value: Boolean, code: String) { if (!value) throw Refused(code) }
}
