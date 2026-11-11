package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServers
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationsFeed
import io.github.eslamasabry.oc.feature.chats.presentation.HomePreviewRules
import io.github.eslamasabry.oc.feature.chats.presentation.ReturnBriefFeed
import io.github.eslamasabry.oc.feature.chats.presentation.SettingsDoor
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** A1's last-message line: what it says, when it is read, and what a row says without one. */
class HomePreviewsTest {
    private fun text(id: String, text: String, synthetic: Boolean = false, type: PartType = PartType.Text) =
        Part(id, "m-$id", "s", type, type.name.lowercase(), text = text, synthetic = synthetic)

    private fun message(id: String, role: Role, vararg parts: Part) =
        MessageWithParts(MessageInfo(id, "s", role), parts.toList().toImmutableList())

    @Test
    fun `A1-HOME-11 the line is the newest message that says something, on one line and cut at 120`() {
        val page =
            listOf(
                message("1", Role.User, text("a", "Explain   this\nproject")),
                message("2", Role.Assistant, text("b", "Reading lib/ui/kit to map how the parts fit")),
                // The newest message only ran tools and thought: it says nothing, so the one before speaks.
                message(
                    "3",
                    Role.Assistant,
                    text("c", "", type = PartType.Tool),
                    text("d", "thinking hard", type = PartType.Reasoning),
                    text("e", "a note the app added", synthetic = true),
                ),
            )
        assertEquals("Reading lib/ui/kit to map how the parts fit", HomePreviewRules.lastText(page))
        assertEquals("Explain this project", HomePreviewRules.lastText(page.take(1)))
        assertNull(HomePreviewRules.lastText(page.drop(2)))
        val long = HomePreviewRules.oneLine("x".repeat(300))!!
        assertEquals(HomePreviewRules.MAX_CHARS, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun `B-266 the line shows a reply's words, not the marks it was written with`() {
        // The two lines seen raw on the owner's phone.
        assertEquals(
            "Made you something. http://127.0.0.1:8150/ A process is serving it.",
            HomePreviewRules.oneLine("Made you something. **http://127.0.0.1:8150/** A process is serving it."),
        )
        assertEquals(
            "PC Health Check ✅ System is fine",
            HomePreviewRules.oneLine("**PC Health Check** ✅ **System** is fine"),
        )
        // Headings, bullets, code marks and links keep their words.
        assertEquals(
            "Summary Edited src/app/Main.kt See the docs and shot",
            HomePreviewRules.oneLine(
                "## Summary\n- Edited `src/app/Main.kt`\n- See [the docs](https://example.com/a) and ![shot](x.png)"
            ),
        )
        // Code keeps its stars, names keep their underscores, a quote loses only its lead.
        assertEquals(
            "val a = 2 * 3 * 4 quoted _as is_ and snake_case_name stay",
            HomePreviewRules.oneLine("```kotlin\nval a = 2 * 3 * 4\n```\n> quoted _as is_ and snake_case_name stay"),
        )
        // A reply cut mid-emphasis leaves no stray stars.
        assertEquals("an unfinished bold that never clo", HomePreviewRules.oneLine("an unfinished **bold that never clo"))
        assertEquals("old new, 2 * 3 = 6, a_b_c", HomePreviewRules.oneLine("~~old~~ new, 2 * 3 = 6, a_b_c"))
    }

    @Test
    fun `A1-HOME-12 a row is read once, again only when it moved, a running one at most every 15 seconds`() {
        val idle = item("a", at = 1_000)
        assertTrue(HomePreviewRules.needsRead(idle, null, null, nowMs = 0), "never read")
        assertFalse(HomePreviewRules.needsRead(idle, 1_000, 10, nowMs = 20), "read since its last move")
        assertTrue(HomePreviewRules.needsRead(idle.copy(lastActivityMs = 2_000), 1_000, 10, nowMs = 20), "it moved")
        val running = item("r", at = 5_000, status = ChatStatus.Running)
        assertFalse(HomePreviewRules.needsRead(running, 4_000, 10_000, nowMs = 20_000), "running, read 10 s ago")
        assertTrue(HomePreviewRules.needsRead(running, 4_000, 10_000, nowMs = 25_000), "running, read 15 s ago")
        assertFalse(
            HomePreviewRules.needsRead(item("c").copy(agentId = "claude-code"), null, null, nowMs = 0),
            "another protocol's history is not cheap to read: never",
        )
    }

    @Test
    fun `A1-HOME-13 without a line a row says its project and server, never the project its dot already names`() {
        assertEquals("⁨api⁩ · ⁨Laptop⁩", HomePreviewRules.fallback("api", "Laptop", projectShown = false))
        assertEquals("⁨Laptop⁩", HomePreviewRules.fallback("api", "Laptop", projectShown = true))
        assertEquals("⁨api⁩", HomePreviewRules.fallback("api", null, projectShown = false))
        assertNull(HomePreviewRules.fallback("api", null, projectShown = true))
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

    private object Nav : AppNavigator {
        override val top: kotlinx.coroutines.flow.StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    /** A server whose every conversation last said "Last words of <id>"; it counts the reads. */
    private class Talking(private val inner: FakeServerGateway = FakeServerGateway()) : ServerGateway by inner {
        val reads = mutableListOf<String>()

        override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> {
            reads += id.raw
            val words = MessageWithParts(
                MessageInfo("m", id.raw, Role.Assistant),
                persistentListOf(Part("p", "m", id.raw, PartType.Text, "text", text = "Last words of ${id.raw}")),
            )
            return ServerPage(persistentListOf(words), null).ok()
        }
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `A1-HOME-14 only the rows on show are read, once each, and their line reaches the row`() = runTest {
        val feed = Feed()
        val gateway = Talking()
        val servers =
            object : ConversationServers {
                override val servers =
                    MutableStateFlow<ImmutableList<ConversationServer>>(persistentListOf(ConversationServer(P1, "p1", gateway, null)))
            }
        val clock = object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(100_000) }
        val vm = ChatsViewModel(feed, Briefs(), Door, { Instant.fromEpochMilliseconds(0) }, clock, Nav, connections = servers)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val rows = (1..8).map { item("i$it", at = it * 1_000L) } + item("run", at = 500, status = ChatStatus.Running)
        feed.snapshot.value = ChatFeedSnapshot(rows.toImmutableList())
        assertEquals(setOf("run", "i8", "i7", "i6", "i5", "i4"), gateway.reads.toSet(), "Right now and the five Recent rows only")
        assertEquals(6, gateway.reads.size, "one read each")
        assertEquals("Last words of i8", vm.state.value.recent.first().preview)
        assertEquals("Last words of run", vm.state.value.rightNow.single().preview)
        // The same list again: nothing is read twice.
        feed.snapshot.value = ChatFeedSnapshot(rows.reversed().toImmutableList())
        assertEquals(6, gateway.reads.size)
        // A row that moved is read again.
        feed.snapshot.value =
            ChatFeedSnapshot(rows.map { if (it.sessionId.raw == "i8") it.copy(lastActivityMs = 9_500) else it }.toImmutableList())
        assertEquals(listOf("i8"), gateway.reads.drop(6))
    }

    @Test
    fun `A1-HOME-15 without a server to read from, rows keep no line and the list still shows`() = runTest {
        val feed = Feed()
        val clock = object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(100_000) }
        val vm = ChatsViewModel(feed, Briefs(), Door, { Instant.fromEpochMilliseconds(0) }, clock, Nav)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        feed.snapshot.value = ChatFeedSnapshot(persistentListOf(item("a", at = 1_000)))
        assertEquals(listOf("a"), vm.state.value.recent.map { it.ref.sessionId.raw })
        assertNull(vm.state.value.recent.single().preview)
    }
}
