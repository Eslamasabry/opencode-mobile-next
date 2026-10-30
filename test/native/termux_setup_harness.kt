package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Exercises the production shell protocol, never a real Termux installation. */
private class Fixture : AutoCloseable {
    val home = Files.createTempDirectory("termux-setup-test-").toFile()
    private val jobs = mutableSetOf<String>()

    fun shell(script: String): String {
        // Remap only the fixed durable directory; do not change the host HOME.
        val isolated = script.replace("\$HOME/.oc/setup-v2", "${home.path}/.oc/setup-v2")
        val process = ProcessBuilder("bash", "-c", isolated)
            .redirectErrorStream(true).start()
        check(process.waitFor(10, TimeUnit.SECONDS)) { "Shell command timed out" }
        val output = process.inputStream.bufferedReader().readText()
        check(process.exitValue() == 0) { "Shell command failed: $output" }
        return output
    }

    fun launch(job: String, specs: List<Map<String, Any?>>) {
        jobs.add(job)
        shell(TermuxSetupShell.launch(job, specs))
    }

    fun probe(job: String) = shell(TermuxSetupShell.probe(job))

    fun until(predicate: () -> Boolean) {
        // Generous for a loaded machine; the loop returns as soon as the state appears.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!predicate()) {
            check(System.nanoTime() < deadline) { "Lifecycle transition timed out" }
            Thread.sleep(30)
        }
    }

    override fun close() {
        jobs.forEach { job ->
            runCatching { shell(TermuxSetupShell.cancel(job)) }
            runCatching { until { !probe(job).contains("state|running\n") } }
        }
        home.deleteRecursively()
    }
}

private fun install(script: String) = mapOf<String, Any?>(
    "id" to "linux", "native" to true, "script" to script,
)
private val start = mapOf<String, Any?>("id" to "start", "step" to true)

fun main(args: Array<String>) {
    Fixture().use { f ->
        when (args.single()) {
            "progress-and-finish" -> {
                // The launcher exits. A new status command still finds the job.
                f.launch("one", listOf(install("printf '::oc percent 42\\n::oc bytes 5 10\\n'; printf 'password=fake-test-value\\n'"), start))
                f.until { f.probe("one").contains("current|start\n") }
                val restored = f.probe("one")
                check(restored.contains("state|running\n"))
                check(restored.contains("component|linux|done|42|5|10"))
                // A stale acknowledgement for another job cannot finish this one.
                f.shell(TermuxSetupShell.complete("other", "start", true))
                check(f.probe("one").contains("state|running\n"))
                f.shell(TermuxSetupShell.complete("one", "start", true))
                f.until { f.probe("one").contains("state|done\n") }
                check(f.home.walkTopDown().filter { it.isFile }.none {
                    it.readText().contains("fake-test-value")
                }) { "Raw component diagnostics were persisted" }
            }
            "cancel-and-resume" -> {
                val unrelated = ProcessBuilder("sleep", "30").start()
                try {
                    f.launch("two", listOf(install("sleep 30"), start))
                    f.until { f.probe("two").contains("component|linux|running|") }
                    f.shell(TermuxSetupShell.cancel("two"))
                    f.until { f.probe("two").contains("state|cancelled\n") }
                    check(unrelated.isAlive) { "Cancellation killed an unrelated process" }
                    f.launch("resumed", listOf(
                        install("exit 99") + mapOf("skipped" to true), start,
                    ))
                    f.until { f.probe("resumed").contains("current|start\n") }
                    check(f.probe("resumed").contains("component|linux|skipped|"))
                    f.shell(TermuxSetupShell.complete("resumed", "start", false))
                    f.until { f.probe("resumed").contains("state|failed\n") }
                } finally {
                    unrelated.destroyForcibly()
                    unrelated.waitFor(5, TimeUnit.SECONDS)
                }
            }
            "interrupted-launch" -> {
                // Persisted native metadata may precede dispatch. A probe must
                // fence a delayed launch before it permits a replacement job.
                check(f.probe("not-dispatched").contains("state|interrupted\n"))
                val marker = File(f.home, "must-not-execute")
                f.launch("not-dispatched", listOf(install("touch ${TermuxSetupShell.quote(marker.path)}")))
                Thread.sleep(100)
                check(!marker.exists())
                f.launch("killed", listOf(install("sleep 30")))
                f.until { f.probe("killed").contains("component|linux|running|") }
                val pid = File(f.home, ".oc/setup-v2/killed/owner").readText().substringBefore(' ').toLong()
                check(pid > 1)
                f.shell("kill -KILL -- -$pid")
                f.until { f.probe("killed").contains("state|interrupted\n") }
                f.launch("replacement", listOf(install("exit 0")))
                f.until { f.probe("replacement").contains("state|done\n") }
            }
            else -> error("Unknown scenario")
        }
    }
    println("PASS ${args.single()}")
}
