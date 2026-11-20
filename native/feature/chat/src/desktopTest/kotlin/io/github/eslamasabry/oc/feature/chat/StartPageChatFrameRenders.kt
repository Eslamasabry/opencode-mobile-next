package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitStartingConversation
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** Each half gets the same phone window, including the system insets and the kit's phone-width breakpoints. */
@OptIn(ExperimentalTestApi::class)
class StartPageChatFrameRenders {
    @Test
    fun firstSend() {
        if (System.getenv("GITHUB_ACTIONS") == "true") return
        val out = File("build/render/start-page-chat-frame").also(File::mkdirs)
        for (style in listOf(Style.Solid, Style.Glass)) {
            for (mode in listOf(AppearanceMode.Dark, AppearanceMode.Light)) {
                for (scale in listOf(1f, 1.3f)) render(out, style, mode, scale)
            }
        }
    }

    private fun render(out: File, style: Style, mode: AppearanceMode, scale: Float) {
        val prompt = "Check the project notes"
        val model = ComposerModel("demo", "model", "Big Pickle", false)
        val chat = ChatScene().apply {
            composer = composer.copy(models = ComposerModels(selected = model))
        }
        val state = ChatUi.loading(Ref).copy(
            phase = ChatPhase.Live,
            title = prompt,
            pending = persistentListOf(PendingSend(1, prompt, PendingState.Sending)),
        )
        runDesktopComposeUiTest(360, 780) {
            val settled = mutableStateOf(false)
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, scale),
                    LocalKitScreenInsets provides WindowInsets(top = 24.dp, bottom = 20.dp),
                ) {
                    KitTheme(KitAppearance(mode = mode, style = style, motion = MotionLevel.Off), DesktopKitPlatform(), "en", stillFrame = true) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            if (settled.value) {
                                chat.Screen(state, canGoBack = true, projectName = "Notes", agentName = "OpenCode", facts = TurnFactsUi.Empty)
                            } else {
                                KitStartingConversation(prompt, "Notes", model.label, "Sending…", "Back", {}, agent = "OpenCode")
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val start = onRoot().captureToImage().toAwtImage()
            runOnIdle { settled.value = true }
            waitForIdle()
            val end = onRoot().captureToImage().toAwtImage()
            val pair = BufferedImage(start.width + end.width, start.height, BufferedImage.TYPE_INT_ARGB)
            val graphics = pair.createGraphics()
            try {
                graphics.drawImage(start, 0, 0, null)
                graphics.drawImage(end, start.width, 0, null)
            } finally {
                graphics.dispose()
            }
            ImageIO.write(pair, "png", File(out, "${style.name.lowercase()}-${mode.name.lowercase()}-$scale.png"))
        }
    }
}
