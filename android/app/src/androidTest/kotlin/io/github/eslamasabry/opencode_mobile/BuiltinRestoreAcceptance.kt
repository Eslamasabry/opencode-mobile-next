package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption

/** Private event fixture. It never sends a broadcast or launches restoration itself. */
internal class BuiltinRestoreAcceptance(
    private val instrumentation: Instrumentation,
    private val arguments: Bundle,
) {
    class Refused(val safeCode: String) : Exception()
    private val context get() = instrumentation.targetContext
    private val native get() = context.getSharedPreferences("builtin_server_recovery", 0)
    private val flutter get() = context.getSharedPreferences("FlutterSharedPreferences", 0)
    private val fixture get() = File(context.filesDir, "bb7-runtime-qa.json")
    private val cases = setOf("wanted", "idle_enabled", "policy_disabled", "stopped", "timeout")

    fun execute() {
        requireSafe(BuildConfig.BUILTIN_RUNTIME_QA, "bb7_qa_build_required")
        val linux = BuiltinLinux.get(context)
        when (arguments.getString("step")) {
            "bb7RebootPrepare" -> prepare(linux, "boot")
            "bb7UpdatePrepare" -> prepare(linux, "update")
            "bb7RebootVerify" -> verify(linux, "boot")
            "bb7UpdateVerify" -> verify(linux, "update")
            "bb7Cleanup" -> cleanup(linux)
            else -> throw Refused("bb7_step_invalid")
        }
    }

    // A scripted device scenario: its steps must stay in one readable, ordered sequence.
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun prepare(linux: BuiltinLinux, event: String) {
        val mode = arguments.getString("case") ?: "wanted"
        requireSafe(mode in cases, "bb7_case_invalid")
        requireSafe(!Files.exists(fixture.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            !Files.exists(File(fixture.path + ".tmp").toPath(), LinkOption.NOFOLLOW_LINKS), "bb7_fixture_present")
        val owner = native.getString("owner", null) ?: throw Refused("bb7_owner_missing")
        requireOwner(owner)
        requireSafe(linux.installed && linux.serverRunning && linux.serverRestartWanted &&
            linux.serverRestorationArmed && activityResumed(linux), "bb7_live_baseline_required")
        val budget = linux.serverRecoveryBudget(owner)
        val attempts = budget["attempts"] as? Int ?: throw Refused("bb7_budget_invalid")
        requireSafe(attempts in 0..2 && budget["pending"] == false, "bb7_budget_headroom_required")
        val recipe = invoke(linux, "currentIdleRecipe", arrayOf(String::class.java),
            arrayOf(owner)) as NativeServerRecipe
        requireSafe(recipe.profileId == owner && recipe.runtime == "openCode2", "bb7_canonical_recipe_required")
        await(15_000L, "bb7_startup_not_quiescent") {
            requireOwner(owner)
            requireSafe(activityResumed(linux), "bb7_setup_foreground_lost")
            runCatching {
                requireSafe(linux.idleWorkBusy() == false, "bb7_active_or_unknown_work_refused")
                requireRuntime(linux, owner, true)
            }.isSuccess
        }
        val idle = linux.serverIdleStatus()
        requireSafe(idle["serverIdleStopped"] == false && idle["serverIdleHelperStopped"] == false,
            "bb7_idle_baseline_invalid")
        val saved = JSONObject().put("version", 1).put("event", event).put("case", mode)
            .put("owner", owner).put("boot", boot()).put("packageVersion", packageVersion())
            .put("rootfs", recipe.rootfsGeneration).put("attempts", attempts)
            .put("priorIdleEnabled", idle["serverIdleEnabled"])
            .put("priorIdleMinutes", idle["serverIdleMinutes"])
            .put("priorEnabled", native.getBoolean("enabled", false))
            .put("runtimeGeneration", native.getLong("runtimeGeneration", 0L))
        save(saved)
        // Only reduce authority. Existing recipe, credentials and recovery count are never staged/reset.
        linux.setPhoneServerIdlePolicy(mode == "idle_enabled", if (mode == "idle_enabled") 60 else 5)
        finishActivities()
        await(8_000L, "bb7_activity_not_closed") { !activityResumed(linux) && activities().isEmpty() }
        when (mode) {
            "policy_disabled" -> linux.bindServerRecovery(owner, null, false)
            "stopped", "timeout" -> {
                val reason = if (mode == "timeout") "systemTimeout" else "stopped"
                val revision = linux.requestServerStop(reason, includeOther = true)
                linux.stopAllServices(revision)
                await(8_000L, "bb7_stop_not_drained") { runCatching { requireRuntime(linux, owner, false) }.isSuccess }
            }
        }
        requireOwner(owner)
        requireSafe((linux.serverRecoveryBudget(owner)["attempts"] as? Int) == attempts,
            "bb7_prepare_spent_attempt")
        emit(if (event == "boot") "bb7RebootPrepared" else "bb7UpdatePrepared", "bb7NoActivity", "bb7NoHelperRestored")
    }

    // A scripted device scenario: its steps must stay in one readable, ordered sequence.
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun verify(linux: BuiltinLinux, event: String) {
        val saved = load()
        requireSafe(saved.getString("event") == event, "bb7_fixture_event_changed")
        val owner = saved.getString("owner")
        requireOwner(owner)
        requireSafe(!activityResumed(linux) && activities().isEmpty(), "bb7_activity_contaminated")
        val changed = if (event == "boot") boot() != saved.getString("boot") else
            boot() == saved.getString("boot") && packageVersion() > saved.getLong("packageVersion")
        requireSafe(changed, "bb7_actual_event_unproven")
        val rootfs = invoke(linux, "rootfsIdentity", emptyArray(), emptyArray()) as String
        requireSafe(rootfs == saved.getString("rootfs"), "bb7_rootfs_changed")
        val attempts = linux.serverRecoveryBudget(owner)["attempts"] as? Int
            ?: throw Refused("bb7_budget_invalid")
        val wanted = saved.getString("case") == "wanted"
        if (wanted) {
            requireSafe(linux.serverRunning && linux.serverRestartWanted &&
                attempts == saved.getInt("attempts") + 1, "bb7_single_restore_unproven")
            val raw = native.getString("oc.builtinRuntimeRecipe.$owner", null)
                ?: throw Refused("bb7_recipe_missing")
            val json = JSONObject(raw)
            val recipe = NativeServerRecipe.read(json.keys().asSequence().associateWith { json.get(it) })
            requireSafe(recipe.profileId == owner && recipe.runtime == "openCode2" &&
                recipe.packageVersion == packageVersion() && recipe.rootfsGeneration == rootfs,
                "bb7_recipe_promotion_unproven")
            requireRuntime(linux, owner, true)
            emit("bb7SingleAttempt")
        } else {
            requireSafe(!linux.serverRunning && attempts == saved.getInt("attempts"), "bb7_denial_unproven")
            requireRuntime(linux, owner, false)
            if (saved.getString("case") == "timeout") requireSafe(
                native.getString("restoreReason", null) == "systemTimeout", "bb7_timeout_reason_lost")
            emit("bb7DeniedNoAttempt")
        }
        emit(if (event == "boot") "bb7ActualBootChanged" else "bb7ActualVersionIncreased",
            if (event == "boot") "bb7RebootVerified" else "bb7UpdateVerified",
            "bb7NoHelperRestored", "bb7NoActivity")
    }

    private fun cleanup(linux: BuiltinLinux) {
        if (!fixture.exists()) { emit("bb7CleanupComplete"); return }
        val saved = load()
        val owner = saved.getString("owner")
        requireOwner(owner)
        val revision = linux.requestServerStop(includeOther = true)
        linux.stopAllServices(revision)
        await(8_000L, "bb7_cleanup_not_drained") { runCatching { requireRuntime(linux, owner, false) }.isSuccess }
        // Configuration requires foreground admission; host restores typed metadata while dead.
        // This step deliberately leaves policy/recovery/person data to that host restoration.
        requireSafe(fixture.delete(), "bb7_fixture_cleanup_failed")
        emit("bb7CleanupComplete")
    }

    private fun load(): JSONObject {
        requireSafe(RuntimeQaGuard.fixturePathSafe(context.filesDir, fixture, "bb7-runtime-qa.json") &&
            fixture.length() in 1..16_384L, "bb7_fixture_invalid")
        val value = JSONObject(fixture.readText())
        requireSafe(value.keys().asSequence().toSet() == setOf("version", "event", "case", "owner", "boot",
            "packageVersion", "rootfs", "attempts", "priorIdleEnabled", "priorIdleMinutes", "priorEnabled",
            "runtimeGeneration") &&
            value.opt("version") == 1 && value.optString("event") in setOf("boot", "update") &&
            value.optString("case") in cases && value.opt("attempts") is Int && value.getInt("attempts") in 0..2,
            "bb7_fixture_invalid")
        requireSafe(value.opt("priorIdleEnabled") is Boolean && value.opt("priorIdleMinutes") is Int &&
            value.getInt("priorIdleMinutes") in 1..60 && value.opt("priorEnabled") is Boolean &&
            value.getLong("packageVersion") > 0 && value.getLong("runtimeGeneration") >= 0 &&
            Regex("[a-f0-9]{64}").matches(value.getString("rootfs")) &&
            Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}").matches(value.getString("boot")),
            "bb7_fixture_invalid")
        requireOwner(value.getString("owner"))
        return value
    }

    private fun requireOwner(owner: String) = requireSafe(Regex("[A-Za-z0-9_-]{1,80}").matches(owner) &&
        native.getString("owner", null) == owner && flutter.getString("flutter.oc.builtinServerOwner", null) == owner,
        "bb7_owner_changed")

    @Suppress("UNCHECKED_CAST")
    private fun requireRuntime(linux: BuiltinLinux, owner: String, server: Boolean) = synchronized(linux) {
        requireSafe(LocalTerminal.get(context).list().none { it.running }, "bb7_terminal_present")
        requireSafe(!context.getSharedPreferences("builtin_component_writer", 0).contains("ticket"),
            "bb7_component_writer_present")
        val services = BuiltinLinux::class.java.getDeclaredField("services")
            .apply { isAccessible = true }.get(linux) as Map<String, *>
        val live = services.values.map { entry -> entry!!.javaClass.getDeclaredField("process")
            .apply { isAccessible = true }.get(entry) as Process }.filter { it.isAlive }
        val processes = BuiltinLinux::class.java.getDeclaredField("processes")
            .apply { isAccessible = true }.get(linux) as List<Process>
        requireSafe(processes.filter { it.isAlive }.all { process -> live.any { it === process } },
            "bb7_other_process_present")
        requireSafe(linux.runningServices().toSet() == if (server) setOf(BuiltinLinux.SERVER) else emptySet<String>(),
            "bb7_other_service_present")
        val current = invoke(linux, "sameUidInventory", emptyArray(), emptyArray()) as List<RuntimeProcessIdentity>
        val registered = invoke(linux, "registeredRuntimePids", emptyArray(), emptyArray()) as Set<Int>
        if (server) {
            val receipt = invoke(linux, "ownership", arrayOf(String::class.java, String::class.java),
                arrayOf(owner, "oc.builtinRuntimeOwnership.$owner")) as NativeRuntimeReceipt
            val plan = NativeRuntimeOwnership.plan(receipt, boot(), current, registered,
                requireCompleteInventory = true, nonceMatches = { pid, nonce ->
                    invoke(linux, "nonceMatches", arrayOf(Int::class.javaPrimitiveType!!, String::class.java),
                        arrayOf(pid, nonce)) as Boolean
                })
            requireSafe(!receipt.prepared && receipt.boot == boot() && plan.server.isNotEmpty() && plan.other.isEmpty(),
                "bb7_owned_runtime_unproven")
        } else requireSafe(current.all { it.pid in registered }, "bb7_runtime_survivor_present")
    }

    private fun invoke(linux: BuiltinLinux, name: String, types: Array<Class<*>>, values: Array<Any>): Any? =
        BuiltinLinux::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(linux, *values)

    private fun activityResumed(linux: BuiltinLinux): Boolean {
        val lock = BuiltinLinux::class.java.getDeclaredField("recoveryLock").apply { isAccessible = true }.get(linux)
        return synchronized(lock) {
            BuiltinLinux::class.java.getDeclaredField("activityResumed").apply { isAccessible = true }.getBoolean(linux)
        }
    }

    private fun activities(): List<Activity> {
        var result = emptyList<Activity>()
        instrumentation.runOnMainSync {
            val type = Class.forName("android.app.ActivityThread")
            val thread = type.getDeclaredMethod("currentActivityThread").invoke(null)
            val records = type.getDeclaredField("mActivities").apply { isAccessible = true }.get(thread) as Map<*, *>
            result = records.values.mapNotNull { record -> record?.javaClass?.getDeclaredField("activity")
                ?.apply { isAccessible = true }?.get(record) as? MainActivity }.filter { !it.isDestroyed }
        }
        return result
    }

    private fun finishActivities() {
        val current = activities()
        requireSafe(current.isNotEmpty(), "bb7_setup_activity_required")
        instrumentation.runOnMainSync { current.forEach { it.finishAndRemoveTask() } }
    }

    @Suppress("DEPRECATION")
    private fun packageVersion(): Long = context.packageManager.getPackageInfo(context.packageName, 0).let {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            it.longVersionCode
        } else {
            it.versionCode.toLong()
        }
    }
    private fun boot(): String = File("/proc/sys/kernel/random/boot_id").readText().trim()
    private fun save(value: JSONObject) {
        val temp = File(fixture.path + ".tmp")
        requireSafe(temp.createNewFile(), "bb7_fixture_temp_present")
        FileOutputStream(temp).use { it.write(value.toString().toByteArray()); it.fd.sync() }
        requireSafe(!Files.exists(fixture.toPath(), LinkOption.NOFOLLOW_LINKS) && temp.renameTo(fixture),
            "bb7_fixture_save_failed")
    }
    private fun await(duration: Long, code: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + duration
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return
            Thread.sleep(100L)
        }
        throw Refused(code)
    }
    private fun emit(vararg keys: String) =
        instrumentation.sendStatus(0, Bundle().apply { keys.forEach { putBoolean(it, true) } })
    private fun requireSafe(value: Boolean, code: String) { if (!value) throw Refused(code) }
}
