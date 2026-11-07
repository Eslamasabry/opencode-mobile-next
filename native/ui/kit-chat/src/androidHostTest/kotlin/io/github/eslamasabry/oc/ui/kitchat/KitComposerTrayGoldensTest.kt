package io.github.eslamasabry.oc.ui.kitchat

import android.view.View
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
 * E20-S20 goldens of the composer's "+" tray (Flutter `test/goldens/states_tray_{dark,light}.png`): closed and open,
 * dark/light × text 1.0/1.3 × en/ar (RTL), at 360 dp. Motion off (the tray shows at once). Record:
 * `:ui:kit-chat:recordRoborazziAndroidHostTest`; verify: `:ui:kit-chat:verifyRoborazziAndroidHostTest`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w412dp-h915dp-420dpi")
class KitComposerTrayGoldensTest(private val variant: Variant) {
    data class Variant(val dark: Boolean, val fontScale: Float, val localeTag: String) {
        val name: String =
            (if (dark) "dark" else "light") + "-" + (if (fontScale == 1f) "text100" else "text130") + "-" + localeTag
        override fun toString(): String = name
    }

    @get:Rule val compose = createComposeRule()

    private fun capture(open: Boolean) {
        compose.setContent {
            ChatTestTheme(
                dark = variant.dark,
                localeTag = variant.localeTag,
                platform = AndroidKitPlatform(View(ApplicationProvider.getApplicationContext())),
            ) {
                TrayScene(open = open, words = TrayWords.of(variant.localeTag), fontScale = variant.fontScale)
            }
        }
        compose.waitForIdle()
        val scene = if (open) "tray-open" else "tray-closed"
        compose.onNodeWithTag(TRAY_SCENE).captureRoboImage("src/screenshots/KitComposerTray/$scene-${variant.name}.png")
    }

    @Test fun trayOpen() = capture(open = true)

    @Test fun trayClosed() = capture(open = false)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Variant> =
            listOf(true, false).flatMap { dark ->
                listOf(1f, 1.3f).flatMap { scale -> listOf("en", "ar").map { Variant(dark, scale, it) } }
            }
    }
}
