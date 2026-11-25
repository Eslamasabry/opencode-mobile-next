package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class AppearanceB545RendersTest {
    private val out = File("build/b545-renders").also { it.mkdirs() }

    @Test
    fun `B-545 appearance screen renders option cards review picture`() {
        val prev = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            val ui =
                SettingsUi(
                    stack = persistentListOf(SettingsPage.Hub, SettingsPage.Appearance),
                    appearance =
                        AppearanceUi(
                            packs = persistentListOf(PackId.Opencode, PackId.Nord, PackId.Dracula),
                            style = SurfaceStyle.Solid,
                        ),
                )
            runDesktopComposeUiTest(360, 800) {
                setContent {
                    SettingsThemed(dark = true, width = 360f) {
                        SettingsScreen(ui, RecordingActions())
                    }
                }
                waitForIdle()
                val img = onRoot().captureToImage().toAwtImage()
                ImageIO.write(img, "png", File(out, "b545-before-appearance-dark.png"))
            }
        } finally {
            Locale.setDefault(prev)
        }
    }
}
