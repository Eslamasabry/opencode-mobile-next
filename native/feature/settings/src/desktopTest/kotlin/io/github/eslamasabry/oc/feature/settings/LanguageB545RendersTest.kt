package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class LanguageB545RendersTest {
    private val out = File("build/b545-renders").also { it.mkdirs() }

    private fun near(a: Color, b: Color, tol: Float = 0.12f): Boolean =
        abs(a.red - b.red) < tol && abs(a.green - b.green) < tol && abs(a.blue - b.blue) < tol

    private fun leadingAccentCount(px: PixelMap, rowBounds: Rect, leadingPx: Float, accent: Color): Int {
        val left = rowBounds.left.roundToInt().coerceIn(0, px.width - 1)
        val right = (rowBounds.left + leadingPx).roundToInt().coerceIn(0, px.width - 1)
        val top = rowBounds.top.roundToInt().coerceIn(0, px.height - 1)
        val bottom = rowBounds.bottom.roundToInt().coerceIn(0, px.height - 1)
        var count = 0
        for (x in left..right) {
            for (y in top..bottom) {
                if (near(px[x, y], accent)) count++
            }
        }
        return count
    }

    @Test
    fun `B-545 language screen marks chosen row only with kit check and no flag ring`() {
        val prev = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            val ui =
                SettingsUi(
                    stack = persistentListOf(SettingsPage.Hub, SettingsPage.Language),
                    language = AppLanguage.English,
                )
            runDesktopComposeUiTest(360, 800) {
                var accent = Color.Unspecified
                setContent {
                    SettingsThemed(dark = true, width = 360f) {
                        accent = LocalKitColors.current.accent
                        SettingsScreen(ui, RecordingActions())
                    }
                }
                waitForIdle()
                onAllNodesWithTag("kit-choice-tick", useUnmergedTree = true).assertCountEquals(1)
                val img = onRoot().captureToImage()
                ImageIO.write(img.toAwtImage(), "png", File(out, "b545-before-language-dark.png"))
                val rowBounds = languageRow("English").fetchSemanticsNode().boundsInRoot
                val flagAccentPx = leadingAccentCount(img.toPixelMap(), rowBounds, 64f * density.density, accent)
                assertEquals(
                    0,
                    flagAccentPx,
                    "chosen English row flag must draw no accent ring (found $flagAccentPx accent px)",
                )
            }
        } finally {
            Locale.setDefault(prev)
        }
    }
}
