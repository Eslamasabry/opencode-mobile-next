package io.github.eslamasabry.oc.engine.attention.automation

import io.github.eslamasabry.oc.core.model.AutomationAction
import io.github.eslamasabry.oc.core.model.AutomationOutcome
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.AutomationTrigger
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.core.model.transcript.MessageAutomation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-620: pure automation notification tracking, quiet suppression, grouping, capping and offline rules. */
class AutomationAlertTrackerTest {
    private fun automation(
        id: String = "aut_1",
        name: String = "Nightly check",
        status: AutomationStatus = AutomationStatus.Active,
        runCount: Int = 1,
        lastRunAtMs: Long? = 1_000L,
        lastOutcome: AutomationOutcome? = AutomationOutcome.Fired,
        sessionId: String? = "ses_1",
    ) = ServerAutomation(
        id = id,
        name = name,
        status = status,
        trigger = AutomationTrigger.Every("1h"),
        action = AutomationAction.Prompt("Check nightly status"),
        runCount = runCount,
        lastRunAtMs = lastRunAtMs,
        lastOutcome = lastOutcome,
        sessionId = sessionId,
    )

    @Test
    fun `B-620 quiet runs and initial connect baseline make no notification`() {
        val tracker = AutomationAlertTracker(profileId = "p1")
        tracker.setConnected(true)

        // Baseline on connect sees prior failure and paused state from while offline: zero notifications.
        val baseline = tracker.observeAutomations(
            listOf(
                automation(id = "aut_1", runCount = 2, lastOutcome = AutomationOutcome.Failed),
                automation(id = "aut_2", status = AutomationStatus.Paused, runCount = 1),
            ),
        )
        assertTrue(baseline.post.isEmpty())

        // Quiet runs (Fired, Late, Skipped, Expired) make no notification.
        for ((idx, outcome) in listOf(
            AutomationOutcome.Fired,
            AutomationOutcome.Late,
            AutomationOutcome.Skipped,
            AutomationOutcome.Expired,
        ).withIndex()) {
            val quiet = tracker.observeAutomations(
                listOf(
                    automation(
                        id = "aut_1",
                        runCount = 3 + idx,
                        lastRunAtMs = 2_000L + idx * 1_000L,
                        lastOutcome = outcome,
                    ),
                ),
            )
            assertTrue(quiet.post.isEmpty(), "quiet outcome $outcome must not notify")
        }

        // Prompt run with blank reply or interrupted end is quiet and makes no notification.
        tracker.onAutomationMessage("ses_1", MessageAutomation(id = "aut_1", name = "Nightly check", run = 10))
        val blankReply = tracker.onSessionRunEnded("ses_1", failed = false, interrupted = false, replyExcerpt = "   ")
        assertTrue(blankReply.post.isEmpty())

        tracker.onAutomationMessage("ses_1", MessageAutomation(id = "aut_1", name = "Nightly check", run = 11))
        val interrupted = tracker.onSessionRunEnded("ses_1", failed = false, interrupted = true, replyExcerpt = "Hi")
        assertTrue(interrupted.post.isEmpty())
    }

    @Test
    fun `B-620 message needing reading, failure, and server pause each emit one notification`() {
        val tracker = AutomationAlertTracker(profileId = "p1")
        tracker.setConnected(true)
        tracker.observeAutomations(listOf(automation(id = "aut_1", runCount = 3)))

        // 1. Live metadata.automation message with readable reply emits one masked notification.
        tracker.onAutomationMessage("ses_1", MessageAutomation(id = "aut_1", name = "Nightly check", run = 4))
        assertTrue(tracker.isAutomationSession("ses_1"))
        val msgDiff = tracker.onSessionRunEnded(
            sessionId = "ses_1",
            failed = false,
            interrupted = false,
            replyExcerpt = "Found 2 failing tests token=sk-live-secret12345678",
        )
        val finding = msgDiff.post.single()
        assertEquals(AutomationAlertReason.Message, finding.reason)
        assertEquals("Nightly check", finding.automationName)
        assertEquals("aut_1", finding.automationId)
        assertEquals("ses_1", finding.sessionId)
        assertTrue("Found 2 failing tests" in finding.detail)
        assertFalse("sk-live-secret" in finding.detail)
        assertTrue(finding.canPause)
        assertEquals(1, finding.coveredRuns)

        // 2. Live failed run notifies once and does not double-notify when poll observes the same runCount.
        tracker.onAutomationMessage("ses_1", MessageAutomation(id = "aut_1", name = "Nightly check", run = 5))
        val failDiff = tracker.onSessionRunEnded("ses_1", failed = true, interrupted = false, replyExcerpt = "")
        val failed = failDiff.post.single()
        assertEquals(AutomationAlertReason.Failed, failed.reason)
        assertEquals(2, failed.coveredRuns)

        val sameRunPoll = tracker.observeAutomations(
            listOf(
                automation(
                    id = "aut_1",
                    runCount = 5,
                    lastRunAtMs = 5_000L,
                    lastOutcome = AutomationOutcome.Failed,
                ),
            ),
        )
        assertTrue(sameRunPoll.post.isEmpty(), "same failed run already notified via live stream")

        // 3. Server-side pause transition emits one Paused notification.
        val pausedDiff = tracker.observeAutomations(
            listOf(
                automation(
                    id = "aut_1",
                    status = AutomationStatus.Paused,
                    runCount = 5,
                    lastRunAtMs = 5_000L,
                    lastOutcome = AutomationOutcome.Disabled,
                ),
            ),
        )
        val paused = pausedDiff.post.single()
        assertEquals(AutomationAlertReason.Paused, paused.reason)
        assertEquals("Nightly check", paused.automationName)
        assertEquals(3, paused.coveredRuns)
    }

    @Test
    fun `B-620 noisy automation groups by automation and caps visible slots while counting all runs`() {
        val tracker = AutomationAlertTracker(profileId = "p1", maxVisiblePerAutomation = 3)
        tracker.setConnected(true)
        tracker.observeAutomations(
            listOf(
                automation(id = "aut_1", name = "Nightly check", runCount = 0),
                automation(id = "aut_2", name = "PR triage", runCount = 0),
            ),
        )

        val aut1Slots = mutableSetOf<String>()
        var lastAut1: AutomationNotification? = null
        for (run in 1..7) {
            val diff = tracker.observeAutomations(
                listOf(
                    automation(
                        id = "aut_1",
                        name = "Nightly check",
                        runCount = run,
                        lastRunAtMs = run * 1_000L,
                        lastOutcome = AutomationOutcome.Failed,
                    ),
                    automation(id = "aut_2", name = "PR triage", runCount = 0),
                ),
            )
            val posted = diff.post.single()
            aut1Slots += posted.slotKey
            lastAut1 = posted
            assertEquals(run, posted.coveredRuns)
            assertEquals(run > 3, posted.capped)
        }
        assertEquals(3, aut1Slots.size, "visible slot keys must be capped at 3")
        assertEquals(7, lastAut1!!.coveredRuns)

        // Separate automation uses its own group and count.
        val aut2Diff = tracker.observeAutomations(
            listOf(
                automation(
                    id = "aut_1",
                    name = "Nightly check",
                    runCount = 7,
                    lastRunAtMs = 7_000L,
                    lastOutcome = AutomationOutcome.Failed,
                ),
                automation(
                    id = "aut_2",
                    name = "PR triage",
                    runCount = 1,
                    lastRunAtMs = 8_000L,
                    lastOutcome = AutomationOutcome.Failed,
                ),
            ),
        )
        val aut2 = aut2Diff.post.single()
        assertEquals(1, aut2.coveredRuns)
        assertFalse(aut2.capped)
        assertTrue(aut2.groupKey != lastAut1.groupKey)
    }

    @Test
    fun `B-620 local pause dismisses automation notifications and suppresses echoed server pause`() {
        val tracker = AutomationAlertTracker(profileId = "p1")
        tracker.setConnected(true)
        tracker.observeAutomations(listOf(automation(id = "aut_1", runCount = 1)))
        val fail = tracker.observeAutomations(
            listOf(
                automation(
                    id = "aut_1",
                    runCount = 2,
                    lastRunAtMs = 2_000L,
                    lastOutcome = AutomationOutcome.Failed,
                ),
            ),
        )
        val posted = fail.post.single()

        val pauseDismiss = tracker.markPausedLocally("aut_1")
        assertTrue(posted.slotKey in pauseDismiss.dismissKeys)
        assertTrue(posted.groupKey in pauseDismiss.dismissGroups)

        val echoedPause = tracker.observeAutomations(
            listOf(automation(id = "aut_1", status = AutomationStatus.Paused, runCount = 2, lastRunAtMs = 2_000L)),
        )
        assertTrue(echoedPause.post.isEmpty(), "locally paused automation must not fire a paused alert")
    }

    @Test
    fun `B-620 offline connection emits nothing and reconnect starts from a fresh baseline`() {
        val tracker = AutomationAlertTracker(profileId = "p1")
        // Disconnected: nothing emits.
        tracker.onAutomationMessage("ses_1", MessageAutomation(id = "aut_1", name = "Nightly check", run = 1))
        val offlineEnd = tracker.onSessionRunEnded("ses_1", failed = false, interrupted = false, replyExcerpt = "Bug")
        assertTrue(offlineEnd.post.isEmpty())
        val offlinePoll = tracker.observeAutomations(
            listOf(automation(id = "aut_1", runCount = 1, lastOutcome = AutomationOutcome.Failed)),
        )
        assertTrue(offlinePoll.post.isEmpty())

        // Connect, then disconnect, then reconnect with runs that happened offline -> baseline only.
        tracker.setConnected(true)
        tracker.observeAutomations(listOf(automation(id = "aut_1", runCount = 1)))
        tracker.setConnected(false)
        val whileOff = tracker.observeAutomations(
            listOf(automation(id = "aut_1", runCount = 2, lastOutcome = AutomationOutcome.Failed)),
        )
        assertTrue(whileOff.post.isEmpty())

        tracker.setConnected(true)
        val afterReconnect = tracker.observeAutomations(
            listOf(
                automation(
                    id = "aut_1",
                    runCount = 4,
                    lastRunAtMs = 9_000L,
                    lastOutcome = AutomationOutcome.Failed,
                ),
            ),
        )
        assertTrue(afterReconnect.post.isEmpty(), "runs while offline appear on the row, not as late alerts")
    }
}
