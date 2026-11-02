package io.github.eslamasabry.oc.android.notifications

import io.github.eslamasabry.oc.core.common.ids.ConversationRef

/** Status situations (plan 11 §10.3). Wire names unchanged from `NotificationStatusKind.wireValue`. */
public enum class StatusKind(public val wire: String) {
    NeedsYou("needs_you"),
    Working("working"),
    SeveralWorking("several_working"),
    Waiting("waiting"),
    PausedAndroid("paused_android"),
    PausedHot("paused_hot"),
    PausedIdle("paused_idle"),
    Reconnecting("reconnecting"),
    Idle("idle"),
    Setup("setup"),
    ;

    public companion object {
        public fun fromWire(value: String?): StatusKind? = entries.firstOrNull { it.wire == value }
    }
}

/** Status row buttons. Wire names unchanged (`open`, `stop_reply`, `start`, `new_chat`, `stop_server`). */
public enum class StatusAction(public val wire: String) {
    Open("open"),
    StopReply("stop_reply"),
    Start("start"),
    NewChat("new_chat"),
    StopServer("stop_server"),
    ;

    public companion object {
        public fun fromWire(value: String?): StatusAction? = entries.firstOrNull { it.wire == value }
    }
}

/** A single turn, so a later reply in the same conversation has a different dismissal identity. */
public data class ReplyRun(val conversation: ConversationRef, val turnId: String)

/**
 * What the one status row (4747) shows, already worded by the status model (`:engine:attention`). Port of the Flutter
 * `StatusContent`: a malformed content is refused ([of] returns null) so a bad update never replaces a good one.
 */
public data class StatusContent(
    val kind: StatusKind,
    val title: String,
    val text: String = "",
    val lines: List<String> = emptyList(),
    val subText: String = "",
    /** 0–100 when a real fraction is known; null = no bar, never an endless spinner (B-ANDR-042). */
    val progress: Int? = null,
    val actions: List<StatusAction> = listOf(StatusAction.Open),
    /** The chat **Stop the reply** stops, and the run the row's dismissal ends watching. */
    val conversation: ConversationRef? = null,
    /** Stable identity of this turn, distinct from the conversation. */
    val turnId: String = "",
    /** The lock screen's version: counts only (B-ANDR-047). */
    val publicTitle: String = "OpenCode",
    /** How many chats work / need you; drives the Live Update chip plural. */
    val count: Int = 1,
) {
    public val run: ReplyRun?
        get() = conversation?.takeIf {
            it.profileId.raw.isNotBlank() && it.sessionId.raw.isNotBlank() && turnId.isNotBlank()
        }?.let { ReplyRun(it, turnId) }

    public companion object {
        public const val MAX_LINES: Int = 4
        public const val MAX_TEXT: Int = 240
        public const val MAX_ACTIONS: Int = 3
        public const val MAX_PROGRESS: Int = 100

        /** Validates and caps (plan 11 §10.3 "Validation"). Null when the kind or title is missing. */
        public fun of(
            kind: StatusKind?,
            title: String?,
            text: String? = null,
            lines: List<String?> = emptyList(),
            subText: String? = null,
            progress: Int? = null,
            actions: List<StatusAction> = emptyList(),
            conversation: ConversationRef? = null,
            turnId: String = "",
            publicTitle: String? = null,
            count: Int = 1,
        ): StatusContent? {
            val cleanTitle = clip(title)
            if (kind == null || cleanTitle.isNullOrEmpty()) return null
            return StatusContent(
                kind = kind,
                title = cleanTitle,
                text = clip(text).orEmpty(),
                lines = lines.mapNotNull { clip(it)?.takeIf { line -> line.isNotEmpty() } }.take(MAX_LINES),
                subText = clip(subText).orEmpty(),
                progress = progress?.coerceIn(0, MAX_PROGRESS),
                actions = actions.distinct().take(MAX_ACTIONS).ifEmpty { listOf(StatusAction.Open) },
                conversation = conversation,
                turnId = turnId,
                publicTitle = clip(publicTitle)?.takeIf { it.isNotEmpty() } ?: "OpenCode",
                count = count.coerceAtLeast(1),
            )
        }

        private fun clip(value: String?): String? = value?.trim()?.take(MAX_TEXT)
    }
}

/**
 * The surface throttle (plan 11 §10.3, map T12): one post per 2,000 ms, only on changed content, a trailing post of
 * the newest. Android drops more than about five updates a second. Port of the Flutter `StatusThrottle`.
 */
public class StatusThrottle(private val minGapMs: Long = 2_000L) {
    public sealed interface Step {
        public data object Skip : Step

        public data class Post(val content: StatusContent) : Step

        public data class Wait(val delayMs: Long) : Step
    }

    private var shown: StatusContent? = null
    private var shownAt: Long = Long.MIN_VALUE
    private var pending: StatusContent? = null

    @Synchronized
    public fun offer(content: StatusContent, nowMs: Long): Step {
        if (content == shown) {
            pending = null
            return Step.Skip
        }
        val elapsed = if (shownAt == Long.MIN_VALUE) Long.MAX_VALUE else nowMs - shownAt
        if (elapsed >= minGapMs) {
            record(content, nowMs)
            return Step.Post(content)
        }
        pending = content
        return Step.Wait(minGapMs - elapsed)
    }

    /** The trailing post, when its time has come; null when nothing is pending or it is too early. */
    @Synchronized
    public fun due(nowMs: Long): StatusContent? {
        val next = pending ?: return null
        if (shownAt != Long.MIN_VALUE && nowMs - shownAt < minGapMs) return null
        pending = null
        if (next == shown) return null
        record(next, nowMs)
        return next
    }

    @Synchronized
    public fun reset() {
        shown = null
        pending = null
        shownAt = Long.MIN_VALUE
    }

    private fun record(content: StatusContent, nowMs: Long) {
        shown = content
        shownAt = nowMs
    }
}

/** A Live Update chip request: which plural, with which count. */
public data class LiveChip(val pluralRes: Int, val count: Int)

/**
 * Live Updates (promoted ongoing) policy (plan 11 §10.5). Only rows about runs the person started are promoted:
 * `working`/`several_working`, and `needs_you` inside a running turn. Never `idle`, `paused_*`, `setup`, `waiting`,
 * `reconnecting` (parity at the switch), and never alerts. A row the person dismissed is not promoted again (S11-9).
 */
public object LiveUpdatePolicy {
    public fun chipFor(content: StatusContent, dismissedRun: Boolean, turnRunning: Boolean = true): LiveChip? {
        if (dismissedRun) return null
        return when (content.kind) {
            StatusKind.Working -> LiveChip(R.plurals.notify_chip_running, 1)
            StatusKind.SeveralWorking -> LiveChip(R.plurals.notify_chip_running, content.count)
            StatusKind.NeedsYou -> if (turnRunning) LiveChip(R.plurals.notify_chip_pending, content.count) else null
            else -> null
        }
    }
}
