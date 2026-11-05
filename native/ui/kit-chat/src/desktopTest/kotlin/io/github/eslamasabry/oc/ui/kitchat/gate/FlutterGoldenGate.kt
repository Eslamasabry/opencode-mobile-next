package io.github.eslamasabry.oc.ui.kitchat.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/**
 * The golden-diff gate (looks pass, 2026-11-05): render a native kit part with the inputs and size of a named Flutter
 * kit golden (`test/goldens/kit/<name>_<light|dark>.png` at the product ref), and measure how far it is from it.
 *
 * Flutter's gallery shoots at 412 × 915 logical px and stores 412 × 915 px, so the native side renders at density 1 in
 * the same window. [Frame.Part] repeats `kitGalleryPart`'s body (ground, 16 dp top and bottom padding, centred, at most
 * 720 dp wide); [Frame.Screen] gives the content the whole window (for scenes that lay out their own backdrop).
 *
 * Output per shot, under `looks-out/gate/`: `<name>_<mode>-flutter.png`, `-native.png`, `-diff.png` (the native frame
 * dimmed, every differing pixel red) and one line in `report.tsv`: name, mode, differing pixels %, size check.
 * A pixel differs when any channel is more than [CHANNEL_SLACK] apart (anti-aliasing and font hinting noise).
 * Flutter goldens are read from `-Doc.flutterGoldens=<dir>` (default `looks-gate/flutter` beside the native tree).
 */
@OptIn(ExperimentalTestApi::class)
object FlutterGoldenGate {
    enum class Frame { Part, Screen }

    const val CHANNEL_SLACK: Int = 24
    private const val W = 412
    private const val H = 915

    private val root: File = File(System.getProperty("user.dir"), "../../..").canonicalFile
    private val flutterDir: File = File(System.getProperty("oc.flutterGoldens") ?: File(root, "looks-gate/flutter").path)
    private val out: File = File(root, "looks-out/gate").also { it.mkdirs() }

    /** Renders [content] in both modes and diffs each against `<name>_<mode>.png`; returns the diff % per mode. */
    fun check(name: String, frame: Frame = Frame.Part, motion: MotionLevel = MotionLevel.Off, content: @Composable () -> Unit): Map<String, Double> {
        Locale.setDefault(Locale.forLanguageTag("en"))
        val result = LinkedHashMap<String, Double>()
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            var native: BufferedImage? = null
            runDesktopComposeUiTest(W, H) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        KitTheme(
                            appearance = KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = motion),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    when (frame) {
                                        Frame.Screen -> content()
                                        Frame.Part ->
                                            Column(
                                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) { Box(Modifier.widthIn(max = 720.dp).fillMaxWidth()) { content() } }
                                    }
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_200)
                waitForIdle()
                native = onRoot().captureToImage().toAwtImage()
            }
            result[mode] = compare("${name}_$mode", native!!)
        }
        return result
    }

    private fun compare(shot: String, native: BufferedImage): Double {
        val goldenFile = File(flutterDir, "$shot.png")
        ImageIO.write(native, "png", File(out, "$shot-native.png"))
        if (!goldenFile.isFile) {
            File(out, "report.tsv").appendText("$shot\tmissing-flutter-golden\t-\n")
            return 100.0
        }
        val flutter = ImageIO.read(goldenFile)
        ImageIO.write(flutter, "png", File(out, "$shot-flutter.png"))
        val w = minOf(flutter.width, native.width)
        val h = minOf(flutter.height, native.height)
        val diff = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        var differing = 0L
        for (y in 0 until h) for (x in 0 until w) {
            val a = flutter.getRGB(x, y)
            val b = native.getRGB(x, y)
            val off = (0..16 step 8).any { s -> kotlin.math.abs((a shr s and 0xFF) - (b shr s and 0xFF)) > CHANNEL_SLACK }
            if (off) {
                differing++
                diff.setRGB(x, y, Color(230, 30, 30).rgb)
            } else {
                val g = ((b shr 16 and 0xFF) * 0.3 + (b shr 8 and 0xFF) * 0.59 + (b and 0xFF) * 0.11).toInt() / 3 + 40
                diff.setRGB(x, y, Color(g, g, g).rgb)
            }
        }
        ImageIO.write(diff, "png", File(out, "$shot-diff.png"))
        val pct = differing * 100.0 / (w.toLong() * h)
        val sizeNote = if (flutter.width == native.width && flutter.height == native.height) "same-size" else "size ${native.width}x${native.height} vs ${flutter.width}x${flutter.height}"
        File(out, "report.tsv").appendText("$shot\t${"%.2f".format(pct)}\t$sizeNote\n")
        return pct
    }
}
