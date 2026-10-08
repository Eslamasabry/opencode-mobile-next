package io.github.eslamasabry.opencode_mobile

import android.app.ActivityManager
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import android.util.Base64

/** Phased test-APK fixture. The host must prove OS recovery before starting another phase. */
internal class BuiltinRuntimeReclaimAcceptance(
    private val instrumentation: Instrumentation,
    private val arguments: Bundle,
) {
    class Refused(val safeCode: String) : Exception()
    private val context get() = instrumentation.targetContext
    private val native get() = context.getSharedPreferences("builtin_server_recovery", 0)
    private val flutter get() = context.getSharedPreferences("FlutterSharedPreferences", 0)
    private val evidence get() = File(context.filesDir, "bb3-runtime-qa.json")
    private val witnessCommand get() = File(context.filesDir, "bb3-witness-command.json")
    private val marker get() = "flutter.oc.builtinRecovery.$PROFILE"
    private val policy get() = "flutter.oc.automation.$PROFILE"
    private var executable: String? = null

    fun execute() {
        if (arguments.getString("step") == "bb3Preflight") { preflight(); return }
        val linux = BuiltinLinux.get(context)
        if (arguments.getString("step") == "bb3Capabilities") {
            requireSafe(BuildConfig.BUILTIN_RUNTIME_QA, "bb3_qa_build_required")
            capabilities(linux)
            return
        }
        requireSafe(linux.installed, "bb3_ubuntu_unavailable")
        when (arguments.getString("step")) {
            "bb3Prepare", "bb3Prepared" -> prepare(linux, arguments.getString("step") == "bb3Prepared")
            "bb3WitnessCommand" -> saveWitnessCommand(linux)
            "bb3StaleWitness" -> commitStaleWitness(linux)
            "bb3Stop" -> {
                requireOwner()
                context.startService(Intent(context, BuiltinServerService::class.java).setAction("stop"))
                awaitStopped(linux, "stopped")
            }
            "bb3Timeout" -> {
                requireOwner()
                requireSafe(Build.VERSION.SDK_INT >= 35, "bb3_timeout_api_unavailable")
                var service: BuiltinServerService? = null
                instrumentation.runOnMainSync {
                    // Invoke the real live service's callback, never a constructed Service.
                    val type = Class.forName("android.app.ActivityThread")
                    val thread = type.getDeclaredMethod("currentActivityThread").invoke(null)
                    val field = type.getDeclaredField("mServices").apply { isAccessible = true }
                    service = (field.get(thread) as Map<*, *>).values
                        .filterIsInstance<BuiltinServerService>().singleOrNull()
                    service?.let { live ->
                        val startId = BuiltinServerService::class.java.getDeclaredField("runtimeQaLastStartId")
                            .apply { isAccessible = true }.getInt(live)
                        requireSafe(startId > 0, "bb3_service_start_id_missing")
                        live.onTimeout(startId, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    }
                }
                requireSafe(service != null, "bb3_live_service_missing")
                awaitStopped(linux, "systemTimeout")
            }
            "bb3Cleanup" -> cleanup(linux)
            else -> throw Refused("bb3_step_invalid")
        }
    }

    private fun capabilities(linux: BuiltinLinux) = synchronized(linux) {
        fun probe(method: String): Pair<Boolean, String> = try {
            BuiltinLinux::class.java.getDeclaredMethod(method).apply { isAccessible = true }.invoke(linux)
            true to "none"
        } catch (failure: Throwable) {
            val error = (failure as? java.lang.reflect.InvocationTargetException)?.targetException ?: failure
            false to when (error) {
                is java.util.ConcurrentModificationException -> "inventoryChanged"
                is NullPointerException -> "invalidContext"
                is IllegalArgumentException -> "identityInvalid"
                is LinkageError -> "runtimeLinkageUnavailable"
                is ClassCastException -> "identityTypeInvalid"
                is android.system.ErrnoException -> "kernelUnavailable"
                is SecurityException -> "permissionDenied"
                is java.io.IOException -> "ioUnavailable"
                is IllegalStateException -> "identityUnavailable"
                is ReflectiveOperationException -> "reflectionUnavailable"
                else -> "capabilityUnavailable"
            }
        }
        val boot = probe("bootIdentity")
        val inventory = probe("knownOtherRuntime")
        val bootReadable = try { File("/proc/sys/kernel/random/boot_id").readText().trim().isNotEmpty() }
            catch (_: Throwable) { false }
        val bootCountAvailable = try { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) >= 0 }
            catch (_: Throwable) { false }
        val recipePresent = try {
            BuiltinLinux::class.java.getDeclaredField("serverRecipe").apply { isAccessible = true }.get(linux) != null
        } catch (_: Throwable) { false }
        val bindingMatches = try {
            val profile = BuiltinLinux::class.java.getDeclaredField("supervisionProfile").apply { isAccessible = true }.get(linux)
            val enabled = BuiltinLinux::class.java.getDeclaredField("supervisionEnabled").apply { isAccessible = true }.getBoolean(linux)
            val owner = native.getString("owner", null)
            owner != null && profile == owner && enabled && native.getBoolean("enabled", false)
        } catch (_: Throwable) { false }
        emit {
            putBoolean("bb3KernelBootIdReadable", bootReadable)
            putBoolean("bb3BootCountAvailable", bootCountAvailable)
            putBoolean("bb3BootIdentitySucceeded", boot.first)
            putString("bb3BootIdentityFailure", boot.second)
            putBoolean("bb3KnownRuntimeSucceeded", inventory.first)
            putString("bb3KnownRuntimeFailure", inventory.second)
            putBoolean("bb3MemoryRecipePresent", recipePresent)
            putBoolean("bb3BoundOwnerEnabledMatches", bindingMatches)
            putString("bb3RestorePhase", linux.restorePhase.takeIf { it in listOf("idle", "waiting", "restoring", "unavailable") } ?: "unavailable")
            putString("bb3RestoreReason", linux.restoreReason?.takeIf { it in listOf("stopped", "policyDisabled",
                "budgetExhausted", "ownershipUnknown", "storageUnavailable", "componentRecoveryRequired", "systemTimeout") }
                ?: if (linux.restoreReason == null) "none" else "ownershipUnknown")
        }
    }

    @Suppress("DEPRECATION")
    private fun preflight() {
        val info = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = info.signingInfo?.apkContentsSigners ?: emptyArray()
        requireSafe(signers.size == 1, "bb3_installed_signer_unavailable")
        val digest = MessageDigest.getInstance("SHA-256").digest(signers.single().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val owner = native.getString("owner", null)
        val markerRaw = flutter.getString("flutter.oc.builtinRecovery.$owner", null)
        val policyRaw = flutter.getString("flutter.oc.automation.$owner", null)
        val baselineValid = try {
            requireSafe(owner != null && owner != PROFILE && native.getBoolean("enabled", false),
                "bb3_baseline_owner_unavailable")
            val savedMarker = JSONObject(markerRaw ?: "{}")
            val markerValid = NativeRecoveryBudget.migrationAllows(mapOf(
                "version" to savedMarker.opt("version"), "nativeAuthority" to savedMarker.opt("nativeAuthority")))
            val policyValid = policyRaw == null || JSONObject(policyRaw).let { saved ->
                val behaviors = saved.optJSONObject("behaviors")
                NativeRecoveryBudget.policyAllows(mapOf("version" to saved.opt("version"),
                    "supervision" to saved.opt("supervision"), "behaviors" to mapOf(
                        "restartPhoneServer" to behaviors?.opt("restartPhoneServer"),
                        "pollRestartHealth" to behaviors?.opt("pollRestartHealth"))))
            }
            markerValid && policyValid
        } catch (_: Throwable) { false }
        emit {
            putString("installedCertificateSha256", digest); putLong("installedVersion", info.longVersionCode)
            putBoolean("baselinePolicyMarkerValid", baselineValid)
            putBoolean("installedRuntimeQa", BuildConfig.BUILTIN_RUNTIME_QA)
            putBoolean("baselineRestorationArmed", BuiltinLinux.get(context).serverRestorationArmed)
            fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }
            if (markerRaw != null) putString("baselineMarkerSha256", hash(markerRaw))
            if (policyRaw != null) putString("baselinePolicySha256", hash(policyRaw))
        }
    }

    private fun prepare(linux: BuiltinLinux, preparedDeath: Boolean) {
        requireSafe(!linux.serverRunning && !linux.serverRestartWanted, "bb3_person_server_present")
        requireSafe(!flutter.contains(marker) && !flutter.contains(policy) &&
            !native.contains("oc.builtinRecoveryBudget.$PROFILE") && !evidence.exists(), "bb3_fixture_exists")
        requireSafe(linux.run("command -v opencode2 >/dev/null 2>&1 && test -s /root/.oc-builtin/server.password", 10L).exitCode == 0,
            "bb3_actual_opencode2_unavailable")
        val binary = linux.run("readlink -f \"${'$'}(command -v opencode2)\"", 10L)
        val guest = binary.output.trim()
        requireSafe(binary.exitCode == 0 && Regex("/[A-Za-z0-9_./-]{1,500}").matches(guest), "bb3_executable_unknown")
        executable = File(linux.rootfs, guest.removePrefix("/")).canonicalPath
        requireSafe(executable!!.startsWith(linux.rootfs.canonicalPath + File.separator), "bb3_executable_outside_rootfs")
        linux.stageServerRecovery(PROFILE, NativeRecoveryBudget().map())
        requireSafe(flutter.edit().putString(marker, "{\"version\":2,\"nativeAuthority\":true}")
            .putString(policy, "{\"version\":1,\"supervision\":\"high\",\"behaviors\":{\"restartPhoneServer\":true,\"pollRestartHealth\":true}}")
            .commit(), "bb3_fixture_save_failed")
        val activity = existingMainActivity() ?: instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            linux.startService(BOOTSTRAP, "sleep 180", null, null)
            instrumentation.runOnMainSync { activity.finish() }
            val deadline = SystemClock.elapsedRealtime() + 10_000L
            while (!activity.isDestroyed && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
            requireSafe(activity.isDestroyed, "bb3_activity_not_destroyed")
            linux.setActivityResumed(false)
            val initialProcesses = identities()
            saveEvidence(JSONObject().put("version", 1).put("stage", "bootstrapping")
                .put("app", JSONObject(identity(Process.myPid()).map())).put("members", JSONArray())
                .put("fixtureOthers", JSONArray(bootstrapIdentities(linux, initialProcesses).map { JSONObject(it.map()) }))
                .put("serverExecutable", executable).put("activityAbsent", true))
            Thread.sleep(2_000L)
            linux.bindServerRecovery(PROFILE, null, true)
            val recipe = NativeServerRecipe(PROFILE, "openCode2", 1L, "0".repeat(64))
            val request = mapOf("version" to 1, "profileId" to PROFILE, "runtime" to "openCode2")
            if (preparedDeath) {
                prepareHeldGate(linux, recipe, request)
                return
            }
            linux.startServer(recipe.restorationScript(), 4097, request)
            linux.stopService(BOOTSTRAP)
            requireSafe(linux.serverRestorationArmed, "bb3_sticky_not_armed")
            awaitHealth()
            writeEvidence(linux, "armed", healthy = true)
            if (arguments.getString("staleIdentity") == "true") {
                val key = "oc.builtinRuntimeOwnership.$PROFILE"
                val record = JSONObject(native.getString(key, null) ?: throw Refused("bb3_ownership_missing"))
                for (name in listOf("root", "leader")) {
                    val value = record.getJSONObject(name)
                    value.put("startTicks", value.getLong("startTicks") + 1L)
                }
                // Both identity and nonce mismatch ensure no fallback to the live session.
                val nonce = record.getString("nonce")
                record.put("nonce", (if (nonce[0] == '0') "1" else "0") + nonce.drop(1))
                requireSafe(native.edit().putString(key, record.toString()).commit(), "bb3_stale_receipt_save_failed")
                val saved = JSONObject(evidence.readText()).put("staleIdentity", true)
                saveEvidence(saved)
            }
            // --no-restart completion detaches AMS instrumentation while retaining this FGS.
        } finally {
            // Normal failure cleanup only; process death leaves durable host evidence intact.
            instrumentation.runOnMainSync { if (!activity.isDestroyed) activity.finish() }
        }
    }

    private fun writeEvidence(linux: BuiltinLinux, stage: String, healthy: Boolean, receipt: NativeRuntimeReceipt? = null) {
        val pid = Process.myPid()
        val processes = identities()
        val stored = receipt ?: run {
            val raw = native.getString("oc.builtinRuntimeOwnership.$PROFILE", null)
                ?: throw Refused("bb3_ownership_missing")
            val value = JSONObject(raw)
            fun process(key: String): RuntimeProcessIdentity {
                val item = value.getJSONObject(key)
                return RuntimeProcessIdentity(item.getInt("pid"), item.getLong("startTicks"),
                    item.getInt("parent"), item.getInt("group"), item.getInt("session"))
            }
            NativeRuntimeReceipt(value.getString("boot"), value.getString("nonce"), value.getLong("generation"),
                process("root"), process("leader"), emptyList())
        }
        val root = stored.root ?: throw Refused("bb3_ownership_root_missing")
        val children = mutableSetOf(root.pid)
        repeat(processes.size) { processes.forEach { identity -> if (identity.parent in children) children.add(identity.pid) } }
        val safe = JSONObject().put("version", 1).put("stage", stage).put("app", JSONObject(identity(pid).map()))
            .put("members", JSONArray(processes.filter { it.pid in children }.map { JSONObject(it.map()) }))
            .put("serverExecutable", executable)
            .put("fixtureOthers", JSONArray(bootstrapIdentities(linux, processes).map { JSONObject(it.map()) }))
            .put("activityAbsent", activityAbsent()).put("actualOpenCodeHealthy", healthy)
            .put("stickyArmed", if (stage == "armed") linux.serverRestorationArmed else false)
        requireSafe(safe.getBoolean("activityAbsent"), "bb3_activity_present")
        saveEvidence(safe)
        emit { putBoolean("bb3Ready", true) }
    }

    private fun saveEvidence(safe: JSONObject, target: File = evidence) {
        val temporary = File(target.parentFile, target.name + ".tmp")
        temporary.outputStream().use { stream -> stream.write(safe.toString().toByteArray()); stream.fd.sync() }
        val identityValid = try {
            val savedApp = JSONObject(temporary.readText()).optJSONObject("app")
            val current = identity(Process.myPid())
            val pid = savedApp?.opt("pid")
            val ticks = savedApp?.opt("startTicks")
            (pid is Int || pid is Long) && (ticks is Int || ticks is Long) &&
                (pid as Number).toLong() == current.pid.toLong() && (ticks as Number).toLong() == current.startTicks
        } catch (_: Throwable) { false }
        requireSafe(identityValid, "bb3_app_identity_not_object")
        requireSafe(temporary.renameTo(target), "bb3_evidence_save_failed")
    }

    private fun requireWitnessScope(linux: BuiltinLinux) {
        requireSafe(BuildConfig.BUILTIN_RUNTIME_QA, "bb3_qa_build_required")
        requireOwner()
        requireSafe(native.getString("restoreOwner", null) == PROFILE && linux.serverRestorationArmed,
            "bb3_witness_fixture_not_armed")
        requireSafe(evidence.isFile, "bb3_witness_fixture_missing")
        val fixture = JSONObject(readBounded(evidence, 128 * 1024))
        requireSafe(fixture.optInt("version") == 1 && fixture.optString("stage") == "armed",
            "bb3_witness_fixture_invalid")
    }

    private fun saveWitnessCommand(linux: BuiltinLinux) {
        requireWitnessScope(linux)
        val environment = linux.prootEnvironment()
        requireSafe(environment.keys == setOf("PROOT_LOADER", "PROOT_TMP_DIR", "LD_LIBRARY_PATH"),
            "bb3_witness_environment_invalid")
        val safe = JSONObject().put("version", 1).put("uid", Process.myUid())
            .put("app", JSONObject(identity(Process.myPid()).map()))
            .put("command", JSONArray(linux.prootCommand(listOf("/bin/sleep", "180"))))
            .put("environment", JSONObject(environment))
        saveEvidence(safe, witnessCommand)
        emit { putBoolean("bb3WitnessCommandSaved", true) }
    }

    private fun commitStaleWitness(linux: BuiltinLinux) {
        requireWitnessScope(linux)
        val pidValue = arguments.getString("witnessPid")
        val ticksValue = arguments.getString("witnessStartTicks")
        requireSafe(pidValue != null && Regex("[1-9][0-9]{0,9}").matches(pidValue), "bb3_witness_pid_invalid")
        requireSafe(ticksValue != null && Regex("[1-9][0-9]{0,18}").matches(ticksValue), "bb3_witness_ticks_invalid")
        val pid = pidValue?.toIntOrNull() ?: throw Refused("bb3_witness_pid_invalid")
        val ticks = ticksValue?.toLongOrNull() ?: throw Refused("bb3_witness_ticks_invalid")
        requireSafe(pid > 1 && pid != Process.myPid() && ticks in 1L until Long.MAX_VALUE,
            "bb3_witness_identity_invalid")
        val actual = RuntimeProcessIdentity.stat(readBounded(File("/proc/$pid/stat"), 4096))
        requireSafe(actual.pid == pid && actual.startTicks == ticks && actual.session == pid && actual.group == pid,
            "bb3_witness_kernel_identity_invalid")
        fun uidMatches(): Boolean {
            val uidLine = readBounded(File("/proc/$pid/status"), 32 * 1024).lineSequence()
                .singleOrNull { it.startsWith("Uid:") } ?: return false
            val values = uidLine.substringAfter(':').trim().split(Regex("\\s+"))
            return values.size == 4 && values.all { it.toIntOrNull() == Process.myUid() }
        }
        requireSafe(uidMatches(), "bb3_witness_uid_invalid")
        val witnessGroup = readBounded(File("/proc/$pid/cgroup"), 16 * 1024).trim()
        val appGroup = readBounded(File("/proc/${Process.myPid()}/cgroup"), 16 * 1024).trim()
        requireSafe(witnessGroup.isNotEmpty() && appGroup.isNotEmpty() && witnessGroup != appGroup,
            "bb3_witness_lifecycle_group_invalid")
        val key = "oc.builtinRuntimeOwnership.$PROFILE"
        val raw = native.getString(key, null) ?: throw Refused("bb3_ownership_missing")
        requireSafe(raw.length <= 64 * 1024, "bb3_witness_receipt_invalid")
        val record = JSONObject(raw)
        requireSafe(record.keys().asSequence().toSet() == setOf("version", "boot", "nonce", "generation", "root", "leader", "other") &&
            record.optInt("version") == 1 && record.optJSONObject("root") != null && record.optJSONObject("leader") != null,
            "bb3_witness_receipt_invalid")
        val nonce = record.getString("nonce")
        requireSafe(Regex("[0-9a-f]{64}").matches(nonce), "bb3_witness_receipt_invalid")
        val mismatched = JSONObject(actual.map()).put("startTicks", ticks + 1L)
        record.put("root", JSONObject(mismatched.toString())).put("leader", JSONObject(mismatched.toString()))
            .put("nonce", (if (nonce[0] == '0') "1" else "0") + nonce.drop(1))
        // Recheck the exact tuple and app UID after all bounded reads, before committing.
        val rechecked = RuntimeProcessIdentity.stat(readBounded(File("/proc/$pid/stat"), 4096))
        val currentWitnessGroup = readBounded(File("/proc/$pid/cgroup"), 16 * 1024).trim()
        val currentAppGroup = readBounded(File("/proc/${Process.myPid()}/cgroup"), 16 * 1024).trim()
        requireSafe(actual == rechecked && uidMatches() && currentWitnessGroup == witnessGroup &&
            currentAppGroup.isNotEmpty() && currentWitnessGroup != currentAppGroup, "bb3_witness_identity_changed")
        requireSafe(native.getString("owner", null) == PROFILE && native.getString("restoreOwner", null) == PROFILE &&
            native.getString(key, null) == raw && linux.serverRestorationArmed, "bb3_witness_fixture_changed")
        requireSafe(native.edit().putString(key, record.toString()).commit(), "bb3_stale_receipt_save_failed")
        val fixture = JSONObject(readBounded(evidence, 128 * 1024)).put("staleIdentity", true)
            .put("staleWitness", JSONObject(actual.map())).put("app", JSONObject(identity(Process.myPid()).map()))
        saveEvidence(fixture)
        emit { putBoolean("bb3StaleWitnessCommitted", true) }
    }

    private fun readBounded(file: File, maximum: Int): String = file.inputStream().use { input ->
        val bytes = input.readBytesBounded(maximum + 1)
        requireSafe(bytes.size <= maximum, "bb3_witness_kernel_read_unbounded")
        bytes.toString(Charsets.UTF_8)
    }

    private fun java.io.InputStream.readBytesBounded(maximum: Int): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (bytes.size() < maximum) {
            val count = read(buffer, 0, minOf(buffer.size, maximum - bytes.size()))
            if (count < 0) break
            if (count == 0) throw Refused("bb3_witness_kernel_read_failed")
            bytes.write(buffer, 0, count)
        }
        return bytes.toByteArray()
    }

    private fun identities() = File("/proc").listFiles().orEmpty().mapNotNull { file ->
        file.name.toIntOrNull()?.let { pid -> try { identity(pid) } catch (_: Throwable) { null } }
    }
    private fun identity(pid: Int) = RuntimeProcessIdentity.stat(File("/proc/$pid/stat").readText())

    private fun bootstrapIdentities(linux: BuiltinLinux, processes: List<RuntimeProcessIdentity>): List<RuntimeProcessIdentity> {
        val field = BuiltinLinux::class.java.getDeclaredField("services").apply { isAccessible = true }
        val service = (field.get(linux) as Map<*, *>)[BOOTSTRAP] ?: return emptyList()
        val process = service.javaClass.getDeclaredField("process").apply { isAccessible = true }.get(service) as java.lang.Process
        val root = (process.javaClass.getDeclaredField("pid").apply { isAccessible = true }.get(process) as Number).toInt()
        val owned = mutableSetOf(root)
        repeat(processes.size) { processes.forEach { if (it.parent in owned) owned.add(it.pid) } }
        return processes.filter { it.pid in owned }
    }

    @Suppress("DEPRECATION")
    private fun activityAbsent() = context.getSystemService(ActivityManager::class.java)
        .appTasks.none { it.taskInfo?.topActivity?.packageName == context.packageName }

    private fun existingMainActivity(): Activity? {
        var activity: Activity? = null
        instrumentation.runOnMainSync {
            val type = Class.forName("android.app.ActivityThread")
            val thread = type.getDeclaredMethod("currentActivityThread").invoke(null)
            val records = type.getDeclaredField("mActivities").apply { isAccessible = true }.get(thread) as Map<*, *>
            activity = records.values.mapNotNull { record ->
                record?.javaClass?.getDeclaredField("activity")?.apply { isAccessible = true }?.get(record) as? MainActivity
            }.singleOrNull { !it.isDestroyed }
        }
        return activity
    }

    private fun prepareHeldGate(linux: BuiltinLinux, recipe: NativeServerRecipe, request: Map<String, Any>) {
        val checkpoint = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        val field = BuiltinLinux::class.java.getDeclaredField("runtimeQaGateCheckpoint").apply { isAccessible = true }
        val callback: (String, NativeRuntimeReceipt) -> Unit = { stage, receipt ->
            if (stage == "prepared") {
                writeEvidence(linux, "prepared", healthy = false, receipt = receipt)
                checkpoint.countDown()
                // The real native launch worker stays blocked after instrumentation has finished.
                // Watchdog revokes before throwing, so legacy manual fallback cannot run the payload.
                Thread.sleep(150_000L)
                try { linux.requestServerStop() } catch (_: Throwable) { }
                throw Refused("bb3_host_death_not_observed")
            }
        }
        field.set(linux, callback)
        val sentinel = File(linux.rootfs, "root/.oc-bb3-gate-workload")
        requireSafe(!sentinel.exists(), "bb3_gate_sentinel_exists")
        Thread({
            try {
                linux.startServer("printf executed > /root/.oc-bb3-gate-workload\n" + recipe.restorationScript(), 4097, request)
                failure.set("bb3_gate_checkpoint_not_held")
            } catch (error: Refused) { failure.set(error.safeCode) }
            catch (_: Throwable) { failure.set("bb3_gate_launch_failed") }
            finally { field.set(linux, null) }
        }, "bb3-prepared-native-launch").start()
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (!checkpoint.await(100, TimeUnit.MILLISECONDS)) {
            failure.get()?.let { throw Refused(it) }
            if (SystemClock.elapsedRealtime() >= deadline) {
                try { linux.requestServerStop() } catch (_: Throwable) { }
                throw Refused("bb3_gate_checkpoint_timeout")
            }
        }
        // execute() returns: the runner's normal finish clears active instrumentation without Stop.
    }

    private fun awaitHealth() {
        val password = File(BuiltinLinux.get(context).rootfs, "root/.oc-builtin/server.password").readText().trim()
        requireSafe(password.isNotEmpty(), "bb3_private_password_missing")
        val authorization = "Basic " + Base64.encodeToString("opencode:$password".toByteArray(), Base64.NO_WRAP)
        val deadline = SystemClock.elapsedRealtime() + 60_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            for (route in listOf("/api/health", "/api/info")) {
              val healthy = try {
                val connection = URL("http://127.0.0.1:4097$route").openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 500; connection.readTimeout = 500
                    connection.setRequestProperty("Authorization", authorization)
                    if (connection.responseCode != 200) false else {
                        val text = connection.inputStream.bufferedReader().use { reader ->
                            val buffer = CharArray(4097)
                            val count = reader.read(buffer)
                            if (count !in 1..4096) "" else String(buffer, 0, count)
                        }
                        val body = JSONObject(text)
                        body.optString("version").isNotEmpty() &&
                            (if (route == "/api/health") body.optBoolean("healthy", false)
                             else body.has("pid") || body.optJSONArray("urls") != null)
                    }
                } finally { connection.disconnect() }
              } catch (_: Throwable) { false }
              if (healthy) return
            }
            Thread.sleep(100)
        }
        throw Refused("bb3_health_timeout")
    }

    private fun requireOwner() {
        requireSafe(native.getString("owner", null) == PROFILE, "bb3_qa_owner_missing")
    }

    private fun awaitStopped(linux: BuiltinLinux, reason: String) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while ((linux.serverRunning || linux.serverRecoveryScheduled || foreground()) &&
            SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        requireSafe(!linux.serverRunning && !linux.serverRestartWanted && !linux.serverRestorationArmed &&
            !linux.serverRecoveryScheduled && !foreground(), "bb3_stop_not_drained")
        requireSafe(native.getString("restoreReason", null) == reason, "bb3_stop_reason_invalid")
        emit { putBoolean("bb3StopRevoked", true); putString("bb3StopReason", reason)
            putInt("bb3CallbackAppPid", Process.myPid()) }
    }

    @Suppress("DEPRECATION")
    private fun foreground() = context.getSystemService(ActivityManager::class.java).getRunningServices(100)
        .any { it.service.className == BuiltinServerService::class.java.name && it.foreground }

    private fun cleanup(linux: BuiltinLinux) {
        // Only fixture keys and the dedicated gate sentinel. No account/auth operations.
        if (native.getString("owner", null) == PROFILE) {
            linux.requestServerStop()
            linux.stopServer()
            linux.stopService(BOOTSTRAP)
        }
        linux.deleteServerRecovery(PROFILE)
        requireSafe(flutter.edit().remove(marker).remove(policy).commit(), "bb3_cleanup_preferences_failed")
        requireSafe(!evidence.exists() || evidence.delete(), "bb3_cleanup_evidence_failed")
        File(evidence.parentFile, evidence.name + ".tmp").delete()
        requireSafe(!witnessCommand.exists() || witnessCommand.delete(), "bb3_cleanup_witness_command_failed")
        File(witnessCommand.parentFile, witnessCommand.name + ".tmp").delete()
        val sentinel = File(linux.rootfs, "root/.oc-bb3-gate-workload")
        requireSafe(!sentinel.exists() || sentinel.delete(), "bb3_cleanup_sentinel_failed")
        emit { putBoolean("bb3CleanupComplete", true) }
    }

    private fun emit(block: Bundle.() -> Unit) { instrumentation.sendStatus(0, Bundle().apply(block)) }
    private fun requireSafe(value: Boolean, code: String) { if (!value) throw Refused(code) }
    private companion object {
        const val PROFILE = "qa_bb3_reclaim"
        const val BOOTSTRAP = "qa-bb3-bootstrap"
    }
}
