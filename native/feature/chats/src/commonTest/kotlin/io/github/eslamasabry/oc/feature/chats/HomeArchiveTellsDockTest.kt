package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServers
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationsFeed
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The home tells the app, by conversation, when an archive (or delete) it sent was taken, so the edge dock drops that
 * ring: only after the Undo window, never for an undone or refused one.
 */
class HomeArchiveTellsDockTest {
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

    private object Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private class Ops(var refuse: Boolean = false) : FakeOperationsGateway() {
        val archived = mutableListOf<String>()

        override suspend fun archiveSession(id: SessionId): Outcome<Unit> {
            archived += id.raw
            return if (refuse) Failure.Server(500).failed() else Unit.ok()
        }
    }

    private class Servers(server: ConversationServer) : ConversationServers {
        val left = mutableListOf<ConversationRef>()
        override val servers = MutableStateFlow<ImmutableList<ConversationServer>>(persistentListOf(server))

        override fun left(ref: ConversationRef) {
            left += ref
        }
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun servers(ops: Ops): Servers {
        val gateway = FakeServerGateway()
        gateway.capabilities.value = ServerCapabilities(sessionArchive = true)
        return Servers(ConversationServer(P1, "p1", gateway, ops))
    }

    private fun vm(servers: Servers): ChatsViewModel {
        val clock = object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(100_000) }
        return ChatsViewModel(Feed().also { it.snapshot.value = ChatFeedSnapshot(persistentListOf(item("a"))) }, Briefs(), Door, { Instant.fromEpochMilliseconds(0) }, clock, Nav, connections = servers)
    }

    @Test
    fun `an archive the server took is told by conversation when the Undo window ends`() = runTest {
        val ops = Ops()
        val servers = servers(ops)
        val vm = vm(servers)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val row = vm.state.value.recent.single()
        vm.archive(row)
        assertTrue(servers.left.isEmpty(), "nothing is told inside the Undo window")
        vm.commitArchive()
        assertEquals(listOf("a"), ops.archived)
        assertEquals(listOf(row.ref), servers.left)
    }

    @Test
    fun `an undone or refused archive tells nothing`() = runTest {
        val ops = Ops()
        val servers = servers(ops)
        val vm = vm(servers)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val row = vm.state.value.recent.single()
        vm.archive(row)
        vm.undoArchive()
        vm.commitArchive()
        assertTrue(ops.archived.isEmpty())
        assertTrue(servers.left.isEmpty(), "Undo keeps the ring")
        ops.refuse = true
        vm.archive(vm.state.value.recent.single())
        vm.commitArchive()
        assertEquals(listOf("a"), ops.archived)
        assertTrue(servers.left.isEmpty(), "a refused archive keeps the ring (the row comes back)")
    }
}
