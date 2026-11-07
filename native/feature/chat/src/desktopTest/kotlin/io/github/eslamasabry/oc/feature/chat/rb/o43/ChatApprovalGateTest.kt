package io.github.eslamasabry.oc.feature.chat.rb.o43

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.feature.chat.FakeChatWorld
import io.github.eslamasabry.oc.feature.chat.Immediate
import io.github.eslamasabry.oc.feature.chat.RecordingNavigator
import io.github.eslamasabry.oc.feature.chat.Ref
import io.github.eslamasabry.oc.feature.chat.block
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.footer
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipActions
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipUi
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChoice
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderActions
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.header.presentation.ContinueChoice
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptWords
import io.github.eslamasabry.oc.feature.chat.header.ui.ApprovalChipSlot
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptReadingUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnMetadataUi
import io.github.eslamasabry.oc.feature.chat.prompt
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerMic
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/**
 * Order 43 (scoreboard group F): the `chat_approval_*` goldens, rendered by the real native chat with the composer
 * seam. Scene inputs from Flutter `test/goldens/chat_approval_chip_golden_test.dart` (FLUTTER_REF 8302de537): the "Fix
 * flaky checkout test" turn, approvals offered, the server's default model, a server that takes files (so "+" opens
 * the tray and its More opens the tools sheet, `test/support/composer_tools.dart`), and voice (the empty box's mic).
 *
 * Not rendered here: `chat_approval_sheet_{ask,auto,everything}`. Flutter opens an Approvals sheet from the "+"
 * sheet's "Approval settings…" row (`showSessionApprovalsSheet`); native has neither the row nor the sheet, only the
 * chip's popup menu, which the gate cannot capture (a second window root). Blocked on :feature:chat (evidence note
 * docs/design/native-ui-rebuild/evidence/score-F.md).
 */
class ChatApprovalGateTest {
    private val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
    private val headerActions = object : ChatHeaderActions {
        override fun openMenu() = Unit
        override fun closeMenu() = Unit
        override fun choose(action: HeaderAction) = Unit
        override fun copyTranscript(words: TranscriptWords, mask: (String) -> String) = Unit
        override fun renameRequested() = Unit
        override fun renameDone(title: String?) = Unit
        override fun closeSheet() = Unit
        override fun continueWith(choice: ContinueChoice) = Unit
        override fun retryContinueWith() = Unit
        override fun confirm() = Unit
        override fun dismissConfirm() = Unit
        override fun dismissFailure() = Unit
        override fun clipDone(id: Long) = Unit
    }
    private val approvalActions = object : ApprovalChipActions {
        override fun openApprovalMenu() = Unit
        override fun closeApprovalMenu() = Unit
        override fun chooseApproval(choice: ApprovalChoice) = Unit
        override fun confirmEverything() = Unit
        override fun cancelEverything() = Unit
        override fun dismissApprovalFailure() = Unit
    }
    private val composerActions = object : BasicComposerActions {
        override fun draftChanged(text: String) = Unit
        override suspend fun send(text: String) = true
        override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
    }

    /** Flutter's capture has voice, so the empty box shows the mic (kit_composer.dart:434 `_Trailing.mic`). */
    private val voice = object : KitComposerVoice {
        @Composable
        override fun Mic(field: KitVoiceField, modifier: Modifier) {
            KitComposerMic("Talk instead of typing", onClick = {}, modifier = modifier)
        }

        @Composable
        override fun Panel(field: KitVoiceField, modifier: Modifier) = Unit
    }
    private val host = object : ComposerHost {
        @Composable
        override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
            BasicComposer(
                BasicComposerUi(
                    available = true,
                    sending = false,
                    steers = false,
                    targetKey = "checkout",
                    models = ComposerModels(usesDefault = true),
                    filesAvailable = true,
                ),
                composerActions,
                chrome,
                modifier,
                voice = voice,
            )
        }
    }
    private val text =
        "The flakiness comes from `CheckoutBloc` racing the price refresh: `applyCoupon()` awaits the network " +
            "before the cart total has settled, so the assertion sometimes reads the stale total.\n\n" +
            "I made the test wait on the settled state instead of a fixed delay:"

    private fun state() =
        ChatUi.loading(Ref).copy(
            title = "Fix flaky checkout test",
            phase = ChatPhase.Live,
            rows =
                persistentListOf(
                    footer("msg_user"),
                    block("msg_user", "msg_assistant", "part_intro", 0, text),
                    prompt("msg_user", "Fix the flaky checkout test"),
                ),
        )

    private val facts =
        TurnFactsUi(
            reading =
                TranscriptReadingUi(metadata = mapOf("msg_user" to TurnMetadataUi(listOf("Sonnet 4"), 0, 0.0, true)))
        )

    private fun shot(name: String, approval: ApprovalChipUi, act: (ComposeUiTest.() -> Unit)? = null) {
        val state = state()
        val header =
            ChatHeaderSlot(
                ChatHeaderUi(title = state.title, projectName = "shopfront"),
                headerActions,
                approval = ApprovalChipSlot(approval, approvalActions),
            )
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, act = act) {
            KitStandaloneRedaction { ChatScreen(state, actions, host, header = header, facts = facts) }
        }
    }

    private fun mode(choice: ApprovalChoice, confirm: Boolean = false) =
        ApprovalChipUi(offered = true, choice = choice, confirmEverything = confirm)

    /** "+", then the tray's More: the tools sheet, as a person reaches it (Flutter `openComposerTools`). */
    private val openTools: ComposeUiTest.() -> Unit = {
        onNodeWithTag("chat-attach-button", useUnmergedTree = true).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(TRAY_UNFOLD_MS)
        onNodeWithTag("composer-tray-more", useUnmergedTree = true).performClick()
    }

    @Test fun approvalAsk() = shot("chat_approval_ask", mode(ApprovalChoice.Ask))

    @Test fun approvalAuto() = shot("chat_approval_auto", mode(ApprovalChoice.Auto))

    @Test fun approvalEverything() = shot("chat_approval_everything", mode(ApprovalChoice.Everything))

    @Test fun approvalPlus() = shot("chat_approval_plus", mode(ApprovalChoice.Auto), act = openTools)

    @Test fun approvalConfirm() = shot("chat_approval_confirm", mode(ApprovalChoice.Ask, confirm = true))

    private companion object {
        /** F: test/support/composer_tools.dart:11, the tray's unfold before More is tapped. */
        const val TRAY_UNFOLD_MS = 300L
    }
}
