package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.ui.LanguageBody
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.settingsV3Language
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import org.jetbrains.compose.resources.stringResource

/**
 * Pictures only, asserts nothing: the Language bottom sheet with its new rows and round flag marks, dark and
 * light, the app in English (language "System default") and in Arabic (Arabic chosen), at 360 dp wide. Writes PNGs to `LANGUAGE_SHEET_OUT` (or the picture server's render-out folder); does nothing when neither
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

    private fun shoot(mode: AppearanceMode, tag: String, chosen: AppLanguage, name: String, text: Float) {
        Locale.setDefault(Locale.forLanguageTag(tag))
        runDesktopComposeUiTest(945, 2100) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.625f, text)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        localeTag = tag,
                        appearance = KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                    ) {
                        KitSheetHost(Modifier.fillMaxSize()) {
                            KitSheet(
                                visible = true,
                                onDismissRequest = {},
                                title = stringResource(Res.string.settingsV3Language),
                            ) {
                                LanguageBody(chosen, RecordingActions())
                            }
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
        for ((mode, look) in listOf(AppearanceMode.Dark to "dark", AppearanceMode.Light to "light")) {
            for ((scale, t) in listOf(1.0f to "t10", 1.3f to "t13")) {
                shoot(mode, "en", AppLanguage.System, "A-flags-en-$look-$t", scale)
                shoot(mode, "ar", AppLanguage.Arabic, "A-flags-ar-$look-$t", scale)
            }
        }
    }
}
