package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDetailUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptReadingUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.ui.thoughtBody
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The chat while a reply runs, as Flutter draws it (owner, 2026-11-09: "check flutter"). */
@OptIn(ExperimentalTestApi::class)
class ChatRunningTurnTest {
    private val sid = Ref.sessionId.raw

    private fun info(id: String, role: Role, at: Long) =
        MessageInfo(id, sid, role, providerId = "anthropic", modelId = "m", time = MsgTime(at, null))

    private fun bash(id: String, command: String, status: ToolStatus, start: Long, end: Long? = null) =
        Part(
            id, "m2", sid, PartType.Tool, "tool",
            callId = "call-$id",
            toolName = "bash",
            tool =
                ToolState(
                    status,
                    input = JsonObject(mapOf("command" to JsonPrimitive(command))),
                    output = "ok",
                    startedAtMs = start,
                    completedAtMs = end,
                ),
        )

    /** A turn that ran one command and is running `npm test` 12 s after its prompt. */
    private fun runningFacts(): TurnFactsUi {
        val keeper = TurnFeedKeeper(sid)
        keeper.history(
            listOf(
                MessageWithParts(
                    info("m1", Role.User, 0),
                    persistentListOf(Part("p1", "m1", sid, PartType.Text, "text", text = "fix the build")),
                ),
                MessageWithParts(
                    info("m2", Role.Assistant, 1_000),
                    listOf(
                        bash("t1", "git status", ToolStatus.Completed, 1_000, 1_500),
                        bash("t2", "npm test", ToolStatus.Running, 4_000),
                    ).toImmutableList(),
                ),
            )
        )
        keeper.event(GatewayEvent.RunStarted(SessionId(sid)), 0)
        return TurnFactsPresenter(FixedClock).present(keeper.feed.value, 12_000)
    }

    private val composerSlot =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor(), content = content)
        }
    }

    private fun running(vararg rows: io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow) =
        ChatUi.loading(Ref).copy(
            title = "Native chat",
            phase = ChatPhase.Live,
            rows = persistentListOf(*rows),
            live = LiveTurnUi(stopping = false),
        )

    @Test
    // D1 "steps only" (owner 2026-11-14) replaces "the live line names the step and the work line waits behind it": the
    // steps stay in view while the turn runs, the running one on the rail, and the live line under them says the agent
    // works and for how long without naming that step a second time.
    fun `B-CHAT-063 while a step runs the steps stay in view and the live line says how long the turn has run`() =
        runComposeUiTest {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            val state = running(WorkLineRow("work:m1", "m1", calls = 2, failed = 0, state = WorkState.Running), prompt("m1", "fix the build"))
            setContent { Root { ChatScreen(state, actions, composerSlot, facts = runningFacts()) } }
            // Flutter kit_turn.dart:910-919: after five seconds, how long the turn has run ("12 s" never breaks).
            onNodeWithText("Working · 12 s").assertIsDisplayed()
            onAllNodesWithText("Running npm test", substring = true).assertCountEquals(0)
            // The steps, the finished one and the running one, in view without a tap.
            onNodeWithTag("chat-work-line").assertExists()
            onNodeWithText("git status", substring = true).assertIsDisplayed()
            onNodeWithText("npm test", substring = true).assertIsDisplayed()
        }

    @Test
    fun `B-CHAT-063 an opened thought says its first line once, the rest under it`() =
        runComposeUiTest {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            val thought = "The fetch failed.\nThe race is in the bloc, so the test waits on the settled total."
            val part = Part("r1", "m2", sid, PartType.Reasoning, "reasoning", text = thought, ended = true)
            val key = "thought:m2/r1"
            val state = running(ThoughtRow(key, "m1", "The fetch failed.", thought), prompt("m1", "fix the build")).copy(reasoningExpanded = true)
            val facts = TurnFactsUi(reading = TranscriptReadingUi(details = mapOf(key to TranscriptDetailUi(key, part))))
            setContent { Root { ChatScreen(state, actions, composerSlot, facts = facts) } }
            onAllNodesWithText("The fetch failed.", substring = true).assertCountEquals(1)
            onNodeWithText("The race is in the bloc", substring = true).assertIsDisplayed()
        }

    @Test
    fun `audit 1 a thought the reading does not know folds under Thought and never prints its words`() =
        runComposeUiTest {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            val thought = "User asks to create file. Previous write attempts were declined."
            val state = running(ThoughtRow("thought:m2/r1", "m1", thought, thought), prompt("m1", "make a file"))
            setContent { Root { ChatScreen(state, actions, composerSlot, facts = TurnFactsUi()) } }
            onNodeWithText("Thought").assertIsDisplayed()
            onAllNodesWithText("User asks to create file", substring = true).assertCountEquals(0)
        }

    @Test
    fun `B-CHAT-063 a thought's body starts after its first line`() {
        assertEquals("second\n\nthird", thoughtBody("\n**first**\nsecond\n\nthird\n"))
        assertEquals("", thoughtBody("only one line"))
    }

    @Test
    // Owner 2026-11-14 ("no new bar or row level"): in every style the box holds its controls; nothing rides above it.
    // Was: "Stop is the chip at the end of the strip, never in the message box".
    fun `B-CHAT-006 while a reply runs Stop is in the message box, and no strip rides above it`() =
        runComposeUiTest {
            val actions =
                object : BasicComposerActions {
                    override fun draftChanged(text: String) {}

                    override suspend fun send(text: String): Boolean = true

                    override suspend fun selectModel(choice: ComposerModel?, variant: String): Boolean = true
                }
            val ui = BasicComposerUi(available = true, sending = false, steers = true, targetKey = "p/s")
            val chrome = ComposerChrome({ true }, { false }, {}, { null }, {}, { null }, {}, {}, { false })
            setContent { Root { Box(Modifier.width(360.dp)) { BasicComposer(ui, actions, chrome) } } }
            onNodeWithTag("chat-stop-button", useUnmergedTree = true).assertIsDisplayed()
            onNode(hasTestTag("chat-stop-button") and hasAnyAncestor(hasTestTag("kit-composer-box")), useUnmergedTree = true).assertExists()
            onNodeWithTag("composer-status-strip", useUnmergedTree = true).assertDoesNotExist()
            onNode(hasTestTag("chat-model-button") and hasAnyAncestor(hasTestTag("kit-composer-box")), useUnmergedTree = true).assertExists()
        }

    @Test
    fun `audit 1 the model chip says the model's name only, free or not, as the list and the home do`() =
        runComposeUiTest {
            val actions =
                object : BasicComposerActions {
                    override fun draftChanged(text: String) {}

                    override suspend fun send(text: String): Boolean = true

                    override suspend fun selectModel(choice: ComposerModel?, variant: String): Boolean = true
                }
            val pickle = ComposerModel("opencode", "big-pickle", "Big Pickle", supportsAttachments = false)
            val ui =
                BasicComposerUi(
                    available = true,
                    sending = false,
                    steers = false,
                    models = io.github.eslamasabry.oc.feature.chat.world.ComposerModels(choices = listOf(pickle), selected = pickle),
                    targetKey = "p/s",
                )
            val chrome = ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false }, freeModel = { true })
            setContent { Root { Box(Modifier.width(360.dp)) { BasicComposer(ui, actions, chrome) } } }
            onNodeWithText("Big Pickle", substring = true, useUnmergedTree = true).assertExists()
            onAllNodesWithText("Free", substring = true, useUnmergedTree = true).assertCountEquals(0)
        }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(12_000)
    }
}
