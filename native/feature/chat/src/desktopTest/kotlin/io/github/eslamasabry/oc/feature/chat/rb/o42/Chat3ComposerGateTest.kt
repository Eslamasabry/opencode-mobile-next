package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptReadingUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.prompt
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/**
 * Flutter test/revamp/chat_3_golden_test.dart (unit chat-3, the composer) on the real native chat: an untitled
 * conversation with no project and two prompts that got no reply ("Review the parser for edge cases", "Write the
 * migration notes"), the server's default model, approvals asked first. Each scene at 412 x 915 and 1280 x 800.
 *
 * Not rendered yet: `chat_3_composer_sign_in` (needs the composer's no-model sign-in state in the fake),
 * `chat_3_history_sheet` and `chat_3_prompt_editor` (their sheets come from :feature:chat-composer hosts).
 */
class Chat3ComposerGateTest {
    private val scene = ChatScene()

    private fun state(busy: Boolean = false): ChatUi = scene.live().copy(
        title = null,
        rows = persistentListOf(prompt("m2", "Write the migration notes"), prompt("m1", "Review the parser for edge cases")),
        live = if (busy) LiveTurnUi(stopping = false) else null,
    )

    /** chat_3_golden_test.dart: the last prompt got no reply, so its turn says so ("No reply came back"). */
    private val facts = TurnFactsUi(reading = TranscriptReadingUi(endNotes = mapOf("m2" to TurnEndNote.Silent)))

    /** [names]: the phone golden and its 1280 x 800 twin. */
    private fun shot(names: List<String>, draft: String = "", busy: Boolean = false, act: (ComposeUiTest.() -> Unit)? = null) {
        scene.composer = scene.composer.copy(draft = DraftFacts(text = draft), steers = busy)
        val state = state(busy)
        for (name in names) {
            FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, act = act) {
                KitStandaloneRedaction { scene.Screen(state, facts = facts, projectName = null) }
            }
        }
    }

    /** "+", then the tray's More (Flutter test/support/composer_tools.dart `openComposerTools`). */
    private val openTools: ComposeUiTest.() -> Unit = {
        onNodeWithTag("chat-attach-button", useUnmergedTree = true).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(TRAY_UNFOLD_MS)
        onNodeWithTag("composer-tray-more", useUnmergedTree = true).performClick()
    }

    @Test fun composerText() = shot(listOf("chat_3_composer_text", "chat_3_composer_text_1280x800"), draft = "Tidy the parser and add a test")

    @Test fun composerBusy() = shot(listOf("chat_3_composer_busy", "chat_3_composer_busy_1280x800"), draft = "Also check the lexer", busy = true)

    @Test fun toolsSheet() = shot(listOf("chat_3_tools_sheet", "chat_3_tools_sheet_1280x800"), act = openTools)

    private companion object {
        /** F: test/support/composer_tools.dart:11, the tray's unfold before More is tapped. */
        const val TRAY_UNFOLD_MS = 300L
    }
}
