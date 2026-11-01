@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.feed

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from test/paseo_chat_feed_source_test.dart and the feed half of test/paseo_title_agree_test.dart. */
class PaseoChatFeedTest {
    private val labels = mapOf("codex" to "Codex", "claude" to "Claude Code")

    private fun port(): FakeFeedPort =
        FakeFeedPort().apply {
            agents += FakeAgent("one", "codex")
            agents += FakeAgent("two", "claude", status = "running")
        }

    private fun TestScope.feed(
        port: FakeFeedPort,
        maxPages: Int = 5,
        refreshTimeoutMs: Long = 45_000,
        persist: (suspend (String) -> Unit)? = null,
        savedTitle: ((String) -> String?)? = null,
        saveTitle: (suspend (String, String) -> Boolean)? = null,
    ) = PaseoChatFeed(
        port,
        backgroundScope,
        maxPages = maxPages,
        refreshTimeoutMs = refreshTimeoutMs,
        persistLastUsedProject = persist,
        savedTitle = savedTitle,
        saveTitle = saveTitle,
        agentLabel = labels::get,
    )

    private fun page(rows: List<Session>, next: String? = null) = ServerPage(rows.toImmutableList(), next)

    @Test
    fun `B-AGENT-153 the scoped feed keeps provider identity without a selected model and reads no timeline`() = runTest {
        val port = port()
        val feed = feed(port)
        assertFalse(feed.snapshot().complete)
        feed.refresh()
        val snapshot = feed.snapshot()
        assertTrue(snapshot.complete)
        assertFalse(snapshot.acrossProjects)
        assertEquals(setOf("codex", "claude"), snapshot.rows.map { it.agentId }.toSet())
        assertEquals(setOf("Codex", "Claude Code"), snapshot.rows.map { it.agentLabel }.toSet())
        assertEquals(2, feed.projectSummaries.single().chatCount)
        assertEquals(1, feed.projectSummaries.single().runningCount)
        assertEquals("IPTV_King", feed.projectSummaries.single().name)
        assertEquals(listOf<String?>(null), port.pageCalls)
    }

    @Test
    fun `B-AGENT-153 agent filtering and clearing keep the filter contract`() = runTest {
        val feed = feed(port())
        feed.refresh()
        val filter = PaseoFeedFilter(agentId = "codex", projectDirectory = FEED_DIR)
        assertEquals(SessionId("one"), feed.snapshot(filter).rows.single().sessionId)
        assertEquals(PaseoFeedFilter(projectDirectory = FEED_DIR), filter.copy(agentId = null))
        assertTrue(feed.snapshot(PaseoFeedFilter(projectDirectory = "/other")).rows.isEmpty())
        assertEquals(listOf(SessionId("two")), feed.snapshot(PaseoFeedFilter(running = true)).rows.map { it.sessionId })
    }

    @Test
    fun `B-AGENT-153 bounded pagination reports partial rows without inventing a global inventory`() = runTest {
        val port = port()
        port.pages = { page(port.agents.map { it.session() }, next = "older") }
        val feed = feed(port, maxPages = 1)
        feed.refresh()
        assertEquals(2, feed.snapshot().rows.size)
        assertFalse(feed.snapshot().complete)
        assertFalse(feed.snapshot().acrossProjects)
        assertEquals(1, port.pageCalls.size)
    }

    @Test
    fun `B-AGENT-153 repeated cursors stop the bounded read`() = runTest {
        val port = port()
        port.pages = { page(port.agents.map { it.session() }, next = "same") }
        val feed = feed(port)
        feed.refresh()
        assertEquals(2, port.pageCalls.size)
        assertFalse(feed.snapshot().complete)
    }

    @Test
    fun `B-AGENT-153 a failed refresh keeps the rows and exposes only partial truth`() = runTest {
        val port = port()
        val feed = feed(port)
        feed.refresh()
        port.pages = { throw PaseoException(PaseoFailureKind.InvalidResponse) }
        feed.refresh()
        assertEquals(2, feed.snapshot().rows.size)
        assertFalse(feed.snapshot().complete)
    }

    @Test
    fun `B-AGENT-153 a coalesced refresh and disposal fence a late response`() = runTest {
        val port = port()
        val answer = CompletableDeferred<ServerPage<Session>>()
        port.pages = { answer.await() }
        val feed = feed(port)
        val first = async { feed.refresh() }
        val second = async { feed.refresh() }
        runCurrent()
        assertEquals(1, port.pageCalls.size)
        assertTrue(feed.snapshot().loading)
        feed.dispose()
        answer.complete(page(port.agents.map { it.session() }))
        first.await()
        second.await()
        assertTrue(feed.snapshot().rows.isEmpty())
        assertFalse(feed.snapshot().complete)
    }

    @Test
    fun `B-AGENT-153 a changed gateway scope hides held rows and prevents retargeting`() = runTest {
        val port = port()
        val feed = feed(port)
        feed.refresh()
        port.directory = "/root/projects/other"
        assertTrue(feed.snapshot().rows.isEmpty())
        assertTrue(feed.projectSummaries.isEmpty())
        val refused = feed.startChat("/root/projects/other")
        assertEquals(RefusalReason.StaleScope, assertIs<Failure.Refused>((refused as Outcome.Failed).failure).reason)
        assertTrue(port.created.isEmpty())
    }

    @Test
    fun `B-AGENT-153 a timeout keeps bounded partial state and rejects a late completion`() = runTest {
        val port = port()
        val answer = CompletableDeferred<ServerPage<Session>>()
        port.pages = { answer.await() }
        val feed = feed(port, refreshTimeoutMs = 15)
        feed.refresh()
        assertEquals(15, testScheduler.currentTime)
        answer.complete(page(port.agents.map { it.session() }))
        runCurrent()
        assertTrue(feed.snapshot().rows.isEmpty())
        assertFalse(feed.snapshot().complete)
    }

    @Test
    fun `B-AGENT-153 unknown agents and other projects are refused before a draft is made`() = runTest {
        val port = port().apply { providerList = listOf("gemini") }
        val feed = feed(port)
        val unknown = feed.startChat(FEED_DIR, agentId = "not-reported") as Outcome.Failed
        assertEquals("hostAgentProviders", assertIs<Failure.Unavailable>(unknown.failure).feature.name)
        for (dir in listOf("/etc", "/tmp/x", "/root/other", "/root", "/root/projects/IPTV_King/../x", "relative")) {
            assertIs<Outcome.Failed>(feed.startChat(dir), dir)
        }
        assertTrue(port.created.isEmpty())
        assertTrue(port.prompts.isEmpty())
    }

    @Test
    fun `B-AGENT-154 a pending permission or question outranks a running row without exposing details`() = runTest {
        val port = port()
        port.permissions += "one"
        val feed = feed(port)
        feed.refresh()
        val first = feed.snapshot().rows.first()
        assertEquals(SessionId("one"), first.sessionId)
        assertEquals(PaseoFeedStatus.NeedsYou, first.status)
        assertEquals(1, feed.projectSummaries.single().needsYouCount)
        // A row has no preview at all: nothing of the request can reach the list.
        assertEquals(PaseoFeedRow(first.sessionId, "Conversation one", FEED_DIR, "IPTV_King", false, PaseoFeedStatus.NeedsYou, 2_000, null, "codex", "Codex"), first)
        port.permissions.clear()
        port.questions += "one"
        assertEquals(PaseoFeedStatus.NeedsYou, feed.snapshot().rows.first().status)
        port.questions.clear()
        assertEquals(SessionId("two"), feed.snapshot().rows.first().sessionId)
    }

    @Test
    fun `B-AGENT-154 attention changes notify while the scope is current`() = runTest {
        val port = port()
        val feed = feed(port)
        var notices = 0
        backgroundScope.launch { feed.changes.collect { notices++ } }
        runCurrent()
        port.attentionChanges.emit(Unit)
        runCurrent()
        assertEquals(1, notices)
        port.directory = "/root/projects/other"
        port.attentionChanges.emit(Unit)
        runCurrent()
        assertEquals(1, notices)
    }

    @Test
    fun `B-AGENT-154 the selected runtime survives an empty draft until its first prompt`() = runTest {
        val port = port()
        val feed = feed(port)
        val id = (feed.startChat(FEED_DIR, agentId = "codex") as Outcome.Ok).value
        assertTrue(port.prompts.isEmpty())
        assertEquals("codex", port.seeded[id.raw])
        assertEquals("codex", feed.snapshot().rows.single { it.sessionId == id }.agentId)
        assertEquals(FEED_DIR, feed.lastUsedProjectDirectory)
    }

    @Test
    fun `B-AGENT-154 a first prompt creates the agent on its runtime and the chat opens by the daemon's id`() = runTest {
        val port = port()
        val feed = feed(port)
        val id = (feed.startChat(FEED_DIR, agentId = "claude", firstPrompt = "hello") as Outcome.Ok).value
        assertEquals(SessionId("real-draft-1"), id)
        assertEquals(Triple("draft-1", "hello", ModelRef("claude", "default")), port.prompts.single())
        // A refused first prompt leaves no draft row behind.
        port.promptFailure = PaseoException(PaseoFailureKind.Unavailable, "mode not offered")
        val refused = feed.startChat(FEED_DIR, agentId = "claude", firstPrompt = "again") as Outcome.Failed
        assertIs<Failure.ComputerDidNotAnswer>(refused.failure)
        assertEquals(listOf("draft-2"), port.discarded)
    }

    @Test
    fun `B-AGENT-154 phone Claude always asks for the asking mode`() {
        val phone = mapOf("claude" to "default")
        assertEquals(PaseoCreateMode.Send("default"), paseoCreateMode("claude", null, phone, listOf("default")))
        // Paseo 0.9.2 on a phone can list no Claude modes: the asking mode is still requested by name.
        assertEquals(PaseoCreateMode.Send("default"), paseoCreateMode("claude", null, phone, emptyList()))
        // A list that offers other modes but not the asking one is refused before any create.
        assertEquals(PaseoCreateMode.Refuse, paseoCreateMode("claude", null, phone, listOf("bypassPermissions")))
        // Elsewhere a chosen mode is sent only when listed.
        assertEquals(PaseoCreateMode.Send("plan"), paseoCreateMode("codex", "plan", phone, listOf("plan")))
        assertEquals(PaseoCreateMode.Omit, paseoCreateMode("codex", "plan", phone, emptyList()))
        assertEquals(PaseoCreateMode.Omit, paseoCreateMode("pi", null, emptyMap(), listOf("default")))
    }

    @Test
    fun `B-AGENT-154 the remember callback is optional and scope-fenced`() = runTest {
        val saved = mutableListOf<String>()
        val port = port()
        val feed = feed(port, persist = { saved += it })
        feed.rememberLastUsedProject("/tmp/transient")
        assertTrue(saved.isEmpty())
        feed.rememberLastUsedProject(FEED_DIR)
        assertEquals(listOf(FEED_DIR), saved)
        assertEquals(FEED_DIR, feed.lastUsedProjectDirectory)
        port.directory = "/root/projects/other"
        assertNull(feed.lastUsedProjectDirectory)
        // Without a callback nothing fails; a callback that throws is a storage failure, never its text.
        val bare = feed(FakeFeedPort())
        assertIs<Outcome.Ok<Unit>>(bare.rememberLastUsedProject(FEED_DIR))
        val broken = feed(FakeFeedPort(), persist = { error("disk said no") })
        val failure = (broken.rememberLastUsedProject(FEED_DIR) as Outcome.Failed).failure
        assertEquals(StorageProblem.WriteRefused, assertIs<Failure.Storage>(failure).problem)
        assertNull(failure.details)
    }

    @Test
    fun `B-AGENT-180 a resumed copy's row and header carry the title the older record had`() = runTest {
        val port = FakeFeedPort()
        port.agents += FakeAgent("old", "kilo", title = "Say pong", updatedMs = 1_000, key = "kilo:ses_1")
        port.agents += FakeAgent("new", "kilo", title = null, updatedMs = 5_000, key = "kilo:ses_1")
        val store = mutableMapOf<String, String>()
        val feed = feed(port, savedTitle = store::get, saveTitle = { key, title -> store[key] = title; true })
        feed.refresh()
        val row = feed.snapshot().rows.single()
        assertEquals(SessionId("new"), row.sessionId)
        assertEquals("Say pong", row.title)
        // The gateway is told, so the conversation's own header says the same as this row.
        assertEquals("Say pong", port.titleHints["new"])
        runCurrent()
        // The older record is archived (housekeeping) only after its title was saved.
        assertEquals("Say pong", store["kilo:ses_1"])
        assertEquals(listOf("old"), port.archived)
    }

    @Test
    fun `B-AGENT-180 an older copy stays when its title cannot be saved or it is busy`() = runTest {
        val port = FakeFeedPort()
        port.agents += FakeAgent("old", "kilo", title = "Say pong", updatedMs = 1_000, key = "k")
        port.agents += FakeAgent("new", "kilo", title = "Fresh", updatedMs = 5_000, key = "k")
        feed(port, saveTitle = { _, _ -> false }).refresh()
        runCurrent()
        assertTrue(port.archived.isEmpty())
        val busy = FakeFeedPort()
        busy.agents += FakeAgent("old", "kilo", title = "Say pong", status = "running", updatedMs = 1_000, key = "k")
        busy.agents += FakeAgent("new", "kilo", title = null, updatedMs = 5_000, key = "k")
        feed(busy).refresh()
        runCurrent()
        assertTrue(busy.archived.isEmpty())
    }

    @Test
    fun `B-AGENT-181 a lone record without a title takes the title saved for it, else the stand-in stays`() = runTest {
        val port = FakeFeedPort()
        port.agents += FakeAgent("new", "kilo", title = null, updatedMs = 5_000, key = "kilo:ses_1")
        val feed = feed(port, savedTitle = mapOf("kilo:ses_1" to "Say pong")::get)
        feed.refresh()
        assertEquals("Say pong", feed.snapshot().rows.single().title)
        assertEquals("Say pong", port.titleHints["new"])
        val plain = FakeFeedPort()
        plain.agents += FakeAgent("new", "kilo", title = null, updatedMs = 5_000, key = "kilo:ses_1")
        val standIn = feed(plain, savedTitle = { null })
        standIn.refresh()
        assertEquals("kilo conversation", standIn.snapshot().rows.single().title)
        assertTrue(plain.titleHints.isEmpty())
    }

    @Test
    fun `B-AGENT-153 rows of another folder or a temporary folder are not this feed's`() = runTest {
        val port = port()
        port.agents += FakeAgent("elsewhere", "codex", directory = "/root/projects/other")
        port.agents += FakeAgent("failed", "codex", failed = true)
        val feed = feed(port)
        feed.refresh()
        assertEquals(setOf("one", "two", "failed"), feed.snapshot().rows.map { it.sessionId.raw }.toSet())
        assertEquals(PaseoFeedStatus.Failed, feed.snapshot().rows.single { it.sessionId.raw == "failed" }.status)
    }
}
