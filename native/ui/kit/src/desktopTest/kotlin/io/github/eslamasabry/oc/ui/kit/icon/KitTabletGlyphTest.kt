package io.github.eslamasabry.oc.ui.kit.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Verifies KitIcons.tablet renders visibly and differs from KitIcons.phone. */
@OptIn(ExperimentalTestApi::class)
class KitTabletGlyphTest {
    @Test
    fun `B-KIT-300 tablet glyph renders ink pixels and is distinct from phone`() {
        assertNotEquals(KitIcons.phone, KitIcons.tablet)
        runDesktopComposeUiTest(64, 64) {
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                    platform = FakeKitPlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    Box(Modifier.size(64.dp).background(LocalKitColors.current.ground), contentAlignment = Alignment.Center) {
                        KitIcon(KitIcons.tablet, size = KitIconSize.Large)
                    }
                }
            }
            waitForIdle()
            val map = onRoot().captureToImage().toPixelMap()
            val bg = map[0, 0]
            var ink = 0
            for (y in 0 until map.height) {
                for (x in 0 until map.width) {
                    val p = map[x, y]
                    if (abs(p.red - bg.red) + abs(p.green - bg.green) + abs(p.blue - bg.blue) > 0.15f) ink++
                }
            }
            assertTrue(ink >= 40, "Expected visible ink pixels for KitIcons.tablet, found $ink")
        }
    }
}
