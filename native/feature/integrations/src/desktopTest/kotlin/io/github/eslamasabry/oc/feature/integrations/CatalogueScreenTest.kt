package io.github.eslamasabry.oc.feature.integrations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connectors.catalog.CatalogueFact
import io.github.eslamasabry.oc.engine.connectors.catalog.CatalogueRowUi
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogueUi
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueActions
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueAsk
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueBlock
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueProblem
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueProblemKind
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueRow
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueUi
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorServer
import io.github.eslamasabry.oc.feature.integrations.ui.CatalogueScreen
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

/** The real CatalogueScreen, found by the words a person sees. */
@OptIn(ExperimentalTestApi::class)
class CatalogueScreenTest {
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

    private class Actions : CatalogueActions {
        val calls = mutableListOf<String>()

        override fun back() { calls += "back" }

        override fun openServers() { calls += "servers" }

        override fun load() { calls += "load" }

        override fun forget() { calls += "forget" }

        override fun search(query: String) { if (query.isNotEmpty()) calls += "search $query" }

        override fun retry() { calls += "retry" }

        override fun toggle(catalogId: String, on: Boolean) { calls += "toggle $catalogId $on" }

        override fun confirm() { calls += "confirm" }

        override fun cancel() { calls += "cancel" }

        override fun dismissProblem() { calls += "dismiss" }
    }

    @Composable
    private fun Screen(state: CatalogueUi, actions: CatalogueActions) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            Box(Modifier.width(412.dp)) { CatalogueScreen(state, actions) }
        }
    }

    private fun base(page: McpCatalogueUi) =
        CatalogueUi(servers = persistentListOf(ConnectorServer(home, "Home")), server = home, page = page)

    private fun row(id: String, name: String, title: String, on: Boolean, facts: List<CatalogueFact>, block: CatalogueBlock?) =
        CatalogueRow(CatalogueRowUi(id, name, title, "About $title.", on, facts, null), block)

    private fun listed() =
        base(McpCatalogueUi.Rows(emptyList(), stale = false))
            .copy(
                canForget = true,
                rows =
                    persistentListOf(
                        row("io.github.acme/notes", "notes", "Notes", true, listOf(CatalogueFact.NeedsPython), null),
                        row("com.example/design", "design", "Design", false, listOf(CatalogueFact.HostedBy("design.example")), null),
                        row("com.example/weather", "weather", "Weather", false, listOf(CatalogueFact.NeedsKey), CatalogueBlock.NeedsKey),
                        row("io.github.acme/box", "box", "box", false, emptyList(), CatalogueBlock.NeedsDocker),
                    ),
            )

    @Test
    fun `B-AGENT-133 the first visit asks before loading the registry`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(base(McpCatalogueUi.Consent), actions) }
        onNodeWithText("MCP catalogue").assertExists()
        onNodeWithText("Load the MCP registry?").assertExists()
        onNodeWithText(
            "The app asks registry.modelcontextprotocol.io for its list of MCP servers. It sends only what you search for, nothing about you or your servers."
        ).assertExists()
        onNodeWithText("Stop using the registry").assertDoesNotExist()
        onNodeWithText("Load the list").performClick()
        assertEquals(listOf("load"), actions.calls)
    }

    @Test
    fun `B-AGENT-212 listings show as switches with what they need, and blocked ones say why`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(listed(), actions) }
        onNodeWithText("Public MCP registry").assertExists()
        onNodeWithText("Notes").assertExists()
        onNodeWithText("Hosted by design.example\nAbout Design.").assertExists()
        onNodeWithText("Needs an API key. Add it in the server's configuration on the computer.").assertExists()
        onNodeWithText("Runs in Docker. The app can't add it from here.").assertExists()
        onNodeWithText("The registry lists no prices. A hosted server's owner may charge for it or ask for an account.").assertExists()
        onNodeWithText("Design").performClick()
        onNodeWithText("Weather").performClick() // disabled: nothing happens
        onNodeWithText("Notes").performClick()
        assertEquals(listOf("toggle com.example/design true", "toggle io.github.acme/notes false"), actions.calls)
    }

    @Test
    fun `B-AGENT-212 turning one on asks a short question before adding until restart`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(listed().copy(ask = CatalogueAsk.Add("design", "Design")), actions) }
        onNodeWithText("Add Design until OpenCode restarts?").assertExists()
        onNodeWithText("Add until restart").performClick()
        assertEquals(listOf("confirm"), actions.calls)
    }

    @Test
    fun `B-AGENT-212 turning one off asks before removing until restart`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(listed().copy(ask = CatalogueAsk.Remove("notes")), actions) }
        onNodeWithText("Remove notes until restart?").assertExists()
        onNodeWithText("Remove until restart").performClick()
        assertEquals(listOf("confirm"), actions.calls)
    }

    @Test
    fun `B-AGENT-212 a refused add is a plain line, never the server's words`() = runComposeUiTest {
        setContent {
            Screen(
                listed().copy(problem = CatalogueProblem(CatalogueProblemKind.AddFailed, "design", Failure.Server(500))),
                Actions(),
            )
        }
        onNodeWithText("Couldn't add design").assertExists()
        onNodeWithText("The list shows what the server reports now.").assertExists()
    }

    @Test
    fun `B-AGENT-133 Stop using the registry is offered once the person opted in`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(listed(), actions) }
        onNodeWithText("Search the registry").assertExists()
        onNodeWithText("Stop using the registry").performClick()
        assertEquals(listOf("forget"), actions.calls)
    }

    @Test
    fun `B-AGENT-212 an unreachable registry and a search with no match say so`() = runComposeUiTest {
        var state by mutableStateOf(base(McpCatalogueUi.Failed(null)).copy(canForget = true))
        val actions = Actions()
        setContent { Screen(state, actions) }
        onNodeWithText("Couldn't load the public MCP registry").assertExists()
        onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), actions.calls)
        state = base(McpCatalogueUi.Empty("zzz")).copy(canForget = true)
        waitForIdle()
        onNodeWithText("Nothing in the registry matches “zzz”").assertExists()
    }

    @Test
    fun `B-AGENT-212 a server that takes no MCP servers explains, and no server points to Servers`() = runComposeUiTest {
        var state by mutableStateOf(base(McpCatalogueUi.Unavailable))
        val actions = Actions()
        setContent { Screen(state, actions) }
        onNodeWithText("This server doesn't take MCP servers from the app").assertExists()
        state = CatalogueUi(page = McpCatalogueUi.Unavailable)
        waitForIdle()
        onNodeWithText("Open servers").performClick()
        assertEquals(listOf("servers"), actions.calls)
    }
}
