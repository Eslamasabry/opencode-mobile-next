package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.attention.notify.NotificationText

/**
 * The edge dock outside the app: the one ongoing row (4747) as a pure function of [DockUi]. The dock is the single
 * source of "which conversation is in which state"; nothing here knows Android. The row shows no command, no reply
 * text and no request body: only a masked, cut name, a plain state word, counts and (for one working conversation) its
 * task progress. Allow / Deny stay on the request alert.
 */
public interface DockOutsideWords {
    public fun untitled(): String

    /** Stands in for a sub-agent that has no name. */
    public fun helperGeneric(): String

    /** "{name} needs you". */
    public fun needsYouNamed(name: String): String

    /** "2 need you" (plural by [count]). */
    public fun needsYouCount(count: Int): String
    public fun waitingForAnswer(): String

    /** "2 others working". */
    public fun othersWorking(count: Int): String

    /** "Its helper {child} is asking". */
    public fun helperAsking(child: String): String

    /** "Working on {name}". */
    public fun workingOn(name: String): String

    /** "3 conversations working". */
    public fun conversationsWorking(count: Int): String

    /** "3 of 7 tasks". */
    public fun tasks(done: Int, total: Int): String
    public fun stateWorking(): String
    public fun stateThinking(): String
    public fun stateNeedsYou(): String
    public fun stateDone(): String
    public fun stateFailed(): String

    /** "2 helpers". */
    public fun helpers(count: Int): String

    /** "+3 more". */
    public fun more(count: Int): String

    /** "3 working" (lock screen, count only). */
    public fun lockWorking(count: Int): String

    /** "on {server}". */
    public fun onServer(server: String): String
    public fun openNamed(name: String): String
    public fun stopNamed(name: String): String
}

/** What the dock does not know and the app does. All keyed by conversation; names inside are masked again here. */
public data class DockOutsideFacts(
    /** A live connection is dropped: the existing reconnecting row speaks until a request needs the person. */
    val reconnecting: Boolean = false,
    /** Android ended the background connection: the existing paused row speaks until a request needs the person. */
    val androidLimit: Boolean = false,
    /** The plain sentence of the tool a conversation runs now (from `LiveStatusComposer`), when it has no task list. */
    val toolLines: Map<ConversationRef, String> = emptyMap(),
    /** Last activity (epoch ms) per conversation, to pick the newest working one for Open. */
    val recency: Map<ConversationRef, Long> = emptyMap(),
)

public enum class OutsideKind { NeedsYou, Working, SeveralWorking }

/** A button of the row: the conversation it acts on and its label, which already names it. */
public data class OutsideAction(val ref: ConversationRef, val label: String)

/** The row, worded. Mapped to the notification content by the Android wiring. */
public data class OutsideRow(
    val kind: OutsideKind,
    val title: String,
    val text: String,
    /** At most [DockOutside.MAX_LINES]; empty when the title and text already say everything (one ring). */
    val lines: List<String>,
    /** The lock screen's version: fixed words and counts only, never a name. */
    val publicTitle: String,
    /** How many need you (kind NeedsYou) or work (otherwise): drives the chip. */
    val count: Int,
    val needsCount: Int,
    val workingCount: Int,
    /** A real fraction 0..100, only when exactly one conversation is live and it keeps a task list. */
    val progress: Int?,
    val subText: String,
    val open: OutsideAction,
    /** Only when exactly one conversation is working and it does not need the person. */
    val stop: OutsideAction?,
    /**
     * The sum of every ring's and sub-agent's `pulse`. It only goes up when a request or failure is new, so a row the
     * person swiped away comes back only when this exceeds the number they dismissed at.
     */
    val pulses: Int,
)

public object DockOutside {
    public const val MAX_LINES: Int = 4
    public const val MAX_NAME: Int = 24
    private const val SEP: String = " · "

    private fun DockState.isWorking(): Boolean =
        this is DockState.WorkingWithTasks || this == DockState.Working || this == DockState.Thinking

    private fun DockState.isLive(): Boolean = isWorking() || this == DockState.NeedsYou

    /** Null when the row should not speak: nothing is live, or the app's own pause/reconnecting row is more true. */
    public fun build(ui: DockUi, facts: DockOutsideFacts, words: DockOutsideWords): OutsideRow? {
        val entries = ui.entries
        val needs = entries.filter { it.state == DockState.NeedsYou }
        val working = entries.filter { it.state.isWorking() }
        if (needs.isEmpty() && working.isEmpty()) return null
        if (needs.isEmpty() && (facts.androidLimit || facts.reconnecting)) return null

        fun name(raw: String?): String =
            raw?.let { NotificationText.redact(it, MAX_NAME) }?.takeIf { it.isNotBlank() } ?: words.untitled()

        fun entryName(e: DockEntry) = name(e.name)

        fun childName(e: DockEntry): String {
            val child = e.children.firstOrNull { it.ref == e.causedBy }
            return child?.name?.let { NotificationText.redact(it, MAX_NAME) }?.takeIf { it.isNotBlank() }
                ?: words.helperGeneric()
        }

        fun stateText(e: DockEntry): String =
            when (val s = e.state) {
                is DockState.WorkingWithTasks -> words.tasks(s.done, s.total)
                DockState.Working ->
                    facts.toolLines[e.ref]?.let { NotificationText.redact(it) }?.takeIf { it.isNotBlank() }
                        ?: words.stateWorking()
                DockState.Thinking -> words.stateThinking()
                DockState.NeedsYou -> if (e.causedBy != null) words.needsYouNamed(childName(e)) else words.stateNeedsYou()
                DockState.Finished -> words.stateDone()
                DockState.Failed -> words.stateFailed()
                DockState.Idle -> ""
            }

        // Lines: live rings in the dock's own order; finished and failed ones only because something else is live.
        val candidates = entries.filter { it.state.isLive() || it.state == DockState.Finished || it.state == DockState.Failed }
        fun line(e: DockEntry): String {
            val liveChildren = e.children.count { it.state.isWorking() }
            val helpers = if (liveChildren > 0 && e.state.isLive() && e.causedBy == null) words.helpers(liveChildren) else null
            return listOfNotNull(entryName(e), stateText(e), helpers).joinToString(SEP)
        }
        val total = candidates.size + ui.overflow
        val lines =
            when {
                candidates.size == 1 && ui.overflow == 0 -> emptyList()
                total <= MAX_LINES ->
                    candidates.map(::line) + listOfNotNull(words.more(ui.overflow).takeIf { ui.overflow > 0 })
                else -> {
                    val shown = candidates.take(MAX_LINES - 1)
                    shown.map(::line) + words.more(total - shown.size)
                }
            }

        val kind: OutsideKind
        val title: String
        val text: String
        when {
            needs.size == 1 -> {
                val e = needs.single()
                kind = OutsideKind.NeedsYou
                title = words.needsYouNamed(entryName(e))
                val first = if (e.causedBy != null) words.helperAsking(childName(e)) else words.waitingForAnswer()
                text = listOfNotNull(first, working.size.takeIf { it > 0 }?.let(words::othersWorking)).joinToString(SEP)
            }
            needs.size > 1 -> {
                kind = OutsideKind.NeedsYou
                title = words.needsYouCount(needs.size)
                text = needs.joinToString(", ") { entryName(it) }
            }
            working.size == 1 -> {
                val e = working.single()
                kind = OutsideKind.Working
                title = words.workingOn(entryName(e))
                text = stateText(e)
            }
            else -> {
                kind = OutsideKind.SeveralWorking
                title = words.conversationsWorking(working.size)
                text = working.joinToString(", ") { entryName(it) }
            }
        }

        // Open: whoever needs you first, else the newest working conversation (dock order breaks ties).
        val openEntry =
            needs.firstOrNull()
                ?: working.withIndex()
                    .maxWith(
                        compareBy<IndexedValue<DockEntry>>({ facts.recency[it.value.ref] ?: Long.MIN_VALUE }, { -it.index })
                    )
                    .value
        val stopEntry = working.singleOrNull()?.takeIf { needs.none { n -> n.ref == it.ref } }

        val only = if (needs.isEmpty() && working.size == 1) working.single() else null
        val progress =
            (only?.state as? DockState.WorkingWithTasks)?.takeIf { it.total > 0 }?.let { (it.done * 100 / it.total).coerceIn(0, 100) }

        val server = openEntry.serverName?.takeIf { ui.severalServers && it.isNotBlank() }
        val publicTitle =
            listOfNotNull(
                    "OpenCode",
                    working.size.takeIf { it > 0 }?.let(words::lockWorking),
                    needs.size.takeIf { it > 0 }?.let(words::needsYouCount),
                )
                .joinToString(SEP)

        return OutsideRow(
            kind = kind,
            title = title,
            text = text,
            lines = lines,
            publicTitle = publicTitle,
            count = if (needs.isNotEmpty()) needs.size else working.size,
            needsCount = needs.size,
            workingCount = working.size,
            progress = progress,
            subText = server?.let { words.onServer(NotificationText.redact(it, MAX_NAME)) }.orEmpty(),
            open = OutsideAction(openEntry.ref, words.openNamed(entryName(openEntry))),
            stop = stopEntry?.let { OutsideAction(it.ref, words.stopNamed(entryName(it))) },
            pulses = entries.sumOf { e -> e.pulse + e.children.sumOf { it.pulse } },
        )
    }
}

/**
 * Remembers a swipe-away of the row (API 34+ lets an ongoing row be dismissed). The row stays gone, and is not
 * promoted again, until a ring's pulse rises (a new request or failure), or until nothing is live and a later run begins.
 */
public class DockOutsideGate {
    private var dismissedAt: Int? = null

    public fun dismiss(pulses: Int) {
        dismissedAt = pulses
    }

    /** Whether [row] may be posted now. Pass null when nothing is live: that forgets an earlier dismissal. */
    public fun allows(row: OutsideRow?): Boolean {
        if (row == null) {
            dismissedAt = null
            return false
        }
        val at = dismissedAt ?: return true
        if (row.pulses > at) {
            dismissedAt = null
            return true
        }
        return false
    }
}
