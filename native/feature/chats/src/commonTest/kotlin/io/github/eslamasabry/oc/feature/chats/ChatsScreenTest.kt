package io.github.eslamasabry.oc.feature.chats

import kotlin.math.abs
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.persistentListOf
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
    fun `B-NAV-037 B15 the home has no bottom bar, and the gear word says Settings and opens it`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            setContent { Themed { ChatsScreen(ui(*rows), actions) } }
            onNodeWithText("Conversations").assertExists()
            // Flutter's old bottom destinations are gone; the top bar carries the only ways out.
            listOf("Chats", "Work", "Files", "Inbox").forEach {
                onAllNodesWithText(it).assertCountEquals(0)
            }
            // The gear is a round icon button beside Search (Flutter's shell bar); its words are what TalkBack says.
            onNodeWithContentDescription("Settings").performClick()
            assertEquals(listOf("settings"), actions.calls.filter { it == "settings" })
            onAllNodesWithText("Servers").assertCountEquals(0)
        }

    @Test
    fun `B-NAV-037 the server pill names the server and its state and opens the one-tap switcher`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            val phone =
                ChatsListSourceUi(ProfileId("p1"), "On this phone", shown = true, main = true, onThisPhone = true, unreachable = false)
            setContent {
                Themed { ChatsScreen(ui(*rows).copy(home = ChatsHomeExtraState(sources = persistentListOf(phone))), actions) }
            }
            onNodeWithContentDescription("On this phone, Connected, Switch server").performClick()
            // F: lib/ui/screens/home_screen.dart:434-470: the switcher sheet, never the full Servers page.
            assertTrue("switcher" in actions.calls)
            assertTrue("servers" !in actions.calls)
            // No overflow, and (owner order 2026-11-08, as Flutter's home) no place rows after the list either: All
            // conversations is reached from Search.
            onAllNodesWithContentDescription("More").assertCountEquals(0)
            onAllNodesWithText("Show all conversations").assertCountEquals(0)
        }

    private fun refreshAction(test: androidx.compose.ui.test.ComposeUiTest) =
        test.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher("offers Refresh") { node ->
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.CustomActions).orEmpty().any {
                    it.label == "Refresh"
                }
            }
        )

    @Test
    fun `PARITY-B-217 the home list reads itself again on a pull, and not while its server still opens`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            var state by androidx.compose.runtime.mutableStateOf(ui(*rows))
            setContent { Themed { ChatsScreen(state, actions) } }
            val refresh =
                refreshAction(this).fetchSemanticsNodes().single().config[
                    androidx.compose.ui.semantics.SemanticsActions.CustomActions
                ].first { it.label == "Refresh" }
            runOnIdle { refresh.action() }
            assertEquals(listOf("refresh"), actions.calls.filter { it == "refresh" })
            state =
                ui(*rows).copy(
                    home =
                        ChatsHomeExtraState(
                            opening =
                                io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi(
                                    io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase.Connecting,
                                    "Home PC",
                                )
                        )
                )
            waitForIdle()
            refreshAction(this).assertCountEquals(0)
        }

    @Test
    fun `SCREENS-NAV-27 G3 the empty home is Flutter's calm page with New conversation as its one button`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            setContent { Themed { ChatsScreen(ui(), actions) } }
            onNodeWithText("No conversations yet").assertExists()
            onNodeWithText("Say what you want to build, and an agent gets to work.").assertExists()
            // F: chats_home_screen.dart:744-761: the full-width button says its words; no floating + over an empty page,
            // and none of the old start rows.
            onAllNodesWithContentDescription("New conversation").assertCountEquals(0)
            onAllNodesWithText("Open a project").assertCountEquals(0)
            onAllNodesWithText("See what an agent can do").assertCountEquals(0)
            onNodeWithText("New conversation").performClick()
            onNodeWithText("Connect a computer").performClick()
            assertEquals(listOf("new", "computer"), actions.calls)
        }

    private fun failedOpening(name: String, phone: Boolean, off: Boolean = false) =
        io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi(
            io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase.Failed,
            name,
            onThisPhone = phone,
            phoneOff = off,
        )

    @Test
    fun `NEW-BLOCKED the phone's own server turned off is one card with Start, not two`() = runComposeUiTest {
        val actions = RecordingChatsActions()
        setContent {
            Themed {
                ChatsScreen(
                    ui().copy(home = ChatsHomeExtraState(opening = failedOpening("This phone", phone = true, off = true)), newBlocked = true),
                    actions,
                )
            }
        }
        onAllNodesWithText("OpenCode on this phone is off").assertCountEquals(1)
        onAllNodesWithText("Start OpenCode on this phone").assertCountEquals(1)
        onAllNodesWithText("Dismiss").assertCountEquals(0)
        onAllNodesWithText("Restart OpenCode").assertCountEquals(0)
    }

    @Test
    fun `NEW-BLOCKED a computer that does not answer is one card that says nothing was started, with Try again`() = runComposeUiTest {
        setContent {
            Themed {
                ChatsScreen(
                    ui().copy(home = ChatsHomeExtraState(opening = failedOpening("Home PC", phone = false)), newBlocked = true),
                    RecordingChatsActions(),
                )
            }
        }
        onAllNodesWithText("Home PC isn’t answering").assertCountEquals(1)
        onAllNodesWithText("New conversations start on Home PC. It isn’t answering yet, so nothing was started.").assertCountEquals(1)
        onAllNodesWithText("Try again").assertCountEquals(1)
        onAllNodesWithText("Change server").assertCountEquals(1)
        onAllNodesWithText("Dismiss").assertCountEquals(0)
    }

    @Test
    fun `NEW-BLOCKED a server that is only still opening gets the small notice`() = runComposeUiTest {
        val opening =
            io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi(
                io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase.Connecting,
                "Home PC",
            )
        setContent { Themed { ChatsScreen(ui().copy(home = ChatsHomeExtraState(opening = opening), newBlocked = true), RecordingChatsActions()) } }
        onNodeWithText("Home PC is still opening. Try again in a moment.").assertExists()
    }

    @Test
    fun `PHONE-OFF the new-conversation page says the server is off and offers Start, never Please wait`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val phone = io.github.eslamasabry.oc.core.common.ids.ProfileId("phone")
        val actions =
            object : io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions {
                override fun back() {}

                override fun addServer() {}

                override fun chooseServer(profile: io.github.eslamasabry.oc.core.common.ids.ProfileId) {}

                override fun draftChanged(text: String) {}

                override fun start() {}

                override fun sheet(sheet: io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet?) {}

                override fun chooseDirectory(directory: String) {}

                override fun chooseModel(model: io.github.eslamasabry.oc.core.model.ModelRef?) {}

                override fun chooseAgent(agent: String?) {}

                override fun separateCopy(on: Boolean) {}

                override fun import(conversation: io.github.eslamasabry.oc.core.domain.ImportableConversation) {}

                override fun retryLoad() {}

                override fun startPhoneServer() {
                    calls += "start"
                }
            }
        val state =
            io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi(
                servers = listOf(io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer(phone, "This phone", false, phoneOff = true)),
                profile = phone,
                phoneOff = true,
            )
        setContent { Themed { io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen(state, actions) } }
        onAllNodesWithText("OpenCode on this phone is off").assertCountEquals(1)
        onAllNodesWithText("Please wait for the current action.").assertCountEquals(0)
        onNodeWithText("Start OpenCode on this phone").performClick()
        assertEquals(listOf("start"), calls)
    }

    @Test
    fun `SCREENS-NAV-27 G3 the calm words sit above the middle and the button at the bottom`() =
        runComposeUiTest {
            setContent { Themed { ChatsScreen(ui(), RecordingChatsActions()) } }
            val root = onRoot().fetchSemanticsNode().boundsInRoot
            val words = onNodeWithText("No conversations yet").fetchSemanticsNode().boundsInRoot
            val button = onNodeWithText("New conversation").fetchSemanticsNode().boundsInRoot
            // Centred across the page, in the upper part of the room under the heading, and the button in the bottom
            // quarter (a top-aligned state put the words right under the heading).
            // Centred on the page column the full-width button spans.
            assertTrue(abs(words.center.x - button.center.x) < 2f, "words centred: ${words.center.x} vs ${button.center.x}")
            assertTrue(words.top > root.height * 0.25f && words.bottom < root.center.y, "words above the middle: $words")
            assertTrue(button.top > root.height * 0.75f, "button at the bottom: $button in $root")
        }

    @Test
    fun `SCREENS-NAV-27 G3 Running with nothing running says so and offers New conversation and Show all`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            val state =
                ChatsState.build(
                    ChatFeedSnapshot(persistentListOf(item("old", "/work/api", at = 5, title = "Done thing"))),
                    ReturnBrief.None,
                    ChatsFilters(running = true),
                    10_000,
                    true,
                )
            setContent { Themed { ChatsScreen(state, actions) } }
            // F: chats_home_screen.dart:686-707 (KitStateView.calm "chats-empty-running")
            onNodeWithText("Nothing running").assertExists()
            onAllNodesWithText("No matching conversations").assertCountEquals(0)
            onNodeWithText("Show all conversations").performClick()
            onNodeWithText("New conversation").performClick()
            assertEquals(listOf("running", "new"), actions.calls)
        }

    @Test
    fun `SCREENS-NAV-27 G2 one row follows the next with only its hairline between them`() =
        runComposeUiTest {
            setContent { Themed { ChatsScreen(ui(*rows), RecordingChatsActions()) } }
            val first = onNodeWithContentDescription("Fix the reconnect loop", substring = true).fetchSemanticsNode().boundsInRoot
            val second = onNodeWithContentDescription("Theme packs", substring = true).fetchSemanticsNode().boundsInRoot
            val (upper, lower) = if (first.top < second.top) first to second else second to first
            // F: KitSliverRowGroup: a 0.5-1 dp hairline and no other gap (the 4 dp list spacing put each row 4 px lower).
            assertTrue(lower.top - upper.bottom <= 1.5f, "gap between rows: ${lower.top - upper.bottom}")
        }

    @Test
    fun `SCREENS-NAV-27 G1 the page alone draws no bar, as Flutter's home leaves the bar to the shell`() =
        runComposeUiTest {
            setContent { Themed { ChatsScreen(ui(*rows), RecordingChatsActions(), shellBar = false) } }
            onNodeWithText("Conversations").assertExists()
            onAllNodesWithContentDescription("Search").assertCountEquals(0)
            onAllNodesWithContentDescription("Settings").assertCountEquals(0)
            // The heading is the page's first part (Flutter: 16 dp under the top edge, chats_home_mixed golden y≈20-48).
            assertTrue(onNodeWithText("Conversations").fetchSemanticsNode().boundsInRoot.top < 40f)
        }

    @Test
    fun `B-NAV-037 without a server-management door there is no dead gear`() = runComposeUiTest {
        setContent { Themed { ChatsScreen(ui(*rows, settings = false), RecordingChatsActions()) } }
        onAllNodesWithContentDescription("Settings").assertCountEquals(0)
    }

    @Test
    fun `A1-HOME-06 rows sit under Right now and Recent with cleaned titles and one status word`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            setContent { Themed { ChatsScreen(ui(*rows), actions) } }
            onNodeWithText("Right now").assertExists()
            onNodeWithText("Recent").assertExists()
            onAllNodesWithText("Today").assertCountEquals(0)
            // A row is one merged node that reads title, state, project and its line (KitRecentRow), not loose texts.
            onNodeWithContentDescription("Fix the reconnect loop", substring = true).assertExists()
            // One name for one thing (design review §2.3): the placeholder row and the floating button both say
            // "New conversation".
            onAllNodesWithContentDescription("New conversation", substring = true).assertCountEquals(2)
            // P-107: the floating button is round and draws no words, so it never covers a row's time with them; it
            // is the one node named exactly "New conversation".
            onAllNodesWithContentDescription("New conversation").assertCountEquals(1)
            onAllNodesWithText("New conversation").assertCountEquals(0)
            onAllNodesWithText("New chat").assertCountEquals(0)
            onAllNodesWithContentDescription("New chat", substring = true).assertCountEquals(0)
            // One status word per row: the needs-you row says it once, the running row says Working.
            // One agent on every row: its name is not repeated on each (P-021).
            onNodeWithContentDescription("Theme packs, Needs you, web", substring = true).assertExists()
            onNodeWithContentDescription("Fix the reconnect loop, Working, api", substring = true).assertExists()
            onNodeWithContentDescription("Theme packs", substring = true).performClick()
            assertTrue("open ask" in actions.calls)
        }

    @Test
    fun `SCREENS-NAV-27 Search, Running, the needs-you line and the project chip reach their intents`() =
        runComposeUiTest {
            // Flutter chats_home_screen.dart (2.1.4): search sits on the shell bar, "N need you" under the title
            // opens the Inbox, and projects are one chip that opens the project sheet.
            val actions = RecordingChatsActions()
            setContent { Themed { ChatsScreen(ui(*rows), actions) } }
            onAllNodes(hasSetTextAction()).assertCountEquals(0)
            onNodeWithContentDescription("Search").performClick()
            onNodeWithContentDescription("Running", substring = true).performClick()
            onNodeWithText("Needs you").performClick()
            onNodeWithContentDescription("All projects").performClick()
            assertEquals(listOf("openSearch", "running", "inbox", "projectChoices"), actions.calls)
        }

    @Test
    fun `SCREENS-NAV-27 an empty server offers New conversation once, in the empty state`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            setContent { Themed { ChatsScreen(ui(), actions) } }
            onNodeWithText("No conversations yet").assertExists()
            // G3: the calm page's own button, drawn with its words; the floating + waits for a list.
            onAllNodesWithText("New conversation").assertCountEquals(1)
            onAllNodesWithContentDescription("New conversation").assertCountEquals(0)
            onNodeWithText("New conversation").performClick()
            assertEquals(listOf("new"), actions.calls)
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
            // The finished conversation shows once, in the brief, not again in the list under it.
            onAllNodesWithContentDescription("Done", substring = true).assertCountEquals(1)
            onNodeWithText("Mark all seen").performClick()
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
            onAllNodesWithText("Mark all seen").assertCountEquals(0)
        }

    @Test
    fun `the one-project line names the project in quotes and starts where the brief notice starts`() =
        runComposeUiTest {
            val unknown = ReturnBrief.build(emptyList(), false, false, { true }, { false }, { null }, scope = BRIEF_SCOPE)
            setContent { Themed { ChatsScreen(ui(brief = unknown).copy(onlyProject = "new"), RecordingChatsActions()) } }
            val line = onNodeWithText("Showing conversations in", substring = true).fetchSemanticsNode()
            val shown = line.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
            assertTrue(shown.filter { it != '\u2068' && it != '\u2069' }.startsWith("Showing conversations in \u201Cnew\u201D only."), shown)
            val project = line.boundsInRoot
            val brief = onNodeWithText("Can't tell what's new on this server").fetchSemanticsNode().boundsInRoot
            assertEquals(brief.left, project.left, "same text column")
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
            onNodeWithText("Mark all seen").assertExists()
        }

    @Test
    fun `SCREENS-ROWS-01 an agent with no name reads Another agent, not the conversation's title`() =
        runComposeUiTest {
            val mystery = item("m", title = "Tidy the logs").copy(agentId = "hermes")
            // Two agents in the list, so each row says whose it is (P-021).
            setContent { Themed { ChatsScreen(ui(mystery, item("o", title = "Other")), RecordingChatsActions()) } }
            onNodeWithContentDescription("Tidy the logs, Another agent", substring = true).assertExists()
            onAllNodesWithContentDescription("Tidy the logs, Tidy the logs", substring = true).assertCountEquals(0)
        }

    @Test
    fun `P-021 rows name their agent only when the list holds more than one`() = runComposeUiTest {
        val mixed = arrayOf(item("c", title = "Port the parser").copy(agentId = "codex"), item("o", title = "Other"))
        setContent { Themed { ChatsScreen(ui(*mixed), RecordingChatsActions()) } }
        onNodeWithContentDescription("Port the parser, Codex", substring = true).assertExists()
        onNodeWithContentDescription("Other, OpenCode", substring = true).assertExists()
    }

    @Test
    fun `P-021 the finished group is headed like Right now and Recent`() = runComposeUiTest {
        val brief =
            ReturnBrief.build(
                listOf(Session(SessionId("old"), directory = "/work/api", time = SessionTime(idleMs = 10))),
                true,
                false,
                { true },
                { false },
                { null },
                scope = BRIEF_SCOPE,
            )
        setContent { Themed { ChatsScreen(ui(*rows, brief = brief), RecordingChatsActions()) } }
        onNodeWithText("Since you were away").assertExists()
    }

    @Test
    fun `P-021 no finished rows, no heading`() = runComposeUiTest {
        setContent { Themed { ChatsScreen(ui(*rows), RecordingChatsActions()) } }
        onAllNodesWithText("Since you were away").assertCountEquals(0)
    }

    @Test
    fun `P-023 the connections chip opens its choices with the same chevron as the project chip`() =
        runComposeUiTest {
            val phone = ChatsListSourceUi(ProfileId("p1"), "On this phone", shown = true, main = true, onThisPhone = true, unreachable = false)
            val laptop = ChatsListSourceUi(ProfileId("p2"), "Laptop", shown = false, main = false, onThisPhone = false, unreachable = false)
            val actions = RecordingChatsActions()
            setContent {
                Themed { ChatsScreen(ui(*rows).copy(home = ChatsHomeExtraState(sources = persistentListOf(phone, laptop))), actions) }
            }
            onNodeWithContentDescription("1 of 2 connections").assertExists()
            // The project chip's and the connections chip's.
            onAllNodesWithTag("kit-chip-chevron", useUnmergedTree = true).assertCountEquals(2)
        }

    @Test
    fun `B-NAV-037 an archive that failed says so and the notice can be closed`() = runComposeUiTest {
        val actions = RecordingChatsActions()
        setContent {
            Themed {
                ChatsScreen(
                    ui(*rows).copy(archiveFailure = io.github.eslamasabry.oc.core.common.Failure.Device()),
                    actions,
                )
            }
        }
        onNodeWithText("Couldn’t archive the conversation.").assertExists()
        onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(listOf("dismissArchiveFailure"), actions.calls)
    }
}
