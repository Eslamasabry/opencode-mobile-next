package io.github.eslamasabry.opencode_mobile

import android.os.Build
import java.util.concurrent.TimeUnit

/** No provider output, passwords or OAuth data enter ordinary service logs. */
class PhoneAgentHost(private val linux: BuiltinLinux) {
    private val children = mutableMapOf<String, Process>()
    private val generations = mutableMapOf<String, Long>()
    private val deleted = mutableSetOf<String>()

    private fun identity(profile: String) {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profile)) { "The agent host is unavailable." }
    }

    fun status(profile: String): Map<String, Any?> = synchronized(this) {
        identity(profile)
        mapOf("running" to (children[profile]?.isAlive == true), "abi" to (Build.SUPPORTED_ABIS.firstOrNull() ?: ""))
    }

    fun start(profile: String, password: String, port: Int, config: String): Map<String, Any?> {
        identity(profile)
        check(Regex("^[a-f0-9]{64}$").matches(password) && port == 4099) { "The agent host is unavailable." }
        val generation = synchronized(this) {
            check(profile !in deleted) { "The agent host is unavailable." }
            if (children[profile]?.isAlive == true) return status(profile)
            check(children.values.none { it.isAlive }) { "The agent host is unavailable." }
            (generations[profile] ?: 0L).also { generations[profile] = it }
        }
        linux.writeAgentConfig(profile, config)
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
            exec /home/oc/.local/bin/paseo start --foreground --home "${'$'}HOME/paseo" --listen 127.0.0.1:$port --no-relay --no-web-ui --no-inject-mcp </dev/null
        """.trimIndent()
        val child = linux.startAgentProcess(profile, listOf("/bin/sh", "-c", script))
        // No reader retains even one line: CLI startup can mention host secrets.
        val drain = Thread {
            try { child.inputStream.use { input -> val bytes = ByteArray(4096); while (input.read(bytes) >= 0) bytes.fill(0) } }
            catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
        try {
            child.outputStream.use { output -> output.write((password + "\n").toByteArray(Charsets.US_ASCII)); output.flush() }
            synchronized(this) {
                check(profile !in deleted && generations[profile] == generation && children[profile]?.isAlive != true)
                children[profile] = child
                linux.trackPrivateAgentService("agent-host.$profile", child, port)
            }
            return status(profile)
        } catch (_: Exception) {
            linux.stopAgentProcess(child)
            drain.join(2000)
            throw IllegalStateException("The agent host could not start.")
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
        child.outputStream.close()
        val drain = Thread { try { child.inputStream.use { input -> val buffer = ByteArray(4096); while (input.read(buffer) >= 0) buffer.fill(0) } } catch (_: Exception) { } }
            .apply { isDaemon = true; start() }
        val complete = child.waitFor(15, TimeUnit.SECONDS)
        if (!complete) linux.stopAgentProcess(child)
        drain.join(2000)
        return mapOf("shared" to (complete && child.exitValue() == 0))
    }

    /** Bounded version projection. No raw stdout survives this call. */
    fun version(profile: String, executable: String, expected: String): Map<String, Any?> {
        identity(profile)
        check(Regex("^[a-z][a-z0-9-]{0,63}$").matches(executable) &&
            Regex("^[A-Za-z0-9][A-Za-z0-9.+-]{0,63}$").matches(expected)) { "The agent is unavailable." }
        if (!linux.installed) return mapOf("installed" to false, "version" to null)
        val child = linux.startAgentProcess(profile, listOf("/home/oc/.local/bin/$executable", "--version"))
        val output = StringBuilder()
        val reader = Thread {
            try { child.inputStream.reader().use { input ->
                val buffer = CharArray(512)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    synchronized(output) { if (output.length < 8192) output.append(buffer, 0, minOf(count, 8192 - output.length)) }
                    buffer.fill('\u0000')
                }
            } } catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
        val complete = child.waitFor(30, TimeUnit.SECONDS)
        if (!complete) linux.stopAgentProcess(child)
        reader.join(2000)
        val installed = synchronized(output) {
            val value = output.toString()
            output.setLength(0)
            complete && child.exitValue() == 0 && Regex("(?<![0-9.])v?${Regex.escape(expected)}(?![0-9.])").containsMatchIn(value)
        }
        return mapOf("installed" to installed, "version" to if (installed) expected else null)
    }
}
