package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.agents.resume.SessionResumeCli
import io.github.eslamasabry.oc.engine.agents.resume.SessionResumeCommand
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderActions
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.header.presentation.ContinueChoice
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderSheet
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptWords
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderOverlays
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlin.test.Test
import kotlin.test.assertEquals

/** The "Continue on computer" sheet by visible words (P12-BUILD-AGENTS; Flutter session_handoff_sheets.dart). */
@OptIn(ExperimentalTestApi::class)
class ContinueOnComputerScreenTest {
    private class Actions : ChatHeaderActions {
        var copies = 0
        var closes = 0

        override fun openMenu() {}

        override fun closeMenu() {}

        override fun choose(action: HeaderAction) {}

        override fun copyTranscript(words: TranscriptWords, mask: (String) -> String) {}

        override fun renameRequested() {}

        override fun renameDone(title: String?) {}

        override fun closeSheet() {
            closes++
        }

        override fun continueWith(choice: ContinueChoice) {}

        override fun retryContinueWith() {}

        override fun confirm() {}

        override fun dismissConfirm() {}

        override fun dismissFailure() {}

        override fun clipDone(id: Long) {}

        override fun copyResumeCommand() {
            copies++
        }
    }

    private fun ui(directory: String?, workspace: String? = null) =
        ChatHeaderUi(
            sheet =
                HeaderSheet.ContinueOnComputer(
                    SessionResumeCommand.build(SessionResumeCli.OpenCode2, "ses_1", directory, workspace),
                ),
        )

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: ChatHeaderUi, actions: Actions) =
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                // The app provides the graph redactor through KitChatEnvironment around the chat entry.
                KitStandaloneRedaction { KitSheetHost { ChatHeaderOverlays(ChatHeaderSlot(state, actions)) } }
            }
        }

    @Test
    fun `B-AGENT-227 Continue on computer shows the whole resume command and Copy command copies it`() = runComposeUiTest {
        val actions = Actions()
        show(ui("/home/me/app"), actions)
        onNodeWithText("Continue on computer").assertIsDisplayed()
        onNodeWithText("cd '/home/me/app' && opencode2 --session 'ses_1'", substring = true).assertIsDisplayed()
        onNodeWithText("Terminal command").assertIsDisplayed()
        onNodeWithText("Copy command").performClick()
        assertEquals(1, actions.copies)
    }

    @Test
    fun `B-AGENT-228 a cloud workspace conversation explains why there is no command`() = runComposeUiTest {
        show(ui("/srv/app", workspace = "wrk_1"), Actions())
        onNodeWithText("This conversation runs inside a cloud environment", substring = true).assertIsDisplayed()
    }

    @Test
    fun `B-AGENT-228 a conversation without a reported folder says so in plain words`() = runComposeUiTest {
        show(ui(null), Actions())
        onNodeWithText("did not report a project folder", substring = true).assertIsDisplayed()
    }
}
