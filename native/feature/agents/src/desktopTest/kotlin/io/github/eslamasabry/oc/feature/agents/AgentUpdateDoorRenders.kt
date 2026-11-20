package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.EVERY_STATE
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.MARKS
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pictures, nothing asserted: the Agents page's phone tab at 360 dp with the second door to the agents' update sheet
 * ("Agent updates ready · Review"), light and dark. Writes `build/agent-update-door/<name>.png`.
 */
@OptIn(ExperimentalTestApi::class)
class AgentUpdateDoorRenders {
    private val out = File("build/agent-update-door").also { it.mkdirs() }

    @Test
    fun `renders of the Agents page with the agents' update door, at 360 dp`() {
        Locale.setDefault(Locale.ENGLISH)
        for (mode in listOf(AppearanceMode.Light, AppearanceMode.Dark)) {
            val density = 2.5f
            runDesktopComposeUiTest((360 * density).toInt(), (800 * density).toInt()) {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density),
                        LocalKitSecretSink provides KitSecretSink {},
                        LocalKitLogoLoader provides MARKS,
                    ) {
                        KitTheme(
                            appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                AgentsScreen(EVERY_STATE.copy(updatesReady = true), AgentsBentoTest.Recorder())
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                val file = File(out, "agents-update-door-${mode.name.lowercase()}.png")
                ImageIO.write(onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage(), "png", file)
            }
        }
    }
}
