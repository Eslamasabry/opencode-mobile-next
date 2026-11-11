package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeed
import io.github.eslamasabry.oc.engine.agents.feed.OpenCodeChatFeedSource
import io.github.eslamasabry.oc.engine.agents.feed.OpenCodeListing
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.AllConversationsRules
import io.github.eslamasabry.oc.feature.chats.presentation.AllModel
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeConnectionState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeConnectionUi
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationAttempt
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationHost
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ServerName
import io.github.eslamasabry.oc.feature.chats.presentation.ServerSwitcherRules
import io.github.eslamasabry.oc.feature.chats.presentation.SourcePages
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * ONE-PHONE (owner 2026-11-11: "This phone" and "Phone agents" side by side in Servers). As in Flutter
 * (side_connections.dart:61-100, phone_agents.dart:1024-1071), the phone agents are part of This phone: their
 * conversations stand beside the phone's own in one list, and they are never a server to pick.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnePhoneTest {
    private val phone = ProfileId("phone")
    private val helper = ProfileId("phone-agents")

    private fun s(id: String, dir: String, at: Long) =
        Session(SessionId(id), title = "Chat $id", directory = dir, time = SessionTime(createdMs = 1, updatedMs = at))

    private fun source(id: ProfileId, name: String, main: Boolean = false, agents: Boolean = false, down: Boolean = false) =
        ChatsListSourceUi(id, name, shown = true, main = main, onThisPhone = true, unreachable = down, agents = agents)

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `ONE-PHONE the Servers sheet and the pill show the phone once and never its agents`() {
        val laptop = ProfileId("laptop")
        val extra =
            ChatsHomeExtraState(
                sources =
                    persistentListOf(
                        // Listed first and even "not answering": still never a server.
                        source(helper, "Claude Code", agents = true, down = true),
                        source(phone, "This phone", main = true),
                        ChatsListSourceUi(laptop, "Laptop", shown = false, main = false, onThisPhone = false, unreachable = false),
                    )
            )
        assertEquals(listOf(phone, laptop), ServerSwitcherRules.rows(extra, emptyList()).map { it.profile })
        assertEquals(HomeConnectionUi("This phone", HomeConnectionState.Connected), HomeConnectionUi.of(extra))
        // With no main known yet, the pill still never names the agents.
        val unsure = extra.copy(sources = persistentListOf(source(helper, "Claude Code", agents = true), source(phone, "This phone")))
        assertEquals("This phone", HomeConnectionUi.of(unsure)?.server)
    }

    @Test
    fun `ONE-PHONE the phone's conversations and its agents' make one list, newest first, each with its agent`() =
        runTest(UnconfinedTestDispatcher()) {
            val own =
                MutableStateFlow(
                    OpenCodeListing(
                        sessions = listOf(s("oc-old", "/root/projects/app", 1_000), s("oc-run", "/root/projects/web", 3_000)),
                        statuses = mapOf(SessionId("oc-run") to SessionStatus.Busy),
                    )
                )
            val agents =
                MutableStateFlow(
                    OpenCodeListing(
                        sessions = listOf(s("cc-new", "/root/projects/app", 5_000), s("cx-mid", "/root/projects/web", 2_000)),
                        statuses = mapOf(SessionId("cx-mid") to SessionStatus.Busy),
                        // The helper reads folder by folder; the app marks it as reaching every project (PhoneAgentRules).
                        acrossProjects = true,
                    )
                )
            val feed =
                ChatFeed(
                    listOf(
                        OpenCodeChatFeedSource(phone, "This phone", own),
                        OpenCodeChatFeedSource(
                            helper,
                            "Phone agents",
                            agents,
                            agentOf = { if (it.id.raw.startsWith("cc")) "claude" to "Claude Code" else "codex" to "Codex" },
                        ),
                    ),
                    backgroundScope,
                )
            fun ui(filters: ChatsFilters = ChatsFilters()) =
                ChatsState.build(feed.snapshot.value, ReturnBrief.None, filters, 0L, settingsAvailable = true)

            val all = ui()
            assertEquals(listOf("oc-run", "cx-mid"), all.rightNow.map { it.ref.sessionId.raw }, "working, newest first")
            assertEquals(listOf("cc-new", "oc-old"), all.recent.map { it.ref.sessionId.raw }, "the rest, newest first")
            assertEquals(listOf("OpenCode", "Codex"), all.rightNow.map { it.agentName })
            assertEquals(listOf("Claude Code", "OpenCode"), all.recent.map { it.agentName })
            assertEquals(null, all.onlyProject, "the agents' list never makes the phone's list single-project")
            assertEquals(listOf(helper, phone), all.recent.map { it.ref.profileId }, "each row opens on its own connection")

            // The project and Running filters cover both.
            val app = ui(ChatsFilters(projectDirectory = "/root/projects/app"))
            assertEquals(listOf("cc-new", "oc-old"), (app.rightNow + app.recent).map { it.ref.sessionId.raw })
            val running = ui(ChatsFilters(running = true))
            assertEquals(listOf("oc-run", "cx-mid"), (running.rightNow + running.recent).map { it.ref.sessionId.raw })

            // The agents still loading, or down: the phone's own rows stay.
            agents.value = OpenCodeListing(loading = true)
            assertEquals(listOf("oc-run", "oc-old"), ui().let { (it.rightNow + it.recent).map { r -> r.ref.sessionId.raw } })
            agents.value = OpenCodeListing(failed = true)
            assertEquals(listOf("oc-run", "oc-old"), ui().let { (it.rightNow + it.recent).map { r -> r.ref.sessionId.raw } })
        }

    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private inner class Host : NewConversationHost {
        override val servers =
            MutableStateFlow(
                listOf(
                    NewConversationServer(ProfileId("laptop"), "Laptop", connected = true),
                    NewConversationServer(phone, "This phone", connected = true, standsFor = setOf(helper)),
                )
            )

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(NewConversationSetup("/root/projects/app", emptyList(), emptyList(), emptyList()))

        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> = Outcome.Ok("")

        override suspend fun saveDraft(profile: ProfileId, directory: String?, text: String): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun start(
            profile: ProfileId,
            directory: String,
            text: String,
            model: ModelRef?,
            agent: String?,
            separateCopy: Boolean,
            previous: NewConversationAttempt?,
            onStarted: (ConversationRef) -> Unit,
        ): NewConversationAttempt = NewConversationAttempt(directory, failure = Failure.Unavailable(FeatureId("test")))

        override suspend fun imports(profile: ProfileId, directory: String): Outcome<ImportableConversations> =
            Outcome.Failed(Failure.Unavailable(FeatureId("test")))

        override suspend fun import(
            profile: ProfileId,
            directory: String,
            conversation: ImportableConversation,
        ): Outcome<ConversationRef> = Outcome.Failed(Failure.Unavailable(FeatureId("test")))
    }

    @Test
    fun `ONE-PHONE a new conversation asked for on the agents' connection starts on This phone`() {
        // "Start new" from an agent's old conversation names the helper's profile; it is not a server here.
        val vm = NewConversationViewModel(ChatsRoute.New(helper, phoneAgent = "claude"), Host(), Nav(), Clock.System, ListenedReports())
        assertEquals(phone, vm.state.value.profile)
        assertTrue(vm.state.value.servers.none { it.profile == helper })
    }

    @Test
    fun `ONE-PHONE All conversations reads the agents' rows as This phone's and names a server only among several`() {
        // The helper carries the phone's name (PhoneAgentRules.helperNames): the phone and its agents are one place.
        val pages =
            mapOf(
                phone to SourcePages(sessions = listOf(s("oc", "/root/projects/app", 2_000)), loaded = true),
                helper to SourcePages(sessions = listOf(s("cc", "/root/projects/app", 3_000)), loaded = true),
            )
        val alone =
            AllConversationsRules.build(
                AllModel(servers = listOf(ServerName(phone, "This phone"), ServerName(helper, "This phone")), known = true, sources = pages)
            )
        assertEquals(listOf("cc", "oc"), alone.rows.map { it.ref.sessionId.raw })
        assertEquals(listOf(null, null), alone.rows.map { it.server }, "one place: no server named on its rows")
        val laptop = ProfileId("laptop")
        val several =
            AllConversationsRules.build(
                AllModel(
                    servers = listOf(ServerName(phone, "This phone"), ServerName(helper, "This phone"), ServerName(laptop, "Laptop")),
                    known = true,
                    sources = pages + (laptop to SourcePages(sessions = listOf(s("lp", "/srv/site", 1_000)), loaded = true)),
                )
            )
        assertEquals(listOf("This phone", "This phone", "Laptop"), several.rows.map { it.server })
    }
}
