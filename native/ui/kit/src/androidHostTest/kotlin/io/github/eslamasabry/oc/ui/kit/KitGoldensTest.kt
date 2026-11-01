package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P7, way 3 of 3: Roborazzi goldens of the P7 parts under Robolectric native graphics, light/dark × en/ar (plan 06
 * §15.3: 412 × 915 at 420 dpi, Solid, Graphite, text 1.0). Record: `:ui:kit:recordRoborazziAndroidHostTest`; verify:
 * `:ui:kit:verifyRoborazziAndroidHostTest`. Goldens live in `src/screenshots/<Part>/<scene>-<variant>.png`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [KitRobolectric.SDK], qualifiers = KitRobolectric.PHONE)
class KitGoldensTest(private val variant: Variant) {
    data class Variant(val dark: Boolean, val localeTag: String) {
        val name: String = (if (dark) "dark" else "light") + "-" + localeTag
        override fun toString(): String = name
    }

    @get:Rule val compose = createComposeRule()

    private val words = SceneWords.of(variant.localeTag)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            KitTheme(
                appearance = KitAppearance(
                    mode = if (variant.dark) AppearanceMode.Dark else AppearanceMode.Light,
                    motion = MotionLevel.Off,
                ),
                platform = KitRobolectric.platform(),
                localeTag = variant.localeTag,
                stillFrame = true,
                content = content,
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun kitButton() {
        show { ButtonsScene(words) }
        compose.onNodeWithTag("buttons").captureRoboImage("src/screenshots/KitButton/states-${variant.name}.png")
    }

    @Test
    fun kitFrameText() {
        show { TextScaleScene(words) }
        compose.onNodeWithTag("text").captureRoboImage("src/screenshots/KitFrameText/scale-1.0-${variant.name}.png")
    }

    @Test
    fun kitSheet() {
        show { SheetScene(words, visible = true, onDismiss = {}) }
        compose.onRoot().captureRoboImage("src/screenshots/KitSheet/open-${variant.name}.png")
    }

    @Test
    fun kitFrost() {
        show { Box(Modifier.fillMaxSize()) { FrostScene(words) } }
        compose.onNodeWithTag("header").captureRoboImage("src/screenshots/KitFrost/header-api${KitRobolectric.SDK}-${variant.name}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> =
            listOf(Variant(false, "en"), Variant(true, "en"), Variant(false, "ar"), Variant(true, "ar")).map { arrayOf(it) }
    }
}
