package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.ui.LanguageBody
import io.github.eslamasabry.oc.feature.settings.ui.LanguageMarkStyle
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.settingsV3Language
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import org.jetbrains.compose.resources.stringResource

/**
 * Pictures only, asserts nothing: the Language bottom sheet with its new rows, in both round marks (A: flags, B:
 * letters), dark and light, the app in English (language "System default") and in Arabic (Arabic chosen), at 360 dp
 * wide. Writes PNGs to `LANGUAGE_SHEET_OUT` (or the picture server's render-out folder); does nothing when neither
 * exists.
 */
@OptIn(ExperimentalTestApi::class)
class LanguageSheetRenders {
    private val out =
        (System.getenv("LANGUAGE_SHEET_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out")
                    .takeIf { it.isDirectory }
                    ?.resolve("language-sheet"))
            ?.also(File::mkdirs)

    private fun shoot(marks: LanguageMarkStyle, mode: AppearanceMode, tag: String, chosen: AppLanguage, name: String) {
        Locale.setDefault(Locale.forLanguageTag(tag))
        runDesktopComposeUiTest(945, 2100) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.625f)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        localeTag = tag,
                        appearance = KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                    ) {
                        KitSheet(
                            visible = true,
                            onDismissRequest = {},
                            title = stringResource(Res.string.settingsV3Language),
                        ) {
                            LanguageBody(chosen, object : SettingsActions {}, marks)
                        }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test
    fun `language sheet renders`() {
        if (out == null) return
        val variants = listOf(LanguageMarkStyle.Flags to "A-flags", LanguageMarkStyle.Letters to "B-letters")
        for ((marks, variant) in variants) {
            for ((mode, look) in listOf(AppearanceMode.Dark to "dark", AppearanceMode.Light to "light")) {
                shoot(marks, mode, "en", AppLanguage.System, "$variant-en-$look")
                shoot(marks, mode, "ar", AppLanguage.Arabic, "$variant-ar-$look")
            }
        }
    }
}
