package io.github.eslamasabry.oc.feature.chat.header

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderActions
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.header.presentation.ContinueChoice
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptWords
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderTopBar
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** S30 story 3 on the screen: the ⋯ menu's Go to lists Subagents and More lists Compact context (Flutter's menu). */
@OptIn(ExperimentalTestApi::class)
class ChatMenuSubagentsCompactScreenTest {
    private class Actions : ChatHeaderActions {
        val chosen = mutableListOf<HeaderAction>()

        override fun openMenu() {}

        override fun closeMenu() {}

        override fun choose(action: HeaderAction) {
            chosen += action
        }

        override fun copyTranscript(words: TranscriptWords, mask: (String) -> String) {}

        override fun renameRequested() {}

        override fun renameDone(title: String?) {}

        override fun closeSheet() {}

        override fun continueWith(choice: ContinueChoice) {}

        override fun retryContinueWith() {}

        override fun confirm() {}

        override fun dismissConfirm() {}

        override fun dismissFailure() {}

        override fun clipDone(id: Long) {}

        override fun copyResumeCommand() {}
    }

    private fun state() =
        ChatHeaderUi(
            title = "Upgrade pin one",
            menu =
                persistentListOf(
                    HeaderAction.Find,
                    HeaderAction.Subagents,
                    HeaderAction.Details,
                    HeaderAction.Compact,
                    HeaderAction.Fork,
                    HeaderAction.Rename,
                    HeaderAction.CopyTranscript,
                    HeaderAction.Reload,
                ),
            menuOpen = true,
            hasPrompt = true,
        )

    @androidx.compose.runtime.Composable
    private fun Bar(actions: Actions, onCompact: () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            KitStandaloneRedaction {
                KitSheetHost {
                    ChatHeaderTopBar(ChatHeaderSlot(state(), actions), onBack = {}, onFind = {}, onCompact = onCompact)
                }
            }
        }
    }

    @Test
    fun `S30-CHAT-003 Go to lists Subagents and choosing it asks the header to open them`() = runComposeUiTest {
        val actions = Actions()
        setContent { Bar(actions) {} }
        onNodeWithText("Go to").performClick()
        onNodeWithText("Subagents").assertIsDisplayed()
        onNodeWithText("Subagents").performClick()
        assertEquals(listOf(HeaderAction.Subagents), actions.chosen)
    }

    @Test
    fun `S30-CHAT-003 More lists Compact context and choosing it opens the chat's own confirm`() = runComposeUiTest {
        val actions = Actions()
        var compacts = 0
        setContent { Bar(actions) { compacts++ } }
        onNodeWithText("More").performClick()
        onNodeWithText("Shorten conversation").assertIsDisplayed()
        onNodeWithText("Shorten conversation").performClick()
        assertEquals(1, compacts)
        assertEquals(emptyList(), actions.chosen, "the header itself runs nothing")
    }
}
