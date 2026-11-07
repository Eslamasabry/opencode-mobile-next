package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.chevronEndPad
import kotlin.test.Test
import kotlin.test.assertEquals

/** Order 08: KitRow's rules from kit_row.dart (heights, the caret's rail, the unavailable form, the long-press menu). */
@OptIn(ExperimentalTestApi::class)
class KitRowTest {
    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) { content() }
            }
        }
    }

    @Test
    fun `RB08-ROW-001 the caret leaves the rail minus its ink inset after its box`() {
        // rail 16 - 4 = 12; caret-right ink ends .28125 of 20 dp short of its box.
        assertEquals(6.375f, chevronEndPad(20f).value, 0.001f)
        assertEquals(0f, chevronEndPad(60f).value)
    }

    @Test
    fun `RB08-ROW-002 one-line rows are 56 dp and two-line rows 64 dp, with or without a trailing target`() = runComposeUiTest {
        setContent {
            Themed {
                KitRowGroup {
                    KitRow("Model", leading = KitRowLeading.Glyph(KitIcons.model), trailing = KitRowTrailing.Chevron, onClick = {})
                    KitRow("Fix the login bug", supporting = "Finished 5h ago", leading = KitRowLeading.Glyph(KitIcons.chat), onClick = {})
                    KitRow(
                        "Conversation", leading = KitRowLeading.Glyph(KitIcons.chat),
                        trailing = KitRowTrailing.Menu("Conversation actions", listOf(KitMenuItem("Rename", {}))),
                    )
                }
            }
        }
        val rows = onAllNodes(androidx.compose.ui.test.hasTestTag("kit-row")).fetchSemanticsNodes()
        assertEquals(listOf(56f, 64f, 56f), rows.map { it.boundsInRoot.height })
    }

    @Test
    fun `RB08-ROW-003 an unavailable row's tap anywhere runs its enable flow`() = runComposeUiTest {
        var runs = 0
        setContent {
            Themed {
                KitRow("Voice input", unavailableReason = "Voice needs a model on this phone.", enable = KitLineAction("Download", { runs++ }))
            }
        }
        onNodeWithTag("kit-row").performSemanticsAction(SemanticsActions.OnClick)
        onNodeWithTag("kit-row-action", useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(2, runs)
    }

    @Test
    fun `RB08-ROW-004 a long-press opens the row's menu`() = runComposeUiTest {
        setContent {
            Themed { KitRow("Fix the login bug", menu = listOf(KitMenuItem("Rename", {})), onClick = {}) }
        }
        onNodeWithText("Rename", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Fix the login bug", useUnmergedTree = true).performTouchInput { longClick() }
        waitForIdle()
        onNodeWithText("Rename", useUnmergedTree = true).assertExists()
    }
}
