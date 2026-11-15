package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitSelectMode
import io.github.eslamasabry.oc.ui.kit.KitSelectable
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
private class RecordingPlatformClipboard : Clipboard {
    var copied: String? = null
    @Suppress("OVERRIDE_DEPRECATION")
    override val nativeClipboard: NativeClipboard
        get() = error("unused")

    override suspend fun getClipEntry(): ClipEntry? = null

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val transferable = clipEntry?.nativeClipEntry as? java.awt.datatransfer.Transferable
        copied = transferable?.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
    }
}

private class RecordingToolbar : TextToolbar {
    var shown = false
    override var status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        shown = true
        status = TextToolbarStatus.Shown
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
    }
}

@OptIn(ExperimentalTestApi::class)
class KitSelectableModeTest {
    private fun ComposeUiTest.dragSelectAndCopy(tag: String) {
        onNodeWithTag(tag).performMouseInput {
            moveTo(Offset(1f, centerY))
            press()
            moveTo(Offset(centerX, centerY))
            moveTo(Offset(right, centerY))
            release()
            exit()
        }
        onRoot().performKeyInput {
            withKeyDown(Key.CtrlLeft) { pressKey(Key.C) }
        }
        waitForIdle()
    }

    @Test
    fun `B-KIT-SELECT-FINE FinePointer toggles selection with pointer kind while Always stays on`() =
        runComposeUiTest {
            val platform = FakeKitPlatform(KitSystemSignals(finePointer = false))
            val clipboard = RecordingPlatformClipboard()
            val toolbar = RecordingToolbar()
            var mode by mutableStateOf(KitSelectMode.FinePointer)

            setContent {
                CompositionLocalProvider(
                    LocalClipboard provides clipboard,
                    LocalTextToolbar provides toolbar,
                ) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), platform, "en", stillFrame = true) {
                        KitSelectable(mode = mode) {
                            KitText("Selectable phrase", Modifier.testTag("sel-text"))
                        }
                    }
                }
            }
            waitForIdle()

            // 1. FinePointer + touch (finePointer = false): no SelectionContainer, no selection/copy.
            onNodeWithTag("sel-text").performTouchInput { longClick() }
            dragSelectAndCopy("sel-text")
            assertFalse(toolbar.shown, "touch + FinePointer must not open selection toolbar")
            assertNull(clipboard.copied, "touch + FinePointer must not select or copy text")

            // 2. FinePointer + mouse (finePointer = true): SelectionContainer active, drag + copy works.
            platform.signals.value = KitSystemSignals(finePointer = true)
            waitForIdle()
            dragSelectAndCopy("sel-text")
            assertEquals("Selectable phrase", clipboard.copied, "finePointer + FinePointer must select and copy")

            // 3. Switching back to touch disables FinePointer selection again.
            clipboard.copied = null
            toolbar.shown = false
            platform.signals.value = KitSystemSignals(finePointer = false)
            waitForIdle()
            dragSelectAndCopy("sel-text")
            assertNull(clipboard.copied, "returning to touch disables FinePointer selection")

            // 4. Always stays selectable even when finePointer = false.
            mode = KitSelectMode.Always
            waitForIdle()
            onNodeWithTag("sel-text").performTouchInput { longClick() }
            waitForIdle()
            assertTrue(toolbar.shown, "Always mode must keep selection active on touch")
        }
}
