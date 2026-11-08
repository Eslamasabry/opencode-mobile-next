package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-173 (twin of Flutter #129): `KitDivider.prominent` is 1 dp in the theme's tertiary ink at 30 %, where the plain
 * divider is the one-physical-pixel hairline, so conversation rows read apart on OLED dark screens.
 */
@OptIn(ExperimentalTestApi::class)
class KitDividerProminentTest {
    private fun lineRows(prominent: Boolean): Pair<Int, Int> {
        var rows = 0
        var color = 0
        runComposeUiTest {
            var ground = Color.Unspecified
            var expected = Color.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(3f)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        stillFrame = true,
                        localeTag = "en",
                    ) {
                        val c = LocalKitColors.current
                        ground = c.ground
                        expected = c.text3.copy(alpha = 0.3f).compositeOver(c.ground)
                        Column(Modifier.width(40.dp).height(9.dp).background(c.ground).testTag("box")) {
                            Box(Modifier.height(4.dp))
                            KitDivider(prominent = prominent)
                        }
                    }
                }
            }
            val map = onNodeWithTag("box").captureToImage().toPixelMap()
            val x = map.width / 2
            val painted = (0 until map.height).filter { map[x, it] != ground }
            rows = painted.size
            if (prominent) {
                val got = map[x, painted.first()]
                assertTrue(
                    abs(got.red - expected.red) < 0.02f && abs(got.green - expected.green) < 0.02f &&
                        abs(got.blue - expected.blue) < 0.02f,
                    "prominent ink ${got.toArgb().toUInt().toString(16)} vs ${expected.toArgb().toUInt().toString(16)}",
                )
            }
            color = map[x, painted.first()].toArgb()
        }
        return rows to color
    }

    @Test
    fun `B-173-001 the prominent divider is 1 dp of tertiary ink at 30 percent, thicker than the hairline`() {
        val (prominentRows, prominentColor) = lineRows(prominent = true)
        val (hairlineRows, hairlineColor) = lineRows(prominent = false)
        assertEquals(3, prominentRows, "1 dp at density 3")
        assertTrue(hairlineRows < prominentRows, "the hairline stays thinner ($hairlineRows px)")
        assertTrue(prominentColor != hairlineColor, "a different ink from the hairline")
    }
}
