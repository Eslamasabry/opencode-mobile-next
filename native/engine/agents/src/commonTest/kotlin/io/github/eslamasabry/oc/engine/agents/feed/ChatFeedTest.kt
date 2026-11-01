package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.engine.agents.AgentId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ports of test/chat_feed_test.dart (engine-side parts). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatFeedTest {
    @Test
    fun `B-CHAT-013 temporary folders are never projects`() {
        for (dir in listOf(
            "/tmp", "/tmp/", "tmp", "/tmp/scratch", "/var/tmp/x", "/private/tmp", "/var/folders/ab/cd", "/data/local/tmp",
            "C:\\Windows\\Temp", "C:/Users/me/AppData/Local/Temp/x", "/", "global", "", null,
        )) {
            assertTrue(FeedFolders.isTemporary(dir), "$dir")
        }
        for (dir in listOf("/work/app", "/home/me/tmpl", "/root/projects/tmp2")) assertFalse(FeedFolders.isTemporary(dir), dir)
    }

    @Test
    fun `B-CHAT-013 a temporary folder's chat is listed but its folder is never offered as a project`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeFeedSource()
        source.state.value = SourceFeed(
            items = listOf(row("t", directory = "/tmp/scratch"), row("h", directory = "/home/user"), row("r", directory = "/work/app"))
                .let { kotlinx.collections.immutable.persistentListOf<ChatFeedItem>().addAll(it) },
            projects = kotlinx.collections.immutable.persistentListOf(
                ProjectSummary("/tmp/scratch", "scratch", false, 1, 0, 0, 5),
                ProjectSummary("/work/app", "app", true, 1, 0, 0, 1),
            ),
        )
        val merged = MergedChatFeed(listOf(NamedChatFeedSource("opencode", "This phone", source)), backgroundScope)
        assertEquals(listOf("/work/app"), merged.projectSummaries().map { it.directory })
        assertEquals(setOf("t", "h"), merged.snapshot(ChatFeedFilter(otherFolders = true)).items.map { it.sessionId }.toSet())
        assertEquals(listOf("r"), merged.snapshot(ChatFeedFilter(projectDirectory = "/work/app")).items.map { it.sessionId })
        assertTrue(FeedFolders.isProtected("/home/user"))
        assertFalse(ChatFeedFilter(otherFolders = true) == ChatFeedFilter.All)
    }

    @Test
    fun `B-CHAT-014 notifications that change nothing do not change the revision`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeFeedSource()
        source.rows(row("a", time = 1), row("b", time = 2))
        val merged = MergedChatFeed(listOf(NamedChatFeedSource("opencode", "This phone", source)), backgroundScope)
        val revision = merged.revision.value
        repeat(20) {
            source.rows(row("a", time = 1), row("b", time = 2)) // same visible content, a new value object
            merged.snapshot()
            merged.snapshot(ChatFeedFilter(running = true))
            merged.projectSummaries()
        }
        assertEquals(revision, merged.revision.value)
    }

    @Test
    fun `B-CHAT-014 a new session, a title, a time and a status show at once`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeFeedSource()
        source.rows(row("a", time = 1), row("b", time = 2))
        val merged = MergedChatFeed(listOf(NamedChatFeedSource("opencode", "This phone", source)), backgroundScope)
        assertEquals(listOf("b", "a"), merged.items.value.map { it.sessionId })
        var before = merged.revision.value

        source.rows(row("a", time = 1), row("b", time = 2), row("c", time = 3))
        assertEquals(listOf("c", "b", "a"), merged.items.value.map { it.sessionId })
        assertTrue(merged.revision.value > before)
        before = merged.revision.value

        source.rows(row("a", time = 1, title = "Renamed"), row("b", time = 2), row("c", time = 3))
        assertEquals("Renamed", merged.items.value.first { it.sessionId == "a" }.title)
        assertTrue(merged.revision.value > before)

        source.rows(row("a", time = 9, title = "Renamed"), row("b", time = 2), row("c", time = 3))
        assertEquals("a", merged.items.value.first().sessionId)

        source.rows(row("a", time = 9, title = "Renamed"), row("b", time = 2), row("c", time = 3, status = ChatStatus.Running))
        assertEquals("c", merged.items.value.first().sessionId)
        assertEquals(ChatStatus.Running, merged.items.value.first().status)
    }

    @Test
    fun `B-CHAT-014 identity is the Dart jsonEncode of source, session and folder`() {
        val item = row("ses_1").copy(sourceId = "paseo:/root/projects/app")
        assertEquals("""["paseo:/root/projects/app","ses_1","/work/project"]""", item.identity)
        assertEquals("""[null,"ses_1","/work/project"]""", row("ses_1").identity)
        assertEquals(AgentId("codex"), item.agentId)
    }

    @Test
    fun `B-CHAT-014 sub-agents are excluded unless asked for`() {
        val child = row("child", parent = "p")
        assertFalse(ChatFeedFilter.All.matches(child))
        assertTrue(ChatFeedFilter.Everything.matches(child))
    }
}
