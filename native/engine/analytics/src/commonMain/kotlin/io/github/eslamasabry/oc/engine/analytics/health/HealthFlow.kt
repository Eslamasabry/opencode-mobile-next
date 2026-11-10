package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import kotlin.concurrent.Volatile

/**
 * The process-wide [Telemetry] for code that has no injected one (start-up hooks, engines whose constructors predate
 * telemetry), as Flutter's `AppTelemetry`. Inert until the app installs its service.
 */
public object AppTelemetry {
    @Volatile private var installed: Telemetry = NoTelemetry

    public val current: Telemetry
        get() = installed

    public fun install(telemetry: Telemetry) {
        installed = telemetry
    }

    /** Test hook: back to the inert default. */
    public fun reset() {
        installed = NoTelemetry
    }

    /** A breadcrumb (`area.action`, optionally `:word`); dropped unless listed in [BreadcrumbWords]. */
    public fun crumb(name: String) {
        installed.breadcrumb(name)
    }

    public fun track(event: ProductEvent, props: Map<String, Any?> = emptyMap()) {
        installed.track(event, props)
    }
}

/** What kind of failure [failure] is, in the contract's fixed words. Only the word leaves; the failure is dropped. */
public fun failKindOf(failure: Failure?, forSend: Boolean = false): String =
    when (failure) {
        null -> "other"
        is Failure.TimedOut, is Failure.ComputerDidNotAnswer -> "timeout"
        is Failure.Certificate -> "tls"
        is Failure.SignIn -> "auth"
        is Failure.Server -> "server_5xx"
        is Failure.Rejected ->
            when (failure.status) {
                401, 403 -> "auth"
                413 -> if (forSend) "too_large" else "protocol"
                else -> if (forSend) "rejected" else "protocol"
            }
        is Failure.Incompatible, is Failure.UnreadableReply -> "protocol"
        is Failure.Network ->
            when (failure.why) {
                NetworkWhy.Dns -> "dns"
                NetworkWhy.Refused -> "refused"
                NetworkWhy.Offline, NetworkWhy.NoRoute -> "offline"
                NetworkWhy.Reset, NetworkWhy.Unknown -> "other"
            }
        else -> "other"
    }

/**
 * The health flow events (section B; Flutter `HealthFlow`), one function each, so a call site names a result and a
 * duration and the words come from here. Each event is dropped unless the person agreed to `usage`; each also leaves
 * the breadcrumb the contract lists, and the flows section C2 names produce their Performance trace.
 */
public class HealthFlow(private val telemetry: () -> Telemetry = { AppTelemetry.current }) {
    public fun connAttempt(serverKind: String, ok: Boolean, elapsedMs: Long, failure: Failure? = null) {
        val t = telemetry()
        val result = if (ok) "ok" else "fail"
        t.breadcrumb("conn.connect")
        if (!ok) ProblemTally.note(t.ring, failKindOf(failure))
        t.track(
            ProductEvent.ConnAttempt,
            buildMap {
                put("server_kind", serverKind)
                put("result", result)
                if (!ok) put("fail_kind", failKindOf(failure))
                put("ms_range", HealthRanges.connMsOf(elapsedMs))
            },
        )
        t.performanceTrace(PerfSchema.CONN_CONNECT, elapsedMs, mapOf("server_kind" to serverKind, "result" to result))
    }

    public fun connDrop(serverKind: String, reason: String, recovered: Boolean) {
        val t = telemetry()
        t.breadcrumb(if (recovered) "conn.reconnect" else "conn.drop")
        t.track(
            ProductEvent.ConnDrop,
            mapOf("server_kind" to serverKind, "reason" to reason, "recovered" to if (recovered) "yes" else "no"),
        )
    }

    /** [firstReplyMs] is how long the first reply took, or null for none; [serverKind] is `phone` or `computer`. */
    public fun sendResult(
        result: String,
        failure: Failure? = null,
        firstReplyMs: Long? = null,
        serverKind: String? = null,
        traits: SendTraits? = null,
        /** A failure already reduced to a word of [HealthWords.modelFailKinds] (the model's side refused the turn). */
        failWord: String? = null,
    ) {
        val t = telemetry()
        val kindWord = failWord?.takeIf { it in HealthWords.modelFailKinds } ?: failKindOf(failure, forSend = true)
        t.breadcrumb("chat.send")
        if (result == "fail") ProblemTally.note(t.ring, kindWord)
        t.track(
            ProductEvent.SendResult,
            buildMap {
                put("result", result)
                if (result == "fail") put("fail_kind", kindWord)
                put("first_reply_range", HealthRanges.firstReplyOf(firstReplyMs))
                if (traits != null) {
                    put("agent", chatAgentWord(traits.agent))
                    put("input", traits.input)
                    if (serverKind != null) put("server_kind", serverKind)
                }
            },
        )
        if (firstReplyMs != null && serverKind != null) {
            t.performanceTrace(PerfSchema.SEND_FIRST_REPLY, firstReplyMs, mapOf("server_kind" to serverKind))
        }
    }

    public fun turnStall(stage: String, waitedMs: Long) {
        val t = telemetry()
        t.breadcrumb("chat.tool_wait")
        t.track(ProductEvent.TurnStall, mapOf("stage" to stage, "minutes_range" to HealthRanges.stallMinutesOf(waitedMs)))
    }

    public fun queueFlush(sent: Int, failed: Int) {
        telemetry().track(
            ProductEvent.QueueFlush,
            mapOf("sent" to HealthRanges.countOf(sent), "failed" to HealthRanges.countOf(failed)),
        )
    }

    /** One built-in Linux setup step. [exit] is the exit code when it gave one. */
    public fun linuxSetupStep(
        step: String,
        ok: Boolean,
        elapsedMs: Long,
        exit: Int? = null,
        compatMode: String = "none",
        freeGigabytes: Double? = null,
        attempt: Int? = null,
        failKind: String? = null,
        /** `skipped` or `cancelled` in place of ok/fail; [ok] is then false and no failure is counted. */
        notRun: String? = null,
    ) {
        val t = telemetry()
        val result = notRun ?: if (ok) "ok" else "fail"
        val failed = result == "fail"
        t.breadcrumb("linux.step:$step")
        if (failed) t.breadcrumb("linux.fail")
        t.track(
            ProductEvent.LinuxSetupStep,
            buildMap {
                put("step", step)
                put("result", result)
                if (attempt != null) put("attempt", HealthRanges.attemptOf(attempt))
                if (failed && failKind != null) put("fail_kind", failKind)
                put("ms_range", HealthRanges.setupMsOf(elapsedMs))
                // Native steps have no exit code: leave the two out rather than send a word that means "unknown".
                if (ok || exit != null) {
                    put("exit_kind", exitKindOf(exit, ok))
                    put("signal", if (ok) "none" else signalOfExit(exit))
                }
                put("compat_mode", compatMode)
                if (freeGigabytes != null) put("free_space_range", HealthRanges.storageOf(freeGigabytes))
            },
        )
        t.performanceTrace(
            PerfSchema.linuxSetup(step),
            elapsedMs,
            mapOf("step" to step, "result" to if (result == "ok" || result == "fail") result else "fail"),
        )
    }

    public fun agentStart(agent: String, ok: Boolean, failKind: String = "other", elapsedMs: Long? = null) {
        val t = telemetry()
        val word = agentWord(agent)
        val result = if (ok) "ok" else "fail"
        t.breadcrumb("agent.start:$word")
        if (!ok) t.breadcrumb("agent.fail")
        t.track(
            ProductEvent.AgentStart,
            buildMap {
                put("agent", word)
                put("result", result)
                if (!ok) put("fail_kind", failKind)
            },
        )
        if (elapsedMs != null) {
            t.performanceTrace(PerfSchema.AGENT_START, elapsedMs, mapOf("agent" to word, "result" to result))
        }
    }

    public fun bgServiceEnd(reason: String, batteryExempt: Boolean) {
        telemetry().track(
            ProductEvent.BgServiceEnd,
            mapOf("reason" to reason, "battery_exempt" to if (batteryExempt) "yes" else "no"),
        )
    }

    public fun updateResult(kind: String, result: String, failKind: String? = null) {
        val t = telemetry()
        t.breadcrumb(
            when (result) {
                "installed" -> "update.install"
                "failed" -> "update.fail"
                else -> "update.offer"
            }
        )
        t.track(
            ProductEvent.UpdateResult,
            buildMap {
                put("kind", kind)
                put("result", result)
                if (failKind != null) put("fail_kind", failKind)
            },
        )
    }

    // ---- setup journey ----

    public fun setupStepReached(step: String, attempt: Int) {
        telemetry().track(
            ProductEvent.SetupStepReached,
            mapOf("step" to step, "attempt" to HealthRanges.attemptOf(attempt)),
        )
    }

    public fun setupDone(totalMs: Long, serverKind: String) {
        telemetry().track(
            ProductEvent.SetupDone,
            mapOf("minutes_range" to HealthRanges.setupTotalOf(totalMs), "server_kind" to serverKind),
        )
    }

    public fun setupAbandoned(step: String, reason: String, awayMs: Long) {
        telemetry().track(
            ProductEvent.SetupAbandoned,
            mapOf("step" to step, "reason" to reason, "minutes_range" to HealthRanges.awayOf(awayMs)),
        )
    }

    public fun setupResumed(step: String, awayMs: Long) {
        telemetry().track(
            ProductEvent.SetupResumed,
            mapOf("step" to step, "minutes_range" to HealthRanges.awayOf(awayMs)),
        )
    }

    /** The first reply this install ever saw. */
    public fun firstReply(serverKind: String, firstReplyMs: Long) {
        telemetry().track(
            ProductEvent.FirstReply,
            mapOf("server_kind" to serverKind, "first_reply_range" to HealthRanges.firstReplyOf(firstReplyMs)),
        )
    }

    // ---- chat journey ----

    public fun chatTurn(turn: ChatTurnSummary) {
        val t = telemetry()
        t.track(
            ProductEvent.ChatTurn,
            buildMap {
                put("outcome", turn.outcome)
                put("agent", chatAgentWord(turn.agent))
                put("server_kind", turn.serverKind)
                put("first_reply_range", HealthRanges.firstReplyOf(turn.firstReplyMs))
                put("ms_range", HealthRanges.turnOf(turn.totalMs))
                put("tools_range", HealthRanges.toolsOf(turn.tools))
                if (turn.failKind != null) put("fail_kind", turn.failKind)
                put("recovered", turn.recovered)
                put("follow_up", turn.followUp)
            },
        )
    }

    public fun feature(feature: io.github.eslamasabry.oc.engine.analytics.product.ProductFeature) {
        telemetry().feature(feature)
    }

    public fun approval(kind: String, result: String, waitedMs: Long) {
        val t = telemetry()
        t.breadcrumb(if (result == "denied" || result == "declined") "chat.deny" else "chat.approve")
        t.track(
            ProductEvent.Approval,
            mapOf("kind" to kind, "result" to result, "ms_range" to HealthRanges.turnOf(waitedMs)),
        )
    }

    public companion object {
        /** The contract's step word for a setup component id, or null for one the contract does not count. */
        public fun linuxStepOf(componentId: String?): String? =
            when (componentId) {
                "linux" -> "unpack"
                "essentials", "python" -> "essentials"
                "node" -> "node"
                "opencode" -> "opencode"
                "paseo" -> "paseo"
                "aiteam" -> "agents"
                "start" -> "start"
                "voice" -> "voice"
                else -> null
            }

        /** The chat agent word: the built-in names only; a custom agent's own name is never sent. */
        public fun chatAgentWord(agent: String?): String =
            agent?.trim()?.lowercase()?.takeIf { it in HealthWords.chatAgents && it != "other" } ?: "other"

        /** The agent word: a native agent id (`claude`, `claude-code`) or a contract word; anything else `other`. */
        public fun agentWord(agent: String): String {
            val id = agent.lowercase().replace('-', '_')
            return when {
                id in HealthWords.agents -> id
                id == "claude" -> "claude_code"
                else -> "other"
            }
        }

        public fun exitKindOf(exit: Int?, ok: Boolean): String =
            when {
                ok -> "0"
                exit == null -> "other"
                exit > SIGNAL_BASE -> "signal"
                exit == 0 -> "0"
                exit == 1 -> "1"
                exit == 126 -> "126"
                exit == 127 -> "127"
                else -> "other"
            }

        /** `SIGILL` ... for an exit code of 128 plus a signal, `other` for another signal, `none` for no signal. */
        public fun signalOfExit(exit: Int?): String {
            if (exit == null || exit <= SIGNAL_BASE) return "none"
            return when (exit - SIGNAL_BASE) {
                4 -> "SIGILL"
                11 -> "SIGSEGV"
                7 -> "SIGBUS"
                9 -> "SIGKILL"
                else -> "other"
            }
        }

        private const val SIGNAL_BASE = 128
    }
}

/** What the send path knows about a prompt, as words: [input] is one of [HealthWords.inputs]. */
public data class SendTraits(val agent: String? = null, val input: String = "text")

/** One finished turn, as words and numbers only. */
public data class ChatTurnSummary(
    val outcome: String,
    val agent: String?,
    val serverKind: String,
    val firstReplyMs: Long?,
    val totalMs: Long,
    val tools: Int,
    val failKind: String?,
    val recovered: String,
    val followUp: String,
)

/** The inert default: sends nothing, keeps nothing, has no vendor. */
public object NoTelemetry : Telemetry {
    private val off = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val usage: kotlinx.coroutines.flow.StateFlow<Boolean> = off
    override val crashes: kotlinx.coroutines.flow.StateFlow<Boolean> = off
    override val reportingConsent: kotlinx.coroutines.flow.StateFlow<Boolean> = off
    override val available: Boolean = false
    override val healthKeys: Map<String, String> = emptyMap()
    override val ring: AppLogRing = AppLogRing(capacity = 0)

    override suspend fun setConsent(usage: Boolean, crashes: Boolean): Boolean = false

    override fun setReportingConsent(granted: Boolean): Unit = Unit

    override fun reportCrash(category: String, safeStack: List<String>): Unit = Unit

    override fun reportFailure(name: String, keys: Map<String, String>): Unit = Unit

    override fun recordFailure(name: String, keys: Map<String, Any?>): Unit = Unit

    override fun breadcrumb(text: String): Unit = Unit

    override fun setHealthKeys(keys: Map<String, Any?>): Unit = Unit

    override fun setUserProperties(properties: Map<String, Any?>): Unit = Unit

    override fun track(event: ProductEvent, props: Map<String, Any?>): Unit = Unit

    override fun performanceTrace(name: String, elapsedMs: Long, attributes: Map<String, Any?>): Unit = Unit

    override suspend fun sendProblemReport(redactedText: String): String? = null

    override fun takeSessionTurns(): Int = 0

    override suspend fun reportCode(): String? = null
}
