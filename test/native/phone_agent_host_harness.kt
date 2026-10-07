package io.github.eslamasabry.opencode_mobile
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
private class HostSpaceFile(path: String, private val available: Long) : File(path) {
    override fun getUsableSpace(): Long = available
}
fun main(args: Array<String>) {
    val dir = Files.createTempDirectory("host-native-").toFile()
    val low = args.single() == "low-space-launch"
    val linux = BuiltinLinux(HostSpaceFile(dir.path, if (low) 1L else 2_000_000_000L))
    val host = PhoneAgentHost(linux)
    try {
        if (args.single() == "concurrent-start") {
            linux.holdStart = true
            val firstFailure = AtomicReference<Throwable?>()
            val secondFailure = AtomicReference<Throwable?>()
            val secondDone = CountDownLatch(1)
            val first = Thread { try { host.start("qa", "a".repeat(64), 4099, "{}") } catch (e: Throwable) { firstFailure.set(e) } }
            val second = Thread { try { host.start("other", "a".repeat(64), 4099, "{}") } catch (e: Throwable) { secondFailure.set(e) } finally { secondDone.countDown() } }
            first.start()
            check(linux.startEntered.await(3, TimeUnit.SECONDS))
            second.start()
            val rejectedBeforeLaunch = secondDone.await(300, TimeUnit.MILLISECONDS)
            linux.releaseStart.countDown()
            first.join(5000)
            second.join(5000)
            try {
                check(rejectedBeforeLaunch && secondFailure.get() != null) { "second start was admitted while first pending" }
                check(firstFailure.get() == null)
                check(linux.configWrites == 1 && linux.launches == 1) { "another profile touched host state during startup" }
            } finally { host.stop("other") }
        } else if (args.single() == "exited-at-once") {
            linux.script = "read password; sleep 0.2; printf 'private-provider-output\\n'; exit 7"
            val failure = runCatching { host.start("qa", "a".repeat(64), 4099, "{}") }.exceptionOrNull()
            check(failure != null) { "host accepted a daemon that exited immediately" }
            check(failure.message?.contains("stopped as soon as it started") == true) { "early exit lacks named plain reason" }
            check(failure.message?.contains("try again") == true)
            check(failure.message?.contains("private-provider-output") == false)
            check(linux.tracked == 0) { "dead child was tracked as running" }
            check(host.status("qa")["running"] == false)
        } else if (low) {
            val failure = runCatching { host.start("qa", "a".repeat(64), 4099, "{}") }.exceptionOrNull()
            check(failure != null) { "host launched despite low space" }
            check(failure.message?.contains("free space") == true)
            check(failure.message?.contains("try again") == true)
            check(linux.configWrites == 0 && linux.launches == 0 && linux.tracked == 0) { "work started before admission" }
        } else {
            val status = host.start("qa", "a".repeat(64), 4099, "{}")
            check(status["running"] == true)
            check(linux.configWrites == 1 && linux.launches == 1)
            host.stop("qa")
            check(host.status("qa")["running"] == false)
        }
        println("PASS ${args.single()}")
    } finally { host.stop("qa"); dir.deleteRecursively() }
}
