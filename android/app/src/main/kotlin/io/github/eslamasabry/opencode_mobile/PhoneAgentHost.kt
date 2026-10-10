package io.github.eslamasabry.opencode_mobile

import android.os.Build
import java.util.concurrent.TimeUnit

/** No provider output, passwords or OAuth data enter ordinary service logs. */
class PhoneAgentHost(private val linux: BuiltinLinux) {
    private companion object {
        const val STARTUP_GRACE_MILLIS = 1500L
    }

    private val children = mutableMapOf<String, Process>()
    private val generations = mutableMapOf<String, Long>()
    private val deleted = mutableSetOf<String>()
    private val starting = mutableSetOf<String>()

    private fun identity(profile: String) {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profile)) { "The agent host is unavailable." }
    }

    fun status(profile: String): Map<String, Any?> = synchronized(this) {
        identity(profile)
        linux.privateAgentDiagnostics("agent-host.$profile") +
            mapOf("running" to (children[profile]?.isAlive == true),
                "abi" to (Build.SUPPORTED_ABIS.firstOrNull() ?: ""))
    }

    fun start(profile: String, password: String, port: Int, config: String): Map<String, Any?> {
        identity(profile)
        check(Regex("^[a-f0-9]{64}$").matches(password) && port == 4099) { "The agent host is unavailable." }
        val generation = synchronized(this) {
            check(profile !in deleted) { "The agent host is unavailable." }
            if (children[profile]?.isAlive == true) return status(profile)
            check(children.values.none { it.isAlive }) { "The agent host is unavailable." }
            check(starting.isEmpty()) { "The agent host is still starting. Wait a moment and try again." }
            SetupDiskSpace.error(linux.home, SetupDiskSpace.MIN_LAUNCH_BYTES)?.let { error(it) }
            starting.add(profile)
            (generations[profile] ?: 0L).also { generations[profile] = it }
        }
        // A Claude process Android stopped mid-refresh leaves Claude's login
        // lock behind, and the first message then fails with "another Claude
        // Code process is refreshing it". No Claude process of this profile
        // runs now (the helper starts them all), so a lock left is stale.
        val script = """
            set -eu
            umask 077
            [ "${'$'}(id -u)" = 1000 ]
            IFS= read -r PASEO_PASSWORD
            export PASEO_PASSWORD
            export PASEO_LOG_LEVEL=fatal
            export PASEO_LOG_FILE_PATH=/dev/null
            export PASEO_SERVICE_PROXY_ENABLED=false
            export PASEO_LOG_FORMAT=json
            export PASEO_LISTEN=127.0.0.1:$port
            export PASEO_RELAY_ENABLED=false
            export PASEO_WEB_UI_ENABLED=false
            export PASEO_DICTATION_ENABLED=false
            export PASEO_VOICE_MODE_ENABLED=false
            # Paseo 0.9.2 refuses the old launch flags (--foreground, --listen,
            # --no-relay, --no-web-ui, --no-inject-mcp) and exits at once; the
            # foreground deployment command reads config.json plus these overrides.
            exec /home/oc/.local/bin/paseo daemon run --home "${'$'}HOME/paseo" </dev/null
        """.trimIndent()
        var child: Process? = null
        // No reader retains even one line: CLI startup can mention host secrets.
        var outputReader: Thread? = null
        var failureMessage = "The agent host could not start. Run its setup check and try again."
        try {
            linux.writeAgentConfig(profile, config)
            linux.clearStaleAgentLoginLock(profile)
            val started = linux.startAgentProcess(profile, listOf("/bin/sh", "-c", script))
            child = started
            outputReader = drain(started)
            started.outputStream.use { output ->
                output.write((password + "\n").toByteArray(Charsets.US_ASCII))
                output.flush()
            }
            // Catch rejected launch flags and startup crashes before registering
            // the daemon. Its output is discarded even when it exits here.
            if (started.waitFor(STARTUP_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                failureMessage = "The agent host stopped as soon as it started. Run its setup check and try again."
                error(failureMessage)
            }
            registerStartedHost(profile, generation, started, port)
            return status(profile)
        } catch (_: Throwable) {
            synchronized(this) { if (children[profile] === child) children.remove(profile) }
            child?.let { cleanup(linux, it, outputReader) }
            error(failureMessage)
        } finally {
            synchronized(this) { starting.remove(profile) }
        }
    }

    private fun registerStartedHost(profile: String, generation: Long, child: Process, port: Int) {
        synchronized(this) {
            check(profile !in deleted && generations[profile] == generation && children[profile]?.isAlive != true)
            children[profile] = child
            linux.trackPrivateAgentService("agent-host.$profile", child, port)
        }
    }

    fun stop(profile: String): Map<String, Any?> {
        identity(profile)
        val child = synchronized(this) {
            generations[profile] = (generations[profile] ?: 0L) + 1L
            children.remove(profile)
        }
        if (child != null) linux.stopAgentProcess(child)
        linux.stopService("agent-host.$profile")
        return status(profile)
    }

    fun delete(profile: String) {
        synchronized(this) { identity(profile); deleted.add(profile) }
        linux.blockAgentProfile(profile)
        stop(profile)
        linux.agentSignIn.deleteProfile(profile)
        linux.deleteAgentHome(profile)
    }

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
            val complete = child.waitFor(15, TimeUnit.SECONDS)
            return mapOf("shared" to (complete && child.exitValue() == 0))
        } finally { cleanup(linux, child, reader) }
    }

    private fun drain(child: Process): Thread = Thread({
        try {
            child.inputStream.use { input ->
                val bytes = ByteArray(4096)
                while (input.read(bytes) >= 0) bytes.fill(0)
            }
        } catch (_: Throwable) { }
    }, "phone-agent-output-discard").apply { isDaemon = true; start() }

    /** Bounded version projection. No raw stdout survives this call. */
    fun version(profile: String, executable: String, expected: String): Map<String, Any?> {
        identity(profile)
        check(Regex("^[a-z][a-z0-9-]{0,63}$").matches(executable) &&
            Regex("^[A-Za-z0-9][A-Za-z0-9.+-]{0,63}$").matches(expected)) { "The agent is unavailable." }
        val installed = linux.installed && reportsVersion(profile, executable, expected)
        return mapOf(
            "installed" to installed, "version" to if (installed) expected else null,
            "payloadPresent" to PhoneAgentInventory.present(linux.rootfs, executable),
        )
    }

    private fun reportsVersion(profile: String, executable: String, expected: String): Boolean {
        val child = try {
            linux.startAgentProcess(profile, listOf("/home/oc/.local/bin/$executable", "--version"))
        } catch (_: Exception) { null } ?: return false
        val output = StringBuilder()
        var reader: Thread? = null
        return try {
            child.outputStream.close()
            val thread = collectBounded(child, output)
            reader = thread
            val complete = child.waitFor(30, TimeUnit.SECONDS)
            thread.join(2000)
            synchronized(output) {
                val value = output.toString()
                output.setLength(0)
                complete && child.exitValue() == 0 &&
                    Regex("(?<![0-9.])v?${Regex.escape(expected)}(?![0-9.])").containsMatchIn(value)
            }
        } catch (_: Exception) {
            false
        } finally {
            cleanup(linux, child, reader)
            synchronized(output) { output.setLength(0) }
        }
    }
}

private const val CLEANUP_JOIN_MS = 2000L
private const val READ_CHUNK = 512
private const val OUTPUT_CAP = 8192

/** Cleanup cannot replace a safe channel error with an uncaught IO error. */
private fun cleanup(linux: BuiltinLinux, child: Process, reader: Thread?) {
    try { linux.stopAgentProcess(child) } catch (_: Throwable) { }
    try { reader?.join(CLEANUP_JOIN_MS) }
    catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    catch (_: Throwable) { }
    try { child.outputStream.close() } catch (_: Throwable) { }
    try { child.inputStream.close() } catch (_: Throwable) { }
}

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
