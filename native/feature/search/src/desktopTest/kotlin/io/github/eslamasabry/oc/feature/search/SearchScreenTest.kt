package io.github.eslamasabry.oc.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.search.presentation.ContentPhase
import io.github.eslamasabry.oc.feature.search.presentation.GoToRow
import io.github.eslamasabry.oc.feature.search.presentation.SearchActions
import io.github.eslamasabry.oc.feature.search.presentation.SearchHitUi
import io.github.eslamasabry.oc.feature.search.presentation.SearchPhase
import io.github.eslamasabry.oc.feature.search.presentation.SearchServer
import io.github.eslamasabry.oc.feature.search.presentation.SearchUi
import io.github.eslamasabry.oc.feature.search.ui.SearchScreen
import io.github.eslamasabry.oc.feature.search.ui.SearchWebOption
import io.github.eslamasabry.oc.feature.search.presentation.SearchActivity
import io.github.eslamasabry.oc.feature.search.presentation.SearchIndex
import io.github.eslamasabry.oc.feature.search.presentation.SearchIndexEntry
import io.github.eslamasabry.oc.feature.search.presentation.SearchProject
import io.github.eslamasabry.oc.feature.search.presentation.SearchRecentConversation
import io.github.eslamasabry.oc.feature.search.presentation.SearchRowStatus
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import kotlinx.collections.immutable.toImmutableList
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** The real SearchScreen, found by the words a person sees. */
@OptIn(ExperimentalTestApi::class)
class SearchScreenTest {
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

    private class Actions : SearchActions {
        val calls = mutableListOf<String>()

        override fun back() {
            calls += "back"
        }

        override fun search(query: String) {
            calls += "search:$query"
        }

        override fun submit(query: String) {
            calls += "submit:$query"
        }

        override fun chooseServer(id: ProfileId) {
            calls += "server:${id.raw}"
        }

        override fun searchInside(on: Boolean) {
            calls += "inside:$on"
        }

        override fun loadMore() {
            calls += "more"
        }

        override fun retry() {
            calls += "retry"
        }

        override fun open(hit: SearchHitUi) {
            calls += "open:${hit.ref.sessionId.raw}"
        }

        override fun forgetRecent(query: String) {
            calls += "forget:$query"
        }

        override fun clearRecent() {
            calls += "clear"
        }

        override fun openServers() {
            calls += "servers"
        }

        override fun goTo(row: GoToRow) {
            calls += "goto:${row.place ?: row.ref?.sessionId?.raw}"
        }

        override fun openProject(project: SearchProject) {
            calls += "project:${project.directory}"
        }

        override fun openRecent(conversation: SearchRecentConversation) {
            calls += "recent:${conversation.ref.sessionId.raw}"
        }

        override fun openDestination(entry: SearchIndexEntry) {
            calls += "place:${entry.place}"
        }
    }

    @Composable
    private fun Screen(state: SearchUi, actions: SearchActions, locale: String = "en", web: SearchWebOption? = null) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = locale,
            stillFrame = true,
        ) {
            Box(Modifier.width(412.dp)) { SearchScreen(state, actions, web = web) }
        }
    }

    private val servers = persistentListOf(SearchServer(home, "Home"))

    @Test
    fun `SCREENS-NAV-34 an empty field shows recent searches and the visible inside-text switch`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(SearchUi(servers = servers, server = home, recent = persistentListOf("deploy")), actions) }
        assertTrue(onAllNodesWithText("Search conversations").fetchSemanticsNodes().isNotEmpty())
        onNodeWithText("Recent searches").assertExists()
        onNodeWithContentDescription("deploy").assertExists() // the kit chip speaks its label as one node
        onNodeWithContentDescription("Inside messages").assertExists()
        onNodeWithContentDescription("Clear recent searches").performClick()
        assertEquals(listOf("clear"), actions.calls)
    }

    @Test
    fun `SCREENS-NAV-34 B1 the option chips toggle Web and Inside messages, say whether they are on, and the info explains both`() =
        runComposeUiTest {
            val actions = Actions()
            var webOpen = false
            var state by mutableStateOf(SearchUi(servers = servers, server = home))
            setContent { Screen(state, actions, web = SearchWebOption(webOpen) { webOpen = !webOpen; actions.calls += "web" }) }
            onNodeWithContentDescription("Inside messages").assertIsNotSelected().performClick()
            assertEquals(listOf("inside:true"), actions.calls)
            state = state.copy(content = ContentPhase.Done)
            onNodeWithContentDescription("Inside messages").assertIsSelected().performClick()
            assertEquals(listOf("inside:true", "inside:false"), actions.calls)
            onNodeWithContentDescription("Web").assertIsNotSelected().performClick()
            assertEquals("web", actions.calls.last())
            onNodeWithContentDescription("About search options").performClick()
            waitForIdle()
            onNodeWithText("Search options").assertExists()
            onNodeWithText("Reads the newest messages of your 20 most recent conversations on this server.").assertExists()
            onNodeWithText("Search with this server’s provider. Searches stay out of conversation history.").assertExists()
        }

    @Test
    fun `SCREENS-NAV-34 B1 an empty field offers the places with the waiting count, then recent conversations`() = runComposeUiTest {
        val actions = Actions()
        val ref = ConversationRef(home, SessionId("r1"))
        val activity =
            SearchActivity(
                persistentListOf(SearchRecentConversation(ref, "Explain this project", "new", null, "", null, SearchRowStatus.Working)),
                needsYou = 1,
            )
        setContent { Screen(SearchUi(servers = servers, server = home, activity = activity), actions) }
        onNodeWithText("Needs you").assertExists()
        onNodeWithText("Settings").assertExists()
        onNodeWithText("Servers").assertExists()
        onNodeWithText("Recent conversations").assertExists()
        onNodeWithText("Working", substring = true).assertExists()
        onNodeWithText("Needs you").performClick()
        onNodeWithText("Explain this project").performClick()
        assertEquals(listOf("goto:NeedsYou", "recent:r1"), actions.calls)
    }

    @Test
    fun `SCREENS-NAV-34 B1 typing groups results into Conversations with See all, Projects and Settings and pages`() = runComposeUiTest {
        val actions = Actions()
        val hits = (1..4).map { SearchHitUi(ConversationRef(home, SessionId("s$it")), "App task $it", "shop") }
        val state =
            SearchUi(
                query = "app",
                servers = servers,
                server = home,
                phase = SearchPhase.Results,
                titleHits = hits.toImmutableList(),
                activity = SearchActivity(projects = persistentListOf(SearchProject("app-store", "/src/app-store", 3))),
                destinations = SearchIndex.appEntries(PlatformCapabilities.desktop(), hasShell = true).toImmutableList(),
            )
        setContent { Screen(state, actions) }
        onNodeWithText("Conversations").assertExists()
        onNodeWithText("App task 3").assertExists()
        onNodeWithText("App task 4").assertDoesNotExist()
        onNodeWithContentDescription("See all 4 conversations").performClick()
        onNodeWithText("App task 4").assertExists()
        onNodeWithText("Projects").assertExists()
        onNodeWithText("Home · 3 conversations").assertExists()
        onNodeWithText("Settings and pages").assertExists()
        onNodeWithText("Appearance").assertExists()
        onNodeWithText("app-store").performClick()
        onNodeWithText("Appearance").performClick()
        assertEquals(listOf("project:/src/app-store", "place:Appearance"), actions.calls)
    }

    @Test
    fun `SCREENS-NAV-34 B1 a conversation result marks the typed words in the accent ink and shows the matching message`() =
        runComposeUiTest {
            var accent = androidx.compose.ui.graphics.Color.Unspecified
            val hit =
                SearchHitUi(ConversationRef(home, SessionId("s1")), "Debug build", "app", snippet = "install the Debian image first", messageId = "m1")
            setContent {
                accent = io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current.accent
                Screen(
                    SearchUi(
                        query = "deb",
                        servers = servers,
                        server = home,
                        phase = SearchPhase.Results,
                        contentHits = persistentListOf(hit),
                        content = ContentPhase.Done,
                    ),
                    Actions(),
                )
            }
            val title = onAllNodesWithTag("kit-row-title", useUnmergedTree = true).fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.Text].single() }
                .single { it.text == "Debug build" }
            assertEquals(listOf(0 to 3), title.spanStyles.map { it.start to it.end })
            assertEquals(accent, title.spanStyles.single().item.color)
            onNodeWithText("Reply: ⁨install the Debian image first⁩").assertExists()
        }

    @Test
    fun `SCREENS-NAV-34 results list titles first, then the matching words inside conversations, and open on tap`() =
        runComposeUiTest {
            val actions = Actions()
            val title = SearchHitUi(ConversationRef(home, SessionId("s1")), "Fix login", "app")
            val inside =
                SearchHitUi(
                    ConversationRef(home, SessionId("s2")),
                    "Deploy notes",
                    "app",
                    snippet = "the rollback plan",
                    snippetFromYou = true,
                    messageId = "m1",
                )
            setContent {
                Screen(
                    SearchUi(
                        query = "login",
                        servers = servers,
                        server = home,
                        phase = SearchPhase.Results,
                        titleHits = persistentListOf(title),
                        contentHits = persistentListOf(inside),
                        content = ContentPhase.Done,
                    ),
                    actions,
                )
            }
            onNodeWithText("Conversations").assertExists()
            onNodeWithText("You:", substring = true).assertExists()
            onNodeWithText("Fix login").performClick()
            assertEquals(listOf("open:s1"), actions.calls)
        }

    @Test
    fun `SCREENS-NAV-34 a failed search reads plainly with Try again and no raw error`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(SearchUi(query = "x", servers = servers, server = home, phase = SearchPhase.Failed), actions) }
        onNodeWithText("Couldn't search conversations.").assertExists()
        onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), actions.calls)
    }

    @Test
    fun `SCREENS-NAV-34 one-project servers say what was searched`() = runComposeUiTest {
        val hit = SearchHitUi(ConversationRef(home, SessionId("s1")), "Fix login", null)
        setContent {
            Screen(
                SearchUi(
                    query = "login",
                    servers = servers,
                    server = home,
                    phase = SearchPhase.Results,
                    titleHits = persistentListOf(hit),
                    allProjects = false,
                ),
                Actions(),
            )
        }
        onNodeWithText("Only this server's current project is searched.").assertExists()
    }

    @Test
    fun `SCREENS-NAV-34 no server connected offers Servers`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(SearchUi(phase = SearchPhase.NoServer), actions) }
        onNodeWithText("No server connected").assertExists()
        onNodeWithText("Open Servers").performClick()
        assertEquals(listOf("servers"), actions.calls)
    }

    @Test
    fun `B-L10N-010 the search screen reads in Arabic`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        setContent { Screen(SearchUi(servers = servers, server = home, recent = persistentListOf("نشر")), Actions(), "ar") }
        assertTrue(onAllNodesWithText("ابحث في المحادثات").fetchSemanticsNodes().isNotEmpty())
        onNodeWithText("عمليات البحث الأخيرة").assertExists()
        onNodeWithContentDescription("داخل الرسائل").assertExists()
    }

    @Test
    fun `B-PROTO-020 no match on this page still offers Load more while the server has more, and Try again after a failure`() =
        runComposeUiTest {
            val actions = Actions()
            var state by mutableStateOf(
                SearchUi(query = "plan", servers = servers, server = home, phase = SearchPhase.NoMatch, canLoadMore = true)
            )
            setContent { Screen(state, actions) }
            onNodeWithText("Load more conversations").performClick()
            state = state.copy(loadMoreFailed = true)
            onNodeWithText("Could not load more conversations").assertExists()
            onNodeWithText("Try again").performClick()
            assertEquals(listOf("more", "more"), actions.calls)
        }
}
