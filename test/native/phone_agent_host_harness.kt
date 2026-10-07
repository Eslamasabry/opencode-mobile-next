package io.github.eslamasabry.opencode_mobile
import java.io.File
import java.nio.file.Files
private class HostSpaceFile(path: String, private val available: Long) : File(path) {
    override fun getUsableSpace(): Long = available
}
fun main(args: Array<String>) {
    val dir = Files.createTempDirectory("host-native-").toFile()
    val low = args.single() == "low-space-launch"
    val linux = BuiltinLinux(HostSpaceFile(dir.path, if (low) 1L else 2_000_000_000L))
    val host = PhoneAgentHost(linux)
    try {
        if (low) {
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
