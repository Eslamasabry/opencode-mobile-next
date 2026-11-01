package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList

/** The Conversations home, found by visible text (desktop, headless). */
@OptIn(ExperimentalTestApi::class)
class ChatsScreenTest {
    private fun ui(
        vararg items: io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem,
        brief: ReturnBrief = ReturnBrief.None,
        settings: Boolean = true,
        loading: Boolean = false,
    ): ChatsUi =
        ChatsState.build(
            ChatFeedSnapshot(items.toList().toImmutableList(), loading = loading),
            brief,
            ChatsFilters(),
            10_000,
            settings,
        )

    private val rows =
        arrayOf(
            item(
                "run",
                "/work/api",
                at = 12_000,
                title = "Fix the reconnect loop",
                status = ChatStatus.Running,
            ),
            item(
                "ask",
                "/work/web",
                at = 11_000,
                title = "Theme packs",
                status = ChatStatus.NeedsYou,
            ),
            item("old", "/work/api", at = 5, title = "New session - 2026-09-02T14:47:06Z"),
        )

    @Test
    fun `B-NAV-037 the home has no bottom bar, and the gear word opens Settings`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            setContent { Themed { ChatsScreen(ui(*rows), actions) } }
            onNodeWithText("Conversations").assertExists()
            // Flutter's old bottom destinations are gone; the top bar carries the only ways out.
            listOf("Chats", "Work", "Files", "Inbox").forEach {
                onAllNodesWithText(it).assertCountEquals(0)
            }
            onNodeWithText("Settings").performClick()
            assertEquals(listOf("settings"), actions.calls.filter { it == "settings" })
        }

    @Test
    fun `B-NAV-037 without a Settings door there is no dead gear`() = runComposeUiTest {
        setContent { Themed { ChatsScreen(ui(*rows, settings = false), RecordingChatsActions()) } }
        onAllNodesWithText("Settings").assertCountEquals(0)
    }

    @Test
    fun `SCREENS-NAV-27 rows sit under Today and Earlier with cleaned titles and one status word`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            setContent { Themed { ChatsScreen(ui(*rows), actions) } }
            onNodeWithText("Today").assertExists()
            onNodeWithText("Earlier").assertExists()
            onNodeWithText("Fix the reconnect loop").assertExists()
            // The placeholder row reads "New conversation", beside the top-bar action of the same
            // words.
            onAllNodesWithText("New conversation").assertCountEquals(2)
            onAllNodesWithText("Needs you").assertCountEquals(1)
            onNodeWithText("Theme packs").performClick()
            assertTrue("open ask" in actions.calls)
        }

    @Test
    fun `SCREENS-NAV-27 search, Running, needs-you and recent project chips reach their intents`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            setContent { Themed { ChatsScreen(ui(*rows), actions) } }
            onNode(hasSetTextAction()).performTextInput("api")
            onAllNodesWithText("Running")[0].performClick() // the chip; the running row says it too
            onNodeWithText("1 needs you").performClick()
            onAllNodesWithText("web")[0]
                .performClick() // the project chip; the row names its project too
            assertTrue("search api" in actions.calls, actions.calls.toString())
            assertTrue(
                actions.calls.containsAll(listOf("running", "needsYou", "project /work/web")),
                actions.calls.toString(),
            )
        }

    @Test
    fun `SCREENS-NAV-27 an empty server offers New conversation once, in the empty state`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            setContent { Themed { ChatsScreen(ui(), actions) } }
            onNodeWithText("No conversations yet").assertExists()
            onAllNodesWithText("New conversation").assertCountEquals(1)
            onNodeWithText("New conversation").performClick()
            onNodeWithText("Show all conversations").performClick()
            assertEquals(listOf("new", "all"), actions.calls)
        }

    @Test
    fun `B-CHAT-094 the brief rows show above the list and Continue dismisses them`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            val sessions =
                listOf(
                    Session(
                        SessionId("old"),
                        directory = "/work/api",
                        time = SessionTime(idleMs = 10),
                    )
                )
            val brief =
                ReturnBrief.build(
                    sessions,
                    true,
                    false,
                    { true },
                    { false },
                    { null },
                    scope = BRIEF_SCOPE,
                )
            setContent { Themed { ChatsScreen(ui(*rows, brief = brief), actions) } }
            onAllNodesWithText("Done").assertCountEquals(1)
            onNodeWithText("Continue").performClick()
            assertEquals(listOf("dismiss"), actions.calls)
        }

    @Test
    fun `B-CHAT-095 finding 5 unsupported read state is visibly distinct from empty`() =
        runComposeUiTest {
            val unknown =
                ReturnBrief.build(
                    emptyList(),
                    false,
                    false,
                    { true },
                    { false },
                    { null },
                    scope = BRIEF_SCOPE,
                )
            setContent { Themed { ChatsScreen(ui(brief = unknown), RecordingChatsActions()) } }
            onNodeWithText("Can't tell what's new on this server").assertExists()
            onAllNodesWithText("Continue").assertCountEquals(0)
        }

    @Test
    fun `B-CHAT-093 finding 5 overflow has a visible filtered-list action`() = runComposeUiTest {
        val actions = RecordingChatsActions()
        val brief =
            ReturnBrief.build(
                (1..5).map {
                    Session(
                        SessionId("s$it"),
                        directory = "/work/api",
                        time = SessionTime(idleMs = it * 10L),
                    )
                },
                true,
                false,
                { true },
                { false },
                { null },
                scope = BRIEF_SCOPE,
            )
        setContent {
            Themed {
                ChatsScreen(ui(*(1..5).map { item("s$it") }.toTypedArray(), brief = brief), actions)
            }
        }
        onNodeWithText("and 2 more").performClick()
        assertEquals(listOf("overflow"), actions.calls)
    }

    @Test
    fun `B-CHAT-095 finding 5 partial inventory explains uncertainty alongside known rows`() =
        runComposeUiTest {
            val brief =
                ReturnBrief.build(
                    listOf(
                        Session(
                            SessionId("known"),
                            directory = "/work/api",
                            time = SessionTime(idleMs = 10),
                        )
                    ),
                    true,
                    true,
                    { true },
                    { false },
                    { null },
                    scope = BRIEF_SCOPE,
                )
            setContent {
                Themed { ChatsScreen(ui(item("known"), brief = brief), RecordingChatsActions()) }
            }
            onNodeWithText("Can't tell what's new on this server").assertExists()
            onNodeWithText("Continue").assertExists()
        }
}
