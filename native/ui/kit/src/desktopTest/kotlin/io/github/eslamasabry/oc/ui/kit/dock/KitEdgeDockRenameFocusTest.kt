@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Device QA 2026-11-11 (run 5, c): with the keyboard already open on the chat's message box, Rename left focus in that
 * box and the first typed characters went into it. The rename field takes focus as it appears; when the rename ends
 * focus goes to nothing, not back to the box.
 */
class KitEdgeDockRenameFocusTest {
    private val rings =
        listOf(
            KitDockRing("one", "say hi", null, KitDockRingState.Idle),
            KitDockRing("two", "Alpha", null, KitDockRingState.Idle),
        )

    private fun scene(block: androidx.compose.ui.test.ComposeUiTest.(KitEdgeDockState) -> Unit) =
        runDesktopComposeUiTest(360, 800) {
            val state = KitEdgeDockState()
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.fillMaxSize()) {
                        val box = rememberTextFieldState("")
                        val request = remember { FocusRequester() }
                        LaunchedEffect(Unit) { request.requestFocus() }
                        BasicTextField(box, Modifier.align(Alignment.BottomStart).fillMaxWidth().height(48.dp).focusRequester(request).testTag("message-box"))
                        KitEdgeDock(
                            KitDockModel(rings = rings, placement = KitDockPlacement(KitDockSide.End, 0.5f)),
                            object : KitDockIntents {},
                            TestDockWords,
                            insets = KitDockInsets(top = 24.dp, bottom = 48.dp),
                            state = state,
                        )
                    }
                }
            }
            waitForIdle()
            block(state)
        }

    @Test
    fun `rename focus - the message box has focus before the rename starts`() = scene { onNode(hasTestTag("message-box")).assertIsFocused() }

    @Test
    fun `rename focus - the rename field is focused as it appears`() = scene { state ->
        runOnIdle { state.startRename(0) }
        waitForIdle()
        onNode(hasContentDescription(TestDockWords.nickname)).assertIsFocused()
    }

    @Test
    fun `rename focus - the message box is not focused while renaming`() = scene { state ->
        runOnIdle { state.startRename(0) }
        waitForIdle()
        onNode(hasTestTag("message-box")).assertIsNotFocused()
    }

    @Test
    fun `rename focus - typed text lands in the rename field and not in the message box`() = scene { state ->
        runOnIdle { state.startRename(0) }
        waitForIdle()
        onNode(hasContentDescription(TestDockWords.nickname)).performTextInput("Zed")
        waitForIdle()
        assertEquals("Zed", runOnIdle { state.renameText }.takeLast(3))
        assertEquals("", runOnIdle { messageBoxText() })
    }

    @Test
    fun `rename focus - when the rename ends the message box does not take focus back`() = scene { state ->
        runOnIdle { state.startRename(0) }
        waitForIdle()
        runOnIdle { state.endRename(save = false, text = "") }
        waitForIdle()
        onNode(hasTestTag("message-box")).assertIsNotFocused()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.messageBoxText(): String =
        onNode(hasTestTag("message-box")).fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText)?.text.orEmpty()
}
