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
public class ChatJourney(
    private val now: () -> Long,
    private val flow: HealthFlow = HealthFlow(),
    private val firstEver: FirstReplyOnce = FirstReplyOnce.Never,
    /** A turn that showed a reply has just ended without an error: the reply is complete (sent whatever the switches say). */
    private val onReplyDone: () -> Unit = {},
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
        val input =
            when {
                dictated && traits.hasAttachment -> "voice_attachment"
                dictated -> "voice"
                traits.hasAttachment -> "attachment"
                else -> "text"
            }
        if (dictated) flow.feature(ProductFeature.Voice)
        if (traits.hasAttachment) flow.feature(ProductFeature.FileAttach)
        val current = turns.value[sessionId]
        val steer = current != null && current.busy
        pending[sessionId] = Pending(if (onPhone) "phone" else "computer", SendTraits(traits.agent, input), steer)
        if (current != null && steer) {
            current.followUp = "steer"
            current.lastActivityMs = now()
            return
        }
        val turn = Turn(now(), onPhone, traits, connection)
        val failed = failedAt.remove(sessionId)
        if (failed != null && now() - failed <= AFTER_FAIL_WINDOW_MS) turn.followUp = "after_fail"
        turns.update { map ->
            val base = if (map.size >= MAX_TRACKED && sessionId !in map) map - map.keys.first() else map
            base + (sessionId to turn)
        }
    }

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
        // The person's own prompt is a text or file part too: those count only once the message is the assistant's.
        if ((kind != "text" && kind != "file") || messageId in t.assistant) activity(t)
    }

    /** Any streamed piece arrived (text or reasoning): activity only. */
    public fun delta(sessionId: String) {
        val t = turns.value[sessionId] ?: return
        t.lastActivityMs = now()
        t.lastKind = "text"
        activity(t)
    }

    /**
     * Non-empty reply text the person can see: streamed text ([streamed]), or a text part of [messageId], which counts
     * only once that message is known to be the assistant's (the person's own prompt is a text part too).
     */
    public fun text(sessionId: String, messageId: String, streamed: Boolean) {
        val t = turns.value[sessionId] ?: return
        t.lastActivityMs = now()
        t.lastKind = "text"
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

    public fun busy(sessionId: String) {
        turns.value[sessionId]?.let {
            it.busy = true
            it.lastActivityMs = now()
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
        if (connection != null) {
            if (!connected && dropReason != null && connection !in drops) {
                drops[connection] = Drop(if (onPhone) "phone" else "computer", dropReason, at)
            }
            if (connected) drops.remove(connection)?.let { flow.connDrop(it.serverKind, it.reason, recovered = true) }
        }
        for (t in turns.value.values) {
            val mine = if (connection != null && t.connection != null) t.connection == connection else t.onPhone == onPhone
            if (!mine) continue
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
            if (dropped != null && at - dropped >= LOST_AFTER_MS) {
                turns.update { it - id }
                failedAt[id] = at
                summarize(id, t, "connection_lost", null, recoveredOverride = "no")
                continue
            }
            if (!t.busy) {
                if (at - t.lastActivityMs >= LOST_AFTER_MS) {
                    turns.update { it - id }
                    summarize(id, t, if (t.replied) "done" else "no_output", null)
                }
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
