package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsHubControlsUi
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.OwnAgentActions
import io.github.eslamasabry.oc.feature.agents.hub.OwnAgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.OwnProviderActions
import io.github.eslamasabry.oc.feature.agents.hub.OwnProviderUi
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * Issue 147 follow-up: the help of the own-agent sheet and of the provider sheet opens in place, as folds inside the
 * sheet, never as a second sheet on top. Found by visible text.
 */
@OptIn(ExperimentalTestApi::class)
class OwnAgentInlineHelpTest {
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

    private class Recorder : AgentsActions, OwnProviderActions, OwnAgentActions {
        val calls = mutableListOf<String>()

        override fun back() = Unit
        override fun startAdding() = Unit
        override fun cancelAdding() = Unit
        override fun edited() = Unit
        override fun check(address: String, password: String, folder: String) = Unit
        override fun add(name: String, address: String, password: String, folder: String) = Unit
        override fun readAgents(id: ProfileId) = Unit
        override fun newConversation(id: ProfileId) = Unit
        override fun refreshPhone() = Unit
        override fun requestRemoval(id: String) = Unit
        override fun cancelRemoval() = Unit
        override fun confirmRemoval() = Unit
        override fun closeAgent() {
            calls += "close-agent"
        }

        override fun openOwnProvider(agent: String) {
            calls += "own:$agent"
        }

        override fun closeOwnProvider() {
            calls += "own-close"
        }

        override fun editedOwnProvider() = Unit

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

        override fun addOwnAgent() {
            calls += "addOwnAgent"
        }

        override fun editOwnAgent(id: String) {
            calls += "editOwnAgent:$id"
        }

        override fun closeOwnAgent() {
            calls += "closeOwnAgent"
        }

        override fun editedOwnAgent() {
            calls += "editedOwnAgent"
        }

        override fun saveOwnAgent(name: String, start: String, install: String, env: List<Pair<String, String>>) {
            calls += "saveOwnAgent"
        }

        override fun checkOwnAgent() {
            calls += "checkOwnAgent"
        }

        override fun installOwnAgent() {
            calls += "installOwnAgent"
        }

        override fun removeOwnAgent() {
            calls += "removeOwnAgent"
        }
    }

    private val claude = PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.SignInNeeded, signInAvailable = true)

    private fun page() =
        AgentsUi(phone = PhoneAgentsState(available = true, agents = persistentListOf(claude)))

    private fun own(editing: String? = null, help: Boolean = false) = OwnProviderUi(
        available = true,
        supported = persistentSetOf("claude"),
        editing = editing,
        helpOpen = help,
    )

    @Composable
    private fun Screen(
        own: OwnProviderUi,
        actions: Recorder,
        ownAgents: OwnAgentsUi = OwnAgentsUi(available = true, sheetOpen = true),
        width: Dp = 412.dp,
        fontScale: Float = 1f,
    ) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)
                ) {
                    Box(Modifier.width(width)) {
                        AgentsScreen(
                            page(),
                            actions,
                            AgentsHubControlsUi(own = own, ownAgents = ownAgents),
                            null,
                            ownActions = actions,
                            ownAgentActions = actions,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `B-AGENT-OWN-044 the own-agent sheet has no link to a second help sheet`() = kitUiTest {
        val actions = Recorder()
        setContent { Screen(own(), actions) }
        onNodeWithText("Your own agent").assertExists()
        onAllNodesWithText("How agents work on this phone").assertCountEquals(0)
    }

    @Test
    fun `B-AGENT-OWN-045 a help fold opens its words inside the own-agent sheet`() = kitUiTest {
        val actions = Recorder()
        setContent { Screen(own(), actions) }
        val parts = listOf(
            "What runs here" to "Coding agents that speak ACP",
            "Not supported" to "Desktop apps with windows",
            "Installing" to "Built-in agents install from this page",
            "Keys and sign-ins" to "A key you type is kept in Android’s secure storage",
        )
        for ((title, body) in parts) {
            onNodeWithText(title).performScrollTo().performClick()
            onNodeWithText(body, substring = true).assertExists()
            onNodeWithText("Your own agent").assertExists()
            onAllNodesWithText("How agents work on this phone").assertCountEquals(0)
            assertTrue("help" !in actions.calls)
        }
    }

    @Test
    fun `B-AGENT-OWN-046 Where files are and What the phone needs sit in one closed Details row at the end`() =
        kitUiTest {
            val actions = Recorder()
            setContent { Screen(own(), actions) }
            onAllNodesWithText("Where files are").assertCountEquals(0)
            onNodeWithText("Details").performScrollTo().performClick()
            onNodeWithText("Where files are").assertExists()
            onNodeWithText("Agents work in the project you choose", substring = true).assertExists()
            onNodeWithText("What the phone needs").assertExists()
            onNodeWithText("A 64-bit ARM phone (arm64)", substring = true).assertExists()
            onAllNodesWithText("Your own provider").assertCountEquals(0)
        }

    @Test
    fun `B-AGENT-OWN-047 with every help part open the sheet still scrolls at 360 dp and text 1_3`() =
        kitUiTest(360, 780) {
            val actions = Recorder()
            setContent { Screen(own(), actions, width = 360.dp, fontScale = 1.3f) }
            for (title in listOf("What runs here", "Not supported", "Installing", "Keys and sign-ins", "Details"))
                onNodeWithText(title).performScrollTo().performClick()
            for (body in listOf(
                "Coding agents that speak ACP",
                "Desktop apps with windows",
                "Built-in agents install from this page",
                "A key you type is kept in Android’s secure storage",
                "Agents work in the project you choose",
                "A 64-bit ARM phone (arm64)",
            ))
                onNodeWithText(body, substring = true).performScrollTo().assertIsDisplayed()
            onNodeWithText("Save agent").assertExists()
        }

    @Test
    fun `B-AGENT-OWN-048 the provider sheet explains your own provider in place, with no second sheet`() = kitUiTest {
        val actions = Recorder()
        setContent { Screen(own(editing = "claude"), actions, ownAgents = OwnAgentsUi(available = true)) }
        onNodeWithText("Your own provider").performScrollTo().performClick()
        onNodeWithText("Claude Code can use any Anthropic-compatible endpoint", substring = true).assertExists()
        onAllNodesWithText("How agents work on this phone").assertCountEquals(0)
    }
}
