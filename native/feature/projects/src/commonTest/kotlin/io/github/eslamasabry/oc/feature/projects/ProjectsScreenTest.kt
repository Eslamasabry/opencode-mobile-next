package io.github.eslamasabry.oc.feature.projects

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.projects.presentation.ChatTitle
import io.github.eslamasabry.oc.feature.projects.presentation.FolderRowUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectChatUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPageActions
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPagePhase
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPageUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPhase
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPickerActions
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPickerUi
import io.github.eslamasabry.oc.feature.projects.ui.ProjectPageScreen
import io.github.eslamasabry.oc.feature.projects.ui.ProjectsPickerScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** The projects pages, found by visible text (desktop, headless). */
@OptIn(ExperimentalTestApi::class)
class ProjectsScreenTest {
    private class Picker : ProjectsPickerActions {
        val calls = mutableListOf<String>()

        override fun back() { calls += "back" }

        override fun chooseServer(profile: ProfileId) { calls += "server ${profile.raw}" }

        override fun askPath() { calls += "ask" }

        override fun finishPath(path: String?) { calls += "path $path" }

        override fun openProject(directory: String) { calls += "project $directory" }

        override fun search(query: String) { calls += "search $query" }

        override fun retry() { calls += "retry" }

        override fun openServers() { calls += "servers" }
    }

    private class Page : ProjectPageActions {
        val calls = mutableListOf<String>()

        override fun back() { calls += "back" }

        override fun newConversationHere() { calls += "new" }

        override fun openConversation(ref: ConversationRef) { calls += "open ${ref.sessionId.raw}" }

        override fun loadMore() { calls += "more" }

        override fun retry() { calls += "retry" }

        override fun openServers() { calls += "servers" }
    }

    private val ready =
        ProjectsPickerUi(
            phase = ProjectsPhase.Ready,
            serverName = "laptop",
            openedBefore = persistentListOf(FolderRowUi("/work/site", "site")),
            projects = persistentListOf(FolderRowUi("/work/api", "api", conversations = 3, running = 1)),
        )

    @Test
    fun `obvious options come first as visible rows - New project, Open a folder, Folder on this phone, Opened before`() =
        runComposeUiTest {
            val actions = Picker()
            setContent { Themed { ProjectsPickerScreen(ready, actions) } }
            onNodeWithText("Projects").assertExists()
            // Rows that can't run here say why instead of being dead taps.
            onNodeWithText("New project").assertExists()
            onNodeWithText("This server can’t make folders from the phone. Make the folder on that computer, then open it here.")
                .assertExists()
            onNodeWithText("Folder on this phone").assertExists()
            onNodeWithText("OpenCode on this phone isn’t set up in this version yet.").assertExists()
            onNodeWithText("Opened before").assertExists()
            onNodeWithText("Open a folder on the server").performClick()
            onNodeWithText("site").performClick()
            onNodeWithText("api").performClick()
            assertEquals(listOf("ask", "project /work/site", "project /work/api"), actions.calls)
        }

    @Test
    fun `B-NAV-011 the server's projects carry their counts and are named for their server`() = runComposeUiTest {
        setContent { Themed { ProjectsPickerScreen(ready, Picker()) } }
        onNodeWithText("Projects on laptop").assertExists()
        onNodeWithText("3 conversations · 1 running · /work/api").assertExists()
    }

    @Test
    fun `a failed project list keeps a plain line with Try again`() = runComposeUiTest {
        val actions = Picker()
        setContent { Themed { ProjectsPickerScreen(ready.copy(projectsFailure = Failure.Server(500)), actions) } }
        onNodeWithText("Couldn’t load this server’s projects.").assertExists()
        onNodeWithText("Try again").performClick()
        assertTrue("retry" in actions.calls)
    }

    @Test
    fun `the open-folder dialog names its act and starts next to the folder used last`() = runComposeUiTest {
        setContent { Themed { ProjectsPickerScreen(ready.copy(askingPath = true, pathStart = "/work/"), Picker()) } }
        onNodeWithText("Open a project folder").assertExists()
        onNodeWithText("Open").assertExists()
    }

    @Test
    fun `B-NAV-010 the project page leads with New conversation here and lists In other projects`() = runComposeUiTest {
        val actions = Page()
        val page =
            ProjectPageUi(
                directory = "/work/app",
                name = "app",
                phase = ProjectPagePhase.Ready,
                conversations =
                    persistentListOf(ProjectChatUi(ConversationRef(ProfileId("p1"), SessionId("a")), ChatTitle.Text("Fix login"), "app", running = true)),
                elsewhere =
                    persistentListOf(ProjectChatUi(ConversationRef(ProfileId("p1"), SessionId("b")), ChatTitle.Text("Theme packs"), "site", running = false)),
                canLoadMore = true,
            )
        setContent { Themed { ProjectPageScreen(page, actions) } }
        onNodeWithText("Conversations in this project").assertExists()
        onNodeWithText("In other projects").assertExists()
        onNodeWithText("New conversation here").performClick()
        onNodeWithText("Fix login").performClick()
        onNodeWithText("Theme packs").performClick()
        onNodeWithText("Load more conversations").performClick()
        assertEquals(listOf("new", "open a", "open b", "more"), actions.calls)
    }

    @Test
    fun `a refused new conversation reads in plain words with Try again`() = runComposeUiTest {
        val actions = Page()
        val page =
            ProjectPageUi(
                directory = "/work/app",
                name = "app",
                phase = ProjectPagePhase.Ready,
                createFailure = Failure.Server(500),
            )
        setContent { Themed { ProjectPageScreen(page, actions) } }
        onNodeWithText("Couldn’t start the conversation.").assertExists()
        onNodeWithText("No conversations in this project yet.").assertExists()
        onNodeWithText("Try again").performClick()
        assertEquals(listOf("new"), actions.calls)
    }
}
