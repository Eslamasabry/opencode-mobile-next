package io.github.eslamasabry.oc.android.notifications

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId


import android.app.Application
import android.content.Intent
import android.net.Uri
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * `NotificationActionBody` (plan 11 §10.6; 16 WP-16.8 `NotificationActionAuthTest`; `NoTrampolineTest`). Every test
 * also asserts that no activity was started: Android 12+ blocks notification trampolines silently.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationActionBodyTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    private class FakeShade : AlertShade {
        val reposted = mutableListOf<ShadeTap>()
        val dismissed = mutableListOf<String>()

        override fun show(alert: CodingAlert, route: AlertRoute.Post): AlertShown = AlertShown.Shown

        override fun showOpenOnly(tap: ShadeTap): AlertShown {
            reposted += tap
            return AlertShown.Shown
        }

        override fun dismiss(key: String) {
            dismissed += key
        }

        override fun dismissIfCurrent(tap: ShadeTap): Boolean { dismiss(tap.key); return true }

        override fun showOpenOnlyIfCurrent(tap: ShadeTap): AlertShown = showOpenOnly(tap)

        override fun isShowing(key: String): Boolean = false
    }

    private class Lock(override val sdkInt: Int, var locked: Boolean) : DeviceLock {
        override fun isLocked(): Boolean = locked
    }

    private val shade = FakeShade()
    private val answers = mutableListOf<ShadeAnswer>()
    private var reply: suspend (ShadeAnswer) -> Outcome<Unit> = { Unit.ok() }
    private val stops = mutableListOf<ConversationRef>()
    private var lock = Lock(sdkInt = 35, locked = false)
    private val dismissedRows = mutableListOf<ReplyRun>()

    private fun body() =
        NotificationActionBody(
            shade = shade,
            answerer = { a -> answers += a; reply(a) },
            stopper = { s -> stops += s; Unit.ok() },
            lock = lock,
            onRowDismissed = { dismissedRows += it },
        )

    private fun tapIntent(decision: String, request: String = "per_1", kind: String = "permission", extras: Intent.() -> Unit = {}) =
        Intent("${FrozenExtras.ACTION_CODING_ALERT}.$decision").apply {
            data = Uri.parse("opencode://coding-alert/input:p1:ses_1/$decision")
            putExtra(FrozenExtras.EXTRA_CODING_ALERT_KIND, kind)
            putExtra(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID, "ses_1")
            putExtra(FrozenExtras.EXTRA_CODING_ALERT_KEY, "input:p1:ses_1")
            putExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID, "p1")
            putExtra(FrozenExtras.EXTRA_CODING_ALERT_DECISION, decision)
            putExtra(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID, request)
            extras()
        }

    @AfterTest
    fun noTrampoline() {
        assertNull(shadowOf(app).nextStartedActivity, "a receiver body must never start an activity")
    }

    @Test
    fun `B-AGENT-031 Allow once approves exactly that permission once and Reject never grants`() =
        runTest {
            assertEquals(ShadeOutcome.Answered, body().onCodingAction(tapIntent("allow")))
            assertEquals(ShadeOutcome.Answered, body().onCodingAction(tapIntent("deny", request = "per_2")))
            assertEquals(listOf("per_1", "per_2"), answers.map { it.request.raw })
            assertEquals(listOf<ShadeReply>(ShadeReply.AllowOnce, ShadeReply.Reject), answers.map { it.reply })
            assertEquals("p1", answers[0].profile.raw)
            assertEquals(listOf("input:p1:ses_1", "input:p1:ses_1"), shade.dismissed)
        }

    @Test
    fun `B-SEC-183 an empty request id never approves another request and refreshes the alert`() =
        runTest {
            assertEquals(ShadeOutcome.RepostedOpen, body().onCodingAction(tapIntent("allow", request = "")))
            assertTrue(answers.isEmpty())
            assertEquals(1, shade.reposted.size)
        }

    @Test
    fun `B-PROTO-046 a stale request id refreshes the alert instead of resolving`() =
        runTest {
            reply = { Failure.Refused(RefusalReason.StaleScope).let(::Failed) }
            assertEquals(ShadeOutcome.RepostedOpen, body().onCodingAction(tapIntent("deny")))
            assertEquals(1, answers.size)
            assertTrue(shade.dismissed.isEmpty())
            assertEquals("per_1", shade.reposted.single().requestId)
        }

    @Test
    fun `B-AGENT-032 an empty legacy reply is refused before any network call and a typed one answers that request`() =
        runTest {
            val empty = tapIntent("reply", kind = "question") { putExtra(FrozenExtras.EXTRA_CODING_ALERT_OPTION_TEXT, "") }
            assertEquals(ShadeOutcome.Ignored, body().onCodingAction(empty))
            val blankOption = tapIntent("option1", kind = "question") { putExtra(FrozenExtras.EXTRA_CODING_ALERT_OPTION_TEXT, "   ") }
            assertEquals(ShadeOutcome.Ignored, body().onCodingAction(blankOption))
            assertTrue(answers.isEmpty())
            val option = tapIntent("option0", request = "que_1", kind = "question") { putExtra(FrozenExtras.EXTRA_CODING_ALERT_OPTION_TEXT, "Postgres") }
            assertEquals(ShadeOutcome.Answered, body().onCodingAction(option))
            assertEquals(ShadeReply.Text("Postgres"), answers.single().reply)
            assertEquals("que_1", answers.single().request.raw)
        }

    @Test
    fun `B-AGENT-032 a failed reply keeps the request and reposts it with Open only`() =
        runTest {
            reply = { Failed(Failure.Network()) }
            assertEquals(ShadeOutcome.RepostedOpen, body().onCodingAction(tapIntent("allow")))
            assertTrue(shade.dismissed.isEmpty())
            assertEquals(1, shade.reposted.size)
        }

    @Test
    fun `B-CHAT-074 a second tap while the first is in flight sends exactly one reply`() =
        runTest {
            val gate = CompletableDeferred<Outcome<Unit>>()
            reply = { gate.await() }
            val body = body()
            val first = async { body.onCodingAction(tapIntent("allow")) }
            runCurrent()
            assertEquals(ShadeOutcome.IgnoredDuplicate, body.onCodingAction(tapIntent("allow")))
            gate.complete(Unit.ok())
            assertEquals(ShadeOutcome.Answered, first.await())
            assertEquals(1, answers.size)
        }

    @Test
    fun `B-CHAT-074 the in-app tap winning the race dismisses the alert quietly`() =
        runTest {
            reply = { Failed(Failure.Refused(RefusalReason.AlreadyAnswered)) }
            assertEquals(ShadeOutcome.DismissedGone, body().onCodingAction(tapIntent("allow")))
            assertTrue(shade.reposted.isEmpty())
        }

    @Test
    fun `B-CHAT-076 answering a request the server already timed out dismisses with no error`() =
        runTest {
            reply = { Failed(Failure.NotFound()) }
            assertEquals(ShadeOutcome.DismissedGone, body().onCodingAction(tapIntent("deny")))
            assertEquals(listOf("input:p1:ses_1"), shade.dismissed)
            assertTrue(shade.reposted.isEmpty())
        }

    @Test
    fun `plan 11 10_6 no answer within 15 s reposts the alert with Open only`() =
        runTest {
            reply = { awaitCancellation() }
            assertEquals(ShadeOutcome.RepostedOpen, body().onCodingAction(tapIntent("allow")))
            assertEquals(1, shade.reposted.size)
        }

    @Test
    fun `B-SEC-184 on API 26-30 a locked phone answers nothing for Allow once and opens at that request`() =
        runTest {
            lock = Lock(sdkInt = 30, locked = true)
            assertEquals(ShadeOutcome.RepostedOpen, body().onCodingAction(tapIntent("allow")))
            val legacy = tapIntent("option0", kind = "question") { putExtra(FrozenExtras.EXTRA_CODING_ALERT_OPTION_TEXT, "Yes") }
            assertEquals(ShadeOutcome.RepostedOpen, body().onCodingAction(legacy))
            assertTrue(answers.isEmpty())
            assertEquals("per_1", shade.reposted.first().requestId)
            // Reject does not ask for unlock.
            assertEquals(ShadeOutcome.Answered, body().onCodingAction(tapIntent("deny")))
            // Unlocked on API 30, or any API 31+ (Android asks itself): answered.
            lock.locked = false
            assertEquals(ShadeOutcome.Answered, body().onCodingAction(tapIntent("allow")))
            lock = Lock(sdkInt = 31, locked = true)
            assertEquals(ShadeOutcome.Answered, body().onCodingAction(tapIntent("allow", request = "per_3")))
        }

    @Test
    fun `B-AGENT-031 malformed taps, Open broadcasts and Allow on a question do nothing`() =
        runTest {
            assertEquals(ShadeOutcome.Ignored, body().onCodingAction(Intent("x")))
            assertEquals(ShadeOutcome.Ignored, body().onCodingAction(tapIntent("open")))
            assertEquals(ShadeOutcome.Ignored, body().onCodingAction(tapIntent("allow", kind = "question")))
            assertEquals(ShadeOutcome.Ignored, body().onCodingAction(tapIntent("allow", kind = "complete")))
            assertEquals(ShadeOutcome.Ignored, body().onCodingAction(tapIntent("always")))
            assertTrue(answers.isEmpty())
        }

    @Test
    fun `B-ANDR-040 Stop the reply stops exactly the chat the row named and ignores other actions`() =
        runTest {
            fun status(action: String?, session: String = "ses_7", act: String = FrozenExtras.ACTION_STATUS) =
                Intent(act).apply {
                    data = Uri.parse(FrozenExtras.STOP_REPLY_URI)
                    action?.let { putExtra(FrozenExtras.EXTRA_STATUS_ACTION, it) }
                    putExtra(FrozenExtras.EXTRA_STATUS_SESSION, session)
                    putExtra(FrozenExtras.EXTRA_STATUS_PROFILE, "p1")
                }
            assertEquals(ShadeOutcome.StopSent, body().onStatusAction(status("stop_reply")))
            assertEquals(listOf("ses_7"), stops.map { it.sessionId.raw })
            assertEquals(ShadeOutcome.Ignored, body().onStatusAction(status("open")))
            assertEquals(ShadeOutcome.Ignored, body().onStatusAction(status("stop_reply", session = "")))
            assertEquals(ShadeOutcome.Ignored, body().onStatusAction(status("stop_reply", act = "other")))
            assertEquals(1, stops.size)
        }

    @Test
    fun `S11-9 swiping the status row away ends reply watch for that run only`() =
        runTest {
            val swipe =
                Intent(FrozenExtras.ACTION_STATUS).apply {
                    putExtra(FrozenExtras.EXTRA_STATUS_ACTION, StatusRenderer.DISMISSED_ACTION)
                    putExtra(FrozenExtras.EXTRA_STATUS_SESSION, "ses_7")
                    putExtra(FrozenExtras.EXTRA_STATUS_PROFILE, "p1")
                    putExtra(FrozenExtras.EXTRA_STATUS_TURN, "turn_1")
                }
            assertEquals(ShadeOutcome.RowDismissed, body().onStatusAction(swipe))
            assertEquals(listOf(ReplyRun(ConversationRef(ProfileId("p1"), SessionId("ses_7")), "turn_1")), dismissedRows)
            assertTrue(stops.isEmpty())
        }

    @Test
    fun `B-CHAT-074 notify finding 2 old answer completion preserves the replacement request`() = runTest {
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        val words = NotifyWords(app) { "en" }
        val realShade = AndroidAlertShade(app, words, ChannelRegistry(manager, words).also { it.ensure() })
        val route = AlertRoute.Post(NotifyChannel.CodingAction, AlertGroups.NEEDS_YOU)
        val a = CodingAlert(AlertKind.Permission, "ses_1", "input:p1:ses_1", requestId = "per_1", profileId = "p1", detail = "npm test", ask = PermissionAsk(AskAction.Run, "npm test"))
        for (result in listOf(Unit.ok(), Failed(Failure.NotFound()), Failed(Failure.Network()))) {
            realShade.show(a, route)
            val gate = CompletableDeferred<Outcome<Unit>>()
            val body = NotificationActionBody(realShade, { gate.await() }, { Unit.ok() }, lock)
            val answering = async { body.onCodingAction(tapIntent("deny")) }
            runCurrent()
            realShade.show(a.copy(requestId = "per_2"), route)
            gate.complete(result)
            answering.await()
            val row = manager.activeNotifications.single { it.id == NotificationIds.alert(a.key) }.notification
            val open = shadowOf(row.contentIntent).savedIntent
            assertEquals("per_2", open.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID))
            assertEquals(2, row.actions.size)
        }
    }

    @Test
    fun `B-ANDR-040 notify finding 5 refuses a legacy stop without a profile`() = runTest {
        val intent = Intent(FrozenExtras.ACTION_STATUS).apply {
            putExtra(FrozenExtras.EXTRA_STATUS_ACTION, StatusAction.StopReply.wire)
            putExtra(FrozenExtras.EXTRA_STATUS_SESSION, "ses_7")
        }
        assertEquals(ShadeOutcome.Ignored, body().onStatusAction(intent))
        assertTrue(stops.isEmpty())
    }

    @Test
    fun `B-ANDR-040 notify finding 5 status stop and failed-stop Open retain the exact profile and chat`() = runTest {
        val words = NotifyWords(app) { "en" }
        val renderer = StatusRenderer(app, words)
        for (profile in listOf("p1", "p2")) {
            val target = ConversationRef(ProfileId(profile), SessionId("same_session"))
            val row = renderer.build(StatusContent(StatusKind.Working, "Working", conversation = target,
                actions = listOf(StatusAction.Open, StatusAction.StopReply)), true, null)
            val stop = shadowOf(row.actions[1].actionIntent).savedIntent
            var stopped: ConversationRef? = null
            val handler = NotificationActionBody(shade, { Unit.ok() }, { stopped = it; Failed(Failure.Network()) }, lock)
            assertEquals(ShadeOutcome.StopFailed, handler.onStatusAction(stop))
            assertEquals(target, stopped)
            for (pending in listOf(row.contentIntent, row.actions[0].actionIntent)) {
                val open = shadowOf(pending).savedIntent
                assertEquals(profile, open.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID))
                assertEquals("same_session", open.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID))
            }
        }
    }

    @Test
    fun `B-ANDR-040 notify finding 5 a held status action keeps its target after another profile replaces the row`() = runTest {
        val renderer = StatusRenderer(app, NotifyWords(app) { "en" })
        fun row(profile: String) = renderer.build(StatusContent(StatusKind.Working, "Working",
            conversation = ConversationRef(ProfileId(profile), SessionId("same_session")),
            actions = listOf(StatusAction.Open, StatusAction.StopReply)), true, null)
        val first = row("p1")
        row("p2")
        for (pending in listOf(first.contentIntent, first.actions[0].actionIntent, first.actions[1].actionIntent)) {
            assertEquals("p1", shadowOf(pending).savedIntent.statusConversation()?.profileId?.raw)
        }
    }

    private fun Failed(failure: Failure): Outcome<Unit> = Outcome.Failed(failure)
}
