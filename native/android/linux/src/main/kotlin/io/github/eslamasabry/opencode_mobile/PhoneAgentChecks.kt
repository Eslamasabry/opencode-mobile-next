package io.github.eslamasabry.opencode_mobile

import java.util.concurrent.TimeUnit

private const val CLEANUP_JOIN_MS = 2000L
private const val READ_CHUNK = 512
private const val OUTPUT_CAP = 8192
private const val DRAIN_CHUNK = 4096
private const val WORKSPACE_TIMEOUT_S = 15L
private const val VERSION_TIMEOUT_S = 30L
private const val VERSION_JOIN_MS = 2000L

/** The agent host's read-only checks: the shared project bind and the installed program versions. */
class PhoneAgentChecks internal constructor(private val linux: BuiltinLinux) {
    /** The same native project bind, checked as oc without retaining a file. */
    fun workspace(profile: String): Map<String, Any?> {
        identity(profile)
        val script = """
            set -eu
            [ "${'$'}(id -u)" = 1000 ]
            umask 077
            f=${'$'}(mktemp /root/projects/.oc-agent-check.XXXXXX)
            trap 'rm -f "${'$'}f"' EXIT HUP INT TERM
            printf 'phone-project-control' >"${'$'}f"
            [ "${'$'}(cat "${'$'}f")" = phone-project-control ]
        """.trimIndent()
        val child = linux.startAgentProcess(profile, listOf("/bin/sh", "-c", script))
        var reader: Thread? = null
        try {
            child.outputStream.close()
            reader = drain(child)
            val complete = child.waitFor(WORKSPACE_TIMEOUT_S, TimeUnit.SECONDS)
            return mapOf("shared" to (complete && child.exitValue() == 0))
        } finally { cleanup(linux, child, reader) }
    }

    /** Bounded version projection. No raw stdout survives this call. */
    fun version(profile: String, executable: String, expected: String): Map<String, Any?> {
        identity(profile)
        check(Regex("^[a-z][a-z0-9-]{0,63}$").matches(executable) &&
            Regex("^[A-Za-z0-9][A-Za-z0-9.+-]{0,63}$").matches(expected)) { "The agent is unavailable." }
        // The exact pinned version is the app's own install; any other version
        // that runs (an older pin, the setup component's /usr/local/bin copy)
        // is still Claude Code on this phone, so the row never says "Not
        // installed" for it. Neither check needs the daemon.
        val found = if (linux.installed) reportedVersion(profile, executable) else null
        val installed = found != null && versionMatches(found, expected)
        return mapOf(
            "installed" to installed, "version" to if (installed) expected else null,
            "foundVersion" to found,
            "payloadPresent" to PhoneAgentInventory.present(linux.rootfs, executable),
        )
    }

    /** The first version number the program prints, or null when it will not run. */
    private fun reportedVersion(profile: String, executable: String): String? {
        for (dir in listOf("/home/oc/.local/bin", "/usr/local/bin")) {
            val output = runVersion(profile, "$dir/$executable") ?: continue
            Regex("(?<![0-9.])[0-9]+\\.[0-9]+\\.[0-9]+(?![0-9.])").find(output)?.let { return it.value }
        }
        return null
    }

    internal fun runVersion(profile: String, path: String): String? {
        val child = try {
            linux.startAgentProcess(profile, listOf(path, "--version"))
        } catch (_: Exception) { null } ?: return null
        val output = StringBuilder()
        var reader: Thread? = null
        return try {
            child.outputStream.close()
            val thread = collectBounded(child, output)
            reader = thread
            val complete = child.waitFor(VERSION_TIMEOUT_S, TimeUnit.SECONDS)
            thread.join(VERSION_JOIN_MS)
            synchronized(output) {
                val value = output.toString()
                output.setLength(0)
                if (complete && child.exitValue() == 0) value else null
            }
        } catch (_: Exception) {
            null
        } finally {
            cleanup(linux, child, reader)
            synchronized(output) { output.setLength(0) }
        }
    }
}

/** Cleanup cannot replace a safe channel error with an uncaught IO error. */
internal fun cleanup(linux: BuiltinLinux, child: Process, reader: Thread?) {
    try { linux.stopAgentProcess(child) } catch (_: Throwable) { }
    try { reader?.join(CLEANUP_JOIN_MS) }
    catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    catch (_: Throwable) { }
    try { child.outputStream.close() } catch (_: Throwable) { }
    try { child.inputStream.close() } catch (_: Throwable) { }
}

internal fun drain(child: Process): Thread = Thread({
    try {
        child.inputStream.use { input ->
            val bytes = ByteArray(DRAIN_CHUNK)
            while (input.read(bytes) >= 0) bytes.fill(0)
        }
    } catch (_: Throwable) { }
}, "phone-agent-output-discard").apply { isDaemon = true; start() }

private fun collectBounded(child: Process, output: StringBuilder): Thread = Thread {
    try {
        child.inputStream.reader().use { input ->
            val buffer = CharArray(READ_CHUNK)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                synchronized(output) {
                    if (output.length < OUTPUT_CAP) {
                        output.append(buffer, 0, minOf(count, OUTPUT_CAP - output.length))
                    }
                }
                buffer.fill('\u0000')
            }
        }
    } catch (_: Throwable) { }
}.apply { isDaemon = true; start() }

private fun versionMatches(output: String, expected: String): Boolean =
    Regex("(?<![0-9.])v?${Regex.escape(expected)}(?![0-9.])").containsMatchIn(output)

internal fun identity(profile: String) {
    check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profile)) { "The agent host is unavailable." }
}
