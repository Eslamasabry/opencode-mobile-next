package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import kotlin.test.Test

/**
 * F36 (first part): a conversation's open sheets survive a rebuild of the screen, a rotation on the phone. The page is
 * the real ChatScreen as ChatTypingFocusTest builds it (ChatScene); the rebuild is what ShellRestoreTest does by hand
 * because StateRestorationTester is not implemented on desktop: save the registry, drop the whole composition, compose
 * again from a registry built out of the saved values.
 */
@OptIn(ExperimentalTestApi::class)
class ChatSheetsSurviveRebuildTest {
    private fun ComposeUiTest.composer(): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("chat-composer-field")), useUnmergedTree = true)

    @Test
    fun `B-CHAT-420 an open sheet is still open after the screen is rebuilt`() =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            var ui by mutableStateOf(scene.live())
            var registry by mutableStateOf(SaveableStateRegistry(null) { true })
            var shown by mutableStateOf(true)
            setContent {
                E20Scene {
                    if (shown) {
                        CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { scene.Screen(ui) }
                    }
                }
            }
            waitForIdle()
            // The commands sheet opens from the keyboard as in ChatTypingFocusTest (Ctrl+K, the launcher key).
            composer().performClick()
            waitForIdle()
            onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
            waitForIdle()
            onNodeWithTag("chat-commands-sheet").assertExists("the commands sheet is shown")
            // A rebuild saves the registry, drops the whole composition and composes again from the saved values.
            runOnIdle {
                registry = SaveableStateRegistry(registry.performSave()) { true }
                shown = false
            }
            waitForIdle()
            runOnIdle { shown = true }
            waitForIdle()
            onNodeWithTag("chat-commands-sheet").assertExists("the commands sheet is still shown after the rebuild")
        }
}
