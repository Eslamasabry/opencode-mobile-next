package io.github.eslamasabry.oc.engine.usagereaders.computer

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.usagereaders.ProviderQuotaStatus
import io.github.eslamasabry.oc.engine.usagereaders.QuotaProvider
import io.github.eslamasabry.oc.engine.usagereaders.SignInMethod
import io.github.eslamasabry.oc.engine.usagereaders.UsageFailure
import io.github.eslamasabry.oc.engine.usagereaders.UsageProblem
import io.github.eslamasabry.oc.engine.usagereaders.UsageReadContext
import io.github.eslamasabry.oc.engine.usagereaders.UsageReading
import io.github.eslamasabry.oc.engine.usagereaders.UsageReadingStatus
import io.github.eslamasabry.oc.engine.usagereaders.UsageServer
import io.github.eslamasabry.oc.engine.usagereaders.UsageSignIn
import io.github.eslamasabry.oc.engine.usagereaders.UsageSource
import io.github.eslamasabry.oc.engine.usagereaders.UsageSourceResult
import io.github.eslamasabry.oc.engine.usagereaders.UsageSwitches
import io.github.eslamasabry.oc.engine.usagereaders.usageBareReading
import io.github.eslamasabry.oc.engine.usagereaders.usageReadingFromSnapshot
import kotlinx.coroutines.CompletableDeferred

/**
 * Reads Codex, GLM and MiniMax plans on a saved OpenCode server that is not In-app Ubuntu, by running a one-shot,
 * read-only check there through the server's own terminal API (Flutter `ComputerUsageSource`). Per server and off
 * until the person turns it on (`oc.usageComputerReader.<profileId>`, swept with the profile).
 *
 * Cadence: a background refresh reads a server at most every [backgroundEveryMs] (15 min), a foreground one at most
 * every [foregroundEveryMs] (1 min); a forced read never waits. Only plain reasons and provider lists are kept in
 * memory; the script and the terminal output never are.
 */
public class ComputerUsageSource(
    private val switches: UsageSwitches,
    private val servers: () -> List<UsageServer>,
    private val isReadable: (String) -> Boolean,
    private val createPort: (UsageServer) -> ComputerTerminalPort,
    /** The reader script (the [COMPUTER_USAGE_ASSET] asset). */
    private val loadSource: suspend () -> String,
    /** Wall clock, epoch ms. */
    private val clock: () -> Long,
    /** Monotonic ms for the check's deadline. */
    private val elapsedMs: () -> Long,
    private val timeoutMs: Long = COMPUTER_USAGE_TIMEOUT_MS,
    private val backgroundEveryMs: Long = 15 * 60_000L,
    private val foregroundEveryMs: Long = 60_000L,
) : UsageSource {
    private val attempted = HashMap<String, Long>()
    private val problems = HashMap<String, ComputerUsageProblem?>()
    private val pending = HashMap<String, ComputerUsageResult>()
    private val lastFound = HashMap<String, List<QuotaProvider>>()
    private val running = HashMap<String, CompletableDeferred<ComputerUsageResult>>()

    public fun isEnabled(profileId: String): Boolean = switches.bool(enabledKey(profileId)) ?: false

    /** Saved OpenCode servers this can read: not In-app Ubuntu, a normal password in place. */
    public fun appliesTo(server: UsageServer): Boolean =
        server.backend == BackendKind.OpenCode2 && !server.inApp && isReadable(server.id) && !server.needsPassword

    /** Why the last check on [connectionId] could not read it; null when it could (or never ran). */
    public fun problemFor(connectionId: String): ComputerUsageProblem? = problems[connectionId]

    /** What the last run found on [connectionId], for the Read-from screen. */
    public fun foundOn(connectionId: String): List<QuotaProvider> = lastFound[connectionId] ?: emptyList()

    private suspend fun run(server: UsageServer): ComputerUsageResult {
        running[server.id]?.let { return it.await() }
        val done = CompletableDeferred<ComputerUsageResult>()
        running[server.id] = done
        try {
            val result = runNow(server)
            done.complete(result)
            return result
        } finally {
            if (!done.isCompleted) done.complete(ComputerUsageResult(problem = ComputerUsageProblem.Unreachable))
            running.remove(server.id)
        }
    }

    private suspend fun runNow(server: UsageServer): ComputerUsageResult {
        attempted[server.id] = clock()
        var port: ComputerTerminalPort? = null
        val outcome = attempt({ Failure.Unexpected() }) {
            val source = loadSource()
            val opened = createPort(server)
            port = opened
            runComputerUsageCheck(opened, source, elapsedMs, timeoutMs)
        }
        port?.let { opened -> attempt({ Failure.Unexpected() }) { opened.close() } }
        val result = when (outcome) {
            is Outcome.Ok -> outcome.value
            is Outcome.Failed -> ComputerUsageResult(problem = ComputerUsageProblem.Unreachable)
        }
        problems[server.id] = result.problem
        lastFound[server.id] = result.found
        return result
    }

    /** Turns reading on for [server] and runs one check right away; the next refresh uses that answer. */
    public suspend fun turnOn(server: UsageServer): ComputerUsageResult {
        switches.putBool(enabledKey(server.id), true)
        val result = run(server)
        if (result.problem == ComputerUsageProblem.ServerTooOld) {
            // This OpenCode has no terminal API: there is nothing to keep trying.
            switches.remove(enabledKey(server.id))
            return result
        }
        pending[server.id] = result
        return result
    }

    /** One check now ("Read now" on the Read-from screen). */
    public suspend fun readNow(server: UsageServer): ComputerUsageResult {
        val result = run(server)
        pending[server.id] = result
        return result
    }

    public suspend fun turnOff(profileId: String) {
        switches.remove(enabledKey(profileId))
        forget(profileId)
    }

    /** A deleted server: drop what is kept in memory. The switch goes with the profile sweep. */
    public fun forget(profileId: String) {
        pending.remove(profileId)
        problems.remove(profileId)
        lastFound.remove(profileId)
        attempted.remove(profileId)
    }

    override suspend fun read(context: UsageReadContext): UsageSourceResult {
        val readings = ArrayList<UsageReading>()
        val signIns = ArrayList<UsageSignIn>()
        val failures = ArrayList<UsageFailure>()
        val unreached = LinkedHashSet<String>()
        for (server in servers()) {
            if (!appliesTo(server) || !isEnabled(server.id)) continue
            var result = pending.remove(server.id)
            if (result == null) {
                val before = attempted[server.id]
                val every = if (context.background) backgroundEveryMs else foregroundEveryMs
                if (!context.force && before != null && context.nowMs - before < every) continue
                result = run(server)
            }
            fold(server.id, result, context, readings, signIns, failures, unreached)
        }
        return UsageSourceResult(readings, signIns, failures, unreached)
    }

    @Suppress("LongParameterList")
    private fun fold(
        id: String,
        result: ComputerUsageResult,
        context: UsageReadContext,
        readings: MutableList<UsageReading>,
        signIns: MutableList<UsageSignIn>,
        failures: MutableList<UsageFailure>,
        unreached: MutableSet<String>,
    ) {
        val problem = result.problem
        if (problem == ComputerUsageProblem.Unreachable) {
            unreached += id
            return
        }
        if (problem != null) {
            // The reader itself is broken: every plan it would have read says so.
            val signedIn = context.signedIn?.invoke(id).orEmpty()
            for (p in QuotaProvider.entries) {
                if (!p.collectionAvailable) continue
                if (context.lastReading?.invoke(id, p.wire) != null || p.wire in signedIn) {
                    failures += UsageFailure(id, p.wire, UsageProblem.Broken)
                }
            }
            return
        }
        for (provider in QuotaProvider.entries) {
            if (!provider.collectionAvailable) continue
            val auth = result.auth[provider]
            val snapshot = result.snapshots[provider]
            if (auth == null) {
                // Signed out here now: only a plan that was read here before stays listed, as "not signed in".
                if (context.lastReading?.invoke(id, provider.wire) != null) {
                    readings += usageBareReading(id, provider, UsageReadingStatus.NotSignedIn, context.nowMs)
                }
                continue
            }
            val payAsYouGo = result.payAsYouGo(provider)
            signIns += UsageSignIn(
                providerId = provider.wire,
                connectionId = id,
                method = if (payAsYouGo) SignInMethod.ApiKey else SignInMethod.Plan,
                displayName = provider.displayName,
            )
            if (payAsYouGo || snapshot == null) continue
            when {
                snapshot.status == ProviderQuotaStatus.Ok && snapshot.canShowWindows ->
                    readings += usageReadingFromSnapshot(id, provider, snapshot)
                snapshot.status == ProviderQuotaStatus.AuthRequired ->
                    readings += usageBareReading(id, provider, UsageReadingStatus.AuthRequired, context.nowMs)
                snapshot.status == ProviderQuotaStatus.RateLimited ->
                    failures += UsageFailure(id, provider.wire, UsageProblem.RateLimited)
                else -> failures += UsageFailure(id, provider.wire, UsageProblem.Broken)
            }
        }
    }

    public companion object {
        public fun enabledKey(profileId: String): String = "oc.usageComputerReader.$profileId"
    }
}
