package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.testing.FakeGatewayEvents
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.core.testing.TestPacer
import io.github.eslamasabry.oc.engine.requests.RequestsEngine
import io.github.eslamasabry.oc.engine.requests.RequestsPort
import io.github.eslamasabry.oc.engine.settings.teaching.StarPromptAnswer
import io.github.eslamasabry.oc.engine.settings.teaching.StarPromptRules
import io.github.eslamasabry.oc.engine.settings.teaching.StarPromptState
import io.github.eslamasabry.oc.engine.settings.teaching.StarPromptUse
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyAskStage
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyAskUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestsModel
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestPlace
import io.github.eslamasabry.oc.feature.chat.starprompt.ChatStarPrompt
import io.github.eslamasabry.oc.feature.chat.starprompt.ReplyAskAnswer
import io.github.eslamasabry.oc.feature.chat.starprompt.ReplyAskUse
import io.github.eslamasabry.oc.feature.chat.starprompt.StarPromptEvent
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one-time first-reply ask: shown at rest on the finished reply's footer, never while a turn runs. */
@OptIn(ExperimentalCoroutinesApi::class)
class StarPromptPresenterTest {
    /** The app's port over the real rules, in memory. */
    private class MemoryStar : ChatStarPrompt {
        var state = StarPromptState()
        val events = mutableListOf<StarPromptEvent>()
        val answers = mutableListOf<ReplyAskAnswer>()
        val uses = mutableListOf<ReplyAskUse>()

        var now = 1L

        override suspend fun promptStarted() {
            state = StarPromptRules.afterPrompt(state, now)
        }

        override suspend fun replyEnded(success: Boolean): Boolean {
            val (next, ask) = StarPromptRules.afterReply(state, success, now)
            state = next
            return ask
        }

        override suspend fun answered(answer: ReplyAskAnswer) {
            answers += answer
            val mapped =
                when (answer) {
                    ReplyAskAnswer.Yes -> StarPromptAnswer.Yes
                    ReplyAskAnswer.No -> StarPromptAnswer.No
                    ReplyAskAnswer.Skip -> StarPromptAnswer.Skip
                }
            if (state.answer == null) state = state.copy(answer = mapped)
        }

        override suspend fun usedFor(use: ReplyAskUse) {
            uses += use
            val mapped =
                when (use) {
                    ReplyAskUse.Everyday -> StarPromptUse.Everyday
                    ReplyAskUse.Code -> StarPromptUse.Code
                    ReplyAskUse.Both -> StarPromptUse.Both
                    ReplyAskUse.Looking -> StarPromptUse.Looking
                }
            if (state.use == null) state = state.copy(use = mapped)
        }

        override fun report(event: StarPromptEvent) {
            events += event
        }
    }

    private class Outside : ChatOutside {
        val ownLinks = mutableListOf<String>()
        val replyLinks = mutableListOf<String>()

        override fun mask(text: String): String = text

        override fun copy(text: String): Boolean = false

        override fun share(text: String): Boolean = false

        override fun checkLink(url: String): LinkCheck = LinkCheck.Refused.also { replyLinks += url }

        override fun openLink(url: String): Boolean = false.also { replyLinks += url }

        override fun openOwnLink(url: String): Boolean = true.also { ownLinks += url }
    }

    private var world: FakeChatWorld = FakeChatWorld()
    private val star = MemoryStar()
    private val outside = Outside()
    private val nav = RecordingNavigator()
    private val busy = RunFacts(busy = true)

    private fun ready() {
        star.state = StarPromptState(prompts = 9, replies = 9, firstMs = 1)
        star.now = 1 + StarPromptRules.ASK_AFTER_MS
    }

    private fun TestScope.chat(): Pair<ChatPresenter, StateFlow<io.github.eslamasabry.oc.feature.chat.presentation.ChatUi>> {
        val p = ChatPresenter(world, nav, Immediate, outside = outside, star = star)
        val flow = p.states(backgroundScope, UnconfinedTestDispatcher(testScheduler), SharingStarted.Eagerly)
        runCurrent()
        return p to flow
    }

    private fun TestScope.running(vararg finished: Int, asking: Int, fail: Boolean = false) {
        val rows = finished.flatMap { turn(it, failed = fail && it == finished.last()) }.toMutableList()
        rows += prompt("u$asking", "ask $asking")
        world.transcript.value = stateOf(*rows.toTypedArray(), run = busy)
        runCurrent()
    }

    private fun TestScope.ended(vararg finished: Int, failedLast: Boolean = false) {
        val rows = finished.flatMap { turn(it, failed = failedLast && it == finished.last()) }
        world.transcript.value = stateOf(*rows.toTypedArray())
        runCurrent()
    }

    @Test
    fun `B-STAR-010 ask shows once after first reply ends, never before, while running, or after Skip`() =
        runTest {
            val (p, ui) = chat()
            assertNull(ui.value.replyAsk, "never at the beginning")
            assertEquals(emptyList(), star.events)
            running(asking = 1)
            assertNull(ui.value.replyAsk, "never while a reply runs")
            assertEquals(emptyList(), star.events)
            ended(1)
            assertEquals(ReplyAskUi(turnId = "u1", stage = ReplyAskStage.Ask, menu = false), ui.value.replyAsk)
            assertEquals(listOf(StarPromptEvent.Shown), star.events)
            p.replyAskSkip()
            runCurrent()
            assertNull(ui.value.replyAsk, "gone after Skip")
            assertEquals(listOf(StarPromptEvent.Shown, StarPromptEvent.Dismissed), star.events)
            running(1, asking = 2)
            ended(1, 2)
            assertNull(ui.value.replyAsk, "never a second time or after Skip")
            assertEquals(listOf(StarPromptEvent.Shown, StarPromptEvent.Dismissed), star.events)
            val (_, secondChat) = chat()
            running(asking = 1)
            ended(1)
            assertNull(secondChat.value.replyAsk, "never in another conversation after Skip")
        }

    @Test
    fun `B-STAR-011 the ask never shows while a turn runs and comes at rest after the finishing reply`() = runTest {
        val (_, ui) = chat()
        ready()
        running(asking = 1)
        assertFalse(ui.value.replyAsk != null, "a turn is running")
        ended(1)
        assertTrue(ui.value.replyAsk != null, "the reply is in and nothing runs")
        assertEquals(listOf(StarPromptEvent.Shown), star.events)
        running(1, asking = 2)
        assertFalse(ui.value.replyAsk != null, "a new turn takes it down")
        ended(1, 2)
        assertFalse(ui.value.replyAsk != null, "never twice")
        assertEquals(listOf(StarPromptEvent.Shown), star.events)
    }

    @Test
    fun `B-STAR-012 a failed reply does not ask`() = runTest {
        val (_, ui) = chat()
        ready()
        running(asking = 1)
        ended(1, failedLast = true)
        assertFalse(ui.value.replyAsk != null)
        assertEquals(emptyList(), star.events)
    }

    @Test
    fun `B-STAR-013 no ask while the link is down`() = runTest {
        val (_, ui) = chat()
        ready()
        running(asking = 1)
        world.link.value = ChatLink.Offline
        ended(1)
        assertFalse(ui.value.replyAsk != null)
        assertEquals(emptyList(), star.events)
    }

    @Test
    fun `B-STAR-014 a steer into a running turn is not a second prompt`() = runTest {
        val (_, ui) = chat()
        running(asking = 1)
        world.transcript.value = stateOf(prompt("u1", "ask 1"), prompt("u1b", "more"), run = busy)
        runCurrent()
        assertFalse(ui.value.replyAsk != null)
        assertEquals(1, star.state.prompts)
    }

    @Test
    fun `B-STAR-017 the repository address is the app's own https address`() {
        assertEquals("https://github.com/Eslamasabry/opencode-mobile-next", StarPromptRules.URL)
    }

    @Test
    fun `B-STAR-028 the ask lands on the footer of the reply that just ended, never while it runs`() = runTest {
        val (_, ui) = chat()
        running(asking = 1)
        assertNull(ui.value.replyAsk, "never while running")
        ended(1)
        assertEquals(ReplyAskUi(turnId = "u1", stage = ReplyAskStage.Ask, menu = false), ui.value.replyAsk)
        assertEquals(listOf(StarPromptEvent.Shown), star.events)
    }

    @Test
    fun `B-STAR-029 Yes stores yes, opens the use question, a pick is stored, Star opens the repository and ends it`() =
        runTest {
            val (p, ui) = chat()
            running(asking = 1)
            ended(1)
            p.replyAskStar()
            assertEquals(emptyList(), outside.ownLinks, "Star while at Ask does nothing")

            p.replyAskYes()
            runCurrent()
            assertEquals(listOf(ReplyAskAnswer.Yes), star.answers)
            assertEquals(ReplyAskUi("u1", ReplyAskStage.Thanks, menu = true), ui.value.replyAsk)

            p.replyAskYes()
            assertEquals(1, star.answers.size, "a second Yes does nothing")

            p.replyAskUse(ReplyAskUse.Code)
            runCurrent()
            assertEquals(listOf(ReplyAskUse.Code), star.uses)
            assertEquals(ReplyAskUi("u1", ReplyAskStage.Thanks, menu = false), ui.value.replyAsk)

            p.replyAskStar()
            runCurrent()
            assertEquals(listOf(StarPromptRules.URL), outside.ownLinks)
            assertEquals(emptyList(), outside.replyLinks, "uses app's own link path")
            assertNull(ui.value.replyAsk)
            assertEquals(listOf(StarPromptEvent.Shown, StarPromptEvent.Tapped), star.events)

            p.replyAskStar()
            assertEquals(1, outside.ownLinks.size, "second Star tap does nothing")
        }

    @Test
    fun `B-STAR-030 No stores no, opens the use question, Report a problem opens the report page and ends it`() =
        runTest {
            val (p, ui) = chat()
            running(asking = 1)
            ended(1)
            p.replyAskReport()
            assertTrue(nav.opened.isEmpty(), "Report while at Ask does nothing")

            p.replyAskNo()
            runCurrent()
            assertEquals(listOf(ReplyAskAnswer.No), star.answers)
            assertEquals(ReplyAskUi("u1", ReplyAskStage.Sorry, menu = true), ui.value.replyAsk)

            p.replyAskUse(ReplyAskUse.Everyday)
            runCurrent()
            assertEquals(listOf(ReplyAskUse.Everyday), star.uses)
            assertEquals(ReplyAskUi("u1", ReplyAskStage.Sorry, menu = false), ui.value.replyAsk)

            p.replyAskReport()
            runCurrent()
            assertEquals(listOf<io.github.eslamasabry.oc.ui.routes.AppRoute>(SettingsRoute.ReportProblem), nav.opened)
            assertNull(ui.value.replyAsk)
            assertEquals(listOf(StarPromptEvent.Shown), star.events)
        }

    @Test
    fun `B-STAR-031 Skip stores skip, reports dismissed and nothing brings the ask back`() = runTest {
        val (p, ui) = chat()
        running(asking = 1)
        ended(1)
        p.replyAskSkip()
        runCurrent()
        assertEquals(listOf(ReplyAskAnswer.Skip), star.answers)
        assertNull(ui.value.replyAsk)
        assertEquals(listOf(StarPromptEvent.Shown, StarPromptEvent.Dismissed), star.events)
        running(1, asking = 2)
        ended(1, 2)
        assertNull(ui.value.replyAsk)
        assertEquals(listOf(StarPromptEvent.Shown, StarPromptEvent.Dismissed), star.events)
    }

    @Test
    fun `B-STAR-032 a new turn takes the ask down and closing the question stores no use`() = runTest {
        val (p, ui) = chat()
        running(asking = 1)
        ended(1)
        p.replyAskYes()
        runCurrent()
        assertEquals(ReplyAskUi("u1", ReplyAskStage.Thanks, menu = true), ui.value.replyAsk)

        p.replyAskMenuClosed()
        runCurrent()
        assertEquals(emptyList(), star.uses, "closing with no pick stores no use")
        assertEquals(ReplyAskUi("u1", ReplyAskStage.Thanks, menu = false), ui.value.replyAsk)

        running(1, asking = 2)
        assertNull(ui.value.replyAsk, "a new turn takes the ask down")
        ended(1, 2)
        assertNull(ui.value.replyAsk, "ask never returns")
    }

    private class FakeRequestsPort : RequestsPort {
        override val events = FakeGatewayEvents()
        override val capabilities = MutableStateFlow(ServerCapabilities())
        var permissions: List<PermissionRequest> = emptyList()

        override suspend fun awaitActionTransport(): Outcome<Unit> = Unit.ok()

        override suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>> =
            permissions.toImmutableList().ok()

        override suspend fun pendingQuestions(): Outcome<ImmutableList<PendingQuestion>> =
            persistentListOf<PendingQuestion>().ok()

        override suspend fun replyPermission(
            session: SessionId,
            request: RequestId,
            reply: PermissionReply,
            note: String?,
        ): Outcome<Unit> = Unit.ok()

        override suspend fun answerQuestion(
            session: SessionId,
            request: RequestId,
            answers: ImmutableList<ImmutableList<String>>,
        ): Outcome<Unit> = Unit.ok()

        override suspend fun rejectQuestion(session: SessionId, request: RequestId): Outcome<Unit> = Unit.ok()
    }

    private class RequestWorld(model: ChatRequestsModel) : FakeChatWorld() {
        override val requests: StateFlow<ChatRequestsModel?> = MutableStateFlow(model)
    }

    private fun permission(id: String): PermissionRequest =
        PermissionRequest(
            RequestId(id),
            Ref.sessionId,
            "edit",
            persistentListOf("a.txt"),
            JsonObject(emptyMap()),
            persistentListOf("*"),
            null,
            true,
            null,
        )

    private fun TestScope.worldWithWaitingCard(id: String): Pair<FakeRequestsPort, ChatRequestsModel> {
        val port = FakeRequestsPort()
        val engine =
            RequestsEngine(
                port,
                backgroundScope,
                TestDispatchers(StandardTestDispatcher(testScheduler)),
                TestClock(),
                TestPacer(),
            )
        val model =
            ChatRequestsModel(
                engine,
                Ref.sessionId,
                port.capabilities,
                MutableStateFlow(RequestPlace("p", "/root/p")),
                backgroundScope,
            )
        runCurrent()
        val perm = permission(id)
        port.permissions = listOf(perm)
        port.events.emit(GatewayEvent.PermissionAsked(perm))
        runCurrent()
        world = RequestWorld(model)
        return port to model
    }

    @Test
    fun `B-STAR-039 a waiting request card does not burn the ask unseen and shows once when cleared`() = runTest {
        val (port, model) = worldWithWaitingCard("perm1")
        assertTrue(model.state.value.card != null, "request card is waiting")
        val (_, ui) = chat()
        running(asking = 1)
        ended(1)
        assertNull(ui.value.replyAsk, "no ask while a request card is waiting")
        assertFalse(star.state.shown, "a blocked first reply must not mark the ask as shown unseen")
        assertEquals(emptyList(), star.events, "Shown is not reported while unseen")

        port.permissions = emptyList()
        port.events.emit(GatewayEvent.PermissionSettled(Ref.sessionId, RequestId("perm1")))
        runCurrent()
        assertNull(model.state.value.card, "request card has cleared")
        assertEquals(ReplyAskUi(turnId = "u1", stage = ReplyAskStage.Ask, menu = false), ui.value.replyAsk)
        assertTrue(star.state.shown, "ask is marked shown once it actually shows")
        assertEquals(listOf(StarPromptEvent.Shown), star.events)

        running(1, asking = 2)
        ended(1, 2)
        assertNull(ui.value.replyAsk, "never shows a second time")
        assertEquals(listOf(StarPromptEvent.Shown), star.events)
    }

    @Test
    fun `B-STAR-040 a waiting card that stays past the first reply lets the next good reply ask once`() = runTest {
        val (port, model) = worldWithWaitingCard("perm2")
        val (_, ui) = chat()
        running(asking = 1)
        ended(1)
        assertNull(ui.value.replyAsk)
        assertFalse(star.state.shown, "must not be used up unseen")

        running(1, asking = 2)
        port.permissions = emptyList()
        port.events.emit(GatewayEvent.PermissionSettled(Ref.sessionId, RequestId("perm2")))
        runCurrent()
        assertNull(model.state.value.card)
        assertNull(ui.value.replyAsk, "never shows while the next turn runs")
        assertFalse(star.state.shown, "still not shown while running")

        ended(1, 2)
        assertEquals(ReplyAskUi(turnId = "u2", stage = ReplyAskStage.Ask, menu = false), ui.value.replyAsk)
        assertTrue(star.state.shown)
        assertEquals(listOf(StarPromptEvent.Shown), star.events)

        running(1, 2, asking = 3)
        ended(1, 2, 3)
        assertNull(ui.value.replyAsk, "never shows a second time")
        assertEquals(listOf(StarPromptEvent.Shown), star.events)
    }
}
