package io.github.eslamasabry.oc.ui.kit.content

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens of the content parts under Robolectric native graphics, light/dark × en/ar (plan 06 §15.3:
 * 412 × 915 dp at 420 dpi, Solid, Graphite, text 1.0, motion Off). Record: `:ui:kit-content:recordRoborazziAndroidHostTest`;
 * verify: `:ui:kit-content:verifyRoborazziAndroidHostTest`. Goldens live in `src/screenshots/<Part>/<scene>-<variant>.png`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w412dp-h915dp-420dpi")
class ContentGoldensTest(private val variant: Variant) {
    data class Variant(val dark: Boolean, val localeTag: String) {
        val name: String = (if (dark) "dark" else "light") + "-" + localeTag
        override fun toString(): String = name
    }

    @get:Rule val compose = createComposeRule()

    private val words = ContentWords.of(variant.localeTag)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            KitTheme(
                appearance = KitAppearance(
                    mode = if (variant.dark) AppearanceMode.Dark else AppearanceMode.Light,
                    motion = MotionLevel.Off,
                ),
                platform = AndroidKitPlatform(View(ApplicationProvider.getApplicationContext())),
                localeTag = variant.localeTag,
                stillFrame = true,
                content = content,
            )
        }
        compose.waitForIdle()
    }

    private fun shot(part: String, scene: String) = "src/screenshots/$part/$scene-${variant.name}.png"

    @Test
    fun kitQr() {
        show { QrScene(words) }
        compose.onNodeWithTag("qr").captureRoboImage(shot("KitQr", "code-and-too-long"))
    }

    @Test
    fun kitViewerText() {
        show { TextViewerScene(words) }
        compose.onRoot().captureRoboImage(shot("KitViewerFrame", "text"))
    }

    @Test
    fun kitViewerCode() {
        show { CodeViewerScene(words) }
        compose.onRoot().captureRoboImage(shot("KitViewerCodeBody", "at-line-5"))
    }

    @Test
    fun kitViewerBinary() {
        show { BinaryViewerScene(words) }
        compose.onRoot().captureRoboImage(shot("KitViewerBinaryBody", "zip"))
    }

    @Test
    fun kitImageView() {
        show { ImageViewerScene() }
        compose.onRoot().captureRoboImage(shot("KitImageView", "fitted"))
    }

    @Test
    fun kitPdfPage() {
        show { PdfPagesScene() }
        compose.onNodeWithTag("pdf").captureRoboImage(shot("KitPdfPage", "ready-loading-failed"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> =
            listOf(Variant(false, "en"), Variant(true, "en"), Variant(false, "ar"), Variant(true, "ar")).map { arrayOf(it) }
    }
}
