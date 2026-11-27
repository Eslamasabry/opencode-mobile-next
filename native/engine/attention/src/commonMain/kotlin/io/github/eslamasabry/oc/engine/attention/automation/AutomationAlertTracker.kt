package io.github.eslamasabry.oc.engine.attention.automation

import io.github.eslamasabry.oc.core.model.AutomationOutcome
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.core.model.transcript.MessageAutomation
import io.github.eslamasabry.oc.engine.attention.notify.NotificationText

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

/**
 * Folds what one server's automations do into alerts (B-620), pure and in memory. Quiet runs never notify. A run
 * whose reply has a readable first line, a failed run and a pause each notify once, grouped per automation: a group
 * holds at most `maxVisiblePerAutomation` visible alerts, later ones replace them in turn, and every one still
 * counts in [AutomationNotification.coveredRuns].
 *
 * The live stream and the polled rows are read as one story: a failure the stream told is not told again by the
 * next read. Nothing is told while the connection is down, and the first read after every (re)connect is a
 * baseline, so runs that happened while offline stay on the automation's row, never late alerts.
 */
public class AutomationAlertTracker(
    private val profileId: String,
    maxVisiblePerAutomation: Int = DEFAULT_VISIBLE_SLOTS,
) {
    private val maxVisible: Int = maxVisiblePerAutomation.coerceAtLeast(1)
    private var connected: Boolean = false
    private var primed: Boolean = false
    private val known: MutableMap<String, Known> = mutableMapOf()
    private val runs: MutableMap<String, AutomationRun> = mutableMapOf()

    /** True while the server's event stream is up; nothing is read or told otherwise. */
    public val isConnected: Boolean
        get() = connected

    /**
     * One read of the server's automations. Offline, or the first read of a connected stretch, posts nothing: what is
     * there is history. Then a new run that failed, or a pause, posts once per automation.
     */
    public fun observeAutomations(automations: List<ServerAutomation>): AutomationAlertDiff =
        when {
            !connected -> AutomationAlertDiff()
            primed -> changes(automations)
            else -> AutomationAlertDiff().also { prime(automations) }
        }

    /**
     * The live stream showed a run's prompt (`metadata.automation`): from here to the run's end the session is that
     * automation's run. A prompt that names no automation id still notifies, but cannot offer to pause it.
     */
    public fun onAutomationMessage(sessionId: String, automation: MessageAutomation) {
        if (!connected) return
        val id = automation.id?.takeIf { it.isNotBlank() }
        runs[sessionId] = AutomationRun(id ?: UNNAMED_PREFIX + automation.name, automation.name, automation.run)
    }

    /** True while [sessionId] runs an automation's prompt; the caller then reads its reply when the run ends. */
    public fun isAutomationSession(sessionId: String): Boolean = sessionId in runs

    /**
     * A run ended in [sessionId]. A failure notifies once; a finished run notifies when its reply has a readable first
     * line ([replyExcerpt], which is masked here); a run the person stopped stays quiet. Any other session changes
     * nothing.
     */
    public fun onSessionRunEnded(
        sessionId: String,
        failed: Boolean,
        interrupted: Boolean,
        replyExcerpt: String,
    ): AutomationAlertDiff {
        val run = runs.remove(sessionId)
        if (!connected || run == null) return AutomationAlertDiff()
        val line = NotificationText.redact(replyExcerpt)
        val reason = when {
            failed -> AutomationAlertReason.Failed
            interrupted || line.isEmpty() -> null
            else -> AutomationAlertReason.Message
        }
        val state = known.getOrPut(run.automationId) {
            Known(run.automationId, AutomationStatus.Unknown, (run.number ?: 1) - 1)
        }
        // The stream told this run: the next read must not tell it again.
        if (run.number == null) state.toldLatest = true else state.told += run.number
        val detail = if (reason == AutomationAlertReason.Message) line else ""
        val notification = reason?.let { emit(state, run.name, it, sessionId, detail) }
        return AutomationAlertDiff(post = listOfNotNull(notification))
    }

    /**
     * The person paused the automation from the shade: its alerts go, and the server's echo of that pause tells
     * nothing. The group starts counting again when the automation is resumed.
     */
    public fun markPausedLocally(automationId: String): AutomationAlertDiff {
        val state = known[automationId] ?: return AutomationAlertDiff()
        state.pausedHere = true
        state.status = AutomationStatus.Paused
        val shown = minOf(state.alerts, maxVisible)
        state.alerts = 0
        return AutomationAlertDiff(
            dismissKeys = (0 until shown).mapTo(mutableSetOf()) { slotKey(automationId, it) },
            dismissGroups = setOf(groupKey(automationId)),
        )
    }

    /** The groups of every automation the tracker knows, for clearing the shade. */
    public fun groups(): Set<String> = known.keys.mapTo(mutableSetOf<String>(), ::groupKey)

    /**
     * The connection's state. Going offline, or coming back, forgets the baseline and the runs in flight: what happened
     * in between is never told.
     */
    public fun setConnected(value: Boolean) {
        if (connected == value) return
        connected = value
        primed = false
        runs.clear()
    }

    /** The next read is a baseline again: what the person could already see never notifies (B-ANDR-011). */
    public fun rebase() {
        primed = false
    }

    /** The app came to the screen and its alerts were cleared: every group starts counting again. */
    public fun alertsCleared() {
        known.values.forEach { it.alerts = 0 }
    }

    private fun changes(automations: List<ServerAutomation>): AutomationAlertDiff {
        val posted = mutableListOf<AutomationNotification>()
        for (automation in automations) {
            val prior = known[automation.id]
            // An automation first seen now is history, like the baseline.
            if (prior == null) known[automation.id] = Known(automation.id, automation.status, automation.runCount)
            else posted += changed(prior, automation)
        }
        val seen = automations.mapTo(mutableSetOf()) { it.id }
        val gone = known.keys.filterTo(mutableSetOf()) { it !in seen }
        known.keys.retainAll(seen)
        return AutomationAlertDiff(post = posted, dismissGroups = gone.mapTo(mutableSetOf<String>(), ::groupKey))
    }

    /**
     * The baseline: what is there now is history. A group still showing keeps its count, so a new alert replaces one
     * of its alerts instead of adding a slot.
     */
    private fun prime(automations: List<ServerAutomation>) {
        val counts = known.mapValues { it.value.alerts }
        known.clear()
        for (automation in automations) {
            val state = Known(automation.id, automation.status, automation.runCount)
            state.alerts = counts[state.id] ?: 0
            known[state.id] = state
        }
        primed = true
    }

    /** One automation's alert since it was last read, or none: a failed run, a pause, nothing for a quiet run. */
    private fun changed(prior: Known, automation: ServerAutomation): List<AutomationNotification> {
        val ran = automation.runCount - prior.runCount
        val told = prior.toldLatest || ((prior.runCount + 1)..automation.runCount).any { it in prior.told }
        prior.told.removeAll { it <= automation.runCount }
        if (ran > 0) prior.toldLatest = false
        val paused = automation.status == AutomationStatus.Paused && prior.status != AutomationStatus.Paused
        val outcome = automation.lastOutcome
        val reason = when {
            paused && !prior.pausedHere -> AutomationAlertReason.Paused
            told || ran <= 0 -> null
            outcome == AutomationOutcome.Failed -> AutomationAlertReason.Failed
            outcome == AutomationOutcome.Disabled && !prior.pausedHere -> AutomationAlertReason.Paused
            else -> null
        }
        // The server's own word of the pause ends the echo a pause from the shade waits for.
        if (automation.status == AutomationStatus.Paused) prior.pausedHere = false
        prior.status = automation.status
        prior.runCount = automation.runCount
        val notification = reason?.let { emit(prior, automation.name, it, automation.sessionId.orEmpty(), "") }
        return listOfNotNull(notification)
    }

    private fun emit(
        state: Known,
        name: String,
        reason: AutomationAlertReason,
        session: String,
        detail: String,
    ): AutomationNotification {
        val count = ++state.alerts
        val slot = (count - 1) % maxVisible
        return AutomationNotification(
            reason = reason,
            automationId = state.id,
            automationName = name,
            sessionId = session,
            detail = detail,
            canPause = !state.id.startsWith(UNNAMED_PREFIX),
            coveredRuns = count,
            slot = slot,
            slotKey = slotKey(state.id, slot),
            groupKey = groupKey(state.id),
            capped = count > maxVisible,
        )
    }

    /** The shade's `AlertGroups.automation`: one group per automation and server. */
    private fun groupKey(automationId: String): String = "automation:$profileId:$automationId"

    /** The shade's `AlertKeys.automation`: one key per visible slot of an automation's group. */
    private fun slotKey(automationId: String, slot: Int): String = "${groupKey(automationId)}:$slot"

    /** What was last seen of one automation; `alerts` counts what was told since its group was last cleared. */
    private class Known(val id: String, var status: AutomationStatus, var runCount: Int) {
        var alerts: Int = 0

        /** The person paused it from the shade: the server's echo of that pause tells nothing. */
        var pausedHere: Boolean = false

        /** Run numbers the live stream already told, which the next read must not tell again. */
        val told: MutableSet<Int> = mutableSetOf()

        /** The stream told a run whose number it did not name: the next read's newest run is that one. */
        var toldLatest: Boolean = false
    }

    /** A session running an automation's prompt, until the run ends. */
    private class AutomationRun(val automationId: String, val name: String, val number: Int?)

    public companion object {
        /** Alerts one automation may hold in the shade at once; later ones replace them in turn. */
        public const val DEFAULT_VISIBLE_SLOTS: Int = 3

        /** The id of a run whose prompt named no automation id: it cannot be paused from the shade. */
        private const val UNNAMED_PREFIX: String = "name:"
    }
}
