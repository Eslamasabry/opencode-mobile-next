package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.persona.AgentPersona
import io.github.eslamasabry.oc.feature.agents.hub.AgentComputer
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsTab
import io.github.eslamasabry.oc.feature.agents.hub.PhoneDevice
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallation
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.logo.KitLogoLoader
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** The Agents page as a bento grid of tiles, and phone agents that connect on their own (owner 2026-11-08). */
@OptIn(ExperimentalTestApi::class)
class AgentsBentoTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private class Recorder : AgentsActions {
        val calls = mutableListOf<String>()

        override fun back() {}

        override fun startAdding() {}

        override fun cancelAdding() {}

        override fun edited() {}

        override fun check(address: String, password: String, folder: String) {}

        override fun add(name: String, address: String, password: String, folder: String) {}

        override fun readAgents(id: ProfileId) {}

        override fun newConversation(id: ProfileId) {}

        override fun refreshPhone() {}

        override fun requestRemoval(id: String) {
            calls += "remove:$id"
        }

        override fun cancelRemoval() {}

        override fun confirmRemoval() {}

        override fun openAgent(id: String) {
            calls += "open:$id"
        }

        override fun requestInstall(id: String) {
            calls += "install:$id"
        }

        override fun signInPhone(id: String) {
            calls += "signin:$id"
        }

        override fun connectPhoneHelper() {
            calls += "connect"
        }
    }

    @Composable
    private fun Screen(state: AgentsUi, actions: AgentsActions, width: Int = 412) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}, LocalKitLogoLoader provides MARKS) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.width(width.dp)) { AgentsScreen(state, actions) }
            }
        }
    }

    @Test
    fun `B-AGENT-186 every agent is a tile with its state line, and there is no Connect phone agents button`() =
        runComposeUiTest {
            setContent { Screen(EVERY_STATE, Recorder()) }
            onNode(hasContentDescription("Claude Code. Ready")).assertExists()
            onNode(hasContentDescription("Codex. Sign in needed")).assertExists()
            onNode(hasContentDescription("Gemini CLI. Not installed", substring = true)).assertExists()
            onNode(hasContentDescription("Kilo. Installing…")).assertExists()
            onNode(hasContentDescription("Antigravity. Phone check needed")).assertExists()
            onNodeWithText("Connect phone agents").assertDoesNotExist()
            // One status line replaces "Check this phone", and no Refresh row is left.
            onNodeWithText("This phone is ready · arm64 · Android 15").assertExists()
            onNodeWithText("Check again").assertExists()
            onNodeWithText("Check this phone").assertDoesNotExist()
            onNodeWithText("Refresh", substring = true).assertDoesNotExist()
            // The install note lives on the tile and in the sheet, not as a notice over the page.
            onNodeWithText("Antigravity is installed", substring = true).assertDoesNotExist()
            onNodeWithText("Install or update", substring = true).assertDoesNotExist()
        }

    @Test
    fun `B-AGENT-187 a tile has at most one small act, and a tap on the tile opens its agent`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Screen(EVERY_STATE, actions) }
        onNode(hasContentDescription("Install Gemini CLI") and hasClickAction()).performClick()
        onNode(hasContentDescription("Sign in to Codex") and hasClickAction()).performClick()
        onNode(hasContentDescription("Claude Code. Ready") and hasClickAction()).performClick()
        assertEquals(listOf("install:gemini", "signin:codex", "open:claude"), actions.calls)
        // Ready, installing and phone-check tiles carry no act of their own.
        onNode(hasContentDescription("Install Claude Code")).assertDoesNotExist()
        onNode(hasContentDescription("Install Kilo")).assertDoesNotExist()
    }

    @Test
    fun `B-AGENT-188 an agent with no bundled logo shows its first letter, never an empty box`() = runComposeUiTest {
        setContent { Screen(EVERY_STATE, Recorder()) }
        onNodeWithText("F", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B-AGENT-189 the sheet holds the agent's acts and the install note`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Screen(ANTIGRAVITY_SHEET, actions) }
        onNodeWithText("Antigravity is installed. Sign-in and the phone compatibility check are still needed before use.")
            .assertExists()
        onNodeWithText("Remove Antigravity").performClick()
        assertTrue("remove:agy" in actions.calls)
    }

    @Test
    fun `B-AGENT-182 a failed connection is one line with its reason and Try again`() = runComposeUiTest {
        val actions = Recorder()
        setContent {
            Screen(
                EVERY_STATE.copy(
                    helperConnectFailed = true,
                    helperConnectFailure = Failure.Runtime(RuntimeProblem.ServerStartUnavailable),
                ),
                actions,
            )
        }
        onNodeWithText("Phone agents are not connected: the agent helper did not start.").assertExists()
        onNode(hasText("Try again") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("connect"), actions.calls)
    }

    @Test
    fun `B-AGENT-181 nothing installed shows no failure line, even after a failure`() = runComposeUiTest {
        val nothing =
            AgentsUi(
                phone = PhoneAgentsState(available = true, agents = persistentListOf(GEMINI), owner = ProfileId("o")),
                helperConnectFailed = true,
                helperConnectFailure = Failure.Runtime(RuntimeProblem.Generic),
            )
        setContent { Screen(nothing, Recorder()) }
        onNodeWithText("Phone agents are not connected", substring = true).assertDoesNotExist()
        onNodeWithText("Try again").assertDoesNotExist()
        onNode(hasContentDescription("Install Gemini CLI")).assertExists()
    }

    @Test
    fun `B-AGENT-191 the phone's own helper is not a computer, and ready phone agents start a chat`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Screen(COMPUTERS, actions) }
        onNodeWithText("Phone agents").assertDoesNotExist()
        onNodeWithText("Desk").assertExists()
        onNodeWithText("Studio Mac").assertExists()
    }

    @Test
    fun `B-AGENT-192 the page's settings live in the top bar's menu, not under the agents`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Screen(EVERY_STATE, actions) }
        onNodeWithText("Reset saved sign-ins").assertDoesNotExist()
        onNodeWithText("More options").assertDoesNotExist()
        onNode(hasContentDescription("More actions") and hasClickAction()).performClick()
        onNodeWithText("Reset saved sign-ins").assertExists()
    }

    @Test
    fun `B-AGENT-181 while connecting or connected there is no line at all`() = runComposeUiTest {
        setContent { Screen(EVERY_STATE.copy(helperConnecting = true, helperConnectFailed = true), Recorder()) }
        onNodeWithText("Phone agents are not connected", substring = true).assertDoesNotExist()
    }

    /**
     * Not a check: the review renders. Runs only when `AGENTS_BENTO_RENDER_DIR` names a folder, and writes one phone
     * screen per image (412 x 915 dp at 2.625) of the page with one agent in each state, in Solid and Glass, light and
     * dark; the agent sheet; the failure line; and a tablet-width page.
     */
    @Test
    fun renders() {
        val out = System.getenv("AGENTS_BENTO_RENDER_DIR")?.let(::File) ?: return
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
        shoot(File(out, "menu-solid-dark.png"), Style.Solid, AppearanceMode.Dark, EVERY_STATE) {
            onNode(hasContentDescription("More actions") and hasClickAction()).performClick()
        }
        shoot(File(out, "phone-tablet-solid-dark.png"), Style.Solid, AppearanceMode.Dark, EVERY_STATE, widthDp = 900, heightDp = 1200)
    }

    private fun shoot(
        file: File,
        style: Style,
        mode: AppearanceMode,
        state: AgentsUi,
        widthDp: Int = 412,
        heightDp: Int = 915,
        then: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {},
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
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { AgentsScreen(state, Recorder()) }
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
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        }
    }

    private companion object {
        /** The phone's own helper profile: it is never listed as a computer. */
        val PHONE_HELPER = AgentComputer(ProfileId("owner-agents"), "Phone agents", "ws://127.0.0.1:4099", "/root/projects", connected = true)

        val GEMINI =
            PhoneAgentRow("gemini", "Gemini CLI", PhoneAgentStatus.NotInstalled, installable = true, downloadBytes = 340_000_000L)

        /** One agent in each state the phone reports. */
        val EVERY_STATE =
            AgentsUi(
                helperConnected = true,
                phone =
                    PhoneAgentsState(
                        available = true,
                        owner = ProfileId("owner"),
                        installation = PhoneAgentInstallation("kilo", PhoneAgentInstallPhase.Installing, 0.42),
                        conversations = PHONE_HELPER.id,
                        device = PhoneDevice("arm64", "15"),
                        agents =
                            persistentListOf(
                                PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready, removable = true, signOutAvailable = true),
                                PhoneAgentRow("codex", "Codex", PhoneAgentStatus.SignInNeeded, removable = true, signInAvailable = true),
                                GEMINI,
                                PhoneAgentRow("kilo", "Kilo", PhoneAgentStatus.NotInstalled, installable = true, downloadBytes = 98_000_000L),
                                PhoneAgentRow("agy", "Antigravity", PhoneAgentStatus.PhoneCheck, removable = true),
                                PhoneAgentRow("fx", "fx", PhoneAgentStatus.Stopped, removable = true),
                            ),
                    )
            )

        val COMPUTERS =
            EVERY_STATE.copy(
                tab = AgentsTab.Computers,
                computers =
                    persistentListOf(
                        PHONE_HELPER,
                        AgentComputer(ProfileId("desk"), "Desk", "ws://100.64.0.2:6767", "/home/me/app", connected = true),
                        AgentComputer(ProfileId("mac"), "Studio Mac", "ws://100.64.0.3:6767", "/Users/me/site", connected = true),
                    ),
                ready =
                    persistentMapOf(
                        ProfileId("desk") to persistentListOf("Claude Code", "Pi"),
                        ProfileId("mac") to persistentListOf("Claude Code"),
                    ),
            )

        /** Antigravity has just finished installing and its sheet is open: the install note is there. */
        val ANTIGRAVITY_SHEET =
            EVERY_STATE.copy(
                phone = EVERY_STATE.phone.copy(installation = PhoneAgentInstallation("agy", PhoneAgentInstallPhase.Done, 1.0)),
                sheetAgent = "agy",
            )

        /** The app's bundled marks (`androidApp/src/main/assets/marks/<slug>.svg`) by agent id. */
        val MARKS = KitLogoLoader { id, _ ->
            val slug = AgentPersona.agentMarkSlug(id) ?: return@KitLogoLoader null
            File(System.getProperty("user.dir"), "../../androidApp/src/main/assets/marks/$slug.svg").takeIf { it.isFile }?.readText()
        }
    }
}
