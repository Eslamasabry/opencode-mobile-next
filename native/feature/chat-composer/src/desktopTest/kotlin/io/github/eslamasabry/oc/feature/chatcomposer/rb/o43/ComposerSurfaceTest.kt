package io.github.eslamasabry.oc.feature.chatcomposer.rb.o43

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class)
class ComposerSurfaceTest {
    @Test fun `composer token radius follows the selected school instead of the panel radius`() = runComposeUiTest {
        var style by mutableStateOf(Style.Solid)
        var wide by mutableStateOf(false)
        var look: KitSurfaceLook? = null
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(style = style, motion = MotionLevel.Off)) {
                val tokens = LocalKitTokens.current
                look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Floating, KitShapeId.Panel,
                    radius = (if (wide) tokens.composerRadiusWide else tokens.composerRadius).dp))
            }
        }
        fun outline() = look!!.shape.createOutline(Size(200f, 100f), LayoutDirection.Ltr, Density(1f))
        runOnIdle { assertEquals(26f, assertIs<Outline.Rounded>(outline()).roundRect.topLeftCornerRadius.x) }
        runOnIdle { wide = true }
        runOnIdle { assertEquals(18f, assertIs<Outline.Rounded>(outline()).roundRect.topLeftCornerRadius.x) }
        runOnIdle { style = Style.Paper }
        runOnIdle { assertIs<Outline.Rectangle>(outline()) }
    }
}
