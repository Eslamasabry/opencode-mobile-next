@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitPaneColumnPage
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-KIT-310..312 (big screens, the shared frame): a Reading or List page fills the window (or its pane) up to the
 * 960 dp cap, starts at the gutter, and its header shares the body's left and right edges; only above the cap do two
 * equal margins appear. Swept every 40 dp from 600 to 1700 on a Settings group page (rows between the gutters), a long
 * form (a scrolling column) and a list page. Under 600 dp the frame is the phone's: nothing is capped.
 */
class SharedFrameFillTest {
    private enum class Sample(val width: KitScreenWidth, val edges: KitScreenEdges, val rows: Int) {
        GroupPage(KitScreenWidth.Reading, KitScreenEdges.Gutter, 6),
        LongForm(KitScreenWidth.Reading, KitScreenEdges.Bleed, 40),
        ListPage(KitScreenWidth.List, KitScreenEdges.Bleed, 30),
    }

    @Composable
    private fun Page(sample: Sample, wide: Float) {
        CompositionLocalProvider(
            LocalDensity provides Density(1f),
            LocalKitScreenInsets provides WindowInsets(),
        ) {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = FakeKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(wide, 800f)) {
                    KitScreen(
                        page = true,
                        topBar = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("bar")) },
                        width = sample.width,
                        edges = sample.edges,
                    ) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            repeat(sample.rows) { Box(Modifier.fillMaxWidth().height(48.dp).testTag("row-$it")) }
                        }
                    }
                }
            }
        }
    }

    private fun bounds(sample: Sample, wide: Int, inPane: Boolean = false): Pair<DpRect, DpRect> {
        var out: Pair<DpRect, DpRect>? = null
        runDesktopComposeUiTest(wide, 800) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    if (inPane) KitPaneColumnPage { Page(sample, wide.toFloat()) } else Page(sample, wide.toFloat())
                }
            }
            waitForIdle()
            out =
                onNodeWithTag("bar", useUnmergedTree = true).getBoundsInRoot() to
                    onNodeWithTag("row-0", useUnmergedTree = true).getBoundsInRoot()
        }
        return out!!
    }

    private fun gutterOf(sample: Sample) = if (sample.edges == KitScreenEdges.Gutter) GUTTER else 0f

    @Test
    fun `B-KIT-310 a Reading page fills its window up to the cap, then two equal margins, header on the body's edges`()
    {
        for (sample in Sample.entries) {
            for (w in 600..1700 step 40) {
                val (bar, row) = bounds(sample, w)
                val at = "${sample.name} at $w dp"
                val g = gutterOf(sample)
                // The header and the body share one pair of edges (the body is the header's box less the
                // gutter it keeps).
                assertTrue(
                    abs(row.left.value - bar.left.value - g) <= 1f,
                    "$at: header left ${bar.left} body left ${row.left}",
                )
                assertTrue(
                    abs(bar.right.value - row.right.value - g) <= 1f,
                    "$at: header right ${bar.right} body right ${row.right}",
                )
                val left = bar.left.value
                val right = w - bar.right.value
                if (w <= CAP) {
                    assertTrue(left <= 1f && right <= 1f, "$at: fills the window ($left, $right), no band")
                } else {
                    assertTrue(abs(left - right) <= 1f, "$at: two equal margins ($left, $right)")
                    assertEquals(CAP.toFloat(), (bar.right.value - bar.left.value), 1f, "$at: the cap is 960")
                }
            }
        }
    }

    @Test
    fun `B-KIT-311 beside a list the page fills the pane, no band wider than the gutter`() {
        for (sample in Sample.entries) {
            for (w in 420..1380 step 40) {
                val (bar, row) = bounds(sample, w, inPane = true)
                val at = "${sample.name} pane $w dp"
                val g = gutterOf(sample)
                assertTrue(
                    abs(row.left.value - bar.left.value - g) <= 1f,
                    "$at: header left ${bar.left} body left ${row.left}",
                )
                assertTrue(
                    abs(bar.right.value - row.right.value - g) <= 1f,
                    "$at: header right ${bar.right} body right ${row.right}",
                )
                val left = bar.left.value
                val right = w - bar.right.value
                if (w <= PAGE_CAP) {
                    assertTrue(left <= 1f && right <= 1f, "$at: the page fills the pane ($left, $right)")
                } else {
                    assertTrue(abs(left - right) <= 1f, "$at: past the cap two equal margins ($left, $right)")
                }
            }
        }
    }

    @Test
    fun `B-KIT-312 under 600 dp the page is the phone's, the header and body fill the window`() {
        for (sample in Sample.entries) {
            for (w in listOf(360, 412, 599)) {
                val (bar, row) = bounds(sample, w)
                assertEquals(0f, bar.left.value, 0.5f, "${sample.name} at $w")
                assertEquals(w.toFloat(), bar.right.value, 0.5f, "${sample.name} at $w")
                assertEquals(gutterOf(sample), row.left.value, 0.5f, "${sample.name} body at $w")
            }
        }
    }

    private companion object {
        const val GUTTER = 16f
        const val CAP = 960
        const val PAGE_CAP = 1000
    }
}
