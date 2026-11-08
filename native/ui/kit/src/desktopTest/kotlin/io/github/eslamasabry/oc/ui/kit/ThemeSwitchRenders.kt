package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.choice.KitModeSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitModeSegmented
import io.github.eslamasabry.oc.ui.kit.icon.KitPixelIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.awt.Color as AwtColor
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Desktop frame renders of the theme switch (theme-switch-premium, Verification › Motion review): for each style, the
 * mode control switching dark → light and light → dark, captured at fixed times after the tap. Not a test: it runs
 * only when `S33_RENDER_DIR` names a folder, and writes `<style>-strip.png` there, plus numbered frames under
 * `frames/<style>-<dir>/` for a video. Desktop renders show the look, not the smoothness: that is judged on the phone.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeSwitchRenders {
    private val outDir: File? = System.getenv("S33_RENDER_DIR")?.let(::File)
    private val stripTimes = listOf(0L, 40L, 80L, 120L, 160L, 220L, 300L, 400L, 520L, 700L)
    private val modes = listOf(AppearanceMode.Light, AppearanceMode.Dark, AppearanceMode.System, AppearanceMode.Saver)
    private val words =
        mapOf(
            AppearanceMode.Light to "Light",
            AppearanceMode.Dark to "Dark",
            AppearanceMode.System to "Auto",
            AppearanceMode.Saver to "Saver",
        )

    /** One switch to render: [style], [from] → [to]; also every [frameStep] ms into [frameDir] when set. */
    private class Switch(
        val style: Style,
        val from: AppearanceMode,
        val to: AppearanceMode,
        val frameStep: Long?,
        val frameDir: File?,
    )

    /** Captures [times] (ms after the tap) of [switch]. */
    private fun capture(switch: Switch, times: List<Long>): List<BufferedImage> {
        val style = switch.style
        val from = switch.from
        val to = switch.to
        val frameStep = switch.frameStep
        val frameDir = switch.frameDir
        val shots = mutableListOf<BufferedImage>()
        runComposeUiTest {
            var mode by mutableStateOf(from)
            val platform = FakeKitPlatform(frost = true)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f)) {
                    KitTheme(KitAppearance(mode = mode, style = style, motion = MotionLevel.Full), platform, "en") {
                        val ground = LocalKitColors.current.ground
                        Box(Modifier.requiredSize(360.dp, 112.dp).background(ground).testTag("stage").padding(16.dp)) {
                            val segments = modes.map { KitModeSegment(it, words.getValue(it), KitPixelIcons.of(it)) }
                            KitModeSegmented(segments, mode, "Mode") { mode = it }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            // The click action, not an injected touch: no press fill left on the frame, and t = 0 is the tap itself.
            onNode(androidx.compose.ui.test.hasText(words.getValue(to)).and(androidx.compose.ui.test.hasClickAction()))
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            var now = 0L
            val every = if (frameStep != null) (0L..760L step frameStep).toList() else emptyList()
            val wanted = (times + every).distinct().sorted()
            var frame = 0
            for (t in wanted) {
                if (t > now) {
                    mainClock.advanceTimeBy(t - now)
                    now = t
                }
                val img = onNodeWithTag("stage").captureToImage().toAwtImage()
                if (t in times) shots += img
                if (frameStep != null && frameDir != null && t % frameStep == 0L) {
                    ImageIO.write(img, "png", File(frameDir, "f%04d.png".format(frame++)))
                }
            }
        }
        return shots
    }

    @Test
    fun renderStrips() {
        val dir = outDir ?: return
        dir.mkdirs()
        for (style in Style.entries) {
            val name = style.storageName
            val rows =
                listOf(AppearanceMode.Dark to AppearanceMode.Light, AppearanceMode.Light to AppearanceMode.Dark).map {
                    (a, b) ->
                    val folder = "frames/$name-${a.name.lowercase()}-to-${b.name.lowercase()}"
                    val frames = File(dir, folder).apply { mkdirs() }
                    val title = "${words.getValue(a)} → ${words.getValue(b)}"
                    title to capture(Switch(style, a, b, 4L, frames), stripTimes)
                }
            val w = rows.first().second.first().width
            val h = rows.first().second.first().height
            val label = 44
            val strip = BufferedImage(w * stripTimes.size, (h + label) * rows.size, BufferedImage.TYPE_INT_RGB)
            val g = strip.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = AwtColor(24, 24, 28)
            g.fillRect(0, 0, strip.width, strip.height)
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 22)
            rows.forEachIndexed { r, (title, shots) ->
                shots.forEachIndexed { c, img ->
                    val y = r * (h + label)
                    g.color = AwtColor(230, 230, 235)
                    g.drawString("$name · $title · ${stripTimes[c]} ms", c * w + 12, y + 30)
                    g.drawImage(img, c * w, y + label, null)
                }
            }
            g.dispose()
            ImageIO.write(strip, "png", File(dir, "$name-strip.png"))
        }
    }
}
