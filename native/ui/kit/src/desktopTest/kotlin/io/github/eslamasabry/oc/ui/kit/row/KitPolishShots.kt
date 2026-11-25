package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

private const val PHASE = "before"

/**
 * Polish sweep #6 (B-549): a value row over a plain chevron row at 360 dp, light and dark, text 1.0 and 1.3, English
 * and Arabic, in `build/polish-render/`. Not a check: a person compares the before and after pictures.
 */
@OptIn(ExperimentalTestApi::class)
class KitPolishShots {
    private data class Variant(val dark: Boolean, val locale: String, val scale: Float)

    private val variants =
        listOf(false, true).flatMap { dark ->
            listOf("en", "ar").flatMap { locale -> listOf(1f, 1.3f).map { scale -> Variant(dark, locale, scale) } }
        }

    @Test
    fun `6 a value row and a chevron row`() {
        val out = File("build/polish-render").also { it.mkdirs() }
        for (v in variants) {
            val name = "6-$PHASE-rows-${if (v.dark) "dark" else "light"}-${v.locale}-${v.scale}.png"
            runDesktopComposeUiTest(360, 2600) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, v.scale)) {
                        KitTheme(
                            appearance =
                                KitAppearance(
                                    mode = if (v.dark) AppearanceMode.Dark else AppearanceMode.Light,
                                    motion = MotionLevel.Off,
                                ),
                            platform = FakeKitPlatform(),
                            localeTag = v.locale,
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                Column {
                                    KitRow(
                                        "Project scope",
                                        trailing = KitRowTrailing.Value("All projects"),
                                        onClick = {},
                                    )
                                    KitRow("Notifications", trailing = KitRowTrailing.Chevron, onClick = {})
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, name))
            }
        }
    }
}
