package io.github.eslamasabry.oc.ui.kit.rb.o07

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** KitActionBlock lays out as kit_buttons.dart does (order 07); each case failed on the block before the port. */
@OptIn(ExperimentalTestApi::class)
class KitActionBlockTest {
    @Composable
    private fun Scene(w: Int, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(w, 915) { content() }
            }
        }
    }

    private val save = KitAction("Save", {})
    private val cancel = KitAction("Cancel", {})

    @Test
    fun `B-KIT-011 a compact stack wraps two tertiary actions side by side at the column's start`() = runComposeUiTest {
        setContent {
            Scene(412) { KitActionBlock(primary = save, secondary = cancel, tertiary = listOf(KitAction("Duplicate", {}), KitAction("Rename", {}))) }
        }
        val first = onNodeWithText("Duplicate").fetchSemanticsNode().boundsInRoot
        val second = onNodeWithText("Rename").fetchSemanticsNode().boundsInRoot
        val primary = onNodeWithText("Save").fetchSemanticsNode().boundsInRoot
        // Flutter's KitInset Wrap: one line, Rename after Duplicate (before: each full width on its own line).
        assertEquals(first.top, second.top, "Duplicate $first and Rename $second share a line")
        assertTrue(second.left > first.right, "Rename follows Duplicate")
        // The words start at the column's edge, not centred under the primary.
        assertTrue(first.left < primary.left + 16f, "Duplicate starts at the start edge: $first vs $primary")
    }

    @Test
    fun `B-KIT-011 a reason or a destructive tertiary puts each tertiary action on its own line`() = runComposeUiTest {
        setContent {
            Scene(412) {
                KitActionBlock(
                    primary = save,
                    tertiary = listOf(KitAction("Restart", {}), KitAction("Delete server", {}, destructive = true)),
                )
            }
        }
        val first = onNodeWithText("Restart").fetchSemanticsNode().boundsInRoot
        val second = onNodeWithText("Delete server").fetchSemanticsNode().boundsInRoot
        assertTrue(second.top >= first.bottom, "Delete server sits under Restart")
        assertEquals(first.left, second.left, "both start at the same edge")
    }

    @Test
    fun `B-KIT-011 from medium up the buttons in the row are the touch target tall, not the full-width 52`() = runComposeUiTest {
        setContent { Scene(1280) { KitActionBlock(primary = save, secondary = cancel) } }
        val window = onNodeWithTag("window").fetchSemanticsNode().boundsInRoot
        val button = onNodeWithText("Save").fetchSemanticsNode()
        var node = button
        while (node.parent != null && node.parent!!.boundsInRoot.height < 60f && node.parent!!.boundsInRoot != window) node = node.parent!!
        assertEquals(48f, node.boundsInRoot.height, "Save is 48 dp tall in a row (Dart minimumSize minTarget)")
    }

    @Test
    fun `B-KIT-011 a disabled tertiary action's reason shows as its own line`() = runComposeUiTest {
        setContent {
            Scene(412) {
                KitActionBlock(
                    secondary = cancel,
                    tertiary = listOf(KitAction("Duplicate", null, enabled = KitEnabled.Disabled("Fill in the server address first."))),
                )
            }
        }
        val action = onNodeWithText("Duplicate").fetchSemanticsNode().boundsInRoot
        val reason = onNodeWithText("Fill in the server address first.").fetchSemanticsNode().boundsInRoot
        assertTrue(reason.top >= action.bottom, "the reason sits under its action")
    }
}
