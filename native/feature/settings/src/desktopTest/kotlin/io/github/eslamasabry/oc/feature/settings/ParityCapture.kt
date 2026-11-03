package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/**
 * UI-PARITY captures (P12 UI-SERVERS-SETTINGS): one screen state in the five frames the parity review compares with
 * the Flutter goldens — light and dark, English and Arabic (RTL), 2× system text (the kit caps frame text at 1.3×) and
 * a 1280 × 800 tablet. Always renders and runs the caller's checks; writes PNGs only when `-Poc.parityOut=<dir>` is
 * given (`oc.parity.out`), as `<dir>/<screen>-<variant>.png`.
 */
@OptIn(ExperimentalTestApi::class)
internal object ParityCapture {
    data class Variant(
        val name: String,
        val dark: Boolean,
        val locale: String,
        val fontScale: Float = 1f,
        val width: Int = 412,
        val height: Int = 915,
    )

    val variants: List<Variant> =
        listOf(
            Variant("light-en", dark = false, locale = "en"),
            Variant("dark-en", dark = true, locale = "en"),
            Variant("dark-ar", dark = true, locale = "ar"),
            Variant("light-en-text2x", dark = false, locale = "en", fontScale = 2f),
            Variant("light-en-tablet", dark = false, locale = "en", width = 1280, height = 800),
        )

    private val out: File? = System.getProperty("oc.parity.out")?.takeIf { it.isNotBlank() }?.let(::File)

    /** Renders [content] in every variant; [arrange] taps to the state, [check] asserts it before the capture. */
    fun capture(
        screen: String,
        only: List<Variant> = variants,
        arrange: ComposeUiTest.(Variant) -> Unit = {},
        check: ComposeUiTest.(Variant) -> Unit = {},
        content: @Composable (Variant) -> Unit,
    ) {
        val saved = Locale.getDefault()
        try {
            for (variant in only) {
                Locale.setDefault(Locale.forLanguageTag(variant.locale))
                runDesktopComposeUiTest(variant.width, variant.height) {
                    setContent { Frame(variant) { content(variant) } }
                    waitForIdle()
                    arrange(variant)
                    mainClock.advanceTimeBy(1_000)
                    waitForIdle()
                    check(variant)
                    out?.let { dir ->
                        dir.mkdirs()
                        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$screen-${variant.name}.png"))
                    }
                }
            }
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Composable
    private fun Frame(variant: Variant, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, variant.fontScale)) {
            KitTheme(
                appearance =
                    KitAppearance(mode = if (variant.dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
                platform = DesktopKitPlatform(),
                localeTag = variant.locale,
                stillFrame = true,
            ) {
                Box(Modifier.size(variant.width.dp, variant.height.dp).background(LocalKitColors.current.ground)) { content() }
            }
        }
    }
}
