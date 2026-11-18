package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.analytics.product.ProductFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Whether this install ever saw a reply (`first_reply` is sent once per install). */
public interface FirstReplyOnce {
    /** A reply was seen on this install, whether or not usage was on at the time. */
    public fun seen(): Boolean

    public fun mark()

    /**
     * `first_reply` was actually sent for this install. An install that agreed after its first reply has [seen] but has
     * not sent: its first reply after agreeing is sent once, marked `after_consent`. Defaults to [seen] for a store that
     * keeps one flag.
     */
    public fun sent(): Boolean = seen()

    public fun markSent(): Unit = Unit

    public object Never : FirstReplyOnce {
        override fun seen(): Boolean = true

        override fun mark(): Unit = Unit
    }
}

/** What the send path knows about a prompt. Words only: [agent] is reduced to a built-in name before it is sent. */
public data class PromptTraits(val agent: String? = null, val hasAttachment: Boolean = false)

/**
 * The chat journey, from "send" to "the reply is done" (B-190 and the journeys doc `docs/native/analytics/journeys-2026-11-09.md`).
 * Fed by the prompt timeline's signals (ids only, never text) and by the stream, stop and approval hooks. Each stage
 * is its own number (analytics-truth, 2026-11-16):
 * - send: `send_result` once per send when the server took it or refused it ([settled]), with agent, server kind and
 *   how the prompt was made; nothing about a reply decides it;
 * - reply: `first_reply_range` is Send to the first output the person can SEE, a non-empty reply text or a tool row
 *   ([text], [tool]); reasoning, step markers and empty text never count; `none` when the turn ended without one;
 * - turn: `chat_turn` `done` only with visible output, `no_output` when it ended without any; a send that never saw
 *   the server start working ends by the same two-minute rule as a lost stream instead of waiting to be evicted.
 * Also `turn_stall`, `first_reply` (once per install) and `feature_used` for voice and attachments.
 *
 * One turn per session. A prompt sent into a running turn is a steer: it changes the turn's follow-up and starts no
 * new one. An idle left over from the previous turn does not end the new one.
 */
// One state machine for the whole chat turn (send, stream, stall, end): its steps share the turn table and its lanes.
@Suppress("TooManyFunctions")
public class ChatJourney(
    private val now: () -> Long,
    private val flow: HealthFlow = HealthFlow(),
    private val firstEver: FirstReplyOnce = FirstReplyOnce.Never,
    /** A turn that showed a reply has just ended without an error: the reply is complete (sent whatever the switches say). */
    private val onReplyDone: () -> Unit = {},
    /** The server says no session of this app is busy any more (after a turn ended, or between two of its steps). */
    private val onQuiet: () -> Unit = {},
) {
    private class Turn(val startMs: Long, val onPhone: Boolean, val traits: PromptTraits, val connection: String?) {
        val assistant = HashSet<String>()
        val tools = HashSet<String>()
        var busy = false
        var firstReplyMs: Long? = null
        var activityMs: Long? = null
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
        var liveness: Boolean? = null
        var deadStream = false
    }

    /** What the last send of a session carried, until the server's answer to it is known ([settled]). */
    private class Pending(val serverKind: String, val traits: SendTraits, val steer: Boolean)

    private val turns = MutableStateFlow<Map<String, Turn>>(emptyMap())
    private val failedAt = HashMap<String, Long>()
    private val pending = HashMap<String, Pending>()
    private var voice = false

    /** A live stream that dropped, per connection (a local id, never sent), until it returns or [LOST_AFTER_MS] passes. */
    private class Drop(val serverKind: String, val reason: String, val atMs: Long)

    private val drops = HashMap<String, Drop>()

    /** The person dictated into the composer: the next prompt counts as a voice prompt. */
    public fun noteVoice() {
        voice = true
    }

    /** [connection] is the local id of the connection it went through: kept in memory to attribute drops, never sent. */
    public fun sent(sessionId: String, onPhone: Boolean, traits: PromptTraits = PromptTraits(), connection: String? = null) {
        val dictated = voice
        voice = false
        val input = inputWord(dictated, traits.hasAttachment)
        if (dictated) flow.feature(ProductFeature.Voice)
        if (traits.hasAttachment) flow.feature(ProductFeature.FileAttach)
        val current = turns.value[sessionId]
        val steer = current != null && current.busy
        pending[sessionId] = Pending(if (onPhone) "phone" else "computer", SendTraits(traits.agent, input), steer)
        if (current != null && steer) {
            steered(current)
            return
        }
        val turn = Turn(now(), onPhone, traits, connection)
        if (followsAFailure(sessionId)) turn.followUp = "after_fail"
        turns.update { map -> map + (sessionId to turn) - evictedFor(map, sessionId) }
    }

    private fun inputWord(dictated: Boolean, attached: Boolean): String =
        when {
            dictated && attached -> "voice_attachment"
            dictated -> "voice"
            attached -> "attachment"
            else -> "text"
        }

    /** A send into a running turn steers it: the turn goes on and counts as a follow-up by steering. */
    private fun steered(current: Turn) {
        current.followUp = "steer"
        current.lastActivityMs = now()
        current.liveness = null
        current.deadStream = false
    }

    private fun followsAFailure(sessionId: String): Boolean {
        val failed = failedAt.remove(sessionId) ?: return false
        return now() - failed <= AFTER_FAIL_WINDOW_MS
    }

    /** The oldest tracked turn leaves when a new session would pass the cap. */
    private fun evictedFor(map: Map<String, Turn>, sessionId: String): Set<String> =
        if (map.size >= MAX_TRACKED && sessionId !in map) setOf(map.keys.first()) else emptySet()

    /** The send link's own answer; a send it failed starts no turn (a failed steer leaves the running turn alone). */
    public fun accepted(sessionId: String, error: Boolean) {
        if (error && pending[sessionId]?.steer != true) turns.update { it - sessionId }
    }

    /**
     * Stage 1, `send_result`: the server took the send ([accepted]) or did not ([failure] says why, as a word). Called
     * once per send by the delivery engine where it decides; the traits are the ones [sent] saw for this session.
     */
    public fun settled(sessionId: String, accepted: Boolean, failure: Failure? = null) {
        val p = pending.remove(sessionId)
        flow.sendResult(if (accepted) "ok" else "fail", failure.takeUnless { accepted }, p?.serverKind, p?.traits)
    }

    public fun message(sessionId: String, messageId: String, assistant: Boolean) {
        if (assistant) turns.value[sessionId]?.assistant?.add(messageId)
    }

    /** Any part of the turn arrived: activity, never a reply by itself (a reasoning or step part shows nothing). */
    public fun part(sessionId: String, messageId: String, kind: String) {
        val t = turns.value[sessionId] ?: return
        t.lastActivityMs = now()
        t.lastKind = kind
        t.liveness = null
        t.deadStream = false
        // The person's own prompt is a text or file part too: those count only once the message is the assistant's.
        if ((kind != "text" && kind != "file") || messageId in t.assistant) activity(t)
    }

    /** The field's closed kind, supplied by delivery (text, reasoning or tool). */
    public fun delta(sessionId: String, kind: String) {
        val t = turns.value[sessionId] ?: return
        t.lastActivityMs = now()
        t.lastKind = when (kind) { "reasoning" -> "reasoning"; "tool" -> "tool"; else -> "text" }
        t.liveness = null
        t.deadStream = false
        activity(t)
    }

    /** A provider retry is progress, not a minute of silent text streaming. */
    public fun retrying(sessionId: String) {
        turns.value[sessionId]?.let { t ->
            t.busy = true
            t.lastActivityMs = now()
            t.lastKind = "retry"
            t.liveness = null
            t.deadStream = false
        }
    }

    /** Evidence from the bounded recovery lane; it never resets the quiet clock. */
    public fun runChecked(sessionId: String, busy: Boolean?) {
        turns.value[sessionId]?.let { t -> t.liveness = busy; t.deadStream = busy != true }
    }

    /** Record a qualifying dead-stream stall before recovered history resets activity and the turn ends. */
    public fun recovered(sessionId: String) {
        turns.value[sessionId]?.let { t -> t.deadStream = true; t.liveness = false }
        checkStalls()
    }

    /** Any streamed piece arrived (text or reasoning): activity only. */
    public fun delta(sessionId: String) {
        delta(sessionId, "text")
    }

    /**
     * Non-empty reply text the person can see: streamed text ([streamed]), or a text part of [messageId], which counts
     * only once that message is known to be the assistant's (the person's own prompt is a text part too).
     */
    public fun text(sessionId: String, messageId: String, streamed: Boolean) {
        val t = turns.value[sessionId] ?: return
        t.lastActivityMs = now()
        t.lastKind = "text"
        t.liveness = null
        t.deadStream = false
        if (streamed || messageId in t.assistant) {
            activity(t)
            firstReply(t)
        }
    }

    /** A tool call of this turn (once per call; later updates of the same call are not counted again). Its row shows. */
    public fun tool(sessionId: String, partId: String) {
        val t = turns.value[sessionId] ?: return
        t.tools.add(partId)
        activity(t)
        firstReply(t)
    }

    /** Sessions the server last called busy, with when. Kept for the one-time consent question only; never sent. */
    private val serverBusy = MutableStateFlow<Map<String, Long>>(emptyMap())

    /**
     * Some session is busy on the server's word right now. A turn that ended on an idle between two of its steps is
     * gone from [turns], so [anyBusy] cannot see the next step; this can. An entry older than [BUSY_STALE_MS] is a
     * missed idle and no longer counts.
     */
    public fun anyServerBusy(): Boolean {
        val at = now()
        return serverBusy.value.values.any { at - it < BUSY_STALE_MS }
    }

    public fun busy(sessionId: String) {
        serverBusy.update { it + (sessionId to now()) }
        turns.value[sessionId]?.let {
            it.busy = true
            it.lastActivityMs = now()
            it.liveness = null
            it.deadStream = false
        }
    }

    /** Some turn of this app is running right now (the chat's `interaction` reads it; nothing is kept). */
    public fun anyBusy(): Boolean = turns.value.values.any { it.busy }

    /** The person pressed Stop for this turn. */
    public fun stopped(sessionId: String) {
        turns.value[sessionId]?.stopped = true
    }

    /** The turn ended on the server's word: idle, or an error ([errorKind] a word of [HealthWords.turnErrorKinds]). */
    public fun ended(sessionId: String, errorKind: String? = null) {
        serverBusy.update { it - sessionId }
        endTurn(sessionId, errorKind)
        if (!anyServerBusy()) onQuiet()
    }

    private fun endTurn(sessionId: String, errorKind: String?) {
        val t = turns.value[sessionId] ?: return
        if (!t.busy) return
        turns.update { it - sessionId }
        // A model refusal (rate limit, free-tier limit, sign-in) is the turn's error with its kind: the send itself was
        // taken, and `send_result` said so when it was.
        val outcome =
            when {
                t.stopped -> "stopped_by_user"
                errorKind == "aborted" -> "stopped_by_user"
                t.approvalExpired && errorKind != null -> "approval_timeout"
                errorKind != null -> "error"
                t.replied -> "done"
                else -> "no_output"
            }
        summarize(sessionId, t, outcome, errorKind.takeIf { outcome == "error" || outcome == "approval_timeout" })
        if (t.replied && errorKind == null) onReplyDone()
    }

    /** The live stream of a connection of this kind dropped or came back (no connection named: every one of the kind). */
    public fun stream(onPhone: Boolean, connected: Boolean) {
        stream(null, onPhone, connected, null)
    }

    /**
     * The live stream of [connection] dropped or came back: only that connection's turns are affected. [dropReason] (a
     * `conn_drop.reason` word) makes a drop count as `conn_drop`, sent once: `recovered` yes when the stream returns
     * within [LOST_AFTER_MS], no when it has not by then ([checkStalls]).
     */
    public fun stream(connection: String?, onPhone: Boolean, connected: Boolean, dropReason: String? = null) {
        val at = now()
        if (connection != null) noteConnection(connection, onPhone, connected, dropReason, at)
        for (t in turns.value.values) {
            if (!ridesOn(t, connection, onPhone)) continue
            if (!connected && t.droppedAtMs == null) t.droppedAtMs = at
            if (connected && t.droppedAtMs != null) {
                t.liveness = null
                t.deadStream = false
                t.droppedAtMs = null
                t.recovered = true
            }
        }
    }

    private fun noteConnection(
        connection: String,
        onPhone: Boolean,
        connected: Boolean,
        dropReason: String?,
        at: Long,
    ) {
        if (!connected && dropReason != null && connection !in drops) {
            drops[connection] = Drop(if (onPhone) "phone" else "computer", dropReason, at)
        }
        if (connected) drops.remove(connection)?.let { flow.connDrop(it.serverKind, it.reason, recovered = true) }
    }

    /** A turn rides on the connection it went through; without one on record, on the same side (phone or computer). */
    private fun ridesOn(t: Turn, connection: String?, onPhone: Boolean): Boolean =
        if (connection != null && t.connection != null) t.connection == connection else t.onPhone == onPhone

    /** An approval or question of this session opened (+1) or left (-1). */
    public fun approvalOpen(sessionId: String, delta: Int) {
        turns.value[sessionId]?.let { it.openApprovals = (it.openApprovals + delta).coerceAtLeast(0) }
    }

    /** Whether the person pressed Stop for [sessionId]'s running turn. */
    public fun runStopped(sessionId: String): Boolean = turns.value[sessionId]?.stopped == true

    /** An approval left without an answer from the person (the turn went on or ended without it). */
    public fun approvalExpired(sessionId: String) {
        turns.value[sessionId]?.approvalExpired = true
    }

    /**
     * Called every half minute or so while a chat is open: reports a turn that went quiet (`turn_stall`, once per
     * range), a turn whose stream never came back (`chat_turn` with `connection_lost`), and a send the server never
     * started working on, after the same [LOST_AFTER_MS] of quiet (`done` with visible output, else `no_output`).
     */
    public fun checkStalls() {
        val at = now()
        for ((connection, drop) in drops.toList()) {
            if (at - drop.atMs < LOST_AFTER_MS) continue
            drops.remove(connection)
            flow.connDrop(drop.serverKind, drop.reason, recovered = false)
        }
        for ((id, t) in turns.value) {
            val dropped = t.droppedAtMs
            when {
                dropped != null && at - dropped >= LOST_AFTER_MS -> {
                    turns.update { it - id }
                    failedAt[id] = at
                    summarize(id, t, "connection_lost", null, recoveredOverride = "no")
                }
                !t.busy -> endIfIdle(id, t, at)
                else -> reportStall(t, at)
            }
        }
    }

    private fun endIfIdle(id: String, t: Turn, at: Long) {
        if (at - t.lastActivityMs < LOST_AFTER_MS) return
        turns.update { it - id }
        summarize(id, t, if (t.replied) "done" else "no_output", null)
    }

    private fun reportStall(t: Turn, at: Long) {
        val quiet = at - t.lastActivityMs
        val bucket = STALL_STEPS_MS.indexOfLast { quiet >= it }
        if (bucket <= t.stallBucket) return
        t.stallBucket = bucket
        flow.turnStall(stallStage(t), quiet, stallCause(t))
    }

    private fun isStreamingKind(t: Turn): Boolean =
        t.lastKind.isEmpty() || t.lastKind == "text" || t.lastKind == "reasoning"

    private fun stallStage(t: Turn): String =
        when {
            t.openApprovals > 0 -> "waiting_approval"
            t.lastKind == "tool" -> "waiting_tool"
            isStreamingKind(t) -> "streaming"
            else -> "other"
        }

    private fun stallCause(t: Turn): String =
        when {
            t.droppedAtMs != null || t.deadStream -> "stream_dead_refetched"
            t.openApprovals > 0 -> "unknown"
            t.lastKind == "retry" -> "retrying"
            t.lastKind == "tool" -> "tool_running"
            isStreamingKind(t) -> if (t.liveness == true) "thinking_silent" else "stream_silent"
            else -> "unknown"
        }

    private fun summarize(sessionId: String, t: Turn, outcome: String, errorKind: String?, recoveredOverride: String? = null) {
        if (outcome == "error" || outcome == "connection_lost") failedAt[sessionId] = now()
        flow.chatTurn(
            ChatTurnSummary(
                outcome = outcome,
                agent = t.traits.agent,
                serverKind = serverKind(t),
                firstReplyMs = t.firstReplyMs,
                activityMs = t.activityMs,
                totalMs = (now() - t.startMs).coerceAtLeast(0),
                tools = t.tools.size,
                failKind = errorKind,
                recovered = recoveredOverride ?: if (t.recovered) "yes" else if (t.droppedAtMs != null) "no" else "none",
                followUp = t.followUp,
            ),
        )
    }

    /** The agent's first sign of life in this turn, of any kind: its own number (`chat_turn.activity_range`), set once. */
    private fun activity(t: Turn) {
        if (t.activityMs == null) t.activityMs = (now() - t.startMs).coerceAtLeast(0)
    }

    /** Stage 2: the first output the person can see in this turn. */
    private fun firstReply(t: Turn) {
        if (t.replied) return
        t.replied = true
        val elapsed = (now() - t.startMs).coerceAtLeast(0)
        t.firstReplyMs = elapsed
        flow.replyShown(serverKind(t), elapsed)
        // `first_reply` is this install's first MEASURED reply. Nothing is sent for a reply seen before the person
        // agreed; the first one after agreeing is sent, saying it was not the very first (`after_consent`).
        val alreadySent = firstEver.sent()
        val seenBefore = firstEver.seen()
        if (!seenBefore) firstEver.mark()
        if (!alreadySent && flow.firstReply(serverKind(t), elapsed, afterConsent = seenBefore)) firstEver.markSent()
    }

    private fun serverKind(t: Turn): String = if (t.onPhone) "phone" else "computer"

    public companion object {
        private const val BUSY_STALE_MS = 900_000L
        private const val MAX_TRACKED = 16
        private const val AFTER_FAIL_WINDOW_MS = 120_000L

        /** A dropped stream that has not come back by now ends the turn as `connection_lost`. */
        public const val LOST_AFTER_MS: Long = 120_000L

        /** `turn_stall` is sent when a turn has been quiet for 1, 5, 15 and 60 minutes. */
        private val STALL_STEPS_MS = listOf(60_000L, 300_000L, 900_000L, 3_600_000L)

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
                "provider_auth", "rate_limit", "free_limit", "context_overflow", "output_length", "content_filter", "model_not_found",
                "provider_connection", "aborted" -> word
                null -> "server"
                else -> "other"
            }
        }
    }
}

/**
 * One approval or question the person was asked (`approval`, stage 4). Fed from the pending list of a connection; ids
 * and words only. An answer counts only once the server confirmed it ([confirmed], then [answered]): a held answer that
 * was undone, or one still on its way, sends nothing. `auto` is a saved Auto or "Approve everything" answer, `stopped`
 * a request that left because its run was stopped, `expired` one that left with no answer while the run went on.
 */
public class ApprovalMeter(private val now: () -> Long, private val flow: HealthFlow = HealthFlow(), private val journey: () -> ChatJourney? = { null }) {
    private class Asked(val sessionId: String, val kind: String, val atMs: Long) {
        var reported = false
        var confirmedAtMs: Long? = null
    }

    private val open = MutableStateFlow<Map<String, Asked>>(emptyMap())

    /** Where an answer was given when it was not in the chat (`home`, `notification`): by request id, in memory only. */
    private val surfaces = HashMap<String, String>()

    /**
     * The answer to [id] is about to be sent from [surface] (one of [HealthWords.surfaces] other than `chat`, which is the
     * default). Call it before the send; [surfaceUnused] takes it back when the send did not go.
     */
    public fun surface(id: String, surface: String) {
        if (surface in HealthWords.surfaces) surfaces[id] = surface
    }

    public fun surfaceUnused(id: String) {
        surfaces.remove(id)
    }

    public fun asked(id: String, sessionId: String, kind: String) {
        if (id in open.value) return
        open.update { it + (id to Asked(sessionId, kind, now())) }
        journey()?.approvalOpen(sessionId, +1)
    }

    /** The server confirmed an answer to [id] (whose, [answered] says): the wait ends here. */
    public fun confirmed(id: String) {
        val a = open.value[id] ?: return
        if (a.confirmedAtMs == null) a.confirmedAtMs = now()
    }

    /**
     * The confirmed answer's word: one of [HealthWords.approvalResults] other than `expired` and `stopped` (`auto` for a
     * saved mode's answer). Sent once per request; never before the server confirmed it.
     */
    public fun answered(id: String, result: String) {
        val a = open.value[id] ?: return
        if (a.reported) return
        a.reported = true
        flow.approval(a.kind, result, (a.confirmedAtMs ?: now()) - a.atMs, surfaceFor(id, result))
    }

    /**
     * The person's answer was taken by the server through a surface that does not go through the chat's request list (a
     * widget). Counted at once, as that surface; a later disappearance of the request adds nothing.
     */
    public fun answeredOn(id: String, result: String, surface: String) {
        val a = open.value[id] ?: return
        if (a.reported) return
        a.reported = true
        flow.approval(a.kind, result, now() - a.atMs, surface.takeIf { it in HealthWords.surfaces })
    }

    /** `surface` is a person's answer's own word: not for a saved rule's `auto`, nor for a request that just left. */
    private fun surfaceFor(id: String, result: String): String? {
        val hinted = surfaces.remove(id)
        return if (result in HealthWords.personAnswers) hinted ?: "chat" else null
    }

    /** The run that asked was stopped (Stop here or anywhere, or the server ended it): nothing can answer it now. */
    public fun stopped(id: String) {
        val a = open.value[id] ?: return
        if (a.reported) return
        a.reported = true
        flow.approval(a.kind, "stopped", now() - a.atMs)
    }

    /** The request left the pending list. Unreported, it left because its run stopped, or else it expired. */
    public fun gone(id: String) {
        val a = open.value[id] ?: return
        open.update { it - id }
        surfaces.remove(id)
        journey()?.approvalOpen(a.sessionId, -1)
        if (a.reported) return
        a.reported = true
        if (journey()?.runStopped(a.sessionId) == true) {
            flow.approval(a.kind, "stopped", now() - a.atMs)
        } else {
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

    /**
     * Stage 1 from the delivery engine, where a send's fate is decided: [accepted] the server took it (a lost or foreign
     * receipt is not taken). Counted by the installed journey (with the traits it saw), else as a bare result.
     */
    public fun sendSettled(sessionId: String, accepted: Boolean, failure: Failure?) {
        val journey = chat
        if (journey != null) journey.settled(sessionId, accepted, failure)
        else HealthFlow().sendResult(if (accepted) "ok" else "fail", failure.takeUnless { accepted })
    }

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
