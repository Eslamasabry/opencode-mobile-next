package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationsFeed
import io.github.eslamasabry.oc.feature.chats.presentation.ReturnBriefFeed
import io.github.eslamasabry.oc.feature.chats.presentation.SettingsDoor
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsViewModelTest {
    private class RecordingNavigator : AppNavigator {
        val calls = mutableListOf<String>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            calls += "open $route"
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            calls += "openChat ${ref.sessionId.raw}"
        }

        override fun land() {
            calls += "land"
        }

        override fun replaceRoot(root: AppRoute) {
            calls += "replaceRoot"
        }

        override fun back() {
            calls += "back"
        }
    }

    private class Feed : ConversationsFeed {
        override val snapshot = MutableStateFlow(ChatFeedSnapshot())

        override suspend fun refresh(): Outcome<Unit> = Unit.ok()
    }

    private class Briefs : ReturnBriefFeed {
        val dismissed = mutableListOf<ReturnBrief>()
        override val brief = MutableStateFlow(ReturnBrief.None)

        override suspend fun dismiss(shown: ReturnBrief): Outcome<Unit> {
            dismissed += shown
            return Unit.ok()
        }
    }

    private class Door(override val available: Boolean) : SettingsDoor {
        var opened = 0

        override fun open() {
            opened++
        }
    }

    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(50_000)
        }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `route argument reaches the assisted ViewModel and back goes through the navigator`() {
        val nav = RecordingNavigator()
        val vm = ChatsAllViewModel(ChatsRoute.All("running"), nav)
        assertEquals("running", vm.state.value.filter)
        vm.back()
        assertEquals(listOf("back"), nav.calls)
    }

    @Test
    fun `SCREENS-NAV-27 rows open their chat, New conversation slides up, the gear opens Settings only when it can`() {
        val nav = RecordingNavigator()
        val closed = Door(available = false)
        val vm =
            ChatsViewModel(
                Feed(),
                Briefs(),
                closed,
                { Instant.fromEpochMilliseconds(0) },
                clock,
                nav,
            )
        assertFalse(vm.state.value.settingsAvailable)
        vm.openSettings()
        assertEquals(0, closed.opened, "a closed door is never a dead tap")
        vm.openConversation(
            ConversationRef(P1, io.github.eslamasabry.oc.core.common.ids.SessionId("s1"))
        )
        vm.newConversation()
        assertEquals(listOf("openChat s1", "open ${ChatsRoute.New(null)}"), nav.calls)

        val open = Door(available = true)
        val withDoor =
            ChatsViewModel(Feed(), Briefs(), open, { Instant.fromEpochMilliseconds(0) }, clock, nav)
        assertTrue(withDoor.state.value.settingsAvailable)
        withDoor.openSettings()
        assertEquals(1, open.opened)
    }

    @Test
    fun `SCREENS-NAV-27 filters and search follow the feed, and Continue dismisses exactly the brief shown`() =
        runTest {
            val feed = Feed()
            val briefs = Briefs()
            val vm =
                ChatsViewModel(
                    feed,
                    briefs,
                    Door(false),
                    { Instant.fromEpochMilliseconds(40_000) },
                    clock,
                    RecordingNavigator(),
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            feed.snapshot.value =
                ChatFeedSnapshot(
                    persistentListOf(
                        item("a", at = 45_000, status = ChatStatus.Running),
                        item("b", "/work/web", at = 1),
                    )
                )
            assertEquals(listOf("a"), vm.state.value.today.map { it.ref.sessionId.raw })
            assertEquals(listOf("b"), vm.state.value.earlier.map { it.ref.sessionId.raw })
            vm.toggleRunning()
            assertEquals(emptyList(), vm.state.value.earlier)
            vm.toggleRunning()
            vm.chooseProject("/work/web")
            assertEquals(
                listOf("b"),
                (vm.state.value.today + vm.state.value.earlier).map { it.ref.sessionId.raw },
            )
            vm.chooseProject("/work/web")
            vm.search("zzz")
            assertEquals(
                io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase.NoMatch,
                vm.state.value.phase,
            )
            vm.clearFilters()
            assertEquals(2, (vm.state.value.today + vm.state.value.earlier).size)

            val shown = ReturnBrief.None.copy(hiddenUnreviewed = 1)
            briefs.brief.value = shown
            vm.dismissBrief(shown)
            assertEquals(listOf(shown), briefs.dismissed)
        }

    @Test
    fun `B-CHAT-094 finding 2 Continue uses the rendered token and excludes missing rows`() =
        runTest {
            val feed = Feed()
            val briefs = Briefs()
            val vm =
                ChatsViewModel(
                    feed,
                    briefs,
                    Door(false),
                    { Instant.fromEpochMilliseconds(0) },
                    clock,
                    RecordingNavigator(),
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            feed.snapshot.value = ChatFeedSnapshot(persistentListOf(item("shown"), item("later")))
            fun brief(vararg ids: String) =
                ReturnBrief.build(
                    ids.map {
                        io.github.eslamasabry.oc.core.model.Session(
                            io.github.eslamasabry.oc.core.common.ids.SessionId(it),
                            directory = "/work/api",
                            time = io.github.eslamasabry.oc.core.model.SessionTime(idleMs = 10),
                        )
                    },
                    true,
                    false,
                    { true },
                    { false },
                    { null },
                    scope = BRIEF_SCOPE,
                )
            briefs.brief.value = brief("shown", "missing")
            val rendered = vm.state.value.brief!!
            briefs.brief.value = brief("later")
            vm.dismissBrief(rendered.acknowledgement)
            assertEquals(
                listOf("shown"),
                briefs.dismissed.single().unreviewed.map { it.session.id.raw },
            )
            assertEquals(BRIEF_SCOPE, briefs.dismissed.single().scope)
        }

    @Test
    fun `B-CHAT-093 finding 5 overflow opens only its unacknowledged conversations`() = runTest {
        val feed = Feed()
        val briefs = Briefs()
        val vm =
            ChatsViewModel(
                feed,
                briefs,
                Door(false),
                { Instant.fromEpochMilliseconds(0) },
                clock,
                RecordingNavigator(),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        feed.snapshot.value = ChatFeedSnapshot((1..5).map { item("s$it") }.toImmutableList())
        briefs.brief.value =
            ReturnBrief.build(
                (1..5).map {
                    io.github.eslamasabry.oc.core.model.Session(
                        io.github.eslamasabry.oc.core.common.ids.SessionId("s$it"),
                        directory = "/work/api",
                        time = io.github.eslamasabry.oc.core.model.SessionTime(idleMs = it * 10L),
                    )
                },
                true,
                false,
                { true },
                { false },
                { null },
                scope = BRIEF_SCOPE,
            )
        vm.showBriefOverflow(vm.state.value.brief!!)
        assertEquals(
            listOf("s1", "s2"),
            (vm.state.value.today + vm.state.value.earlier).map { it.ref.sessionId.raw },
        )
        assertTrue(briefs.dismissed.isEmpty())
    }
}
