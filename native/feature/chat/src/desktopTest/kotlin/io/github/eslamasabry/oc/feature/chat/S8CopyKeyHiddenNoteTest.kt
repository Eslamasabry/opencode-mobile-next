package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.feature.chat.E20Goldens.compareGolden
import io.github.eslamasabry.oc.feature.chat.header.presentation.ClipKind
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderClip
import io.github.eslamasabry.oc.feature.chat.presentation.CopiedUi
import io.github.eslamasabry.oc.feature.chat.ui.CopyKeyHiddenNotes
import io.github.eslamasabry.oc.feature.chat.ui.KeyHiddenNote
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E15-S06 (go/no-go a2e7d3fa5): a masked copy hid the keys but its "Copied. Anything that looked like a password or
 * key was hidden." line never showed. The line was composed beside the chat's KitScreen, which owns the window's one
 * status slot, so it had nowhere to go; inside the screen it shows in the status line.
 */
@OptIn(ExperimentalTestApi::class)
class S8CopyKeyHiddenNoteTest {
    private val masked = "Copied. Anything that looked like a password or key was hidden."
    private val transcriptMasked =
        "Transcript copied as Markdown. Anything that looked like a password or key was hidden."

    @Composable
    private fun Chat(inside: @Composable () -> Unit = {}) {
        KitScreen(
            modifier = Modifier.fillMaxSize(),
            page = true,
            topBar = { KitTopBar(title = "Greeting", exit = KitTopBarExit.Back, exitLabel = "Back", onExit = {}) },
            bottom = { inside() },
        ) {
            Box(Modifier.fillMaxSize()) { KitText("Hello there") }
        }
    }

    @Test
    fun `E15-S06 the note composed beside the screen, as before, never shows`() = runDesktopComposeUiTest(360, 640) {
        setContent {
            E20Scene {
                Chat()
                KeyHiddenNote(copyId = 1, masked = true, message = masked, dismissLabel = "Dismiss")
            }
        }
        waitForIdle()
        assertEquals(0, onAllNodesWithText(masked).fetchSemanticsNodes().size)
    }

    @Test
    fun `E15-S06 a masked message copy shows its note in the screen's status line`() {
        for (scale in listOf(1f, 1.3f)) runDesktopComposeUiTest(360, 640) {
            setContent { E20Scene(scale) { Chat { CopyKeyHiddenNotes(CopiedUi(1, masked = true), null) } } }
            waitForIdle()
            onNodeWithText(masked).assertIsDisplayed()
            compareGolden("s8-e15s06-copy-masked-${if (scale == 1f) "1x" else "1_3x"}")
        }
    }

    @Test
    fun `E15-S06 a masked transcript copy shows its own note, and a clean copy shows none`() {
        runDesktopComposeUiTest(360, 640) {
            setContent {
                E20Scene {
                    Chat { CopyKeyHiddenNotes(null, HeaderClip(3, "# Chat", ClipKind.Transcript, masked = true)) }
                }
            }
            waitForIdle()
            onNodeWithText(transcriptMasked).assertIsDisplayed()
        }
        runDesktopComposeUiTest(360, 640) {
            setContent { E20Scene { Chat { CopyKeyHiddenNotes(CopiedUi(2, masked = false), null) } } }
            waitForIdle()
            assertEquals(0, onAllNodesWithText(masked).fetchSemanticsNodes().size)
        }
    }
}
