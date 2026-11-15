package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.LedgerSnapshot
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerViewModel
import io.github.eslamasabry.oc.feature.chat.presentation.ContinueBrief
import io.github.eslamasabry.oc.feature.chat.world.ChatRestart
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.ReadOnlyChat
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Issue 147: "Continue in a new conversation" on an old phone agent conversation starts a new one with the same agent
 * in the same folder, and its first message (which the person reads and can change) hands over what was done: the
 * person's requests, the files that changed and where it stopped, read from what the page holds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContinueBriefTest {
    private val sid = "ag-1"

    private fun info(id: String, role: Role) = MessageInfo(id, sid, role, providerId = "antigravity", modelId = "default")

    private fun text(id: String, mid: String, words: String) = Part(id, mid, sid, PartType.Text, "text", text = words)

    private val messages =
        listOf(
            MessageWithParts(info("u1", Role.User), listOf(text("u1-t", "u1", "Build a habit tracker")).toImmutableList()),
            MessageWithParts(
                info("a1", Role.Assistant),
                listOf(
                    Part(
                        "a1-w",
                        "a1",
                        sid,
                        PartType.Tool,
                        "tool",
                        callId = "c1",
                        toolName = "write",
                        tool =
                            ToolState(
                                status = ToolStatus.Completed,
                                input = JsonObject(mapOf("filePath" to JsonPrimitive("/root/projects/habits/app.py"))),
                            ),
                    ),
                    text("a1-t", "a1", "Created app.py with a daily list."),
                ).toImmutableList(),
            ),
            MessageWithParts(info("u2", Role.User), listOf(text("u2-t", "u2", "Add a streak counter")).toImmutableList()),
        )

    private val words =
        ContinueBrief.Words(
            intro = "Continue \"%1\$s\".",
            introUntitled = "Continue.",
            asked = "Asked:",
            changed = "Changed:",
            stopped = "Stopped at:",
        )

    @Test
    fun `I147-CONT-001 the brief carries the requests, the changed files with their paths and the last reply`() {
        val brief = ContinueBrief.of(messages)
        assertEquals(listOf("Build a habit tracker", "Add a streak counter"), brief.asks)
        assertEquals(listOf("/root/projects/habits/app.py"), brief.files)
        assertEquals("Created app.py with a daily list.", brief.last)
        assertEquals(
            "Continue \"Habits\".\n\nAsked:\n- Build a habit tracker\n- Add a streak counter\n\n" +
                "Changed:\n- /root/projects/habits/app.py\n\nStopped at:\nCreated app.py with a daily list.",
            brief.text("Habits", words),
        )
        // A long request is cut, and only the newest requests are kept.
        val many = (1..8).map { MessageWithParts(info("u$it", Role.User), listOf(text("t$it", "u$it", "x".repeat(400))).toImmutableList()) }
        val short = ContinueBrief.of(many)
        assertEquals(ContinueBrief.MAX_ASKS, short.asks.size)
        assertTrue(short.asks.all { it.length == ContinueBrief.MAX_ASK_CHARS + 1 && it.endsWith("…") })
    }

    private class Navigator : AppNavigator {
        val routes = mutableListOf<AppRoute>()
        override val top = MutableStateFlow<AppRoute?>(null)

        override fun open(route: AppRoute) {
            routes += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) = Unit

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) = Unit

        override fun back() = Unit
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `I147-CONT-002 Continue opens a new conversation with the same agent and folder, starting with the handoff`() {
        val ref = ConversationRef(ProfileId("agents"), SessionId(sid))
        val base = FakeChatWorld(ref)
        base.title.value = "Habits"
        val world =
            object : ChatWorld by base, TurnFeedSource {
                override val readOnly: StateFlow<ReadOnlyChat?> =
                    MutableStateFlow(ReadOnlyChat(restart = ChatRestart("antigravity", "/root/projects/habits", "Antigravity")))
                override val turnFeed: StateFlow<TurnFeed> =
                    MutableStateFlow(TurnFeed(ledger = LedgerSnapshot(1, messages, emptyList())))
            }
        val worlds =
            object : ChatWorlds {
                override fun open(ref: ConversationRef): ChatWorld = world

                override fun release(world: ChatWorld) = Unit
            }
        val nav = Navigator()
        val vm = BasicComposerViewModel(ComposerTarget("agents", sid), worlds, navigator = nav)
        vm.continueInNewConversation(words)
        val route = nav.routes.single() as ChatsRoute.New
        assertEquals(ref.profileId, route.profile)
        assertEquals("antigravity", route.phoneAgent)
        assertEquals("/root/projects/habits", route.directory)
        assertTrue(route.initialText.startsWith("Continue \"Habits\"."), route.initialText)
        assertTrue("- /root/projects/habits/app.py" in route.initialText, route.initialText)
    }
}
