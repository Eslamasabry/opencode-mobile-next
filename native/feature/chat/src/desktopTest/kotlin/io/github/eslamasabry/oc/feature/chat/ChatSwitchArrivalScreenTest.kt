package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.arrival.ChatArrival
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.ui.LocalReplyHint
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** B-LAMP-C1: what the page shows after an edge-light switch, as a person sees it. */
@OptIn(ExperimentalTestApi::class)
class ChatSwitchArrivalScreenTest {
    /** A box that shows its hint and lets a test "type" through the same callback the real box calls. */
    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                val hint = LocalReplyHint.current
                Column(modifier) {
                    KitText(hint?.text ?: "Ask the agent…")
                    if (hint != null) KitButton("Type a word", { hint.typed() })
                }
            }
        }

    private val state = ChatUi.loading(Ref).copy(title = "Fix login", phase = ChatPhase.Live)

    private fun screen(
        arrival: ChatArrival?,
        hintTyped: MutableList<Long> = mutableListOf(),
        noteShown: MutableList<Long> = mutableListOf(),
        check: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) =
        runComposeUiTest {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    ChatScreen(
                        state,
                        actions,
                        composer,
                        switchArrival = arrival,
                        onHintTyped = { hintTyped += it },
                        onBackNoteShown = { noteShown += it },
                    )
                }
            }
            waitForIdle()
            check()
        }

    @Test
    fun `B-LAMP-C1-004 replyIn_hintSaysTheConversationUntilTyped`() {
        val typed = mutableListOf<Long>()
        screen(ChatArrival(Ref, leftTitle = null, seq = 4, backNote = false), typed) {
            onNodeWithText("Reply in Fix login").assertIsDisplayed()
            onNodeWithText("Type a word").performClick()
            assertEquals(listOf(4L), typed, "the page hears the first typing, with the arrival's seq")
        }
    }

    @Test
    fun `B-LAMP-C1-004 replyIn_notShownWithoutASwitchOrAfterTyping`() {
        screen(null) { onNodeWithText("Ask the agent…").assertIsDisplayed() }
        screen(ChatArrival(Ref, leftTitle = "Other", seq = 5, backNote = false, hint = false)) {
            onNodeWithText("Ask the agent…").assertIsDisplayed()
        }
    }

    @Test
    fun `B-LAMP-C1-004 backNote_saysWhereBackGoes_andIsCountedOnce`() {
        val noteShown = mutableListOf<Long>()
        screen(ChatArrival(Ref, leftTitle = "Deploy checks", seq = 6, backNote = true), noteShown = noteShown) {
            onNodeWithText("Back returns to Deploy checks, where you left it.").assertIsDisplayed()
            assertEquals(listOf(6L), noteShown)
        }
    }

    @Test
    fun `B-LAMP-C1-004 backNote_notShownOnceTheDeviceHasHadThree`() {
        val noteShown = mutableListOf<Long>()
        screen(ChatArrival(Ref, leftTitle = "Deploy checks", seq = 7, backNote = false), noteShown = noteShown) {
            assertEquals(0, onAllNodesWithTextCount("Back returns to"))
            assertEquals(emptyList(), noteShown)
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTextCount(words: String): Int =
        onAllNodesWithText(words, substring = true).fetchSemanticsNodes().size
}
