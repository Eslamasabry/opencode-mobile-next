package io.github.eslamasabry.oc.ui.kit.rb.o52

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FloatingPanelCornersTest {
    @Test
    fun `B-KIT-005 floating sheet radius rounds the bottom while an attached sheet keeps its edge`() =
        runComposeUiTest {
            var floating: Outline.Rounded? = null
            var attached: Outline.Rounded? = null
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                ) {
                    val input =
                        KitSurfaceInput(
                            SurfaceRole.Floating,
                            KitShapeId.Sheet,
                            SurfaceLevel.Surface2,
                        )
                    val density = LocalDensity.current
                    val direction = LocalLayoutDirection.current
                    floating =
                        kitSurfaceLook(input.copy(roundSheetBottom = true))
                            .shape
                            .createOutline(Size(400f, 300f), direction, density) as Outline.Rounded
                    attached =
                        kitSurfaceLook(input)
                            .shape
                            .createOutline(Size(400f, 300f), direction, density) as Outline.Rounded
                }
            }
            runOnIdle {
                val shape = floating!!.roundRect
                assertTrue(shape.bottomLeftCornerRadius.x > 0f)
                assertEquals(shape.topLeftCornerRadius, shape.bottomLeftCornerRadius)
                assertEquals(shape.topRightCornerRadius, shape.bottomRightCornerRadius)
                assertEquals(0f, attached!!.roundRect.bottomLeftCornerRadius.x)
            }
        }
}
