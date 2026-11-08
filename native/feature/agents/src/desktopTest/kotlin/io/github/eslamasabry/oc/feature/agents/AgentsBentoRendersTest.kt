package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.ANTIGRAVITY_SHEET
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.COMPUTERS
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.EVERY_STATE
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.MARKS
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.PHONE_HELPER
import io.github.eslamasabry.oc.feature.agents.hub.AgentsTab
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
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
import kotlinx.collections.immutable.persistentListOf

/**
 * Review renders of the Agents page (native/agents-bento, 2026-11-08): one phone screen per image, both tabs, the
 * computers tab empty and connected, Solid and Glass, light and dark, plus the sheet, the failure line, the menu and a
 * tablet width. Not compared with anything: written to `AGENTS_BENTO_RENDER_DIR`, or under `oc.goldens.dir` when the
 * CI goldens lane records (so the runner's fonts draw them), and skipped otherwise.
 */
@OptIn(ExperimentalTestApi::class)
class AgentsBentoRendersTest {
    /**
     * Not a check: the review renders. Runs only when `AGENTS_BENTO_RENDER_DIR` names a folder, and writes one phone
     * screen per image (412 x 915 dp at 2.625) of the page with one agent in each state, in Solid and Glass, light and
     * dark; the agent sheet; the failure line; and a tablet-width page.
     */
    @Test
    fun renders() {
        Locale.setDefault(Locale.ENGLISH)
        // Review renders, never compared: written only when asked (the goldens lane, or a local folder).
        val out =
            System.getenv("AGENTS_BENTO_RENDER_DIR")?.let(::File)
                ?: System.getProperty("oc.goldens.dir")?.takeIf { System.getProperty("oc.goldens.record") == "true" }
                    ?.let { File(it, "agents-bento") }
                ?: return
        out.mkdirs()
        for (style in listOf(Style.Solid, Style.Glass))
            for (mode in listOf(AppearanceMode.Light, AppearanceMode.Dark)) {
                val look = "${style.storageName}-${mode.name.lowercase()}"
                shoot(File(out, "phone-$look.png"), style, mode, EVERY_STATE)
                shoot(File(out, "computers-empty-$look.png"), style, mode, EVERY_STATE.copy(tab = AgentsTab.Computers, computers = persistentListOf(PHONE_HELPER)))
                shoot(File(out, "computers-connected-$look.png"), style, mode, COMPUTERS)
            }
        shoot(File(out, "sheet-antigravity-solid-dark.png"), Style.Solid, AppearanceMode.Dark, ANTIGRAVITY_SHEET)
        shoot(File(out, "sheet-installing-glass-light.png"), Style.Glass, AppearanceMode.Light, EVERY_STATE.copy(sheetAgent = "kilo"))
        shoot(
            File(out, "phone-connect-failed-solid-light.png"),
            Style.Solid,
            AppearanceMode.Light,
            EVERY_STATE.copy(helperConnected = false, helperConnectFailed = true, helperConnectFailure = Failure.TimedOut()),
        )
        shoot(File(out, "phone-tablet-solid-dark.png"), Style.Solid, AppearanceMode.Dark, EVERY_STATE, widthDp = 900, heightDp = 1200)
        shoot(File(out, "menu-solid-dark.png"), Style.Solid, AppearanceMode.Dark, EVERY_STATE) {
            onNode(hasContentDescription("More actions") and hasClickAction()).performClick()
        }
    }

    private fun shoot(
        file: File,
        style: Style,
        mode: AppearanceMode,
        state: AgentsUi,
        widthDp: Int = 412,
        heightDp: Int = 915,
        then: ComposeUiTest.() -> Unit = {},
    ) {
        val density = 2.625f
        runDesktopComposeUiTest((widthDp * density).toInt(), (heightDp * density).toInt()) {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density),
                    LocalKitSecretSink provides KitSecretSink {},
                    LocalKitLogoLoader provides MARKS,
                ) {
                    KitTheme(
                        appearance = KitAppearance(mode = mode, style = style, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { AgentsScreen(state, AgentsBentoTest.Recorder()) }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            then()
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            // The page is the first root; an open menu is a popup root of its own (captured when it draws in the scene).
            ImageIO.write(onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage(), "png", file)
        }
    }

}
