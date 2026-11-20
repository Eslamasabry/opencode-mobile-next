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
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.AppStart
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStage
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
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
import kotlin.test.Test
import kotlinx.collections.immutable.toImmutableList

/**
 * The picture the owner saw on build 10114: Home while the agents START, five agent rows. Writes
 * `build/agents-start-home/<name>.png` (Solid and Glass, light, 360 dp, text 1.0 and 1.3); asserts nothing.
 */
@OptIn(ExperimentalTestApi::class)
class AgentsStartHomeRenders {
    private val out = File("build/agents-start-home").also { it.mkdirs() }
    private val phone = ProfileId("this-phone")
    private val agents = ProfileId("this-phone-agents")

    private fun state() = run {
        val titles =
            listOf(
                "Hi test" to "antigravity", "Tidy the docs demo README" to "antigravity", "Refactor the sync worker" to "claude",
                "Explain the build files" to "codex", "Plan the release notes" to "claude",
            )
        val items =
            titles.mapIndexed { i, (title, agent) ->
                item("a$i", at = S10_NOW - (12L + i * 9) * 60_000L, title = title)
                    .copy(agentId = agent, profileId = agents, sourceId = "paseo:${agents.raw}", directory = "/work/Lottie", projectName = "Lottie")
            }
        val base =
            ChatsState.build(
                ChatFeedSnapshot(items.toImmutableList()), ReturnBrief.None, ChatsFilters(), S10_NOW - 12 * 3_600_000L, true, nowMs = S10_NOW,
            )
        base.copy(
            home =
                base.home.copy(
                    stage = HomeStage.Helper,
                    sources =
                        kotlinx.collections.immutable.persistentListOf(
                            ChatsListSourceUi(phone, "This phone", shown = true, main = true, onThisPhone = true, unreachable = false),
                            ChatsListSourceUi(agents, "Agents on this phone", shown = true, main = false, onThisPhone = true, unreachable = false, agents = true, starting = AppStart.Agents),
                        ),
                ),
        )
    }

    private fun shoot(name: String, style: Style, fontScale: Float) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        runDesktopComposeUiTest(900, 2000) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.5f, fontScale)) {
                    ChatEnvironment {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off, style = style),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { ChatsScreen(state(), RecordingChatsActions()) }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test
    fun `renders of Home while the agents start`() {
        for (style in listOf(Style.Solid, Style.Glass))
            for (scale in listOf(1f, 1.3f)) shoot("agents-start-${style.name.lowercase()}-${if (scale == 1f) "1.0" else "1.3"}-light", style, scale)
    }
}
