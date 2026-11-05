package io.github.eslamasabry.oc.ui.kit.rb.o42

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ConfirmLayoutTest {
    @Test fun `C34 discard mark is above the question and Keep editing spans the compact rail`() = runComposeUiTest {
        var discarded = 0
        var kept = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                KitSheetHost {
                    KitConfirm(
                        KitConfirmSpec("Discard your note changes?", "Discard changes", KitConfirmKind.Discard, body = "Your edits will be lost."),
                        { discarded++ }, { kept++ },
                    )
                }
            }
        }
        val mark = onNodeWithTag("kit-confirm-mark", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = onNodeWithTag("kit-confirm-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(title.top > mark.bottom)
        assertEquals(mark.left, title.left)
        val confirm = onNode(hasText("Discard changes") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val cancel = onNode(hasText("Keep editing") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(confirm.width, cancel.width)
        assertTrue(cancel.top > confirm.bottom)
        onNodeWithText("Keep editing").performClick()
        assertEquals(1, kept)
        assertEquals(0, discarded)
    }

    @Test fun `C30 failed confirmation offers retry and working guards both answers`() = runComposeUiTest {
        var attempts = 0
        var cancellations = 0
        val working = androidx.compose.runtime.mutableStateOf(false)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                KitSheetHost {
                    KitConfirm(KitConfirmSpec("Delete conversation?", "Delete conversation", KitConfirmKind.Destructive),
                        { attempts++ }, { cancellations++ }, working = working.value, failed = true)
                }
            }
        }
        onNodeWithText("Try again").performClick()
        assertEquals(1, attempts)
        runOnIdle { working.value = true }
        onNodeWithText("Cancel").assertIsNotEnabled()
        onNodeWithText("Try again").performClick()
        onNodeWithText("Delete conversation").performClick()
        assertEquals(1, attempts)
        assertEquals(0, cancellations)
    }

    @Test fun `C34 wide answers use window class rather than the capped panel width`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(1280f, 800f)) {
                    KitSheetHost {
                        KitConfirm(KitConfirmSpec("Discard changes?", "Discard changes", KitConfirmKind.Discard), {}, {})
                    }
                }
            }
        }
        val confirm = onNode(hasText("Discard changes") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val cancel = onNode(hasText("Keep editing") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(confirm.top, cancel.top)
        assertTrue(cancel.right < confirm.left)
    }
}
