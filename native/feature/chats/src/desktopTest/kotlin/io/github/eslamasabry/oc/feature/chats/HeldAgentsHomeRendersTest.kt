package io.github.eslamasabry.oc.feature.chats

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
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldAgents
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pictures of Home with agent updates held for Wi-Fi, at 360 dp wide, text 1.0 and 1.3, dark. Writes
 * `build/held-agents-home/<name>.png` to look at; nothing is compared.
 */
@OptIn(ExperimentalTestApi::class)
class HeldAgentsHomeRendersTest {
    private val out = File("build/held-agents-home").also { it.mkdirs() }

    private fun shoot(name: String, state: ChatsUi, fontScale: Float, dark: Boolean) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        val mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light
        runDesktopComposeUiTest(900, 2000) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.5f, fontScale)) {
                    ChatEnvironment {
                        KitTheme(
                            appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                ChatsScreen(state, RecordingChatsActions())
                            }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test
    fun `B-DL-408 renders of Home with agent updates held for Wi-Fi, at 360 dp`() {
        val agents = HomeHeldAgents(3, 660)
        val one = HomeHeldAgents(1, 360)
        val cases =
            listOf(
                "own-line-and-agents" to
                    HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting, moreAgents = agents),
                "one-more-agent" to
                    HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Waiting, moreAgents = one),
                "only-agents" to HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.AgentsWaiting(agents)),
                "opencode-helper-agents" to
                    HomeDownloadFact(
                        HomeDownloadKind.OpenCode,
                        HomeDownloadPhase.Waiting,
                        megabytes = 460,
                        withHelper = true,
                        moreAgents = agents,
                    ),
            )
        val base = mixed()
        for ((tag, fact) in cases)
            for (scale in listOf(1f, 1.3f))
                shoot("$tag-${if (scale == 1f) "1.0" else "1.3"}-dark", base.copy(download = fact), scale, true)
        // The mobile-data ask for OpenCode with its helper: the one total and what it names.
        val ask = cases.last().second
        val asking = base.copy(download = ask, downloadAsk = HomeDownloadKind.OpenCode)
        for (scale in listOf(1f, 1.3f)) {
            shoot("ask-opencode-helper-${if (scale == 1f) "1.0" else "1.3"}-dark", asking, scale, true)
        }
        assertTrue(out.listFiles().orEmpty().size >= 10)
    }
}
