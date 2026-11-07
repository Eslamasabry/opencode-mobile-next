package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * E20 (owner's phone, 11-07) desktop goldens for the chat's craft stories. Density 1, so a golden's pixels are dp.
 * Record: `./gradlew :feature:chat:desktopTest -Poc.recordGoldens=true --tests '*E20S1*'`; files land in
 * `src/desktopTest/goldens/<name>.png`. A missing golden is written and the test fails once, so every new picture is
 * looked at before it counts.
 */
internal object E20Goldens {
    private val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens")
    private val record = System.getProperty("oc.goldens.record") == "true"
    private const val MAX_SHARE = 0.002
    private const val CHANNEL_SLACK = 8

    @OptIn(ExperimentalTestApi::class)
    fun ComposeUiTest.compareGolden(name: String) {
        val image = onRoot().captureToImage().toAwtImage()
        val file = File(dir, "$name.png")
        if (record || !file.isFile) {
            file.parentFile.mkdirs()
            ImageIO.write(image, "png", file)
            if (!record) fail("$name: no golden yet; recorded ${file.name}. Look at it, then run again.")
            return
        }
        val golden = ImageIO.read(file)
        assertTrue(
            golden.width == image.width && golden.height == image.height,
            "$name: size ${image.width}x${image.height} vs ${golden.width}x${golden.height}",
        )
        var differing = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val p = golden.getRGB(x, y)
            val q = image.getRGB(x, y)
            if (p != q && (0..24 step 8).any { abs((p shr it and 0xFF) - (q shr it and 0xFF)) > CHANNEL_SLACK })
                differing++
        }
        val share = differing.toDouble() / (image.width * image.height)
        if (share > MAX_SHARE) {
            val actual = File("build/golden-actual/$name.png").also { it.parentFile.mkdirs() }
            ImageIO.write(image, "png", actual)
            fail("$name: ${"%.3f".format(share * 100)} % of pixels differ; actual: $actual")
        }
    }

    /** The picture as it is now, for pixel checks. */
    @OptIn(ExperimentalTestApi::class)
    fun ComposeUiTest.picture(): BufferedImage = onRoot().captureToImage().toAwtImage()
}

/** The real kit theme at density 1 and the given text [scale], on the page's ground. */
@Composable
internal fun E20Scene(
    scale: Float = 1f,
    locale: String = "en",
    dark: Boolean = false,
    style: Style = Style.Solid,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
        KitTheme(
            appearance =
                KitAppearance(
                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                    motion = MotionLevel.Off,
                    style = style,
                ),
            platform = DesktopKitPlatform(),
            localeTag = locale,
            stillFrame = true,
        ) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.boundsOfText(text: String, substring: Boolean = false): Rect =
    onNodeWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.boundsOfTag(tag: String): Rect =
    onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

/** Whether the words of the text node [text] end in "…" (their layout overflows its box). */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.isCut(text: String): Boolean {
    val node = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
    val results = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.firstOrNull()?.hasVisualOverflow ?: false
}
