package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.KitLayoutMarks
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitIconSwitchRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E02-S13: the kit declares its layout exceptions next to the part (native/docs/kit/LAYOUT-RULES.md): a list row's
 * highlight bleeds to the edges, a title may end in "…", a row group is one list; a chip's words may not be cut.
 */
@OptIn(ExperimentalTestApi::class)
class KitLayoutMarksTest {
    @Composable
    private fun Themed(width: Int = 412, height: Int = 915, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(width, height) { content() }
            }
        }
    }

    @Test
    fun `E02-S13 a list row bleeds to the edges and its group is one list`() = runComposeUiTest {
        setContent { Themed { KitRowGroup { KitRow("Service", onClick = {}) } } }
        val row = onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNode()
        assertEquals(KitEdge.Bleed, row.config.getOrNull(KitLayoutMarks.Edge))
        val group = onNodeWithTag("kit-row-group").fetchSemanticsNode()
        assertEquals("KitRowGroup", group.config.getOrNull(KitLayoutMarks.List))
    }

    @Test
    fun `E02-S13 a button does not bleed`() = runComposeUiTest {
        setContent { Themed { KitButton("Save", onClick = {}) } }
        val button = onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNode()
        assertNull(button.config.getOrNull(KitLayoutMarks.Edge))
    }

    @Test
    fun `E02-S13 titles may end in an ellipsis and body words and chips may not`() = runComposeUiTest {
        setContent {
            Themed {
                Column {
                    KitText("A title", role = TypeRole.Title, maxLines = 1)
                    KitText("Body words")
                    KitChip("Chip words")
                }
            }
        }
        val mark = { text: String ->
            val node = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            node.config.getOrNull(KitLayoutMarks.MayTruncate)
        }
        assertEquals("title", mark("A title"))
        assertNull(mark("Body words"))
        assertNull(mark("Chip words"))
    }

    @Test
    fun `E02-S13 switch rows and choice rows bleed like list rows`() = runComposeUiTest {
        setContent {
            Themed {
                Column {
                    KitSwitchRow("Quiet hours", checked = false, onCheckedChange = {})
                    KitIconSwitchRow("Check servers", KitIcons.server, checked = true, onCheckedChange = {})
                }
            }
        }
        val rows = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
        assertEquals(2, rows.size)
        rows.forEach { assertEquals(KitEdge.Bleed, it.config.getOrNull(KitLayoutMarks.Edge)) }
    }

    @Test
    fun `E02-S13 a top bar with no exit starts its title on the 16 dp gutter`() = runComposeUiTest {
        setContent { Themed { KitTopBar("Settings") } }
        val title = onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(16f, title.boundsInRoot.left)
    }

    @Test
    fun `E02-S13 a button holds the 400 dp control cap when its caller asks for the full width`() = runComposeUiTest {
        setContent { Themed(1280, 800) { KitButton("Save", onClick = {}, modifier = Modifier.fillMaxWidth()) } }
        val button = onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNode()
        assertTrue(button.size.width <= 400, "width ${button.size.width}")
    }
}
