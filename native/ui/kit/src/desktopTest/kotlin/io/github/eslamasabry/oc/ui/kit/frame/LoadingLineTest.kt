@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.effects.LIQUID_BAR_INSET_Y
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-11-14 (a conversation loading for ten seconds showed no sign of it): under Glass's floating bar the body
 * runs up under the pane, and the loading line, the body's first part, went up under the pane (or the status bar) as
 * soon as the body's header moved. The line now sits on the pane's lower edge, inside the window, at rest and scrolled.
 */
class LoadingLineTest {
    /** Desktop with the lens and a blur, as in PageEdgesTest: the bar floats here under Glass. */
    private class LensPlatform(private val base: FakeKitPlatform = FakeKitPlatform(frost = true, liquidGlass = true)) :
        KitPlatform by base {
        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            val at = arrayOfNulls<LayoutCoordinates>(1)
            return Modifier.onGloballyPositioned { at[0] = it }
                .clip(shape)
                .drawWithCache {
                    val layer = obtainGraphicsLayer()
                    val r = 6.dp.toPx()
                    onDrawBehind {
                        val c = at[0] ?: return@onDrawBehind
                        layer.renderEffect = BlurEffect(r, r)
                        layer.record { backdrop.draw(this, c) }
                        drawLayer(layer)
                        drawRect(spec.fill)
                    }
                }
        }
    }

    @Test
    fun `LOAD-3 under a floating bar the loading line lies on the pane's lower edge, at rest and scrolled`() =
        runDesktopComposeUiTest(W, H) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(KitAppearance(style = Style.Glass, mode = AppearanceMode.Dark), LensPlatform(), "en", stillFrame = true) {
                        CompositionLocalProvider(
                            LocalKitWindow provides KitWindowInfo(W.toFloat(), H.toFloat()),
                            LocalKitScreenInsets provides WindowInsets(top = STATUS_DP.dp, bottom = NAV_DP.dp),
                        ) {
                            Box(Modifier.size(W.dp, H.dp).testTag("scene")) {
                                KitScreen(
                                    page = true,
                                    topBar = { Box(Modifier.fillMaxWidth().height(BAR_DP.dp)) },
                                    floatingBar = KitFloatingBar(),
                                    loading = true,
                                    loadingLabel = "Loading",
                                    edges = KitScreenEdges.Bleed,
                                ) {
                                    val scroll = rememberScrollState()
                                    KitReportPageScroll(scroll)
                                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                                        repeat(ROWS) { Box(Modifier.fillMaxWidth().height(ROW_DP.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            assertOnTheEdge("at rest")
            drag(-MIDDLE_DP)
            assertOnTheEdge("scrolled")
        }

    private fun ComposeUiTest.assertOnTheEdge(at: String) {
        val pane = bounds("kit-liquid-bar")
        val line = bounds("kit-screen-loading")
        assertEquals(2f, line.height, .5f, "$at: the line keeps its 2 dp")
        assertTrue(line.top >= pane.bottom - .5f, "$at: the line $line lies under the pane $pane, not behind it")
        assertTrue(line.top <= pane.bottom + LIQUID_BAR_INSET_Y + .5f, "$at: the line $line lies on the pane's lower edge $pane")
        assertTrue(line.top >= STATUS_DP && line.bottom <= H, "$at: the line $line is inside the window, below the status bar")
    }

    private fun ComposeUiTest.bounds(tag: String): Rect = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** A slow drag of [dyDp] (negative: the content moves up), held before the finger lifts, so nothing flings. */
    private fun ComposeUiTest.drag(dyDp: Float) {
        onNodeWithTag("scene").performTouchInput {
            down(Offset(width / 2f, height * 0.7f))
            moveBy(Offset(0f, viewConfiguration.touchSlop * sign(dyDp)))
            repeat(STEPS) {
                advanceEventTime(16)
                moveBy(Offset(0f, dyDp / STEPS))
            }
            advanceEventTime(200)
            up()
        }
        waitForIdle()
    }

    private companion object {
        const val W = 360
        const val H = 640
        const val STATUS_DP = 24
        const val NAV_DP = 16
        const val BAR_DP = 56
        const val ROW_DP = 48
        const val ROWS = 40
        const val MIDDLE_DP = 300f
        const val STEPS = 20
    }
}
