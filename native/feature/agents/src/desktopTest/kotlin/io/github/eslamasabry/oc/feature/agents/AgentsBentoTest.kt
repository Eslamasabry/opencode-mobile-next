package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.text.PhoneSizes
import io.github.eslamasabry.oc.engine.agents.persona.AgentPersona
import io.github.eslamasabry.oc.feature.agents.hub.AgentComputer
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsTab
import io.github.eslamasabry.oc.feature.agents.hub.PhoneDevice
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallation
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRemoval
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentUpdateNote
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.logo.KitLogoLoader
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.text.KitWholeWordsRules
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    internal class Recorder : AgentsActions {
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

        override fun newPhoneConversation(agent: String) {
            calls += "chat:$agent"
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
            onNode(hasContentDescription("This phone is ready")).assertExists()
            onNode(hasText("Refresh") or hasContentDescription("Refresh")).assertExists()
            onNodeWithText("Check this phone").assertDoesNotExist()
            onNodeWithText("Refresh", substring = true).assertDoesNotExist()
            // The install note lives on the tile and in the sheet, not as a notice over the page.
            onNodeWithText("Antigravity is installed", substring = true).assertDoesNotExist()
            onNodeWithText("Install or update", substring = true).assertDoesNotExist()
        }

    // The automatic update's line: after three agents were updated in a row it named only the last one (the
    // updater's last decision carries one agent). It names every agent it updated, in the order they finished; one
    // stays one name.
    @Test
    fun `B-AGENT-UPD-011 the update note names every agent it updated, in the order they finished`() =
        runComposeUiTest {
            val updated =
                PhoneAgentsState(
                    available = true,
                    owner = ProfileId("owner"),
                    device = PhoneDevice("arm64", "15"),
                    updateNote = PhoneAgentUpdateNote.Updated,
                    updateAgentName = "Kilo",
                    updatedAgentNames = persistentListOf("Claude Code", "Codex", "Kilo"),
                )
            var page by mutableStateOf(AgentsUi(phone = updated))
            setContent { Screen(page, Recorder()) }
            onNode(
                hasContentDescription("This phone is ready · updated Claude Code, Codex, Kilo")
            ).assertExists()
            // One agent updated: one name, exactly as before.
            page =
                AgentsUi(
                    phone =
                        updated.copy(
                            updateAgentName = "Claude Code",
                            updatedAgentNames = persistentListOf("Claude Code"),
                        ),
                )
            waitForIdle()
            onNode(
                hasContentDescription("This phone is ready · updated Claude Code")
            ).assertExists()
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

    // Owner 2026-11-13: Gemini CLI is no longer offered, but a copy already on the phone must not be stranded.
    @Test
    fun `B-AGENT-189 an installed Gemini CLI is still listed with Remove and no Install`() = runComposeUiTest {
        val actions = Recorder()
        val installed =
            PhoneAgentRow("gemini", "Gemini CLI", PhoneAgentStatus.Unavailable, removable = true, signInUnchecked = true)
        setContent {
            Screen(
                AgentsUi(
                    phone = PhoneAgentsState(available = true, agents = persistentListOf(installed), owner = ProfileId("o")),
                    sheetAgent = "gemini",
                ),
                actions,
            )
        }
        onNode(hasContentDescription("Install Gemini CLI")).assertDoesNotExist()
        onNodeWithText("Remove Gemini CLI").performClick()
        assertTrue("remove:gemini" in actions.calls)
    }

    // Small-faults 301: a retired agent left by an update from 2.1.5 (Gemini CLI, its phone check due) says it is no
    // longer offered and offers only Remove: never "Phone check needed", never "install it again".
    @Test
    fun `B-AGENT-199 a retired agent says it is no longer offered and offers only Remove`() = runComposeUiTest {
        val actions = Recorder()
        val retired =
            PhoneAgentRow("gemini", "Gemini CLI", PhoneAgentStatus.PhoneCheck, removable = true, retired = true)
        val phone = PhoneAgentsState(available = true, agents = persistentListOf(retired), owner = ProfileId("o"))
        var sheet by mutableStateOf<String?>(null)
        setContent { Screen(AgentsUi(phone = phone, sheetAgent = sheet), actions) }
        // The tile: the retired words, never the phone check, and no install or check act.
        onNode(hasContentDescription("Gemini CLI. No longer offered on this phone")).assertExists()
        onNodeWithText("Phone check needed", substring = true).assertDoesNotExist()
        onNode(hasContentDescription("Check Gemini CLI")).assertDoesNotExist()
        onNode(hasContentDescription("Install Gemini CLI")).assertDoesNotExist()
        // The sheet: why, and Remove as its only act; never "install it again".
        sheet = "gemini"
        waitForIdle()
        onNodeWithText("no longer offered on this phone", substring = true).assertExists()
        onNodeWithText("Install it again", substring = true).assertDoesNotExist()
        onNodeWithText("Remove Gemini CLI").performClick()
        assertTrue("remove:gemini" in actions.calls)
    }

    // Small-faults 301: "Program removed. Freed 101982208 bytes." was a raw byte count in the copy.
    @Test
    fun `B-AGENT-200 the removal message words the freed space as a size, never a raw byte count`() = runComposeUiTest {
        val row = PhoneAgentRow("codex", "Codex", PhoneAgentStatus.Ready, removable = true)
        setContent {
            Screen(
                AgentsUi(
                    phone = PhoneAgentsState(available = true, agents = persistentListOf(row), owner = ProfileId("o")),
                    removal = PhoneAgentRemoval.Removed(101_982_208L),
                ),
                Recorder(),
            )
        }
        onNodeWithText("Program removed. Freed ${PhoneSizes.text(101_982_208L)}.").assertExists()
        onNodeWithText("101982208", substring = true).assertDoesNotExist()
        onNodeWithText("bytes", substring = true).assertDoesNotExist()
    }

    // Coordinator ruling 2026-11-13: Qwen Code, Goose and omp have no read-only sign-in status. Installed, each says
    // what is true (set it up at first use) and offers Set up and Remove; never "Ready", never "can't check".
    @Test
    fun `B-AGENT-113 an agent set up at first use says so and offers Set up and Remove`() = runComposeUiTest {
        val actions = Recorder()
        val rows =
            persistentListOf(
                PhoneAgentRow("qwen", "Qwen Code", PhoneAgentStatus.SetUpAtFirstUse, removable = true, signInAvailable = true),
                PhoneAgentRow("goose", "Goose", PhoneAgentStatus.SetUpAtFirstUse, removable = true, signInAvailable = true),
                PhoneAgentRow("omp-acp", "Oh My Pi", PhoneAgentStatus.SetUpAtFirstUse, removable = true, signInAvailable = true),
            )
        val page = AgentsUi(phone = PhoneAgentsState(available = true, agents = rows, owner = ProfileId("o")))
        var state by mutableStateOf(page)
        setContent { Screen(state, actions) }
        // The tile reads as one label: "<name>. <state>".
        for (name in listOf("Qwen Code", "Goose", "Oh My Pi"))
            onNode(hasContentDescription("$name. Installed. Set it up the first time you use it.", substring = true))
                .assertExists()
        onNode(hasContentDescription("can't check its sign-in", substring = true)).assertDoesNotExist()
        onNode(hasContentDescription("Ready", substring = true)).assertDoesNotExist()
        onNode(hasContentDescription("Set up Goose") and hasClickAction()).performClick()
        assertTrue("signin:goose" in actions.calls)
        state = page.copy(sheetAgent = "omp-acp")
        onNodeWithText("Set up Oh My Pi").assertExists()
        onNodeWithText("Remove Oh My Pi").performClick()
        assertTrue("remove:omp-acp" in actions.calls)
    }

    // Owner 2026-11-15: "when oh my pi needs a setup after installation why show new conversation? instead of setup
    // cta". With conversations on, an agent never seen set up has Set up as its card's action (no New conversation);
    // once the app has seen it set up, the same card starts a conversation.
    @Test
    fun `B-AGENT-SETUP-002 the card of an agent never seen set up says Set up, then New conversation once seen`() =
        runComposeUiTest {
            val actions = Recorder()
            val fresh =
                PhoneAgentRow(
                    "omp-acp",
                    "Oh My Pi",
                    PhoneAgentStatus.SetUpAtFirstUse,
                    removable = true,
                    signInAvailable = true,
                    needsSetUp = true,
                )
            val page =
                AgentsUi(
                    helperConnected = true,
                    phone =
                        PhoneAgentsState(
                            available = true,
                            agents = persistentListOf(fresh),
                            owner = ProfileId("owner"),
                            conversations = PHONE_HELPER.id,
                        ),
                )
            var state by mutableStateOf(page)
            setContent { Screen(state, actions) }
            onNode(hasContentDescription("New conversation with Oh My Pi")).assertDoesNotExist()
            onNode(hasContentDescription("Set up Oh My Pi") and hasClickAction()).performClick()
            assertEquals(listOf("signin:omp-acp"), actions.calls)

            actions.calls.clear()
            state = page.copy(phone = page.phone.copy(agents = persistentListOf(fresh.copy(needsSetUp = false))))
            onNode(hasContentDescription("New conversation with Oh My Pi") and hasClickAction()).performClick()
            assertEquals(listOf("chat:omp-acp"), actions.calls)
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
        onNode(hasContentDescription("Phone agents are not connected: the agent helper did not start.")).assertExists()
        onNode((hasText("Try again") or hasContentDescription("Try again")) and hasClickAction()).performScrollTo().performClick()
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
        onNode(hasContentDescription("Phone agents are not connected", substring = true)).assertDoesNotExist()
        onNode(hasText("Try again") or hasContentDescription("Try again")).assertDoesNotExist()
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
        onNode(hasContentDescription("Phone agents are not connected", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `COMPACT-302 agent tile action wraps whole label in at most two lines without ellipsis at 360 dp`() {
        // Ruling (the lead, 2026-11-26: two columns kept at 360 dp and text 1.3 by the owner's wish):
        // when a half-width tile's action label cannot fit beside its icon on <= 2 whole-word lines at full
        // TypeRole.Secondary size, the chip drops its decorative icon rather than shrinking or splitting a word.
        val state =
            EVERY_STATE.copy(
                phone =
                    EVERY_STATE.phone.copy(
                        agents =
                            persistentListOf(
                                PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready, removable = true),
                                PhoneAgentRow("codex", "Codex", PhoneAgentStatus.Limit, limitResets = "Today, 6:30 PM"),
                                PhoneAgentRow("agy", "Antigravity", PhoneAgentStatus.SignInNeeded, signInCheckFailed = true, removable = true),
                                PhoneAgentRow("qwen", "Qwen Code", PhoneAgentStatus.SignInNeeded, signInAvailable = true),
                            )
                    )
            )
        val cases =
            listOf(
                Triple("en", 1f, "Check Antigravity again"),
                Triple("en", 1.3f, "Check Antigravity again"),
                Triple("ar", 1f, "تحقق من Antigravity مجددًا"),
                Triple("ar", 1.3f, "تحقق من Antigravity مجددًا"),
                Triple("fr", 1f, "Vérifier Antigravity à nouveau"),
                Triple("fr", 1.3f, "Vérifier Antigravity à nouveau"),
            )
        try {
            for ((locale, scale, expectedSpoken) in cases) {
                Locale.setDefault(Locale.forLanguageTag(locale))
                runDesktopComposeUiTest(360 * 3, 780 * 3) {
                    var expectedSize = TextUnit.Unspecified
                    setContent {
                        CompositionLocalProvider(
                            LocalDensity provides Density(3f, scale),
                            LocalKitSecretSink provides KitSecretSink {},
                            LocalKitLogoLoader provides MARKS,
                        ) {
                            KitTheme(
                                platform = DesktopKitPlatform(),
                                appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                                localeTag = locale,
                                stillFrame = true,
                            ) {
                                expectedSize = LocalKitTypography.current.style(TypeRole.Secondary).fontSize
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    AgentsScreen(state, Recorder())
                                }
                            }
                        }
                    }
                    waitForIdle()
                    onNode(hasContentDescription(expectedSpoken) and hasClickAction()).assertExists()
                    val chips = onAllNodesWithTag("kit-chip-label", useUnmergedTree = true)
                    val count = chips.fetchSemanticsNodes().size
                    assertEquals(3, count, "[$locale/$scale] expected 3 tile action chips (Claude Code, Antigravity, Qwen Code)")
                    for (i in 0 until count) {
                        val layouts = mutableListOf<TextLayoutResult>()
                        chips[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        val layout = layouts.single()
                        val text = layout.layoutInput.text.text
                        assertEquals(
                            expectedSize,
                            layout.layoutInput.style.fontSize,
                            "[$locale/$scale] font shrank on: $text",
                        )
                        assertFalse(layout.hasVisualOverflow, "[$locale/$scale] visual overflow on: $text")
                        assertFalse((0 until layout.lineCount).any { layout.isLineEllipsized(it) }, "[$locale/$scale] ellipsized: $text")
                        assertTrue(layout.lineCount <= 2, "[$locale/$scale] expected <= 2 lines for '$text', got ${layout.lineCount}")
                        assertTrue(KitWholeWordsRules.whole(layout, text), "[$locale/$scale] broke mid-word: $text")
                    }
                }
            }
        } finally {
            Locale.setDefault(Locale.ENGLISH)
        }
    }

    internal companion object {
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
        val MARKS =
            object : KitLogoLoader {
                private fun mark(name: String) =
                    File(System.getProperty("user.dir"), "../../androidApp/src/main/assets/marks/$name.svg").takeIf { it.isFile }?.readText()

                override suspend fun svg(id: String, agent: Boolean): String? = AgentPersona.agentMarkSlug(id)?.let(::mark)

                /** Like the app: the brand's colour file when it has one (`ProviderMarks.agentColorFile`). */
                override suspend fun colorSvg(id: String): String? =
                    io.github.eslamasabry.oc.engine.integrations.catalog.ProviderMarks.agentColorFile(id)?.let(::mark)
            }
    }
}
