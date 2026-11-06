package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * E02-S13: the kit declares its layout exceptions next to the part (native/docs/kit/LAYOUT-RULES.md): a list row's
 * highlight bleeds to the edges, a title may end in "…", a row group is one list; a chip's words may not be cut.
 */
@OptIn(ExperimentalTestApi::class)
class KitLayoutMarksTest {
    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) { content() }
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
}
