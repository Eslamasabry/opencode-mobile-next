package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class RunProcess(private val mode: String) : Process() {
    var alive = true
    var stopped = false
    val log = "private-output\n::oc-check-begin agent-node\nv22.14.0\n::oc-check-end agent-node 0\n"
    override fun getInputStream(): InputStream = if (mode == "reader-failure")
        object : InputStream() { override fun read(): Int = throw IOException("private-output") }
        else ByteArrayInputStream(log.toByteArray())
    override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())
    override fun getOutputStream(): OutputStream = object : ByteArrayOutputStream() {
        override fun close() { if (mode == "stdin-failure") throw IOException("private-output") }
    }
    override fun waitFor(): Int { alive = false; return 0 }
    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
        if (mode == "wait-failure") throw InterruptedException("private-output")
        if (mode == "timeout") return false
        alive = false
        return true
    }
    override fun exitValue(): Int = 0
    override fun isAlive(): Boolean = alive
    override fun destroy() { stopped = true; alive = false }
    override fun destroyForcibly(): Process { destroy(); return this }
}
fun main(args: Array<String>) {
    val mode = args.single()
    val uncaught = AtomicReference<Throwable?>()
    Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught.set(error) }
    val process = RunProcess(mode)
    val linux = BuiltinLinux(process)
    android.util.Log.denied = mode == "log-failure"
    val result = runCatching { linux.run("fixed-check", 1, agentUser = mode != "log-failure") }
    when (mode) {
        "receipts" -> {
            val value = result.getOrThrow()
            check(value.exitCode == 0)
            check(value.output == "::oc-check-begin agent-node\n::oc-check-end agent-node 0\n")
            check(android.util.Log.calls == 0)
        }
        "reader-failure", "log-failure", "timeout" -> {
            check(result.getOrThrow().exitCode == -1)
            check(result.getOrThrow().output.isEmpty())
        }
        "stdin-failure", "wait-failure" -> {
            check(result.isFailure)
            check(process.stopped) { "check process leaked after native IO failure" }
        }
        else -> error("unknown scenario")
    }
    check(uncaught.get() == null) { "check reader exception escaped" }
    println("PASS $mode")
}
