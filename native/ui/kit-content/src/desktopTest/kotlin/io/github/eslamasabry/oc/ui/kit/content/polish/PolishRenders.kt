@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.content.polish

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewer
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerContent
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerSource
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

class PolishRenders {
    companion object {
        const val PHASE = "before"
    }

    @Test
    fun `render kit-content polish scenes`() {
        val dir = File("build/polish-render").also { it.mkdirs() }
        val code =
            (1..20).joinToString("\n") { i ->
                "val resultLine$i = computeSomethingLongEnoughToWrapOnCompactPhoneWidth(inputValue = $i)"
            }
        runDesktopComposeUiTest(360, 640) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(360f, 640f)) {
                            Box(Modifier.size(360.dp, 640.dp)) {
                                KitViewer(
                                    name = "Checkout.kt",
                                    path = "src/main/kotlin/Checkout.kt",
                                    source = KitViewerSource(KitViewerContent.Code(code, language = "kotlin")),
                                    onClose = {},
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(
                onRoot().captureToImage().toAwtImage(),
                "png",
                File(dir, "580-$PHASE-viewer-wrap-360-1.0-dark.png"),
            )
        }
    }
}
