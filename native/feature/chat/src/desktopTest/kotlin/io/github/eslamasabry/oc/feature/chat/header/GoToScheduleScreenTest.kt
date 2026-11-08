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

/** B-163 on the screen: the conversation menu's Go to page lists "Schedule a prompt" (run-5 upgrade gate). */
@OptIn(ExperimentalTestApi::class)
class GoToScheduleScreenTest {
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

    @Test
    fun `B-163 Go to lists Schedule a prompt and choosing it opens the scheduled prompts`() = runComposeUiTest {
        val actions = Actions()
        val state =
            ChatHeaderUi(
                title = "Upgrade pin one",
                menu =
                    persistentListOf(
                        HeaderAction.Find,
                        HeaderAction.Details,
                        HeaderAction.Schedule,
                        HeaderAction.Rename,
                        HeaderAction.CopyTranscript,
                        HeaderAction.Reload,
                    ),
                menuOpen = true,
            )
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                KitStandaloneRedaction {
                    KitSheetHost { ChatHeaderTopBar(ChatHeaderSlot(state, actions), onBack = {}, onFind = {}) }
                }
            }
        }
        onNodeWithText("Go to").performClick()
        onNodeWithText("Schedule a prompt").assertIsDisplayed()
        onNodeWithText("Schedule a prompt").performClick()
        assertEquals(listOf(HeaderAction.Schedule), actions.chosen)
    }
}
