package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.providers.OwnProviderProblem
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsHubControlsUi
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.OwnProviderActions
import io.github.eslamasabry.oc.feature.agents.hub.OwnProviderUi
import io.github.eslamasabry.oc.feature.agents.hub.OwnProviderView
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
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
import kotlinx.collections.immutable.persistentSetOf

/**
 * Issue 147 on the real Agents page: the help row and sheet, and Claude Code's own provider. Found
 * by visible text.
 */
@OptIn(ExperimentalTestApi::class)
class OwnProviderScreenTest {
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

    private class Recorder : AgentsActions, OwnProviderActions {
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

        override fun requestRemoval(id: String) {}

        override fun cancelRemoval() {}

        override fun confirmRemoval() {}

        override fun closeAgent() {
            calls += "close-agent"
        }

        override fun openOwnProvider(agent: String) {
            calls += "own:$agent"
        }

        override fun closeOwnProvider() {
            calls += "own-close"
        }

        override fun editedOwnProvider() {}

        override fun saveOwnProvider(agent: String, endpoint: String, key: String, model: String) {
            calls += "save:$agent:$endpoint:${key.length}:$model"
        }

        override fun removeOwnProvider(agent: String) {
            calls += "remove:$agent"
        }

        override fun openAgentsHelp() {
            calls += "help"
        }

        override fun closeAgentsHelp() {
            calls += "help-close"
        }
    }

    private val claude =
        PhoneAgentRow(
            "claude",
            "Claude Code",
            PhoneAgentStatus.SignInNeeded,
            signInAvailable = true,
        )

    private fun page(sheetAgent: String? = null) =
        AgentsUi(
            phone = PhoneAgentsState(available = true, agents = persistentListOf(claude)),
            sheetAgent = sheetAgent,
        )

    private fun own(
        editing: String? = null,
        help: Boolean = false,
        problem: OwnProviderProblem? = null,
        saved: Boolean = false,
    ) =
        OwnProviderUi(
            available = true,
            supported = persistentSetOf("claude"),
            saved =
                if (saved)
                    persistentMapOf("claude" to OwnProviderView("https://llm.example.test", null))
                else persistentMapOf(),
            editing = editing,
            helpOpen = help,
            problem = problem,
        )

    @Composable
    private fun Screen(state: AgentsUi, own: OwnProviderUi, actions: Recorder) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) {
                    AgentsScreen(
                        state,
                        actions,
                        AgentsHubControlsUi(own = own),
                        null,
                        ownActions = actions,
                    )
                }
            }
        }
    }

    @Test
    fun `B-AGENT-OWN-040 the page explains in place what runs here, providers, files, keys, needs and limits`() =
        runComposeUiTest {
            val actions = Recorder()
            var ui by mutableStateOf(own())
            setContent { Screen(page(), ui, actions) }
            onNodeWithText("Your own agent or provider").performScrollTo().performClick()
            assertEquals(listOf("help"), actions.calls)
            ui = own(help = true)
            waitForIdle()
            for (title in
                listOf(
                    "What runs here",
                    "Your own provider",
                    "Installing",
                    "Where files are",
                    "Keys and sign-ins",
                    "What the phone needs",
                    "Not supported",
                )) onNodeWithText(title).assertExists()
        }

    @Test
    fun `B-AGENT-OWN-041 Claude Code's sheet offers its own provider`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Screen(page(sheetAgent = "claude"), own(), actions) }
        onNode(hasText("Use your own provider") and hasClickAction())
            .performScrollTo()
            .performClick()
        assertEquals(listOf("close-agent", "own:claude"), actions.calls)
    }

    @Test
    fun `B-AGENT-OWN-043 the provider sheet asks for endpoint, key and model, says why a value is refused and saves`() =
        runComposeUiTest {
            val actions = Recorder()
            setContent {
                Screen(
                    page(),
                    own(editing = "claude", problem = OwnProviderProblem.Endpoint),
                    actions,
                )
            }
            onNodeWithText("Your own provider for Claude Code").assertExists()
            onNodeWithText("Endpoint").assertExists()
            onNodeWithText("Key").assertExists()
            onNodeWithText("Model (optional)").assertExists()
            onNodeWithText(
                    "Enter an https:// address. http:// works only for a program on this phone."
                )
                .assertExists()
            onNode(hasText("Save provider for Claude Code") and hasClickAction())
                .performScrollTo()
                .performClick()
            assertTrue(actions.calls.last().startsWith("save:claude:"))
        }

    @Test
    fun `B-AGENT-OWN-042 a saved provider can be stopped from its sheet`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Screen(page(), own(editing = "claude", saved = true), actions) }
        onNode(hasText("Stop using your own provider for Claude Code") and hasClickAction())
            .performScrollTo()
            .performClick()
        assertEquals("remove:claude", actions.calls.last())
    }

    @Test
    fun `B-AGENT-UH-030 the pending card tells the truth and never offers an install`() =
        runComposeUiTest {
            val actions = Recorder()
            val row = PhoneAgentRow("unharnessed", "Unharnessed", PhoneAgentStatus.Unavailable)
            val ui =
                AgentsUi(
                    phone = PhoneAgentsState(available = true, agents = persistentListOf(row)),
                    sheetAgent = row.id,
                )
            setContent { Screen(ui, own(), actions) }
            onNodeWithText("The download is not available yet.").assertExists()
            onNodeWithText("This agent runs commands without asking.").assertExists()
            onNodeWithText("Install Unharnessed").assertDoesNotExist()
            val file =
                File("build/reports/unharnessed/agents-unavailable.png").apply {
                    parentFile.mkdirs()
                }
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        }

    @Test
    fun `B-AGENT-UH-031 setup opens only the key and local server form`() = runComposeUiTest {
        val actions = Recorder()
        val row =
            PhoneAgentRow(
                "unharnessed",
                "Unharnessed",
                PhoneAgentStatus.SignInNeeded,
                signInAvailable = true,
            )
        val ui =
            AgentsUi(
                phone = PhoneAgentsState(available = true, agents = persistentListOf(row)),
                sheetAgent = row.id,
            )
        setContent {
            Screen(
                ui,
                OwnProviderUi(available = true, supported = persistentSetOf(row.id)),
                actions,
            )
        }
        onNodeWithText("Set up Unharnessed").performClick()
        assertEquals(listOf("close-agent", "own:unharnessed"), actions.calls)
    }

    @Test
    fun `B-AGENT-UH-032 setup explains the required model and offers no subscription sign in`() =
        runComposeUiTest {
            val actions = Recorder()
            val row = PhoneAgentRow("unharnessed", "Unharnessed", PhoneAgentStatus.SignInNeeded)
            val ui =
                AgentsUi(phone = PhoneAgentsState(available = true, agents = persistentListOf(row)))
            setContent {
                Screen(
                    ui,
                    OwnProviderUi(
                        available = true,
                        supported = persistentSetOf(row.id),
                        editing = row.id,
                    ),
                    actions,
                )
            }
            onNodeWithText(
                    "Use an API key or a local OpenAI-compatible server. Enter a model. A server on this phone can leave Key empty."
                )
                .assertExists()
            onNodeWithText("Model").assertExists()
            onNodeWithText("Sign in with Google").assertDoesNotExist()
            val file =
                File("build/reports/unharnessed/provider-setup.png").apply { parentFile.mkdirs() }
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        }
}
