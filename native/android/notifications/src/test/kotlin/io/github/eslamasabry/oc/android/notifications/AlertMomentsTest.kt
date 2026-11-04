package io.github.eslamasabry.oc.android.notifications

import io.github.eslamasabry.oc.android.notifications.AlertMoments.Pending
import io.github.eslamasabry.oc.android.notifications.AlertMoments.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cases of the Flutter `test/background_coding_alert_test.dart` (B-ANDR-011) and the quiet-hours / opening parts
 * of `test/notification_moments_test.dart` (B-ANDR-039, B-ANDR-040), against [AlertMoments]. Keys carry the profile
 * as amended by plan 11 §10.4 (`input:p1:session-1`, Flutter `input:session-1`).
 */
class AlertMomentsTest {
    private val input = AlertKeys.input("p1", "session-1")
    private val status = AlertKeys.status("p1", "session-1")

    private fun backgrounded() = AlertMoments("p1").also { it.appVisible(false) }

    @Test
    fun `B-ANDR-011 background permission and question requests share one session alert`() {
        val m = backgrounded()
        val permission = Pending("session-1", "permission-1", AlertKind.Permission)
        val question = Pending("session-1", "question-1", AlertKind.Question)
        m.requests(listOf(permission))
        // Both pending: still exactly the one permission alert, nothing new posted.
        assertEquals(emptyList(), m.requests(listOf(permission, question)))
        val fresh = backgrounded()
        assertEquals(
            listOf(Step.Post(AlertKind.Permission, "session-1", input, "permission-1")),
            fresh.requests(listOf(permission, question)),
        )
        // The permission is answered: the question takes its place in the same alert, no dismissal.
        assertEquals(listOf(Step.Post(AlertKind.Question, "session-1", input, "question-1")), fresh.requests(listOf(question)))
        // The question is rejected: the alert goes.
        assertEquals(listOf(Step.Dismiss(input)), fresh.requests(emptyList()))
    }

    @Test
    fun `B-ANDR-011 a request already visible is alerted when the app backgrounds`() {
        val m = AlertMoments("p1")
        m.appVisible(true)
        assertEquals(emptyList(), m.requests(listOf(Pending("session-1", "question-1", AlertKind.Question))))
        assertEquals(listOf(Step.Post(AlertKind.Question, "session-1", input, "question-1")), m.appVisible(false))
    }

    @Test
    fun `B-ANDR-011 a completed reply alerts once whatever order the ending arrives in`() {
        val m = backgrounded()
        m.runStarted("session-1")
        // A finished step is not the end of the run: nothing until the run ends.
        val first = m.runEnded("session-1", AlertKind.Complete)
        val second = m.runEnded("session-1", AlertKind.Complete)
        assertEquals(listOf(Step.Post(AlertKind.Complete, "session-1", status, requestId = "")), first)
        assertEquals(emptyList(), second)
        // An ending for a run this connection never saw start posts nothing.
        assertEquals(emptyList(), m.runEnded("session-2", AlertKind.Complete))
    }

    @Test
    fun `B-ANDR-011 a background error replaces completion and sub-agent alerts stay quiet`() {
        val m = backgrounded()
        val steps = mutableListOf<Step>()
        for ((session, child) in listOf("session-1" to false, "child-1" to true)) {
            steps += m.runStarted(session)
            steps += m.runEnded(session, AlertKind.Error, subAgent = child)
            steps += m.runEnded(session, AlertKind.Complete, subAgent = child)
        }
        assertEquals(listOf<Step>(Step.Post(AlertKind.Error, "session-1", status, reason = StopReason.Other)), steps)
    }

    @Test
    fun `B-ANDR-011 returning to the foreground dismisses outstanding alerts`() {
        val m = backgrounded()
        m.runStarted("session-1")
        m.runEnded("session-1", AlertKind.Complete)
        m.requests(listOf(Pending("session-2", "per_1", AlertKind.Permission)))
        assertEquals(setOf<Step>(Step.Dismiss(status), Step.Dismiss(AlertKeys.input("p1", "session-2"))), m.appVisible(true).toSet())
        // Nothing is left to dismiss twice.
        m.appVisible(false)
        assertTrue(m.appVisible(true).none { it == Step.Dismiss(status) })
    }

    @Test
    fun `B-ANDR-011 nothing is posted while the app is on screen`() {
        val m = AlertMoments("p1")
        m.runStarted("session-1")
        assertEquals(emptyList(), m.runEnded("session-1", AlertKind.Complete))
    }

    @Test
    fun `B-ANDR-039 in quiet hours a request and a failure post silently and a finished reply waits`() {
        val m = backgrounded()
        assertEquals(
            listOf(Step.Post(AlertKind.Permission, "session-1", input, "per_1", quiet = true)),
            m.requests(listOf(Pending("session-1", "per_1", AlertKind.Permission)), quiet = true),
        )
        m.runStarted("session-2")
        assertEquals(
            listOf(Step.Post(AlertKind.Error, "session-2", AlertKeys.status("p1", "session-2"), reason = StopReason.Auth, quiet = true)),
            m.runEnded("session-2", AlertKind.Error, reason = NotificationText.stoppedReason("ProviderAuthError"), quiet = true),
        )
        m.runStarted("session-3")
        assertEquals(emptyList(), m.runEnded("session-3", AlertKind.Complete, quiet = true))
        // Outside quiet hours a request is not silenced.
        val loud = backgrounded()
        assertEquals(false, (loud.requests(listOf(Pending("s", "r", AlertKind.Question))).single() as Step.Post).quiet)
    }

    @Test
    fun `B-ANDR-040 opening the chat clears its finished alert`() {
        val m = backgrounded()
        m.runStarted("session-1")
        m.runEnded("session-1", AlertKind.Complete)
        assertEquals(listOf(Step.Dismiss(status)), m.chatOpened("session-1"))
        assertEquals(emptyList(), m.chatOpened("session-1"))
    }

    @Test
    fun `B-ANDR-040 a new run in the chat clears its old finished alert`() {
        val m = backgrounded()
        m.runStarted("session-1")
        m.runEnded("session-1", AlertKind.Complete)
        assertEquals(listOf(Step.Dismiss(status)), m.runStarted("session-1"))
    }

    @Test
    fun `B-ANDR-011 an alert the shade could not post is tried again`() {
        val m = backgrounded()
        val pending = listOf(Pending("session-1", "per_1", AlertKind.Permission))
        val post = m.requests(pending).single() as Step.Post
        m.notShown(post)
        assertEquals(listOf<Step>(post), m.requests(pending))
    }
}
