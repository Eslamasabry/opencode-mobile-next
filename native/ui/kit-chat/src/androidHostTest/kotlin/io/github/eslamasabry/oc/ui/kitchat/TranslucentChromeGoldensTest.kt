package io.github.eslamasabry.oc.ui.kitchat

import android.view.View
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
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
 * E20-S30 goldens: the transcript under the translucent composer (API 37, so the real backdrop blur) — newest at the
 * bottom, scrolled away with the "Jump to latest" pill, and scrolled away with the "+" tray open; dark and light.
 * Record: `:ui:kit-chat:recordRoborazziAndroidHostTest`; verify: `:ui:kit-chat:verifyRoborazziAndroidHostTest`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w412dp-h915dp-420dpi")
class TranslucentChromeGoldensTest(private val dark: Boolean) {
    @get:Rule val compose = createComposeRule()

    private val theme: String get() = if (dark) "dark" else "light"

    private fun capture(scene: String, trayOpen: Boolean, away: Boolean) {
        compose.setContent {
            ChatTestTheme(dark = dark, platform = AndroidKitPlatform(View(ApplicationProvider.getApplicationContext()))) {
                TranslucentChromeScene(trayOpen = trayOpen)
            }
        }
        compose.waitForIdle()
        if (away) {
            compose.onNodeWithTag("transcript-list").performScrollToIndex(12)
            compose.waitForIdle()
        }
        compose.onNodeWithTag(TRANSLUCENT_SCENE).captureRoboImage("src/screenshots/TranslucentChrome/$scene-$theme.png")
    }

    @Test fun newestAtBottom() = capture("newest-at-bottom", trayOpen = false, away = false)

    @Test fun awayWithPill() = capture("away-pill", trayOpen = false, away = true)

    @Test fun awayTrayOpen() = capture("away-tray-open", trayOpen = true, away = true)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Boolean> = listOf(true, false)
    }
}
