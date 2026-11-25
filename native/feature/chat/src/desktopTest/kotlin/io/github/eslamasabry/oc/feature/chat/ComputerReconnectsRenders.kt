package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LinkRetryFacts
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pictures of a computer chat with kept rows whose connection is gone, at 360 dp, dark, text 1.0 and 1.3. BEFORE: the
 * Offline line with no retry time (Try again only). AFTER: the same line with the quiet loop's retry 8 s ahead (the
 * countdown shows), then the chat back to Live after the quiet reconnect. It asserts nothing; the pictures land in
 * `COMPUTER_RECONNECTS_OUT`, or in the module's `build/computer-reconnects`.
 */
@OptIn(ExperimentalTestApi::class)
class ComputerReconnectsRenders {
    @Test
    fun `computer reconnects renders`() {
        if (System.getenv("GITHUB_ACTIONS") == "true") return
        val out = System.getenv("COMPUTER_RECONNECTS_OUT")?.let(::File) ?: File("build/computer-reconnects")
        out.mkdirs()
        Locale.setDefault(Locale.forLanguageTag("en"))
        for (scale in listOf(1.0f, 1.3f)) {
            shoot(out, scale, "before-offline", offline(LinkRetryFacts.None))
            shoot(out, scale, "after-countdown", offline(LinkRetryFacts(retryAtMs = RETRY_IN_MS, nowMs = 0)))
            shoot(out, scale, "after-live", ChatScene().live())
        }
    }

    /** The kept rows with the Offline line exactly as the presenter builds it for a failed load. */
    private fun offline(retry: LinkRetryFacts): ChatUi =
        ChatScene().live().copy(status = retry.line(KEPT_OFFLINE_STATUS, ChatStatusKind.Offline))

    private fun shoot(out: File, scale: Float, name: String, state: ChatUi) {
        val scene = ChatScene()
        kitUiTest(WIDTH, HEIGHT) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, scale)) {
                    KitTheme(
                        appearance =
                            KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                scene.Screen(state, canGoBack = true, serverName = SERVER)
                            }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(SETTLE_MS)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name-$scale.png"))
        }
    }

    private companion object {
        /** 360 dp wide at the A54's density. */
        const val DENSITY = 2.625f
        const val WIDTH = 945
        const val HEIGHT = 2100
        const val SETTLE_MS = 800L
        const val RETRY_IN_MS = 8_000L
        const val SERVER = "Workstation"

        /** The presenter's id for the Offline line over kept rows. */
        const val KEPT_OFFLINE_STATUS = "chat-kept-offline"
    }
}
