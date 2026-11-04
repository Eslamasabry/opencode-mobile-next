package io.github.eslamasabry.oc.android.notifications

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shade's answers to questions and **Stop the reply**: the Flutter `test/background_action_test.dart:221-321`
 * (B-AGENT-032) and the question / stop parts of `test/notification_moments_test.dart` (B-ANDR-038, B-ANDR-040),
 * through [NotificationActionBody] with [GuardedShadeAnswerer] and [RunningOnlyStopper].
 *
 * As amended by CR11-16 / plan 11 §10.4, native alerts never offer a reply or option button (questions only open the
 * app); these taps arrive only from Flutter-era alerts still in the shade after the update.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShadeRepliesParityTest {
    private class Shade : AlertShade {
        val reposted = mutableListOf<ShadeTap>()
        val dismissed = mutableListOf<String>()

        override fun show(alert: CodingAlert, route: AlertRoute.Post): AlertShown = AlertShown.Shown

        override fun showOpenOnly(tap: ShadeTap): AlertShown = AlertShown.Shown.also { reposted += tap }

        override fun dismiss(key: String) {
            dismissed += key
        }

        override fun dismissIfCurrent(tap: ShadeTap): Boolean = true.also { dismiss(tap.key) }

        override fun showOpenOnlyIfCurrent(tap: ShadeTap): AlertShown = showOpenOnly(tap)

        override fun isShowing(key: String): Boolean = false
    }

    private object Unlocked : DeviceLock {
        override val sdkInt: Int = 35

        override fun isLocked(): Boolean = false
    }

    private val shade = Shade()
    private val sent = mutableListOf<ShadeAnswer>()
    private val pending = mutableMapOf<String, QuestionShape>()
    private val running = mutableSetOf<ConversationRef>()
    private val aborted = mutableListOf<ConversationRef>()

    private val body =
        NotificationActionBody(
            shade = shade,
            answerer = GuardedShadeAnswerer({ a -> pending[a.request.raw] }) { a -> sent += a; Unit.ok() },
            stopper = RunningOnlyStopper({ it in running }) { c -> aborted += c; Unit.ok() },
            lock = Unlocked,
        )

    private fun reply(text: String?, request: String = "question-1", decision: ShadeDecision = ShadeDecision.Reply) =
        ShadeTap(AlertKind.Question, "session-1", AlertKeys.input("p1", "session-1"), decision, request, "p1", text)

    private val custom = QuestionShape(listOf(QuestionShape.Prompt(listOf("Ship it", "Wait"), custom = true)))
    private val choiceOnly = QuestionShape(listOf(QuestionShape.Prompt(listOf("Ship it", "Wait"))))

    private fun question(options: List<String>, multiple: Boolean = false) =
        QuestionShape(listOf(QuestionShape.Prompt(options, multiple = multiple)))

    // ---- B-AGENT-032 ----

    @Test
    fun `B-AGENT-032 as amended a question alert never offers quick reply, and a legacy reply answers a single custom question`() = runTest {
        assertEquals(listOf(AlertButton(ShadeDecision.Open, AlertButton.Label.Open)), AlertButtons.plan(CodingAlert(AlertKind.Question, "session-1", "k", requestId = "question-1", profileId = "p1", detail = "Ship?")))
        pending["question-1"] = custom
        assertEquals(ShadeOutcome.Answered, body.onCodingAction(reply("  Ship the fix  ")))
        val answer = sent.single()
        assertEquals("session-1", answer.session.raw)
        assertEquals("question-1", answer.request.raw)
        assertEquals(ShadeReply.Text("Ship the fix"), answer.reply)
        assertEquals(listOf(AlertKeys.input("p1", "session-1")), shade.dismissed)
    }

    @Test
    fun `B-AGENT-032 choice-only questions never take free text and the question stays pending`() = runTest {
        pending["question-1"] = choiceOnly
        assertEquals(ShadeOutcome.RepostedOpen, body.onCodingAction(reply("free text")))
        assertTrue(sent.isEmpty())
        assertTrue(shade.dismissed.isEmpty())
        // The alert is refreshed (Open only) and the question is still there for the full screen.
        assertEquals("question-1", shade.reposted.single().requestId)
        assertTrue("question-1" in pending)
    }

    @Test
    fun `B-AGENT-032 an empty quick reply is refused before any network call`() = runTest {
        pending["question-1"] = custom
        assertEquals(ShadeOutcome.Ignored, body.onCodingAction(reply("   ")))
        assertEquals(ShadeOutcome.Ignored, body.onCodingAction(reply(null)))
        assertTrue(sent.isEmpty())
        assertTrue(shade.dismissed.isEmpty())
    }

    @Test
    fun `B-AGENT-032 a stale request id never resolves a different pending request`() = runTest {
        pending["question-2"] = custom
        assertEquals(ShadeOutcome.RepostedOpen, body.onCodingAction(reply("Ship the fix", request = "question-1")))
        assertTrue(sent.isEmpty())
        assertTrue("question-2" in pending)
    }

    @Test
    fun `B-AGENT-032 a failed reply reports unhandled and keeps the request`() = runTest {
        pending["question-1"] = custom
        val failing =
            NotificationActionBody(
                shade, GuardedShadeAnswerer({ pending[it.request.raw] }) { Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Network()) },
                { Unit.ok() }, Unlocked,
            )
        assertEquals(ShadeOutcome.RepostedOpen, failing.onCodingAction(reply("Ship the fix")))
        assertTrue(shade.dismissed.isEmpty())
        assertTrue("question-1" in pending)
    }

    // ---- B-ANDR-038 ----

    @Test
    fun `B-ANDR-038 as amended three or fewer short options are listed under the question, four just open the app`() {
        assertEquals(listOf("Ship it", "Wait"), choiceOnly.shadeOptions)
        assertEquals("Ship it · Wait", AlertCopy.choices(choiceOnly.shadeOptions))
        assertEquals(emptyList(), question(listOf("a", "b", "c", "d")).shadeOptions)
        val four = CodingAlert(AlertKind.Question, "session-1", "k", profileId = "p1", detail = "Pick", options = question(listOf("a", "b", "c", "d")).shadeOptions)
        assertEquals(AlertCopy.Copy(AlertCopy.Title.Literal, "Pick", ""), AlertCopy.of(four))
        assertEquals(listOf(AlertButton(ShadeDecision.Open, AlertButton.Label.Open)), AlertButtons.plan(four))
    }

    @Test
    fun `B-ANDR-038 an option button answers the question with its own label`() = runTest {
        pending["question-1"] = choiceOnly
        assertEquals(ShadeOutcome.Answered, body.onCodingAction(reply("Ship it", decision = ShadeDecision.Option)))
        assertEquals(ShadeReply.Text("Ship it"), sent.single().reply)
    }

    @Test
    fun `B-ANDR-038 free text is still refused for a choice-only question`() = runTest {
        pending["question-1"] = choiceOnly
        body.onCodingAction(reply("something else entirely"))
        assertTrue(sent.isEmpty())
    }

    // ---- B-ANDR-048 (options half) ----

    @Test
    fun `B-ANDR-048 three or fewer short single-choice options become the shade options`() {
        assertEquals(listOf("Yes", "No"), question(listOf("Yes", "No")).shadeOptions)
        assertEquals(listOf("a", "b", "c"), question(listOf("a", "b", "c")).shadeOptions)
    }

    @Test
    fun `B-ANDR-048 too many, too long, multiple-choice, repeated or none give no options`() {
        assertEquals(emptyList(), question(listOf("a", "b", "c", "d")).shadeOptions)
        assertEquals(emptyList(), question(listOf("Use the long answer option now")).shadeOptions)
        assertEquals(emptyList(), question(listOf("a", "b"), multiple = true).shadeOptions)
        assertEquals(emptyList(), question(listOf("a", "a")).shadeOptions)
        assertEquals(emptyList(), question(emptyList()).shadeOptions)
        assertEquals(emptyList(), QuestionShape(listOf(QuestionShape.Prompt(listOf("a")), QuestionShape.Prompt(listOf("b")))).shadeOptions)
    }

    // ---- B-ANDR-040 ----

    private fun stop(action: String, session: String = "session-1") =
        android.content.Intent(FrozenExtras.ACTION_STATUS).apply {
            putExtra(FrozenExtras.EXTRA_STATUS_ACTION, action)
            putExtra(FrozenExtras.EXTRA_STATUS_SESSION, session)
            putExtra(FrozenExtras.EXTRA_STATUS_PROFILE, "p1")
        }

    private val chat = ConversationRef(ProfileId("p1"), SessionId("session-1"))

    @Test
    fun `B-ANDR-040 Stop the reply stops exactly the chat the status named, while it runs`() = runTest {
        running += chat
        assertEquals(ShadeOutcome.StopSent, body.onStatusAction(stop("stop_reply")))
        assertEquals(listOf(chat), aborted)
    }

    @Test
    fun `B-ANDR-040 a chat that is no longer running is left alone`() = runTest {
        assertEquals(ShadeOutcome.StopFailed, body.onStatusAction(stop("stop_reply")))
        assertTrue(aborted.isEmpty())
    }

    @Test
    fun `B-ANDR-040 any other status action is not handled here`() = runTest {
        running += chat
        assertEquals(ShadeOutcome.Ignored, body.onStatusAction(stop("format_disk")))
        assertTrue(aborted.isEmpty())
    }
}
