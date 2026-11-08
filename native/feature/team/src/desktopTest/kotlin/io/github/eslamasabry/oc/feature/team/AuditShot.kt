package io.github.eslamasabry.oc.feature.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/**
 * Audit capture only (throwaway): renders one screen state in six variants at 412 x 915 dp, density 2.625, into
 * `$OC_LOOKS_DIR` (or `-Doc.looks.dir`), named `<module>__<screen>__<state>__<variant>.png`. A variant that throws is
 * logged to `failures.txt` and never fails the build.
 */
@OptIn(ExperimentalTestApi::class)
internal object Audit {
    private class V(val name: String, val style: Style, val dark: Boolean, val locale: String, val scale: Float)

    private val variants = listOf(
        V("solid-light", Style.Solid, false, "en", 1f),
        V("solid-dark", Style.Solid, true, "en", 1f),
        V("glass-light", Style.Glass, false, "en", 1f),
        V("glass-dark", Style.Glass, true, "en", 1f),
        V("solid-dark-ar", Style.Solid, true, "ar", 1f),
        V("solid-light-text13", Style.Solid, false, "en", 1.3f),
    )

    val out: File = File(System.getenv("OC_LOOKS_DIR") ?: System.getProperty("oc.looks.dir") ?: "build/audit-out").also { it.mkdirs() }

    private fun fail(id: String, t: Throwable) {
        synchronized(Audit::class.java) {
            File(out, "failures.txt").appendText("$id\t${t.javaClass.simpleName}: ${(t.message ?: "").lineSequence().firstOrNull().orEmpty().take(300)}\n")
        }
    }

    /** Runs [block] so one screen's exception is logged, not thrown. */
    fun guard(id: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) { fail(id, t) }
    }

    fun shoot(
        module: String,
        screen: String,
        state: String,
        advanceMs: Long = 1_000,
        setup: ComposeUiTest.() -> Unit = {},
        content: @Composable (dark: Boolean) -> Unit,
    ) {
        val saved = Locale.getDefault()
        try {
            for (v in variants) {
                val base = "${module}__${screen}__${state}__${v.name}"
                try {
                    Locale.setDefault(Locale.forLanguageTag(v.locale))
                    val rtl = v.locale == "ar"
                    runDesktopComposeUiTest(1081, 2402) {
                        setContent {
                            CompositionLocalProvider(
                                LocalDensity provides Density(2.625f, v.scale),
                                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                            ) {
                                KitTheme(
                                    appearance = KitAppearance(mode = if (v.dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off, style = v.style),
                                    platform = DesktopKitPlatform(),
                                    localeTag = v.locale,
                                    stillFrame = true,
                                ) {
                                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content(v.dark) }
                                }
                            }
                        }
                        waitForIdle()
                        mainClock.advanceTimeBy(advanceMs)
                        waitForIdle()
                        setup()
                        waitForIdle()
                        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$base.png"))
                    }
                } catch (t: Throwable) {
                    fail(base, t)
                }
            }
        } finally {
            Locale.setDefault(saved)
        }
    }
}
