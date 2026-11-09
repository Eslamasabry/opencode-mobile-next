package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.product.ProductFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Whether this install ever saw a reply (`first_reply` is sent once per install). */
public interface FirstReplyOnce {
    public fun seen(): Boolean

    public fun mark()

    public object Never : FirstReplyOnce {
        override fun seen(): Boolean = true

        override fun mark(): Unit = Unit
    }
}

/** What the send path knows about a prompt. Words only: [agent] is reduced to a built-in name before it is sent. */
public data class PromptTraits(val agent: String? = null, val hasAttachment: Boolean = false)

/**
 * The chat journey, from "send" to "the reply is done" (B-190 and the journeys doc `docs/native/analytics/journeys-2026-11-09.md`).
 * Fed by the prompt timeline's signals (ids only, never text) and by the stream, stop and approval hooks, it sends:
 * `send_result` (first reply or none, with agent, server kind and how the prompt was made), `chat_turn` (the outcome,
 * times, tool count, reconnect and follow-up), `turn_stall` and `first_reply` (once per install), and
 * `feature_used` for voice and attachments. A refused send is counted where it is refused, with its failure kind.
 *
 * One turn per session. A prompt sent into a running turn is a steer: it changes the turn's follow-up and starts no
 * new one. An idle left over from the previous turn does not end the new one.
 */
public class ChatJourney(
    private val now: () -> Long,
    private val flow: HealthFlow = HealthFlow(),
    private val firstEver: FirstReplyOnce = FirstReplyOnce.Never,
) {
    private class Turn(val startMs: Long, val onPhone: Boolean, val traits: PromptTraits, val input: String) {
        val assistant = HashSet<String>()
        val tools = HashSet<String>()
        var busy = false
        var firstReplyMs: Long? = null
        var lastActivityMs: Long = startMs
        var lastKind: String = ""
        var followUp: String = "none"
        var stopped = false
        var approvalExpired = false
        var openApprovals = 0
        var droppedAtMs: Long? = null
        var recovered = false
        var stallBucket = -1
        var replied = false
    }

    private val turns = MutableStateFlow<Map<String, Turn>>(emptyMap())
    private val failedAt = HashMap<String, Long>()
    private var voice = false

    /** The person dictated into the composer: the next prompt counts as a voice prompt. */
    public fun noteVoice() {
        voice = true
    }

    public fun sent(sessionId: String, onPhone: Boolean, traits: PromptTraits = PromptTraits()) {
        val current = turns.value[sessionId]
        if (current != null && current.busy) {
            current.followUp = "steer"
            current.lastActivityMs = now()
            return
        }
        val dictated = voice
        voice = false
        val input =
            when {
                dictated && traits.hasAttachment -> "voice_attachment"
                dictated -> "voice"
                traits.hasAttachment -> "attachment"
                else -> "text"
            }
        if (dictated) flow.feature(ProductFeature.Voice)
        if (traits.hasAttachment) flow.feature(ProductFeature.FileAttach)
        val turn = Turn(now(), onPhone, traits, input)
        val failed = failedAt.remove(sessionId)
        if (failed != null && now() - failed <= AFTER_FAIL_WINDOW_MS) turn.followUp = "after_fail"
        turns.update { map ->
            val base = if (map.size >= MAX_TRACKED && sessionId !in map) map - map.keys.first() else map
            base + (sessionId to turn)
        }
    }

    public fun accepted(sessionId: String, error: Boolean) {
        if (error) turns.update { it - sessionId }
    }

    public fun message(sessionId: String, messageId: String, assistant: Boolean) {
        if (assistant) turns.value[sessionId]?.assistant?.add(messageId)
    }

    public fun part(sessionId: String, messageId: String, kind: String) {
        val t = turns.value[sessionId] ?: return
        t.lastActivityMs = now()
        t.lastKind = kind
        if (messageId in t.assistant || kind in ASSISTANT_ONLY_KINDS) firstReply(sessionId, t)
    }

    public fun delta(sessionId: String) {
        val t = turns.value[sessionId] ?: return
        t.lastActivityMs = now()
        t.lastKind = "text"
        firstReply(sessionId, t)
    }

    /** A tool call of this turn (once per call; later updates of the same call are not counted again). */
    public fun tool(sessionId: String, partId: String) {
        turns.value[sessionId]?.tools?.add(partId)
    }

    public fun busy(sessionId: String) {
        turns.value[sessionId]?.let {
            it.busy = true
            it.lastActivityMs = now()
        }
    }

    /** The person pressed Stop for this turn. */
    public fun stopped(sessionId: String) {
        turns.value[sessionId]?.stopped = true
    }

    /** The turn ended on the server's word: idle, or an error ([errorKind] a word of [HealthWords.turnErrorKinds]). */
    public fun ended(sessionId: String, errorKind: String? = null) {
        val t = turns.value[sessionId] ?: return
        if (!t.busy) return
        turns.update { it - sessionId }
        // A turn the model refused (rate limit, free-tier limit, sign-in needed, any model error) with no reply is a
        // failed send, not an "ok" one; the person pressing Stop is not a failure.
        val refused = errorKind != null && errorKind != "aborted" && !t.stopped && !t.replied
        if (refused) {
            flow.sendResult("fail", firstReplyMs = null, serverKind = serverKind(t), traits = send(t), failWord = sendFailWord(errorKind))
        } else if (!t.replied) {
            flow.sendResult("ok", firstReplyMs = null, serverKind = serverKind(t), traits = send(t))
        }
        val outcome =
            when {
                t.stopped -> "stopped_by_user"
                errorKind == "aborted" -> "stopped_by_user"
                t.approvalExpired && errorKind != null -> "approval_timeout"
                errorKind != null -> "error"
                else -> "done"
            }
        summarize(sessionId, t, outcome, errorKind.takeIf { outcome == "error" || outcome == "approval_timeout" })
    }

    /** The live stream of the connection (phone or computer) dropped or came back. */
    public fun stream(onPhone: Boolean, connected: Boolean) {
        val at = now()
        for (t in turns.value.values) {
            if (t.onPhone != onPhone || !t.busy) continue
            if (!connected && t.droppedAtMs == null) t.droppedAtMs = at
            if (connected && t.droppedAtMs != null) {
                t.droppedAtMs = null
                t.recovered = true
            }
        }
    }

    /** An approval or question of this session opened (+1) or left (-1). */
    public fun approvalOpen(sessionId: String, delta: Int) {
        turns.value[sessionId]?.let { it.openApprovals = (it.openApprovals + delta).coerceAtLeast(0) }
    }

    /** An approval left without an answer from the person (the turn went on or ended without it). */
    public fun approvalExpired(sessionId: String) {
        turns.value[sessionId]?.approvalExpired = true
    }

    /**
     * Called every half minute or so while a chat is open: reports a turn that went quiet (`turn_stall`, once per
     * range) and a turn whose stream never came back (`chat_turn` with `connection_lost`).
     */
    public fun checkStalls() {
        val at = now()
        for ((id, t) in turns.value) {
            if (!t.busy) continue
            val dropped = t.droppedAtMs
            if (dropped != null && at - dropped >= LOST_AFTER_MS) {
                turns.update { it - id }
                if (!t.replied) flow.sendResult("ok", firstReplyMs = null, serverKind = serverKind(t), traits = send(t))
                failedAt[id] = at
                summarize(id, t, "connection_lost", null, recoveredOverride = "no")
                continue
            }
            val quiet = at - t.lastActivityMs
            val bucket = STALL_STEPS_MS.indexOfLast { quiet >= it }
            if (bucket > t.stallBucket) {
                t.stallBucket = bucket
                val stage =
                    when {
                        t.openApprovals > 0 -> "waiting_approval"
                        t.lastKind == "tool" -> "waiting_tool"
                        t.lastKind.isEmpty() || t.lastKind == "text" || t.lastKind == "reasoning" -> "streaming"
                        else -> "other"
                    }
                flow.turnStall(stage, quiet)
            }
        }
    }

    private fun summarize(sessionId: String, t: Turn, outcome: String, errorKind: String?, recoveredOverride: String? = null) {
        if (outcome == "error" || outcome == "connection_lost") failedAt[sessionId] = now()
        flow.chatTurn(
            ChatTurnSummary(
                outcome = outcome,
                agent = t.traits.agent,
                serverKind = serverKind(t),
                firstReplyMs = t.firstReplyMs,
                totalMs = (now() - t.startMs).coerceAtLeast(0),
                tools = t.tools.size,
                failKind = errorKind,
                recovered = recoveredOverride ?: if (t.recovered) "yes" else if (t.droppedAtMs != null) "no" else "none",
                followUp = t.followUp,
            ),
        )
    }

    private fun firstReply(sessionId: String, t: Turn) {
        if (t.replied) return
        t.replied = true
        val elapsed = (now() - t.startMs).coerceAtLeast(0)
        t.firstReplyMs = elapsed
        flow.sendResult("ok", firstReplyMs = elapsed, serverKind = serverKind(t), traits = send(t))
        if (!firstEver.seen()) {
            firstEver.mark()
            flow.firstReply(serverKind(t), elapsed)
        }
    }

    private fun serverKind(t: Turn): String = if (t.onPhone) "phone" else "computer"

    private fun send(t: Turn): SendTraits = SendTraits(t.traits.agent, t.input)

    public companion object {
        private const val MAX_TRACKED = 16
        private val ASSISTANT_ONLY_KINDS = setOf("step-start", "reasoning", "tool")
        private const val AFTER_FAIL_WINDOW_MS = 120_000L

        /** A dropped stream that has not come back by now ends the turn as `connection_lost`. */
        public const val LOST_AFTER_MS: Long = 120_000L

        /** `turn_stall` is sent when a turn has been quiet for 1, 5, 15 and 60 minutes. */
        private val STALL_STEPS_MS = listOf(60_000L, 300_000L, 900_000L, 3_600_000L)

        /** The `send_result` fail kind for a turn error word. */
        public fun sendFailWord(errorKind: String?): String =
            when (errorKind) {
                "provider_auth" -> "sign_in"
                "rate_limit", "free_limit" -> errorKind
                else -> "model_error"
            }

        /**
         * The word for a message error kind's name (`ContextOverflow` becomes `context_overflow`). [tag] is the server's
         * symbolic error tag, read for logic only (a rate-limit or free-tier tag), never sent.
         */
        public fun errorKindWord(kindName: String?, tag: String?): String {
            val t = tag?.lowercase()?.replace(Regex("[^a-z]"), "").orEmpty()
            return when {
                "ratelimit" in t || "toomanyrequests" in t -> "rate_limit"
                "freeusage" in t || "freetier" in t || "quota" in t || "creditsexhausted" in t -> "free_limit"
                else -> errorKindWord(kindName)
            }
        }

        /** The word for a message error kind's name (`ContextOverflow` becomes `context_overflow`). */
        public fun errorKindWord(kindName: String?): String {
            val word = kindName?.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")?.lowercase()
            return when (word) {
                "provider_auth", "context_overflow", "output_length", "content_filter", "model_not_found", "aborted" -> word
                null -> "server"
                else -> "other"
            }
        }
    }
}

/**
 * One approval or question the person was asked (`approval`): asked, then answered (`allowed`, `always`, `denied`,
 * `declined`) or gone without an answer (`expired`). Fed from the pending list of a connection; ids and words only.
 */
public class ApprovalMeter(private val now: () -> Long, private val flow: HealthFlow = HealthFlow(), private val journey: () -> ChatJourney? = { null }) {
    private class Asked(val sessionId: String, val kind: String, val atMs: Long, var answered: Boolean = false)

    private val open = MutableStateFlow<Map<String, Asked>>(emptyMap())

    public fun asked(id: String, sessionId: String, kind: String) {
        if (id in open.value) return
        open.update { it + (id to Asked(sessionId, kind, now())) }
        journey()?.approvalOpen(sessionId, +1)
    }

    /** The person's answer was taken ([result] one of [HealthWords.approvalResults] except `expired`). */
    public fun answered(id: String, result: String) {
        val a = open.value[id] ?: return
        if (a.answered) return
        a.answered = true
        flow.approval(a.kind, result, now() - a.atMs)
    }

    /** The request left the pending list. Unanswered means it expired. */
    public fun gone(id: String) {
        val a = open.value[id] ?: return
        open.update { it - id }
        journey()?.approvalOpen(a.sessionId, -1)
        if (!a.answered) {
            flow.approval(a.kind, "expired", now() - a.atMs)
            journey()?.approvalExpired(a.sessionId)
        }
    }

    public companion object {
        /** The word for a permission name (`bash`, `edit`, ...). */
        public fun kindOf(permission: String?): String =
            when (permission?.lowercase()) {
                "bash", "shell", "run" -> "run"
                "edit", "write", "patch", "multiedit" -> "edit"
                "read", "glob", "grep", "list" -> "read"
                "webfetch", "websearch", "web" -> "web"
                "question" -> "question"
                else -> "other"
            }
    }
}

/** The process-wide chat journey, for hooks in engines that have no injected one. Inert until the app installs it. */
public object AppJourneys {
    @kotlin.concurrent.Volatile private var chat: ChatJourney? = null
    @kotlin.concurrent.Volatile private var approvals: ApprovalMeter? = null
    @kotlin.concurrent.Volatile private var setup: SetupJourney? = null

    public val setupOrNull: SetupJourney?
        get() = setup

    public val chatOrNull: ChatJourney?
        get() = chat

    public val approvalsOrNull: ApprovalMeter?
        get() = approvals

    public fun install(journey: ChatJourney, meter: ApprovalMeter, setupJourney: SetupJourney? = null) {
        chat = journey
        approvals = meter
        setup = setupJourney
    }

    public fun reset() {
        chat = null
        approvals = null
        setup = null
    }
}
