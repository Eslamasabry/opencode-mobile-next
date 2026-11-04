package io.github.eslamasabry.oc.engine.agents.runtime

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Runs one bridge script and answers its stdout. A non-zero exit or a missing bridge is [Outcome.Failed]; the runtime
 * turns every such failure into [LocalAgentFailureKind.Bridge]. Bound by the app to the Termux bridge.
 */
public fun interface LocalAgentScriptRunner {
    public suspend fun run(script: String, timeout: Duration): Outcome<String>
}

/** Opens a visible Termux terminal running [command]; false when Termux could not be opened. */
public fun interface LocalAgentTerminalOpener {
    public suspend fun open(command: String): Outcome<Boolean>
}

/** A runtime verb's answer: the value, or a typed failure (the Dart `LocalAgentFailure` throw). */
public sealed interface LocalAgentResult<out T> {
    public data class Done<out T>(val value: T) : LocalAgentResult<T>

    public data class Failed(val failure: LocalAgentFailure) : LocalAgentResult<Nothing>
}

/**
 * Port of `LocalAgentRuntime` (lib/termux/local_agent_runtime.dart): the Paseo daemon and Claude Code in the
 * app-managed Ubuntu, driven through `claude.sh` verbs over the bridge port [runner].
 */
public class LocalAgentRuntime(
    private val runner: LocalAgentScriptRunner,
    private val terminalOpener: LocalAgentTerminalOpener,
    private val timeSource: TimeSource,
    /** How often [statusStream] polls while a verb runs. */
    private val pollInterval: Duration = 3.seconds,
    /** How long a verb may stay busy before its poll gives up. */
    private val verbTimeout: Duration = 40.minutes,
) {
    private suspend fun runScript(script: String, timeout: Duration): LocalAgentResult<String> =
        when (val r = runner.run(script, timeout)) {
            is Outcome.Ok -> LocalAgentResult.Done(r.value)
            is Outcome.Failed -> LocalAgentResult.Failed(LocalAgentFailure(LocalAgentFailureKind.Bridge))
        }

    /** One `status` read (a health probe that has to time out twice still fits the 40 s). */
    public suspend fun status(): LocalAgentResult<LocalAgentStatus> =
        when (val r = runScript(LocalAgentsScript.verbScript("status"), 40.seconds)) {
            is LocalAgentResult.Done -> LocalAgentResult.Done(LocalAgentStatus.parse(r.value))
            is LocalAgentResult.Failed -> r
        }

    /** The last [lines] of the install log; empty when nothing ran yet or the bridge failed. */
    public suspend fun logTail(lines: Int = 200): String =
        (runner.run(LocalAgentsScript.logTailScript(lines), 15.seconds) as? Outcome.Ok)?.value ?: ""

    /**
     * Status every [pollInterval] while a verb runs; the last element is the first non-busy status (or the status at
     * [verbTimeout]). A bridge failure ends the flow with that failure.
     */
    public fun statusStream(): Flow<LocalAgentResult<LocalAgentStatus>> = flow {
        val deadline = timeSource.markNow() + verbTimeout
        while (true) {
            val current = status()
            emit(current)
            if (current !is LocalAgentResult.Done || !current.value.busy || deadline.hasPassedNow()) return@flow
            delay(pollInterval)
        }
    }

    /** Installs Node.js, Paseo and Claude Code. Failed when the install ends in `failed`. */
    public suspend fun install(): LocalAgentResult<LocalAgentStatus> = dispatch("install")

    public suspend fun start(): LocalAgentResult<LocalAgentStatus> = dispatch("start")

    public suspend fun restart(): LocalAgentResult<LocalAgentStatus> = dispatch("restart")

    public suspend fun stop(): LocalAgentResult<LocalAgentStatus> {
        when (val r = runScript(LocalAgentsScript.verbScript("stop"), 60.seconds)) {
            is LocalAgentResult.Failed -> return r
            is LocalAgentResult.Done -> Unit
        }
        return when (val s = status()) {
            is LocalAgentResult.Done -> settled(s.value)
            is LocalAgentResult.Failed -> s
        }
    }

    /** Removes the daemon, Node and the packages. The Claude sign-in (`~/.claude`) stays unless [forgetSignIn]. */
    public suspend fun remove(forgetSignIn: Boolean = false): LocalAgentResult<LocalAgentStatus> =
        dispatch("remove", if (forgetSignIn) listOf("--forget-signin") else emptyList())

    /** The daemon password; it goes into the saved server's secure storage and nowhere else. Never log it. */
    public suspend fun password(): LocalAgentResult<String> =
        when (val r = runScript(LocalAgentsScript.verbScript("password"), 20.seconds)) {
            is LocalAgentResult.Failed -> r
            is LocalAgentResult.Done -> r.value.trim().let {
                if (PASSWORD.matches(it)) {
                    LocalAgentResult.Done(it)
                } else {
                    LocalAgentResult.Failed(LocalAgentFailure(LocalAgentFailureKind.PasswordMissing))
                }
            }
        }

    public suspend fun signInStatus(): LocalAgentResult<LocalAgentSignIn> =
        when (val r = runScript(LocalAgentsScript.verbScript("signin-status"), 20.seconds)) {
            is LocalAgentResult.Failed -> r
            is LocalAgentResult.Done -> LocalAgentResult.Done(LocalAgentSignIn.parse(SIGNED_IN.find(r.value)?.groupValues?.get(2)))
        }

    /**
     * Opens a Termux terminal running Claude's own sign-in. The app never sees the credential; it re-reads
     * [signInStatus] when the person returns. The status read first writes this build's script to disk.
     */
    public suspend fun openSignIn(): LocalAgentResult<Boolean> {
        (signInStatus() as? LocalAgentResult.Failed)?.let { return it }
        return when (val opened = terminalOpener.open(LocalAgentsScript.SIGN_IN_COMMAND)) {
            is Outcome.Ok -> LocalAgentResult.Done(opened.value)
            is Outcome.Failed -> LocalAgentResult.Failed(LocalAgentFailure(LocalAgentFailureKind.Bridge))
        }
    }

    /** Project folders under `~/projects` as Ubuntu sees them; the script creates `my-first-project` with git init. */
    public suspend fun projects(): LocalAgentResult<List<String>> =
        when (val r = runScript(LocalAgentsScript.verbScript("projects"), 60.seconds)) {
            is LocalAgentResult.Failed -> r
            is LocalAgentResult.Done -> LocalAgentResult.Done(
                r.value.split('\n').filter { it.startsWith("project=/") }.map { it.substring("project=".length).trim() },
            )
        }

    /** Creates [path] (absolute, inside Ubuntu) when it does not exist yet. */
    public suspend fun ensureProject(path: String): LocalAgentResult<String> =
        when (val r = runScript(LocalAgentsScript.verbScript("ensure-project", listOf(path)), 60.seconds)) {
            is LocalAgentResult.Failed -> r
            is LocalAgentResult.Done ->
                if (r.value.split('\n').any { it.trim() == "project=$path" }) {
                    LocalAgentResult.Done(path)
                } else {
                    LocalAgentResult.Failed(LocalAgentFailure(LocalAgentFailureKind.ProjectNotCreated))
                }
        }

    private suspend fun dispatch(verb: String, args: List<String> = emptyList()): LocalAgentResult<LocalAgentStatus> {
        val output = when (val r = runScript(LocalAgentsScript.verbScript(verb, args), 30.seconds)) {
            is LocalAgentResult.Failed -> return r
            is LocalAgentResult.Done -> r.value
        }
        // Refused dispatch (`claude-busy:…`) or anything else is a bridge failure; its raw text is never shown.
        if (!STARTED.containsMatchIn(output.trim())) {
            return LocalAgentResult.Failed(LocalAgentFailure(LocalAgentFailureKind.Bridge))
        }
        return when (val last = statusStream().last()) {
            is LocalAgentResult.Done -> settled(last.value)
            is LocalAgentResult.Failed -> last
        }
    }

    private fun settled(status: LocalAgentStatus): LocalAgentResult<LocalAgentStatus> {
        status.failure?.let { return LocalAgentResult.Failed(it) }
        if (status.busy) return LocalAgentResult.Failed(LocalAgentFailure(LocalAgentFailureKind.Timeout))
        return LocalAgentResult.Done(status)
    }

    private companion object {
        val PASSWORD = Regex("^[0-9a-f]{48}$")
        val SIGNED_IN = Regex("(^|\n)signed_in=(yes|no|unknown)\\s*($|\n)")
        val STARTED = Regex("(^|\n)claude-started:[0-9]+\\s*$")
    }
}
