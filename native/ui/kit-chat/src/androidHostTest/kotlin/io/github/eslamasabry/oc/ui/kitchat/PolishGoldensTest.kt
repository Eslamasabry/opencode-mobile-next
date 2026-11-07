package io.github.eslamasabry.oc.ui.kitchat

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S8 polish goldens (auditor sweep of a2e7d3fa5: P-001, P-002, P-004, P-011), dark and light, at text size 1.0 and
 * 1.3, on the audit's phone (412 dp wide at 420 dpi). Record: `:ui:kit-chat:recordRoborazziAndroidHostTest`; verify:
 * `:ui:kit-chat:verifyRoborazziAndroidHostTest`. Goldens live in `src/screenshots/Polish/<scene>-<variant>.png`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w412dp-h915dp-420dpi")
class PolishGoldensTest(private val variant: Variant) {
    data class Variant(val dark: Boolean, val fontScale: Float) {
        val name: String = (if (dark) "dark" else "light") + "-x" + fontScale
        override fun toString(): String = name
    }

    @get:Rule val compose = createComposeRule()

    private fun show(tag: String, scene: String, content: @Composable () -> Unit) {
        compose.setContent {
            ChatTestTheme(
                dark = variant.dark,
                fontScale = variant.fontScale,
                platform = AndroidKitPlatform(View(ApplicationProvider.getApplicationContext())),
                content = content,
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag(tag).captureRoboImage("src/screenshots/Polish/$scene-${variant.name}.png")
    }

    @Test fun code() = show("polish-code", "code-lines") { PolishCodeScene() }

    @Test fun chunks() = show("polish-chunks", "code-chunks") { PolishChunkScene() }

    @Test fun tray() = show("polish-tray", "composer-tray") { PolishTrayScene() }

    @Test fun work() = show("polish-work", "work-group") { PolishWorkScene() }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Variant> =
            listOf(Variant(true, 1.0f), Variant(true, 1.3f), Variant(false, 1.0f), Variant(false, 1.3f))
    }
}
