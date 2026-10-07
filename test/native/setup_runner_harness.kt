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
