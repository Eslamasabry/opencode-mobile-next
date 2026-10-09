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
        if (args.single() == "partial-inventory") {
            linux.script = "printf private-version-failure; exit 1"
            val guest = File(linux.rootfs, "home/oc")
            val parent = File(guest, ".local/share/oc-agents").apply { mkdirs() }
            val bin = File(guest, ".local/bin").apply { mkdirs() }
            fun partial(executable: String, expected: Boolean?) {
                val value = PhoneAgentHost(linux).version("qa", executable, "1.2.3")
                check(value["installed"] == false)
                check(value["payloadPresent"] == expected) { "incorrect inventory for $executable: $value" }
                check(!value.toString().contains("private-version-failure"))
            }
            partial("fx", false)
            for ((id, exe) in mapOf("fx" to "fx", "codex" to "codex", "gemini" to "gemini",
                    "qwen" to "qwen", "goose" to "goose", "omp-acp" to "omp")) {
                val payload = File(parent, "$id/1.2.3.new").apply { mkdirs() }
                partial(exe, true)
                // A new host and unrelated setup still observe disk truth.
                File(linux.home, "setup.json").writeText("unrelated job")
                partial(exe, true)
                payload.parentFile.deleteRecursively()
            }
            val link = File(bin, "fx").toPath()
            Files.createSymbolicLink(link, File("/home/oc/.local/share/oc-agents/fx/0.0.12/launch").toPath())
            partial("fx", true) // Dangling authored launcher is still removable.
            Files.delete(link)
            Files.createSymbolicLink(link, File(dir, "foreign").toPath())
            partial("fx", false)
            Files.delete(link)
            val stage = File(bin, "fx.new.123").toPath()
            Files.createSymbolicLink(stage, File("/home/oc/.local/share/oc-agents/fx/0.0.12/launch").toPath())
            partial("fx", true)
            Files.delete(stage)
            val lock = File(parent, ".lock-fx").apply { mkdirs() }
            partial("fx", true)
            lock.delete()
            partial("fx", false)
            File(parent, "claude").mkdirs()
            partial("fx", false)
            partial("claude", false) // Owner-managed Claude is never removable.
            val outside = File(dir, "outside").apply { mkdirs() }
            guest.deleteRecursively()
            Files.createSymbolicLink(guest.toPath(), outside.toPath())
            File(outside, ".local/share/oc-agents/fx").mkdirs()
            partial("fx", null) // Do not follow an untrusted ancestor.
            check(File(outside, ".local/share/oc-agents/fx").isDirectory)
        } else if (args.single() == "concurrent-start") {
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
