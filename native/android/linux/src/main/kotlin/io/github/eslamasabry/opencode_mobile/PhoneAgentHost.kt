package io.github.eslamasabry.opencode_mobile

import android.os.Build
import java.util.concurrent.TimeUnit

private const val STARTUP_GRACE_MILLIS = 1500L
private const val AGENT_PORT = 4099
private const val PASEO_PATH = "/home/oc/.local/bin/paseo"
private const val UNAVAILABLE = "The agent host is unavailable."
private const val START_FAILED = "The agent host could not start. Run its setup check and try again."

/** No provider output, passwords or OAuth data enter ordinary service logs. */
class PhoneAgentHost(private val linux: BuiltinLinux) {
    private val children = mutableMapOf<String, Process>()
    private val generations = mutableMapOf<String, Long>()
    private val helperVersions = mutableMapOf<String, String>()

    class Failure(
        val code: String,
        message: String = "The agent host needs attention. Open agent setup and try again.",
    ) : IllegalStateException(message)

    /** Everything one start needs; the helper version pair is optional. */
    class StartRequest(
        val profile: String,
        val password: String,
        val port: Int,
        val config: String,
        val helper: HelperPolicy = HelperPolicy(),
    )

    /** Which helper version is wanted and whether a running host may be replaced for it. */
    class HelperPolicy(
        val expectedVersion: String? = null,
        val restartIfVersionChanged: Boolean = false,
        val forceRestart: Boolean = false,
    )

    companion object {
        /** The wire code of an agent-host failure, or null for any other error. */
        fun failureCode(error: Throwable): String? = when (error) {
            is Failure -> error.code
            else -> null
        }
    }

    private class Admission(val previous: Process?, val generation: Long)

    private class Launched {
        var child: Process? = null
        var reader: Thread? = null
    }

    private val deleted = mutableSetOf<String>()
    private val starting = mutableSetOf<String>()

    /** The workspace and version checks, which never touch the daemon. */
    val checks = PhoneAgentChecks(linux)

    fun status(profile: String): Map<String, Any?> = synchronized(this) {
        identity(profile)
        linux.privateAgentDiagnostics("agent-host.$profile") +
            mapOf("running" to (children[profile]?.isAlive == true),
                "abi" to (Build.SUPPORTED_ABIS.firstOrNull() ?: ""),
                "helperVersion" to helperVersions[profile].takeIf { children[profile]?.isAlive == true })
    }

    fun start(request: StartRequest): Map<String, Any?> {
        val profile = request.profile
        identity(profile)
        check(Regex("^[a-f0-9]{64}$").matches(request.password) && request.port == AGENT_PORT) { UNAVAILABLE }
        val expected = request.helper.expectedVersion
        check(expected == null || Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$").matches(expected))
        val admission = admit(request) ?: return status(profile)
        val launched = Launched()
        val outcome = runCatching { launchHost(request, admission, launched) }
        val failure = outcome.exceptionOrNull()
        if (failure != null) {
            synchronized(this) {
                if (children[profile] === launched.child) { children.remove(profile); helperVersions.remove(profile) }
            }
            launched.child?.let { cleanup(linux, it, launched.reader) }
        }
        synchronized(this) { starting.remove(profile) }
        if (failure != null) throw failure as? Failure ?: IllegalStateException(START_FAILED)
        return outcome.getOrThrow()
    }

    /** Null when the host already runs the wanted version; otherwise the start is admitted. */
    private fun admit(request: StartRequest): Admission? = synchronized(this) {
        val profile = request.profile
        check(profile !in deleted) { UNAVAILABLE }
        val current = children[profile]?.takeIf { it.isAlive }
        if (current != null && !restartWanted(request, current)) return@synchronized null
        requireNoOtherOwner(profile, children)
        check(starting.isEmpty()) { "The agent host is still starting. Wait a moment and try again." }
        SetupDiskSpace.error(linux.home, SetupDiskSpace.MIN_LAUNCH_BYTES)?.let { error(it) }
        starting.add(profile)
        Admission(current, (generations[profile] ?: 0L).also { generations[profile] = it })
    }

    /** Whether a running host is to be replaced; throws when it must be but may not be. */
    private fun restartWanted(request: StartRequest, current: Process): Boolean {
        val profile = request.profile
        val expected = request.helper.expectedVersion
        val changed = expected != null && helperVersions[profile] != expected
        val policy = request.helper
        if (!policy.forceRestart && !changed) return false
        if (!policy.forceRestart && !policy.restartIfVersionChanged) throw Failure("helper_update_required")
        linux.requireAgentHostUpdateIdle(profile, current)
        return true
    }

    private fun launchHost(request: StartRequest, admission: Admission, launched: Launched): Map<String, Any?> {
        val profile = request.profile
        // Probe the installed executable before draining the working daemon.
        // A changed symlink alone says nothing about its loaded version.
        val installedVersion = request.helper.expectedVersion?.let { expected ->
            checks.runVersion(profile, PASEO_PATH)?.trim().also {
                if (it != expected) throw Failure("helper_update_required")
            }
        }
        admission.previous?.let { retire(profile, it) }
        linux.writeAgentConfig(profile, request.config)
        linux.clearStaleAgentLoginLock(profile)
        // No helper of ours runs now (a live one returned above or was
        // stopped). A lock left by a killed supervisor would end this launch.
        PaseoStaleLock.clear(linux.agentPaseoHome(profile),
            synchronized(this) { children[profile]?.isAlive == true }) { linux.agentPortAnswers(request.port) }
        val started = linux.startAgentProcess(profile, listOf("/bin/sh", "-c", hostScript(request.port)))
        launched.child = started
        launched.reader = drain(started)
        started.outputStream.use { output ->
            output.write((request.password + "\n").toByteArray(Charsets.US_ASCII))
            output.flush()
        }
        awaitStartup(started)
        registerStartedHost(profile, admission.generation, started, request.port, installedVersion)
        return status(profile)
    }

    private fun retire(profile: String, current: Process) {
        linux.requireAgentHostUpdateIdle(profile, current)
        linux.stopAgentProcess(current)
        synchronized(this) {
            if (children[profile] === current) children.remove(profile)
            helperVersions.remove(profile)
        }
        linux.stopService("agent-host.$profile")
    }

    private fun registerStartedHost(profile: String, generation: Long, child: Process, port: Int, version: String?) {
        synchronized(this) {
            check(profile !in deleted && generations[profile] == generation && children[profile]?.isAlive != true)
            children[profile] = child
            if (version != null) helperVersions[profile] = version else helperVersions.remove(profile)
            linux.trackPrivateAgentService("agent-host.$profile", child, port)
        }
    }

    /** Read-only admission before any shared pinned payload or symlink is changed. */
    fun prepareUpdate(profile: String): Map<String, Any?> = synchronized(this) {
        identity(profile)
        if (profile in deleted || starting.isNotEmpty()) throw Failure("agent_host_busy")
        requireNoOtherOwner(profile, children)
        linux.requireAgentHostUpdateIdle(profile, children[profile]?.takeIf { it.isAlive })
        mapOf("ready" to true)
    }

    fun stop(profile: String, guardedForUpdate: Boolean = false): Map<String, Any?> {
        identity(profile)
        val child = synchronized(this) {
            if (guardedForUpdate) {
                if (starting.isNotEmpty()) throw Failure("agent_host_busy")
                requireNoOtherOwner(profile, children)
                linux.requireAgentHostUpdateIdle(profile, children[profile]?.takeIf { it.isAlive })
            }
            helperVersions.remove(profile)
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
}

private fun requireNoOtherOwner(profile: String, children: Map<String, Process>) {
    if (children.any { (owner, process) -> owner != profile && process.isAlive }) {
        throw PhoneAgentHost.Failure("agent_host_unowned")
    }
}

/**
 * Catch rejected launch flags and startup crashes before registering the
 * daemon. Its output is discarded even when it exits here.
 */
private fun awaitStartup(started: Process) {
    if (started.waitFor(STARTUP_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
        // A fixed code, so Details can tell this apart from a refused launch.
        throw PhoneAgentHost.Failure("agent_host_exited",
            "The agent host stopped as soon as it started. Run its setup check and try again.")
    }
}

// A Claude process Android stopped mid-refresh leaves Claude's login
// lock behind, and the first message then fails with "another Claude
// Code process is refreshing it". No Claude process of this profile
// runs now (the helper starts them all), so a lock left is stale.
private fun hostScript(port: Int): String {
    return """
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
}
