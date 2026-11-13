package io.github.eslamasabry.oc.feature.chats.lamp

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.FeedPermission
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.attention.dock.DockListState.NeedsYou
import io.github.eslamasabry.oc.feature.chats.P1
import io.github.eslamasabry.oc.feature.chats.item
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServers
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationsFeed
import io.github.eslamasabry.oc.feature.chats.presentation.HeldAnswers
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldReply
import io.github.eslamasabry.oc.feature.chats.presentation.HomePermissionAnswers
import io.github.eslamasabry.oc.feature.chats.presentation.HomePermissionReply
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStop
import io.github.eslamasabry.oc.feature.chats.presentation.ReturnBriefFeed
import io.github.eslamasabry.oc.feature.chats.presentation.SettingsDoor
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** One hold for the home's rows and the light's list: held in one place it shows in the other and leaves once. */
@OptIn(ExperimentalCoroutinesApi::class)
class LampSharedHoldTest {
    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private class Answers : HomePermissionAnswers {
        val sent = mutableListOf<String>()

        override suspend fun answer(ref: ConversationRef, requestId: String, allow: Boolean): Outcome<Unit> =
            answer(ref, requestId, if (allow) HomePermissionReply.Once else HomePermissionReply.Reject)

        override suspend fun answer(ref: ConversationRef, requestId: String, reply: HomePermissionReply): Outcome<Unit> {
            sent += "$requestId $reply"
            return Unit.ok()
        }
    }

    private class Feed : ConversationsFeed {
        override val snapshot = MutableStateFlow(ChatFeedSnapshot())

        override suspend fun refresh(): Outcome<Unit> = Unit.ok()
    }

    private class Briefs : ReturnBriefFeed {
        override val brief = MutableStateFlow(ReturnBrief.None)

        override suspend fun dismiss(shown: ReturnBrief): Outcome<Unit> = Unit.ok()
    }

    private object Door : SettingsDoor {
        override val available: Boolean = false

        override fun open() {}
    }

    private val clock = object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(50_000) }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-LAMP-M1 an answer held from the list shows held on the home row and is sent once`() = runTest {
        val feed = Feed().apply { snapshot.value = ChatFeedSnapshot(persistentListOf(item("ask", status = ChatStatus.NeedsYou).copy(permission = FeedPermission("req1", "npm test")))) }
        val gateway = io.github.eslamasabry.oc.core.testing.FakeServerGateway()
        gateway.capabilities.value = ServerCapabilities(persistentPermissionGrants = true)
        val servers = object : ConversationServers { override val servers = MutableStateFlow(persistentListOf(ConversationServer(P1, "p1", gateway, null))) }
        val answers = Answers()
        val bg = backgroundScope + UnconfinedTestDispatcher(testScheduler)
        val shared = HeldAnswers(bg) { ref, id, reply -> bg.launch { answers.answer(ref, id, reply) } }
        val vm = ChatsViewModel(feed, Briefs(), Door, { Instant.fromEpochMilliseconds(0) }, clock, Nav(), permissionAnswers = setOf(answers), connections = servers, sharedHeld = shared)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val row = vm.state.value.rightNow.single()

        val list = MutableStateFlow(lmodel(lrow("ask", NeedsYou, request = perm("req1", "run", "npm test"))))
        val lamp = LampListController(bg, list, Nav(), shared, HeldStops(bg, HomeStop { Unit.ok() }), NoPort, clock, MutableStateFlow(LampPlace()))

        lamp.allowOnce(row.ref, "req1")
        assertEquals(mapOf("req1" to HomeHeldReply.Allowed), vm.state.value.held, "the home row shows the list's hold")
        assertEquals(LampAnswerUi.Held(HomeHeldReply.Allowed), lamp.ui.value.row("ask").request!!.answer)
        // the home row cannot hold it a second time
        vm.answerPermission(row, false)
        assertEquals(mapOf("req1" to HomeHeldReply.Allowed), vm.state.value.held)
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(listOf("req1 Once"), answers.sent)
        assertEquals(emptyMap(), vm.state.value.held)
        assertTrue(lamp.ui.value.row("ask").request!!.answer is LampAnswerUi.AllowDeny)
    }

    @Test
    fun `B-LAMP-M1 an answer held on the home row shows held in the list, and Undo in the list takes it back`() = runTest {
        val feed = Feed().apply { snapshot.value = ChatFeedSnapshot(persistentListOf(item("ask", status = ChatStatus.NeedsYou).copy(permission = FeedPermission("req1", "npm test")))) }
        val gateway = io.github.eslamasabry.oc.core.testing.FakeServerGateway()
        val servers = object : ConversationServers { override val servers = MutableStateFlow(persistentListOf(ConversationServer(P1, "p1", gateway, null))) }
        val answers = Answers()
        val bg = backgroundScope + UnconfinedTestDispatcher(testScheduler)
        val shared = HeldAnswers(bg) { ref, id, reply -> bg.launch { answers.answer(ref, id, reply) } }
        val vm = ChatsViewModel(feed, Briefs(), Door, { Instant.fromEpochMilliseconds(0) }, clock, Nav(), permissionAnswers = setOf(answers), connections = servers, sharedHeld = shared)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val row = vm.state.value.rightNow.single()
        val list = MutableStateFlow(lmodel(lrow("ask", NeedsYou, request = perm("req1", "run", "npm test"))))
        val lamp = LampListController(bg, list, Nav(), shared, HeldStops(bg, HomeStop { Unit.ok() }), NoPort, clock, MutableStateFlow(LampPlace()))

        vm.answerPermission(row, false)
        assertEquals(LampAnswerUi.Held(HomeHeldReply.Rejected), lamp.ui.value.row("ask").request!!.answer)
        lamp.undoAnswer("req1")
        assertEquals(emptyMap(), vm.state.value.held)
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(answers.sent.isEmpty())
    }

    private object NoPort : LampPort {
        override fun pin(ref: ConversationRef) {}

        override fun unpin(ref: ConversationRef) {}

        override fun reorder(from: ConversationRef, to: ConversationRef) {}

        override fun rename(ref: ConversationRef, name: String, onFailed: () -> Unit) {}

        override fun hide(ref: ConversationRef) {}

        override fun restore(ref: ConversationRef) {}

        override fun newConversation() {}

        override fun allConversations() {}

        override fun place(place: LampPlace) {}

        override fun turnOff() {}
    }
}
