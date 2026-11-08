package io.github.eslamasabry.oc.feature.chat.rb.o43

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipUi
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChoice
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
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
    /** The o42 chat scene (the same turn, server and composer), at its window's root as Flutter's golden has it. */
    private val scene = ChatScene()

    private fun shot(name: String, approval: ApprovalChipUi, act: (ComposeUiTest.() -> Unit)? = null) {
        val state = scene.live()
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, act = act) {
            KitStandaloneRedaction { scene.Screen(state, approval = approval) }
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
