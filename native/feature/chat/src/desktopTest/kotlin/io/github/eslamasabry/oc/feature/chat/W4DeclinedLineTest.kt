package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.feature.chat.ui.TurnEndNote
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * W4 surface: a turn that ended on the person's Deny closes with "You declined this" (the step row's own words), read
 * by a screen reader like the other closing lines, and offers no way on that would run the refused step again.
 */
@OptIn(ExperimentalTestApi::class)
class W4DeclinedLineTest {
    @Test
    fun `W4 the declined closing line says You declined this and offers neither Continue nor Send again`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                // Both ways on are handed in, as the transcript does: the line itself must not show either.
                TurnEndNote(TurnEndNote.Declined, onContinue = {}, onSendAgain = {})
            }
        }
        onNodeWithText("You declined this").assertExists()
        onNodeWithText("Stopped").assertDoesNotExist()
        onNodeWithText("The connection dropped before this reply finished.").assertDoesNotExist()
        onNodeWithTag("stopped-continue").assertDoesNotExist()
        onNodeWithTag("interrupted-send-again").assertDoesNotExist()
    }

    @Test
    fun `W4 older - two declined turns each draw the line once and no turn draws a Stopped line`() = runComposeUiTest {
        val notes = mapOf("m1" to TurnEndNote.Declined, "m3" to TurnEndNote.Declined)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                androidx.compose.foundation.layout.Column {
                    for ((turn, note) in notes) androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.testTag("turn-$turn")) {
                        TurnEndNote(note, onContinue = {}, onSendAgain = {})
                    }
                }
            }
        }
        onNodeWithTag("turn-m1").assertExists()
        onNodeWithTag("turn-m3").assertExists()
        onAllNodesWithText("You declined this").assertCountEquals(2)
        onNodeWithText("Stopped").assertDoesNotExist()
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    @Test
    fun `W4 older gate - through the reading to the chat list, the declined line sits once, under the older turn`() = runComposeUiTest {
        // The real reading: an older turn that ended on the person's Deny, then a newer turn that finished normally.
        val sid = Ref.sessionId.raw
        val declined =
            Part(
                "t-write", "m2", sid, PartType.Tool, "tool", callId = "c-write", toolName = "write",
                tool = ToolState(ToolStatus.Error, input = JsonObject(mapOf("filePath" to JsonPrimitive("/p/a.txt"))), output = "The user declined this tool call", startedAtMs = 2_000, completedAtMs = 3_000),
            )
        val page =
            listOf(
                MessageWithParts(MessageInfo("m1", sid, Role.User, time = MsgTime(1_000, null)), listOf(Part("u1", "m1", sid, PartType.Text, "text", text = "write a file", ended = true)).toImmutableList()),
                MessageWithParts(
                    MessageInfo("m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle", time = MsgTime(1_100, 4_000), finish = "tool-calls"),
                    listOf(Part("x2", "m2", sid, PartType.Text, "text", text = "I will write the file now."), declined).toImmutableList(),
                ),
                MessageWithParts(MessageInfo("m3", sid, Role.User, time = MsgTime(10_000, null)), listOf(Part("u3", "m3", sid, PartType.Text, "text", text = "say hi", ended = true)).toImmutableList()),
                MessageWithParts(
                    MessageInfo("m4", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle", time = MsgTime(10_100, 11_000), finish = "stop"),
                    listOf(Part("x4", "m4", sid, PartType.Text, "text", text = "Hi there!")).toImmutableList(),
                ),
            )
        val keeper = TurnFeedKeeper(sid)
        keeper.history(page, newest = true)
        keeper.idle()
        val facts = TurnFactsPresenter(FixedClock).present(keeper.feed.value, 20_000)
        // The list's rows for the same two turns, newest first, keyed by the same turn ids as the reading.
        val rows =
            persistentListOf(
                footer("m3"),
                block("m3", "m4", "x4", 0, "Hi there!"),
                prompt("m3", "say hi"),
                footer("m1"),
                block("m1", "m2", "x2", 0, "I will write the file now."),
                prompt("m1", "write a file"),
            )
        val state = ChatUi.loading(Ref).copy(title = "Native chat", phase = ChatPhase.Live, rows = rows)
        val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) { ChatScreen(state, actions, composer, facts = facts) }
            }
        }
        onAllNodesWithText("You declined this").assertCountEquals(1)
        onNodeWithText("Stopped").assertDoesNotExist()
        // Under the older turn's reply and above the newer prompt: not on the newest turn.
        val line = onNodeWithText("You declined this", useUnmergedTree = true).getBoundsInRoot()
        val olderReply = onNodeWithText("I will write the file now.", useUnmergedTree = true).getBoundsInRoot()
        val newerPrompt = onNodeWithText("say hi", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(line.top >= olderReply.bottom, "line at ${line.top}, older reply ends at ${olderReply.bottom}")
        assertTrue(line.bottom <= newerPrompt.top, "line ends at ${line.bottom}, newer prompt at ${newerPrompt.top}")
    }
}
