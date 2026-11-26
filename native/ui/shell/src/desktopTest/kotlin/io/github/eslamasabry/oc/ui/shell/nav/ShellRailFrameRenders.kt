package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** Picture-making only: asserts nothing. The shell's frame at phone widths (must not change) and at 600. */
@OptIn(ExperimentalTestApi::class)
class ShellRailFrameRenders {
    private val out = File("build/shellrail").also { it.mkdirs() }

    private fun shoot(name: String, wDp: Int, hDp: Int) {
        runDesktopComposeUiTest(wDp * 2, hDp * 2) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                    KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                        CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(wDp.toFloat(), hDp.toFloat())) {
                            ShellRailFrame(top = ChatsRoute.Home, onSelect = {}) { KitText("Conversations page") }
                        }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test
    fun render() {
        shoot("shell-phone-360x780", 360, 780)
        shoot("shell-phone-412x915", 412, 915)
        shoot("shell-wide-600x900", 600, 900)
        shoot("shell-wide-1280x800", 1280, 800)
    }
}
