package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.ChatRestart
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.ReadOnlyChat
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Capture only (nothing asserted): the foot of an old agent conversation. "refused": the helper is up and truly
 * refused (the notice and its one way on); "waiting": the helper is still starting (the box is not ready yet, no
 * notice, no Continue; the page's status line names the stage). Solid and Glass, light and dark, 360 dp. Writes PNGs
 * to `OLD_AGENT_WAITS_OUT` (or the picture server's render-out folder); does nothing when neither exists.
 */
@OptIn(ExperimentalTestApi::class)
class OldAgentWaitsRenders {
    private val out =
        (
            System.getenv("OLD_AGENT_WAITS_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out").takeIf { it.isDirectory }?.resolve("old-agent-waits")
        )?.also(File::mkdirs)

    private val actions =
        object : BasicComposerActions {
            override fun draftChanged(text: String) = Unit

            override suspend fun send(text: String) = true

            override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
        }

    private val chrome = ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false })

    private fun shoot(refused: Boolean, style: Style, mode: AppearanceMode, name: String) {
        val o = out ?: return
        Locale.setDefault(Locale.forLanguageTag("en"))
        kitUiTest(945, 700) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.625f, 1f)) {
                    KitTheme(
                        appearance = KitAppearance(mode = mode, style = style, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                            BasicComposer(
                                BasicComposerUi(
                                    available = false,
                                    sending = false,
                                    steers = false,
                                    targetKey = "old",
                                    models = ComposerModels(usesDefault = true),
                                    readOnly = if (refused) ReadOnlyChat(restart = ChatRestart("antigravity", "/p", "Antigravity")) else null,
                                ),
                                actions,
                                chrome,
                                Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(o, "$name.png"))
        }
    }

    @Test
    fun `old agent foot renders`() {
        if (out == null) return
        for (style in listOf(Style.Solid, Style.Glass)) {
            for (mode in listOf(AppearanceMode.Light, AppearanceMode.Dark)) {
                val tag = "${style.name.lowercase()}-${mode.name.lowercase()}-360"
                shoot(true, style, mode, "refused-$tag")
                shoot(false, style, mode, "waiting-$tag")
            }
        }
    }
}
