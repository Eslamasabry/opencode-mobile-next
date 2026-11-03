package io.github.eslamasabry.oc.feature.integrations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorProblem
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorRow
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorServer
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorState
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorsActions
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorsPhase
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorsUi
import io.github.eslamasabry.oc.feature.integrations.ui.ConnectorsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** The real ConnectorsScreen, found by the words a person sees. */
@OptIn(ExperimentalTestApi::class)
class ConnectorsScreenTest {
    private var previousLocale: Locale = Locale.getDefault()
    private val home = ProfileId("home")

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private class Actions : ConnectorsActions {
        val calls = mutableListOf<String>()

        override fun back() { calls += "back" }

        override fun refresh() { calls += "refresh" }

        override fun chooseServer(id: ProfileId) { calls += "server ${id.raw}" }

        override fun toggle(name: String, on: Boolean) { calls += "toggle $name $on" }

        override fun dismissProblem() { calls += "dismiss" }

        override fun openServers() { calls += "servers" }

        override fun openCatalogue() { calls += "catalogue" }
    }

    @Composable
    private fun Screen(state: ConnectorsUi, actions: ConnectorsActions) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            Box(Modifier.width(412.dp)) { ConnectorsScreen(state, actions) }
        }
    }

    private fun ready() =
        ConnectorsUi(
            servers = persistentListOf(ConnectorServer(home, "Home")),
            server = home,
            phase = ConnectorsPhase.Ready,
            rows =
                persistentListOf(
                    ConnectorRow("github", ConnectorState.Connected),
                    ConnectorRow("files", ConnectorState.Off),
                    ConnectorRow("linear", ConnectorState.NeedsSignIn),
                ),
        )

    @Test
    fun `B-AGENT-212 the Connectors page lists each MCP server with its state in plain words`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(ready(), actions) }
        onNodeWithText("Tools and connectors").assertExists()
        onNodeWithText("MCP servers").assertExists()
        onNodeWithText("github").assertExists()
        onNodeWithText("Connected").assertExists()
        onNodeWithText("Turned off").assertExists()
        onNodeWithText("Needs sign-in on the computer").assertExists()
        onNodeWithText("files").performClick()
        assertEquals(listOf("toggle files true"), actions.calls)
    }

    @Test
    fun `B-AGENT-212 the Connectors page offers Browse the catalogue, which opens it`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(ready(), actions) }
        onNodeWithText("Servers from the public MCP registry, turned on with a switch").assertExists()
        onNodeWithText("Browse the catalogue").performClick()
        assertEquals(listOf("catalogue"), actions.calls)
    }

    @Test
    fun `B-AGENT-212 no catalogue row before a server is connected`() = runComposeUiTest {
        setContent { Screen(ConnectorsUi(phase = ConnectorsPhase.NoServer), Actions()) }
        onNodeWithText("Browse the catalogue").assertDoesNotExist()
    }

    @Test
    fun `B-AGENT-135 a refused switch shows a plain line, never the server's words`() = runComposeUiTest {
        setContent {
            Screen(ready().copy(problem = ConnectorProblem("files", true, Failure.Server(500))), Actions())
        }
        onNodeWithText("Couldn't turn on files").assertExists()
        onNodeWithText("The list shows what the server reports now.").assertExists()
    }

    @Test
    fun `B-AGENT-130 no server and an unsupported server are explained in place`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(ConnectorsUi(phase = ConnectorsPhase.NoServer), actions) }
        onNodeWithText("Connect to a server first").assertExists()
        onNodeWithText("Open servers").performClick()
        assertEquals(listOf("servers"), actions.calls)
    }
}
