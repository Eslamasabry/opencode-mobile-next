package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRowPanel
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-173 (twin of Flutter #129, home list): a [KitRowPanel] that asks for prominent dividers draws a line of at least
 * 1 dp between its rows on a dark screen, where the plain panel draws only the one-pixel hairline.
 */
@OptIn(ExperimentalTestApi::class)
class KitRowPanelProminentTest {
    private fun seamRows(prominent: Boolean): Int {
        var painted = 0
        runComposeUiTest {
            var fill = Color.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(3f)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        stillFrame = true,
                        localeTag = "en",
                    ) {
                        fill = LocalKitColors.current.surface1
                        Box(Modifier.width(300.dp).testTag("panel")) {
                            KitRowPanel(leadingMarks = false, prominentDividers = prominent) {
                                Box(Modifier.fillMaxWidth().height(20.dp))
                                Box(Modifier.fillMaxWidth().height(20.dp))
                            }
                        }
                    }
                }
            }
            val map = onNodeWithTag("panel").captureToImage().toPixelMap()
            val x = map.width / 2
            painted = (0 until map.height).count { y ->
                val c = map[x, y]
                kotlin.math.abs(c.red - fill.red) > 0.01f || kotlin.math.abs(c.green - fill.green) > 0.01f ||
                    kotlin.math.abs(c.blue - fill.blue) > 0.01f
            }
        }
        return painted
    }

    @Test
    fun `B-173-002 the home panel's prominent divider is 1 dp (3 px), the plain hairline thinner`() {
        val prominent = seamRows(prominent = true)
        val plain = seamRows(prominent = false)
        assertEquals(3, prominent, "1 dp at density 3")
        assertTrue(plain < prominent, "the plain hairline stays thinner ($plain px)")
    }
}
