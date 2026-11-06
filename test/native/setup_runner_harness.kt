package io.github.eslamasabry.opencode_mobile

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Real production runner and filesystem, only Android services are stubbed. */
private fun field(runner: SetupRunner, name: String) =
    SetupRunner::class.java.getDeclaredField(name).apply { isAccessible = true }
private fun write(runner: SetupRunner) {
    try {
        SetupRunner::class.java.getDeclaredMethod("writeNow").apply { isAccessible = true }.invoke(runner)
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }
}
private class PausedFile(path: String) : File(path) {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    private val once = AtomicBoolean()
    override fun getParentFile(): File? {
        if (Thread.currentThread().name == "old-writer" && once.compareAndSet(false, true)) {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "paused writer timed out" }
        }
        return super.getParentFile()
    }
}
private class SpaceFile(path: String, private val initiallyLow: Boolean) : File(path) {
    override fun getUsableSpace(): Long = if (initiallyLow || File(path, "space-drained").exists()) 1L else 2_000_000_000L
}
fun main(args: Array<String>) {
    val dir = Files.createTempDirectory("setup-runner-").toFile()
    try {
        val runner = SetupRunner::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }.newInstance(Context(if (args.single().startsWith("low-space")) SpaceFile(dir.path, args.single() == "low-space-first") else dir))
        val state = SetupJobState("job", "running", emptyList(), startedAt = 1)
        field(runner, "job").set(runner, state)
        when (args.single()) {
            "interrupted-installer-retry" -> {
                val installedCurl = File(dir, "installed-curl")
                val nodeStarted = File(dir, "node-started")
                val specs = listOf(
                    SetupRunner.Spec("essentials", "touch '${installedCurl.path}'", false, false,
                        1.0, false, null, null, emptyMap(), emptyMap()),
                    SetupRunner.Spec("node", "test -f '${installedCurl.path}' && touch '${nodeStarted.path}'", false, false,
                        1.0, false, null, null, emptyMap(), emptyMap()),
                )
                val texts = SetupRunner.Texts("channel", "title", "{percent}", "done", "stopped")
                // Every attempt signalled: the zero launcher exit must still not read as done.
                BuiltinLinux.interruptInstallersWithZeroExit = SetupStepRetry.ATTEMPTS
                runner.start("interrupted", specs, null, texts)
                (field(runner, "worker").get(runner) as Thread).join(30000)
                check(!runner.running) { "interrupted installer did not terminate" }
                val failed = JSONObject(runner.status()!!)
                check(failed.getString("state") == "failed") { "zero launcher exit hid interrupted installation" }
                val components = failed.getJSONObject("components")
                check(components.getJSONObject("essentials").getString("state") == "failed") {
                    "interrupted essentials was recorded as done"
                }
                check(components.getJSONObject("node").getString("state") == "pending") {
                    "Node ran before essentials completed"
                }
                check(!installedCurl.exists() && !nodeStarted.exists())
                check(BuiltinLinux.installerStarts == 3 && BuiltinLinux.installerFinishes == 3)

                // Continue setup on the same runner and process, without relaunch.
                runner.start("retry", specs, null, texts)
                (field(runner, "worker").get(runner) as Thread).join(5000)
                check(!runner.running) { "same-process retry did not terminate" }
                val retried = JSONObject(runner.status()!!)
                check(retried.getString("state") == "done") { "same-process retry failed" }
                check(installedCurl.exists() && nodeStarted.exists()) { "retry skipped required installation" }
                check(BuiltinLinux.installerStarts == 5 && BuiltinLinux.installerFinishes == 5)
            }
            "segfault-compat-retry" -> {
                val marker = File(dir, "ran-compat")
                // Exits 139 (a segfault) unless the runner started it in compatibility mode.
                val specs = listOf(
                    SetupRunner.Spec("node", "[ -n \"\$OC_STUB_COMPAT\" ] || exit 139; touch '${marker.path}'", false, false,
                        1.0, false, null, null, emptyMap(), emptyMap()),
                )
                val texts = SetupRunner.Texts("channel", "title", "{percent}", "done", "stopped")
                runner.start("segv", specs, null, texts)
                (field(runner, "worker").get(runner) as Thread).join(30000)
                check(!runner.running) { "segfaulting step did not terminate" }
                check(JSONObject(runner.status()!!).getString("state") == "done") { "a segfault was not retried" }
                check(marker.exists()) { "the compatibility run did not happen" }
                check(BuiltinLinux.installerStarts == 2 && BuiltinLinux.compatStarts == 1) { "retried more than once" }
            }
            "segfault-report-retries" -> {
                val marker = File(dir, "ran-compat")
                // The script hides the crash behind exit 1; the shell's report gives it away.
                val specs = listOf(
                    SetupRunner.Spec("node", "[ -n \"\$OC_STUB_COMPAT\" ] || { echo 'sh: 1: Segmentation fault'; exit 1; }; touch '${marker.path}'", false, false,
                        1.0, false, null, null, emptyMap(), emptyMap()),
                )
                val texts = SetupRunner.Texts("channel", "title", "{percent}", "done", "stopped")
                runner.start("segv3", specs, null, texts)
                (field(runner, "worker").get(runner) as Thread).join(30000)
                check(JSONObject(runner.status()!!).getString("state") == "done") { "a reported segfault was not retried" }
                check(marker.exists() && BuiltinLinux.compatStarts == 1)
            }
            "segfault-twice-fails" -> {
                val specs = listOf(
                    SetupRunner.Spec("node", "exit 139", false, false, 1.0, false, null, null, emptyMap(), emptyMap()),
                )
                val texts = SetupRunner.Texts("channel", "title", "{percent}", "done", "stopped")
                runner.start("segv2", specs, null, texts)
                (field(runner, "worker").get(runner) as Thread).join(30000)
                check(JSONObject(runner.status()!!).getString("state") == "failed") { "a second segfault was hidden" }
                check(BuiltinLinux.installerStarts == 2 && BuiltinLinux.compatStarts == 1) { "retried more than once" }
            }
            "interrupted-installer-recovers" -> {
                val installedCurl = File(dir, "installed-curl")
                val nodeStarted = File(dir, "node-started")
                val specs = listOf(
                    SetupRunner.Spec("essentials", "touch '${installedCurl.path}'", false, false,
                        1.0, false, null, null, emptyMap(), emptyMap()),
                    SetupRunner.Spec("node", "test -f '${installedCurl.path}' && touch '${nodeStarted.path}'", false, false,
                        1.0, false, null, null, emptyMap(), emptyMap()),
                )
                val texts = SetupRunner.Texts("channel", "title", "{percent}", "done", "stopped")
                // One signalled run, then the step runs again by itself.
                BuiltinLinux.interruptInstallersWithZeroExit = 1
                runner.start("interrupted", specs, null, texts)
                (field(runner, "worker").get(runner) as Thread).join(30000)
                check(!runner.running) { "interrupted installer did not terminate" }
                check(JSONObject(runner.status()!!).getString("state") == "done") { "a signalled step was not run again" }
                check(installedCurl.exists() && nodeStarted.exists()) { "the rerun skipped required installation" }
                check(BuiltinLinux.installerStarts == 3 && BuiltinLinux.installerFinishes == 3)
            }
            "paseo-checksum-failed", "paseo-launch-failed" -> {
                val stage = if (args.single() == "paseo-checksum-failed") "Checking Paseo checksum" else "Checking Paseo launch command"
                val script = "printf '::oc stage $stage\\nprivate-provider-output\\n'; exit 1"
                runner.start("paseo-check", listOf(SetupRunner.Spec("agent-paseo", script, false, false,
                    1.0, false, null, null, emptyMap(), emptyMap(), agentUser = true)), null,
                    SetupRunner.Texts("channel", "title", "{percent}", "done", "stopped"))
                (field(runner, "worker").get(runner) as Thread).join(5000)
                val status = JSONObject(runner.status()!!)
                check(status.getString("state") == "failed")
                val component = status.getJSONObject("components").getJSONObject("agent-paseo")
                check(component.getString("stage") == stage) { "named Paseo stage was lost" }
                check(component.getString("error").contains(if (args.single() == "paseo-checksum-failed") "checksum check" else "launch command check"))
                check(!status.toString().contains("private-provider-output"))
                check(!runner.logFile.readText().contains("private-provider-output"))
            }
            "low-space-first", "low-space-next" -> {
                val next = args.single() == "low-space-next"
                val first = SetupRunner.Spec("first", "touch '${dir.path}/space-drained'", false, false,
                    1.0, false, null, null, emptyMap(), emptyMap())
                val last = SetupRunner.Spec("second", "touch '${dir.path}/should-not-start'", false, false,
                    1.0, false, null, null, emptyMap(), emptyMap())
                runner.start("storage", if (next) listOf(first, last) else listOf(last), null,
                    SetupRunner.Texts("channel", "title", "{percent}", "done", "stopped"))
                (field(runner, "worker").get(runner) as Thread).join(5000)
                check(!runner.running)
                val status = JSONObject(runner.status()!!)
                check(status.getString("state") == "failed") { "low space allowed setup" }
                val error = status.getString("error")
                check(error.contains("free space") && error.contains("try again")) { "low space lacks plain guidance" }
                check(!error.contains("ENOSPC"))
                check(!File(dir, "should-not-start").exists()) { "work started with low space" }
                if (next) check(File(dir, "space-drained").exists()) { "first component never ran" }
            }
            "installer-routed", "setup-work-owned", "setup-work-revoked-before-worker" -> {
                val revoked = args.single() == "setup-work-revoked-before-worker"
                SetupService.revokeWorkOnStart = revoked
                runner.start("installer-job", listOf(SetupRunner.Spec("agent-claude",
                    "printf '::oc version pinned\\n'", false, false, 1.0, false, null, null,
                    emptyMap(), emptyMap(), agentUser=true)), null,
                    SetupRunner.Texts("Setup", "Install", "Progress", "Done", "Stopped"))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (runner.running && System.nanoTime() < deadline) Thread.sleep(10)
                check(!runner.running)
                check(JSONObject(runner.status()!!).getString("state") == "done")
                check(BuiltinLinux.installerStarts == 1 && BuiltinLinux.installerFinishes == 1) {
                    "setup did not use durable installer admission/completion"
                }
                if (args.single() == "setup-work-owned") {
                    check(BuiltinLinux.setupWorkAcquires == 1 && BuiltinLinux.setupWorkFinishes == 1 && !BuiltinLinux.setupWorkHeld) {
                        "setup job did not release its CPU work after completion"
                    }
                    check(SetupService.finishSawSetupWork) { "setup CPU work ended before terminal persistence/notification" }
                }
                if (revoked) {
                    check(BuiltinLinux.setupWorkAcquires == 1 && BuiltinLinux.setupWorkFinishes == 1 && !BuiltinLinux.setupWorkHeld) { "delayed setup worker readmitted CPU after service loss" }
                    check(!SetupService.finishSawSetupWork) { "delayed setup worker readmitted CPU after service loss" }
                }
            }
            "ordered-terminal" -> {
                val file = PausedFile(File(dir, "setup.json").path)
                field(runner, "file").set(runner, file)
                val failure = AtomicReference<Throwable?>()
                val old = Thread({ try { write(runner) } catch (e: Throwable) { failure.set(e) } }, "old-writer")
                val done = CountDownLatch(1)
                val terminal = Thread({
                    try {
                        synchronized(field(runner, "lock").get(runner)) {
                            state.state = "done"
                            write(runner)
                        }
                    } catch (e: Throwable) { failure.set(e) }
                    finally { done.countDown() }
                }, "terminal-writer")
                old.start()
                check(file.entered.await(5, TimeUnit.SECONDS)) { "writer never reached storage" }
                terminal.start()
                val overtook = done.await(300, TimeUnit.MILLISECONDS)
                file.release.countDown()
                old.join(5000)
                terminal.join(5000)
                failure.get()?.let { throw it }
                check(!overtook) { "terminal write overtook an older writer outside the persistence owner" }
                check(JSONObject(file.readText()).getString("state") == "done") { "terminal state was overwritten" }
            }
            "typed-write-failure" -> {
                val target = File(dir, "setup.json").apply { mkdir() }
                File(target, "retained").writeText("previous data")
                val error = runCatching { write(runner) }.exceptionOrNull()
                check(error?.javaClass?.simpleName == "SetupPersistenceException") { "storage failure must be typed, not swallowed" }
                check(error?.message == "setup_persistence") { "storage failure must not expose paths or exception text" }
                check(File(target, "retained").readText() == "previous data")
            }
            "failed-start-status" -> {
                val target = File(dir, "setup.json").apply { mkdir() }
                File(target, "retained").writeText("previous data")
                val failure = runCatching {
                    runner.start("failed", listOf(SetupRunner.Spec(
                        "start", null, false, true, 1.0, false, null, null, emptyMap(), emptyMap(),
                    )), null, SetupRunner.Texts("channel", "title", "{percent}", "done", "stopped"))
                }.exceptionOrNull()
                check(failure?.javaClass?.simpleName == "SetupPersistenceException")
                check(!runner.running)
                val status = JSONObject(runner.status()!!)
                check(status.getString("state") == "failed")
                check(status.getString("errorCode") == "setup_persistence")
                check(status.isNull("error"))
            }
            "service-start-denied" -> {
                SetupService.denyStart = true
                val failure = runCatching {
                    runner.start("denied", emptyList(), null,
                        SetupRunner.Texts("channel", "title", "{percent}", "done", "stopped"))
                }.exceptionOrNull()
                check(failure is IllegalStateException)
                check(!runner.running)
                check(JSONObject(runner.status()!!).getString("state") == "failed")
            }
            "service-finish-denied", "service-update-denied", "agent-output-private" -> {
                val uncaught = AtomicReference<Throwable?>()
                Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught.set(error) }
                SetupService.denyFinish = args.single() == "service-finish-denied"
                SetupService.denyUpdate = args.single() == "service-update-denied"
                val privateOutput = args.single() == "agent-output-private"
                val script = if (privateOutput) "printf 'private-agent-output\\n::oc stage private-agent-output\\n::oc version private-agent-output\\n'; exit 1"
                    else if (SetupService.denyUpdate) "sleep 0.4; printf '::oc stage installing\\n'; exit 0"
                    else "printf '::oc stage installing\\n'; exit 0"
                runner.start("notifications", listOf(SetupRunner.Spec(
                    "agent-node", script, false, false, 1.0, false,
                    null, null, emptyMap(), emptyMap(), agentUser = privateOutput,
                )), null, SetupRunner.Texts("channel", "title", "{percent}", "done", "stopped"))
                if (SetupService.denyUpdate) {
                    // Force the progress interval due without waiting a second.
                    field(runner, "lastNotified").setLong(runner, 0)
                    field(runner, "lastNotifiedText").set(runner, "different")
                    write(runner)
                    check(SetupService.updateAttempts > 0) { "notification update was not exercised" }
                }
                (field(runner, "worker").get(runner) as Thread).join(5000)
                check(!runner.running)
                check(uncaught.get() == null) { "worker notification failure was uncaught" }
                if (SetupService.denyFinish) check(SetupService.finishAttempts > 0)
                val status = runner.status()!!
                check(JSONObject(status).getString("state") == if (privateOutput) "failed" else "done")
                if (privateOutput) {
                    check(!status.contains("private-agent-output"))
                    check(!runner.logFile.readText().contains("private-agent-output"))
                }
            }
            else -> error("unknown scenario")
        }
        println("PASS ${args.single()}")
    } finally { dir.deleteRecursively() }
}
