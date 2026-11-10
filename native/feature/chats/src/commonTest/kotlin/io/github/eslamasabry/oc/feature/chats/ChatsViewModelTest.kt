package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtras
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeSheet
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomePermissionReply
import io.github.eslamasabry.oc.ui.routes.ServersRoute
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

        val routes = mutableListOf<AppRoute>()

        override fun open(route: AppRoute) {
            calls += "open $route"
            routes += route
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
        val vm =
            ChatsAllViewModel(
                ChatsRoute.All("running"),
                nav,
                io.github.eslamasabry.oc.feature.chats.presentation.NoConversationServers(),
                io.github.eslamasabry.oc.core.common.AppCoroutineScope(UnconfinedTestDispatcher()),
            )
        assertTrue(vm.state.value.running)
        vm.back()
        assertEquals(listOf("back"), nav.calls)
    }

    @Test
    fun `SCREENS-NAV-27 rows open their chat, New conversation slides up, the gear opens Settings only when it can`() {
        val nav = RecordingNavigator()
        val closed = Door(available = false)
        // Only a row the feed lists, and that can be reopened, opens (a stale or unresumable ref stays shut).
        val feed = Feed().apply {
            snapshot.value = ChatFeedSnapshot(persistentListOf(item("s1"), item("gone").copy(canReopen = false)))
        }
        val vm =
            ChatsViewModel(
                feed,
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
        vm.openConversation(ConversationRef(P1, io.github.eslamasabry.oc.core.common.ids.SessionId("gone")))
        vm.openConversation(ConversationRef(P1, io.github.eslamasabry.oc.core.common.ids.SessionId("unlisted")))
        vm.newConversation()
        // Each New route carries fresh command and tab ids, so compare what it is, not one instance.
        assertEquals(listOf("openChat s1", "open New"), nav.calls.map { it.substringBefore('(') })
        assertEquals(null, (nav.routes.single() as ChatsRoute.New).profile)

        val open = Door(available = true)
        val withDoor =
            ChatsViewModel(Feed(), Briefs(), open, { Instant.fromEpochMilliseconds(0) }, clock, nav)
        assertTrue(withDoor.state.value.settingsAvailable)
        withDoor.openSettings()
        assertEquals(1, open.opened)
    }

    @Test
    fun `B-MIGR-012 a waiting release puts Update in the bar and it opens the updater`() {
        val nav = RecordingNavigator()
        var opened = 0
        val waiting = MutableStateFlow(true)
        val door =
            object : io.github.eslamasabry.oc.feature.chats.presentation.UpdateDoor {
                override val ready: StateFlow<Boolean> = waiting

                override fun open() {
                    opened++
                }
            }
        val vm =
            ChatsViewModel(Feed(), Briefs(), Door(available = true), { Instant.fromEpochMilliseconds(0) }, clock, nav, door)
        assertTrue(vm.state.value.updateReady)
        vm.openUpdate()
        assertEquals(1, opened)
        val none = ChatsViewModel(Feed(), Briefs(), Door(available = true), { Instant.fromEpochMilliseconds(0) }, clock, nav)
        assertFalse(none.state.value.updateReady)
    }

    @Test
    fun `JANK-HOME-1 a feed change that moves no row does not emit a new home`() =
        runTest {
            val feed = Feed()
            var now = 50_000L
            val ticking =
                object : Clock {
                    override fun now(): Instant = Instant.fromEpochMilliseconds(now++)
                }
            feed.snapshot.value = ChatFeedSnapshot(persistentListOf(item("s1")))
            val vm =
                ChatsViewModel(feed, Briefs(), Door(false), { Instant.fromEpochMilliseconds(0) }, ticking, RecordingNavigator())
            val seen = mutableListOf<io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it } }
            val before = seen.size
            // Same rows, a different snapshot (the flag the list does not draw): the screen is rebuilt on a later clock.
            feed.snapshot.value = feed.snapshot.value.copy(revision = feed.snapshot.value.revision + 1)
            assertEquals(before, seen.size, "an equal home is not announced again")
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
            assertEquals(listOf("a"), vm.state.value.rightNow.map { it.ref.sessionId.raw })
            assertEquals(listOf("b"), vm.state.value.recent.map { it.ref.sessionId.raw })
            vm.toggleRunning()
            assertEquals(emptyList(), vm.state.value.recent)
            vm.toggleRunning()
            vm.chooseProject("/work/web")
            assertEquals(
                listOf("b"),
                (vm.state.value.rightNow + vm.state.value.recent).map { it.ref.sessionId.raw },
            )
            vm.chooseProject("/work/web")
            vm.search("zzz")
            assertEquals(
                io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase.NoMatch,
                vm.state.value.phase,
            )
            vm.clearFilters()
            assertEquals(2, (vm.state.value.rightNow + vm.state.value.recent).size)

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
            (vm.state.value.rightNow + vm.state.value.recent).map { it.ref.sessionId.raw },
        )
        assertTrue(briefs.dismissed.isEmpty())
    }
    /** The home's connection owner as the switcher sees it: saved sources, and every switch it was asked for. */
    private class Home(sources: List<ChatsListSourceUi>) : ChatsHomeExtras {
        val switched = mutableListOf<String>()
        var answer: Outcome<Unit> = Unit.ok()
        override val snapshot = MutableStateFlow(ChatsHomeExtraState(sources = sources.toImmutableList()))

        override suspend fun switchTo(profile: ProfileId): Outcome<Unit> {
            switched += profile.raw
            return answer
        }

        val disconnected = mutableListOf<String>()
        val restarted = mutableListOf<String>()

        override suspend fun restartPhoneServer(profile: ProfileId): Outcome<Unit> {
            restarted += profile.raw
            return answer
        }

        override suspend fun disconnect(profile: ProfileId): Outcome<Unit> {
            disconnected += profile.raw
            return answer
        }

        override suspend fun showSource(profile: ProfileId, shown: Boolean): Outcome<Unit> = Unit.ok()

        override suspend fun retrySource(profile: ProfileId): Outcome<Unit> = Unit.ok()

        override suspend fun retryOpening(): Outcome<Unit> = Unit.ok()

        override suspend fun setWatched(profile: ProfileId, enabled: Boolean): Outcome<Unit> = Unit.ok()

        override suspend fun refreshWatched(): Outcome<Unit> = Unit.ok()

        override suspend fun openWatched(key: String): Outcome<ConversationRef> =
            Outcome.Failed(Failure.Unavailable(FeatureId("test")))
    }

    private fun source(id: String, main: Boolean = false) =
        ChatsListSourceUi(ProfileId(id), id, shown = true, main = main, onThisPhone = false, unreachable = false)

    @Test
    fun `PARITY-B-235 the pill opens the switcher sheet and a tap there switches servers and closes it`() = runTest {
        val nav = RecordingNavigator()
        val home = Home(listOf(source("main", main = true), source("work")))
        val vm =
            ChatsViewModel(Feed(), Briefs(), Door(false), { Instant.fromEpochMilliseconds(0) }, clock, nav, home = home)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.openServerSwitcher()
        assertEquals(ChatsHomeSheet.Servers, vm.state.value.sheet)
        assertEquals(emptyList(), nav.routes, "the pill no longer leaves for the Servers page")
        assertEquals(listOf("main", "work"), vm.state.value.switcher.servers.map { it.profile.raw })
        // The server already in use only closes the sheet: reconnecting would drop live state.
        vm.switchServer(ProfileId("main"))
        assertEquals(emptyList(), home.switched)
        assertEquals(null, vm.state.value.sheet)
        vm.openServerSwitcher()
        vm.switchServer(ProfileId("work"))
        assertEquals(listOf("work"), home.switched)
        assertEquals(null, vm.state.value.sheet)
        assertEquals(null, vm.state.value.switcher.switching)
    }

    @Test
    fun `PARITY-B-235 a switch that fails keeps the sheet open and says why, and Add a server opens the form`() = runTest {
        val nav = RecordingNavigator()
        val home = Home(listOf(source("main", main = true), source("work")))
        val refused = Failure.Refused(io.github.eslamasabry.oc.core.common.RefusalReason.StaleScope)
        home.answer = Outcome.Failed(refused)
        val vm =
            ChatsViewModel(Feed(), Briefs(), Door(false), { Instant.fromEpochMilliseconds(0) }, clock, nav, home = home)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.openServerSwitcher()
        vm.switchServer(ProfileId("work"))
        assertEquals(ChatsHomeSheet.Servers, vm.state.value.sheet)
        assertEquals(refused, vm.state.value.switcher.failure)
        // Opening the sheet again starts clean.
        vm.openServerSwitcher()
        assertEquals(null, vm.state.value.switcher.failure)
        vm.addServer()
        assertEquals(listOf<AppRoute>(ServersRoute.Edit()), nav.routes)
        assertEquals(null, vm.state.value.sheet)
    }

    @Test
    fun `B-OPENING-RESTART the opening card restarts only this phone's own server`() = runTest {
        val home = Home(listOf(source("main", main = true)))
        val vm =
            ChatsViewModel(Feed(), Briefs(), Door(false), { Instant.fromEpochMilliseconds(0) }, clock, RecordingNavigator(), home = home)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val failed = ChatsOpeningUi(ChatsOpeningPhase.Failed, "Phone", profile = ProfileId("main"), onThisPhone = false)
        home.snapshot.value = home.snapshot.value.copy(opening = failed)
        vm.restartOpeningPhoneServer()
        assertEquals(emptyList(), home.restarted, "a computer's server is never restarted from the phone")
        home.snapshot.value = home.snapshot.value.copy(opening = failed.copy(onThisPhone = true))
        vm.restartOpeningPhoneServer()
        assertEquals(listOf("main"), home.restarted)
    }

    @Test
    fun `B-SWITCH-DISCONNECT Disconnect from the server in use closes it and the sheet, a refusal keeps the sheet and says why`() = runTest {
        val home = Home(listOf(source("main", main = true), source("work")))
        val vm =
            ChatsViewModel(Feed(), Briefs(), Door(false), { Instant.fromEpochMilliseconds(0) }, clock, RecordingNavigator(), home = home)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.openServerSwitcher()
        vm.disconnectServer(ProfileId("main"))
        assertEquals(listOf("main"), home.disconnected)
        assertEquals(emptyList(), home.switched, "disconnecting never switches servers")
        assertEquals(null, vm.state.value.sheet)
        assertEquals(null, vm.state.value.switcher.switching)
        val refused = Failure.Refused(io.github.eslamasabry.oc.core.common.RefusalReason.StaleScope)
        home.answer = Outcome.Failed(refused)
        vm.openServerSwitcher()
        vm.disconnectServer(ProfileId("main"))
        assertEquals(ChatsHomeSheet.Servers, vm.state.value.sheet)
        assertEquals(refused, vm.state.value.switcher.failure)
    }

    @Test
    fun `PARITY-B-217 a pull reads the list again once, and says so until the read is back`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var reads = 0
        val feed =
            object : ConversationsFeed {
                override val snapshot = MutableStateFlow(ChatFeedSnapshot(persistentListOf(item("s1"))))

                override suspend fun refresh(): Outcome<Unit> {
                    reads++
                    gate.await()
                    return Unit.ok()
                }
            }
        val vm = ChatsViewModel(feed, Briefs(), Door(false), { Instant.fromEpochMilliseconds(0) }, clock, RecordingNavigator())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.refreshList()
        assertTrue(vm.state.value.refreshing)
        // A second pull while the first reads is the same read.
        vm.refreshList()
        assertEquals(1, reads)
        gate.complete(Unit)
        assertFalse(vm.state.value.refreshing)
        vm.refreshList()
        assertEquals(2, reads)
    }

    private class Answers : io.github.eslamasabry.oc.feature.chats.presentation.HomePermissionAnswers {
        val sent = mutableListOf<String>()

        override suspend fun answer(ref: ConversationRef, requestId: String, allow: Boolean): Outcome<Unit> =
            answer(ref, requestId, if (allow) HomePermissionReply.Once else HomePermissionReply.Reject)

        override suspend fun answer(ref: ConversationRef, requestId: String, reply: HomePermissionReply): Outcome<Unit> {
            sent += "$requestId $reply"
            return Unit.ok()
        }
    }

    private fun asking(grants: Boolean): Pair<ChatsViewModel, Answers> {
        val feed = Feed()
        feed.snapshot.value =
            ChatFeedSnapshot(
                persistentListOf(
                    item("ask", status = ChatStatus.NeedsYou)
                        .copy(permission = io.github.eslamasabry.oc.engine.agents.feed.FeedPermission("req1", "npm test"))
                )
            )
        val gateway = io.github.eslamasabry.oc.core.testing.FakeServerGateway()
        gateway.capabilities.value =
            io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities(persistentPermissionGrants = grants)
        val servers =
            object : io.github.eslamasabry.oc.feature.chats.presentation.ConversationServers {
                override val servers =
                    MutableStateFlow(
                        persistentListOf(io.github.eslamasabry.oc.feature.chats.presentation.ConversationServer(P1, "p1", gateway, null))
                    )
            }
        val answers = Answers()
        val vm =
            ChatsViewModel(
                feed,
                Briefs(),
                Door(false),
                { Instant.fromEpochMilliseconds(0) },
                clock,
                RecordingNavigator(),
                permissionAnswers = setOf(answers),
                connections = servers,
            )
        return vm to answers
    }

    @Test
    fun `PARITY-B-224 a row's waiting permission offers Always allow, which asks first and then answers Always`() = runTest {
        val (vm, answers) = asking(grants = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val row = vm.state.value.rightNow.single()
        assertTrue(row.key in vm.state.value.alwaysAllowable)
        vm.askAlwaysAllow(row)
        assertEquals(row, vm.state.value.confirmAlways)
        assertEquals(emptyList(), answers.sent, "nothing is granted before the confirm")
        vm.cancelAlwaysAllow()
        assertEquals(null, vm.state.value.confirmAlways)
        assertEquals(emptyList(), answers.sent)
        vm.askAlwaysAllow(row)
        vm.confirmAlwaysAllow()
        assertEquals(listOf("req1 Always"), answers.sent)
        assertEquals(null, vm.state.value.confirmAlways)
        // Allow once and Reject still answer as they did.
        vm.answerPermission(row, false)
        assertEquals(listOf("req1 Always", "req1 Reject"), answers.sent)
    }

    @Test
    fun `PARITY-B-224 a server that keeps no grants offers no Always allow`() = runTest {
        val (vm, answers) = asking(grants = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val row = vm.state.value.rightNow.single()
        assertFalse(row.key in vm.state.value.alwaysAllowable)
        vm.askAlwaysAllow(row)
        vm.confirmAlwaysAllow()
        assertEquals(null, vm.state.value.confirmAlways)
        assertEquals(emptyList(), answers.sent)
    }

    @Test
    fun `B-182 Open a project from the project chip opens the real flow, not a path box`() {
        val nav = RecordingNavigator()
        val vm = ChatsViewModel(Feed(), Briefs(), Door(available = false), { Instant.fromEpochMilliseconds(0) }, clock, nav)
        vm.showProjectChoices()
        vm.openFilterProject()
        assertEquals(listOf<AppRoute>(io.github.eslamasabry.oc.ui.routes.ProjectsRoute.Open), nav.routes)
    }
}
