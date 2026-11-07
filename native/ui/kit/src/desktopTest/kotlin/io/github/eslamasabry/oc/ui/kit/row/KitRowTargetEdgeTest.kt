package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P-112: on Files the row's ⋯ (a labelled glyph, [KitRowTrailing.IconAction]) was centred at x≈975 px and the top
 * bar's ⋯ at x≈1005 px: the glyph target kept the 16 dp gutter while a row's own ⋯ menu and the bar's end 4 dp from
 * the edge. Every 48 dp glyph target at a row's end now ends on that line.
 */
@OptIn(ExperimentalTestApi::class)
class KitRowTargetEdgeTest {
    @Test
    fun `B-KIT-112 a row's glyph targets end on the edge line, words and chevrons keep the gutter`() {
        assertTrue(rowTargetOnEdge(KitRowTrailing.Menu("README.md", emptyList())))
        assertTrue(rowTargetOnEdge(KitRowTrailing.IconAction(KitLineAction("Actions", {}, icon = KitIcons.more))))
        assertFalse(rowTargetOnEdge(KitRowTrailing.Chevron))
        assertFalse(rowTargetOnEdge(KitRowTrailing.Action(KitLineAction("Turn on", {}))))
        assertFalse(rowTargetOnEdge(KitRowTrailing.None))
    }

    @Test
    fun `B-KIT-112 a row's labelled ⋯ and a row's ⋯ menu share one centre line, 28 dp from the edge`() =
        runDesktopComposeUiTest(412, 400) {
            setContent {
                KitTheme {
                    Column(Modifier.fillMaxWidth()) {
                        KitRow(
                            title = "README.md",
                            trailing = KitRowTrailing.IconAction(KitLineAction("Actions", {}, icon = KitIcons.more)),
                            onClick = {},
                        )
                        KitRow(
                            title = "lib",
                            trailing = KitRowTrailing.Menu("lib", listOf(KitMenuItem("Copy path", {}))),
                            onClick = {},
                        )
                    }
                }
            }
            val glyph = onNodeWithTag("kit-row-icon-action").getBoundsInRoot()
            val menu = onNodeWithTag("kit-row-menu").getBoundsInRoot()
            val glyphCentre = (glyph.left + glyph.right) / 2
            val menuCentre = (menu.left + menu.right) / 2
            assertTrue(abs((glyphCentre - menuCentre).value) < 0.5f, "glyph $glyphCentre, menu $menuCentre")
            assertEquals(28f, 412f - glyphCentre.value, 0.5f)
        }
}
