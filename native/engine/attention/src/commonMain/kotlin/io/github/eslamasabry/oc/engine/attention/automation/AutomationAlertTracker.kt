package io.github.eslamasabry.oc.engine.attention.automation

import io.github.eslamasabry.oc.core.model.AutomationOutcome
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.core.model.transcript.MessageAutomation
import io.github.eslamasabry.oc.engine.attention.notify.NotificationText

/** The id of a run whose prompt named no automation id: it cannot be paused from the shade. */
private const val UNNAMED_PREFIX: String = "name:"

/** How many told run numbers one automation keeps: far more than the stream and a reading can be apart. */
private const val TOLD_KEPT: Int = 32

/** What an automation's alert says (B-620): a message to read, a failed run, or a pause. */
public enum class AutomationAlertReason { Message, Failed, Paused }

/**
 * One alert to post for one automation (B-620). [groupKey] and [slotKey] mirror the shade's `AlertGroups.automation`
 * and `AlertKeys.automation`: one group per automation and server, holding at most the tracker's visible slots.
 * [detail] is the reply's first line, already masked; empty for a failure or a pause. [sessionId] is empty when the
 * alert has no chat to open (a pause, or a failure seen only on the automation's row).
 */
public data class AutomationNotification(
    val reason: AutomationAlertReason,
    val automationId: String,
    val automationName: String,
    val sessionId: String,
    val detail: String,
    /** False when the alert cannot name an automation the server could pause. */
    val canPause: Boolean,
    /** How many runs the group covers: one more per alert since the group was last cleared. */
    val coveredRuns: Int,
    val slot: Int,
    val slotKey: String,
    val groupKey: String,
    /** True once the visible slots are full: this alert only replaces one, so the shade posts it without a sound. */
    val capped: Boolean,
) {
    /** Never the name or the reply's words (B-SEC-106). */
    override fun toString(): String =
        "AutomationNotification(reason=$reason, automation=$automationId, runs=$coveredRuns)"
}

/** What one tracker call found (B-620): alerts to post, and what a pause asked here takes out of the shade. */
public data class AutomationAlertDiff(
    val post: List<AutomationNotification> = emptyList(),
    val dismissKeys: Set<String> = emptySet(),
    val dismissGroups: Set<String> = emptySet(),
) {
    /** True when there is nothing to post or to dismiss. */
    public val isEmpty: Boolean
        get() = post.isEmpty() && dismissKeys.isEmpty() && dismissGroups.isEmpty()
}

/** Where an automation's run stands in its chat. */
internal enum class RunStage {
    /** Its prompt waits behind a run that is no automation's and is still going: that run's end is not its end. */
    Queued,

    /** The run it waited behind ended; its own run has not started, so another end is still that other run's. */
    Next,

    /** It runs, or its prompt arrived in a chat where nothing else was going. */
    Going,
    Ended,
}

/**
 * One run of an automation's prompt in a session (B-620), from the prompt until the session starts its next run.
 * [promptId] is the prompt's message id and [prompt] its `metadata.automation`: a reply is this run's own only when
 * it follows that prompt.
 */
public class AutomationRun internal constructor(
    public val sessionId: String,
    public val prompt: MessageAutomation,
    public val promptId: String,
    internal val automationId: String,
    internal var stage: RunStage,
) {
    internal var told: Boolean = false

    /** True when [automation] in message [messageId] is this run's own prompt, shown again. */
    internal fun isPrompt(automation: MessageAutomation, messageId: String): Boolean =
        isPromptId(messageId) || (automation.run != null && automation == prompt)

    internal fun isPromptId(messageId: String): Boolean = messageId.isNotBlank() && messageId == promptId
}

/**
 * Folds what one server's automations do into alerts (B-620), pure and in memory. Quiet runs never notify. A run
 * whose reply has a readable first line, a failed run and a pause each notify once, grouped per automation: a group
 * holds at most `maxVisiblePerAutomation` visible alerts, later ones replace them in turn, and every one still
 * counts in [AutomationNotification.coveredRuns].
 *
 * The live stream and the polled rows are read as one story, run by run: a run either of them told is not told
 * again by the other, in whichever order they arrive, and a told run never hides another run. Nothing is told while
 * the connection is down, and the first read after every (re)connect is a baseline, so runs that happened while
 * offline stay on the automation's row, never late alerts. A run that was going when the connection changed is kept:
 * its chat stays the automation's, and its end, when the stream brings it after the reconnect, is told once.
 */
public class AutomationAlertTracker(
    profileId: String,
    maxVisiblePerAutomation: Int = DEFAULT_VISIBLE_SLOTS,
) {
    private val rows = AutomationRows(profileId, maxVisiblePerAutomation.coerceAtLeast(1))
    private val runs: MutableMap<String, AutomationRun> = mutableMapOf()

    /** The sessions where a run that is no automation's is going now. */
    private val busy: MutableSet<String> = mutableSetOf()
    private var connected: Boolean = false

    /** True while the server's event stream is up; nothing is read or told otherwise. */
    public val isConnected: Boolean
        get() = connected

    /**
     * The sessions whose run is an automation's, going or ended: that run tells through the automation's alert only,
     * so the chat's own finished and stopped alerts stand down for it. A session whose automation prompt still waits
     * behind another run is not among them.
     */
    public val automationSessions: Set<String>
        get() = runs.filterValues { it.stage != RunStage.Queued }.keys

    /**
     * One read of the server's automations. Offline, or the first read of a connected stretch, posts nothing: what is
     * there is history. Then a new run that failed, or a pause, posts once per automation.
     */
    public fun observeAutomations(automations: List<ServerAutomation>): AutomationAlertDiff =
        rows.observe(connected, automations)

    /**
     * The live stream showed a run's prompt (`metadata.automation`) in message [messageId]: the session's run is that
     * automation's run until the session starts another one. In a chat where a run that is no automation's is still
     * going the prompt is queued behind it: that run ends and tells as the chat's own, and the automation's run is
     * the one after it. The same prompt shown again changes nothing. A prompt that names no automation id still
     * notifies, but cannot offer to pause it.
     */
    public fun onAutomationMessage(sessionId: String, automation: MessageAutomation, messageId: String = "") {
        if (!connected || runs[sessionId]?.isPrompt(automation, messageId) == true) return
        val id = automation.id?.takeIf { it.isNotBlank() } ?: (UNNAMED_PREFIX + automation.name)
        val stage = if (sessionId in busy) RunStage.Queued else RunStage.Going
        runs[sessionId] = AutomationRun(sessionId, automation, messageId, id, stage)
    }

    /**
     * The live stream showed, in message [messageId], a prompt that no automation sent: a person's own. A run of an
     * automation that is going or ended there gives the chat back, so a run kept from before a reconnect never
     * outlives the chat's next real run: what ends there next tells through the chat's own alerts. The run's own
     * prompt shown again, and a prompt behind an automation's run that has not started, change nothing.
     */
    public fun onPersonPrompt(sessionId: String, messageId: String) {
        val run = runs[sessionId]?.takeUnless { it.isPromptId(messageId) } ?: return
        if (run.stage == RunStage.Going) busy += sessionId
        if (run.stage == RunStage.Going || run.stage == RunStage.Ended) runs.remove(sessionId)
    }

    /**
     * [sessionId] started a run. While the automation's run is going this is one of its steps; once that run ended it
     * is the chat's next run (a person's own prompt), and the session is no longer the automation's.
     */
    public fun onRunStarted(sessionId: String) {
        val run = runs[sessionId]
        // The run an automation's prompt waited behind has ended: this start is the automation's own run.
        if (run?.stage == RunStage.Next) run.stage = RunStage.Going
        if (run == null || run.stage == RunStage.Ended) {
            runs.remove(sessionId)
            busy += sessionId
        }
    }

    /**
     * A run ended in [sessionId]. When it is the automation's run it is returned, once, for [tellRun]. Null for any
     * other session, for the end of the run an automation's prompt waits behind, for a second end of the same run
     * (the idle that follows every end), and while offline.
     */
    public fun endRun(sessionId: String): AutomationRun? {
        val run = runs[sessionId]
        if (run == null || run.stage == RunStage.Queued) {
            // The run that ended is the chat's own; a prompt that waited behind it has the next run.
            busy -= sessionId
            run?.stage = RunStage.Next
        }
        val going = run?.takeIf { connected && it.stage == RunStage.Going }
        going?.stage = RunStage.Ended
        return going
    }

    /**
     * Tells an ended [run], once. A failure notifies; a finished run notifies when its own reply has a readable first
     * line ([replyExcerpt], which is masked here); a run the person stopped stays quiet. A run the read already told
     * tells nothing.
     */
    public fun tellRun(
        run: AutomationRun,
        failed: Boolean,
        interrupted: Boolean,
        replyExcerpt: String,
    ): AutomationAlertDiff {
        if (!connected || run.told) return AutomationAlertDiff()
        run.told = true
        val line = NotificationText.redact(replyExcerpt)
        val reason = when {
            failed -> AutomationAlertReason.Failed
            interrupted || line.isEmpty() -> null
            else -> AutomationAlertReason.Message
        }
        val detail = if (reason == AutomationAlertReason.Message) line else ""
        val notification = reason?.let { rows.told(run, it, detail) }
        return AutomationAlertDiff(post = listOfNotNull(notification))
    }

    /**
     * The person paused the automation from the shade: its alerts go, and the server's echo of that pause tells
     * nothing. The group starts counting again when the automation is resumed.
     */
    public fun markPausedLocally(automationId: String): AutomationAlertDiff = rows.pausedLocally(automationId)

    /** The groups of every automation the tracker knows, for clearing the shade. */
    public val groups: Set<String>
        get() = rows.groups()

    /**
     * The connection's state. Going offline, or coming back, forgets the baseline: the next read tells nothing of what
     * happened in between. A run that is going, or ended, keeps its chat the automation's; a prompt that had not got
     * its run yet is forgotten, as is which chats were busy, since their ends may have been missed.
     */
    public fun setConnected(value: Boolean) {
        if (connected == value) return
        connected = value
        rows.rebase()
        runs.values.removeAll { it.stage == RunStage.Queued || it.stage == RunStage.Next }
        busy.clear()
    }

    /** The next read is a baseline again: what the person could already see never notifies (B-ANDR-011). */
    public fun rebase() {
        rows.rebase()
    }

    /** The app came to the screen and its alerts were cleared: every group starts counting again. */
    public fun alertsCleared() {
        rows.alertsCleared()
    }

    public companion object {
        /** Alerts one automation may hold in the shade at once; later ones replace them in turn. */
        public const val DEFAULT_VISIBLE_SLOTS: Int = 3
    }
}

/** True while [sessionId] is one of the tracker's [AutomationAlertTracker.automationSessions]. */
public fun AutomationAlertTracker.isAutomationSession(sessionId: String): Boolean = sessionId in automationSessions

/**
 * [AutomationAlertTracker.endRun] and [AutomationAlertTracker.tellRun] in one step, for a caller that already holds
 * the run's own reply line when the run ends.
 */
public fun AutomationAlertTracker.onSessionRunEnded(
    sessionId: String,
    failed: Boolean,
    interrupted: Boolean,
    replyExcerpt: String,
): AutomationAlertDiff =
    endRun(sessionId)?.let { tellRun(it, failed, interrupted, replyExcerpt) } ?: AutomationAlertDiff()

/** The rows of one server's automations, as its tracker last saw them, and what a new reading posts. */
private class AutomationRows(private val profileId: String, private val maxVisible: Int) {
    private var primed: Boolean = false
    private val known: MutableMap<String, Known> = mutableMapOf()

    /** A reading while offline, or the first of a connected stretch, is only recorded; a later one posts. */
    fun observe(connected: Boolean, automations: List<ServerAutomation>): AutomationAlertDiff =
        when {
            !connected -> AutomationAlertDiff()
            primed -> changes(automations)
            else -> AutomationAlertDiff().also { prime(automations) }
        }

    /**
     * The live stream tells this run: its alert, or null when a reading already told this very run. Either way the
     * next reading must not tell the run again.
     */
    fun told(run: AutomationRun, reason: AutomationAlertReason, detail: String): AutomationNotification? {
        val number = run.prompt.run
        val state =
            known.getOrPut(run.automationId) {
                Known(run.automationId, AutomationStatus.Unknown, (number ?: 1) - 1)
            }
        if (number == null) state.toldLatest = true else if (!state.told.add(number)) return null
        return state.emit(run.prompt.name, reason, run.sessionId, detail)
    }

    /** The person paused the automation here: its alerts go, and the server's echo of that pause tells nothing. */
    fun pausedLocally(automationId: String): AutomationAlertDiff {
        val state = known[automationId] ?: return AutomationAlertDiff()
        state.pausedHere = true
        state.status = AutomationStatus.Paused
        val shown = minOf(state.alerts, maxVisible)
        state.alerts = 0
        return AutomationAlertDiff(
            dismissKeys = (0 until shown).mapTo(mutableSetOf<String>()) { slotKey(automationId, it) },
            dismissGroups = setOf(groupKey(automationId)),
        )
    }

    fun groups(): Set<String> = known.keys.mapTo(mutableSetOf<String>()) { groupKey(it) }

    fun rebase() {
        primed = false
    }

    fun alertsCleared() {
        known.values.forEach { it.alerts = 0 }
    }

    private fun changes(automations: List<ServerAutomation>): AutomationAlertDiff {
        val posted = mutableListOf<AutomationNotification>()
        for (automation in automations) {
            val prior = known[automation.id]
            // An automation first seen now is history, like the baseline.
            if (prior == null) known[automation.id] = Known(automation.id, automation.status, automation.runCount)
            else posted += prior.changed(automation)
        }
        val seen = automations.mapTo(mutableSetOf()) { it.id }
        val gone = known.keys.filterTo(mutableSetOf()) { it !in seen }
        known.keys.retainAll(seen)
        return AutomationAlertDiff(post = posted, dismissGroups = gone.mapTo(mutableSetOf<String>()) { groupKey(it) })
    }

    /**
     * The baseline: what is there now is history. A group still showing keeps its count, so a new alert replaces one
     * of its alerts instead of adding a slot, and the runs already told stay told.
     */
    private fun prime(automations: List<ServerAutomation>) {
        val before = known.toMap()
        known.clear()
        for (automation in automations) {
            val state = Known(automation.id, automation.status, automation.runCount)
            before[state.id]?.let { was ->
                state.alerts = was.alerts
                state.told += was.told
            }
            known[state.id] = state
        }
        primed = true
    }

    private fun groupKey(automationId: String): String = "automation:$profileId:$automationId"

    private fun slotKey(automationId: String, slot: Int): String = "${groupKey(automationId)}:$slot"

    /** What was last seen of one automation; `alerts` counts what was told since its group was last cleared. */
    inner class Known(val id: String, var status: AutomationStatus, var runCount: Int) {
        var alerts: Int = 0

        /** The person paused it from the shade: the server's echo of that pause tells nothing. */
        var pausedHere: Boolean = false

        /**
         * The numbers of the runs already told, by the live stream or by a reading: neither tells one of them again.
         * Only the newest [TOLD_KEPT] numbers are kept.
         */
        val told: MutableSet<Int> = mutableSetOf()

        /** The stream told a run whose number it did not name: the next reading's one new run is that one. */
        var toldLatest: Boolean = false

        /** Its alert since it was last read, or none: a run that failed, a pause, nothing for a quiet run. */
        fun changed(automation: ServerAutomation): List<AutomationNotification> {
            val reason = reasonOf(automation)
            // A reading speaks of the newest run only: its failure is that run's one alert.
            if (reason == AutomationAlertReason.Failed) told += automation.runCount
            told.removeAll { it <= automation.runCount - TOLD_KEPT }
            if (automation.runCount > runCount) toldLatest = false
            // The server's own word of the pause ends the echo a pause from the shade waits for.
            if (automation.status == AutomationStatus.Paused) pausedHere = false
            status = automation.status
            runCount = automation.runCount
            val session = automation.sessionId.orEmpty()
            return listOfNotNull(reason?.let { emit(automation.name, it, session, "") })
        }

        /**
         * What this reading has to tell. Its outcome is the newest run's, so only that run being told already keeps
         * it quiet: an earlier run the stream told never hides the failure of the run after it.
         */
        private fun reasonOf(automation: ServerAutomation): AutomationAlertReason? {
            val ran = automation.runCount - runCount
            val alreadyTold = automation.runCount in told || (toldLatest && ran == 1)
            val paused = automation.status == AutomationStatus.Paused && status != AutomationStatus.Paused
            val outcome = automation.lastOutcome
            return when {
                paused && !pausedHere -> AutomationAlertReason.Paused
                alreadyTold || ran <= 0 -> null
                outcome == AutomationOutcome.Failed -> AutomationAlertReason.Failed
                outcome == AutomationOutcome.Disabled && !pausedHere -> AutomationAlertReason.Paused
                else -> null
            }
        }

        fun emit(
            name: String,
            reason: AutomationAlertReason,
            session: String,
            detail: String,
        ): AutomationNotification {
            val count = ++alerts
            val slot = (count - 1) % maxVisible
            return AutomationNotification(
                reason = reason,
                automationId = id,
                automationName = name,
                sessionId = session,
                detail = detail,
                canPause = !id.startsWith(UNNAMED_PREFIX),
                coveredRuns = count,
                slot = slot,
                slotKey = slotKey(id, slot),
                groupKey = groupKey(id),
                capped = count > maxVisible,
            )
        }
    }
}
