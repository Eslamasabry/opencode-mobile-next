@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Owner 2026-11-13 ("You are fixing these broken corners everywhere also right?"): on the phone the message box, a
 * row's context menu and the rename dialog drew their glass rim bright at the top-start and bottom-end corners and not
 * at all at the other two, so each pane read as a broken shape. Every glass pane's rim keeps at least the floor of its
 * own peak all the way round ([PaneRim]: rays square to every side and fanned across every corner, no stored picture):
 * the plain liquid pane (the message box's glass), a context menu, a joined body (the bar and the menu out of it), a
 * dialog panel, and the chosen panes (a chosen style card, a chosen segment), each on the dark ground and over Aurora,
 * Glass dark, at the phone's 2.625 px/dp. The liquid panes are drawn by the kit's desktop stand-ins, which draw the rim
 * with the shader text Android's library draws it with; the library's own plumbing on a device (its stroke, clip and
 * layer) is not seen here.
 */
class PaneRimFloorTest {
    /** The share of a pane's peak below which a stretch of rim fails: the floor (RimLight.FLOOR, .35) less anti-aliasing. */
    private val share = .28f

    /** Below this (0-255 colour distance) a stretch has no rim at all. */
    private val least = 10f

    @Test fun `rim floor - every glass pane keeps its rim all the way round, Glass dark on the ground`() = all(aurora = false)

    @Test fun `rim floor - every glass pane keeps its rim all the way round, Glass dark over Aurora`() = all(aurora = true)

    private fun all(aurora: Boolean) {
        val failures =
            PaneRimScenes.cases.map { case ->
                // One scene that cannot be drawn names itself; the others are still measured.
                case.name to (try { check(case, aurora) } catch (e: Throwable) { listOf("not drawn: ${e::class.simpleName}: ${e.message?.take(300)}") })
            }.filter { it.second.isNotEmpty() }
        assertTrue(
            failures.isEmpty(),
            "Rims below ${(share * 100).toInt()} % of the pane's peak or missing (${if (aurora) "Aurora" else "ground"}):\n" +
                failures.joinToString("\n\n") { (name, lines) -> "$name:\n" + lines.joinToString("\n") },
        )
    }

    private fun check(case: PaneRimScenes.Case, aurora: Boolean): List<String> {
        var result = emptyList<String>()
        runDesktopComposeUiTest(PaneRimScenes.WIDTH, PaneRimScenes.HEIGHT) {
            PaneRimScenes.settle(this) { PaneRimScenes.Scene(aurora) { case.content() } }
            val image: PixelMap = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
            val box =
                case.pane { matcher ->
                    // A tag or a state is on the part's own node; a card's words and its tap merge into one node.
                    val node =
                        onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
                            ?: onAllNodes(matcher).fetchSemanticsNodes().first()
                    PaneRimScenes.paneBox(node)
                }
            val samples = PaneRim.around(image, box, PaneRimScenes.DENSITY, case.only(box))
            // "Over Aurora" means the picture is behind the panes: a scene showing the flat ground there tests nothing.
            val page = if (aurora) PaneRimScenes.groundSpread(image) else Float.MAX_VALUE
            result =
                when {
                    page < PaneRimScenes.AURORA_SPREAD -> listOf("the Aurora is not drawn behind the panes (the page's light spreads ${page.toInt()} of 255)")
                    samples.isEmpty() -> listOf("no outline sampled in $box")
                    else -> PaneRim.weak(samples, share, least)
                }
        }
        return result
    }
}
