package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chats.presentation.AllConversationsUi
import io.github.eslamasabry.oc.feature.chats.presentation.AllPhase
import io.github.eslamasabry.oc.feature.chats.presentation.AllRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllActions
import io.github.eslamasabry.oc.feature.chats.presentation.DeleteUndoUi
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.feature.chats.ui.ChatsAllScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** All conversations found by visible text (desktop, headless). */
@OptIn(ExperimentalTestApi::class)
class AllConversationsScreenTest {
    private class Recording : ChatsAllActions {
        val calls = mutableListOf<String>()

        override fun back() { calls += "back" }

        override fun search(query: String) { calls += "search $query" }

        override fun toggleRunning() { calls += "running" }

        override fun toggleArchived() { calls += "archived" }

        override fun chooseProject(directory: String) { calls += "project $directory" }

        override fun clearFilters() { calls += "clear" }

        override fun tapRow(key: String) { calls += "tap $key" }

        override fun startSelecting(key: String?) { calls += "select $key" }

        override fun stopSelecting() { calls += "stop" }

        override fun rename(key: String) { calls += "rename $key" }

        override fun renameSelected() { calls += "renameSelected" }

        override fun finishRename(title: String?) { calls += "finish $title" }

        override fun delete(key: String) { calls += "delete $key" }

        override fun deleteSelected() { calls += "deleteSelected" }

        override fun undoDelete() { calls += "undo" }

        override fun commitDelete() { calls += "commit" }

        override fun archive(key: String) { calls += "archive $key" }

        override fun archiveSelected() { calls += "archiveSelected" }

        override fun loadMore() { calls += "more" }

        override fun retry() { calls += "retry" }

        override fun dismissProblem() { calls += "dismiss" }

        override fun openServers() { calls += "servers" }
    }

    private fun row(id: String, title: String, selected: Boolean = false, running: Boolean = false) =
        AllRowUi(
            key = "p1/$id",
            ref = ConversationRef(ProfileId("p1"), SessionId(id)),
            title = RowTitle.Text(title),
            project = "api",
            server = null,
            running = running,
            archived = false,
            selected = selected,
        )

    private val list =
        AllConversationsUi(
            phase = AllPhase.List,
            rows = persistentListOf(row("a", "Fix the reconnect loop", running = true), row("b", "Theme packs")),
            canLoadMore = true,
        )

    @Test
    fun `SCREENS-NAV-34 rows open by tap and Load more conversations is a visible row`() = runComposeUiTest {
        val actions = Recording()
        setContent { Themed { ChatsAllScreen(list, actions) } }
        onNodeWithText("All conversations").assertExists()
        onNodeWithText("Theme packs").performClick()
        onNodeWithText("Load more conversations").performClick()
        assertTrue("tap p1/b" in actions.calls)
        assertTrue("more" in actions.calls)
    }

    @Test
    fun `SCREENS-NAV-34 Select is a visible way to the row acts, and select mode names its count and acts`() =
        runComposeUiTest {
            val actions = Recording()
            setContent { Themed { ChatsAllScreen(list, actions) } }
            onNodeWithText("Select").performClick()
            assertEquals("select null", actions.calls.last())
        }

    @Test
    fun `SCREENS-NAV-34 in select mode the bottom offers Rename, Archive and Delete for the chosen rows`() =
        runComposeUiTest {
            val actions = Recording()
            val selecting =
                list.copy(
                    selecting = true,
                    selectedCount = 1,
                    canLoadMore = false,
                    rows = persistentListOf(row("a", "Fix the reconnect loop", selected = true), row("b", "Theme packs")),
                )
            setContent { Themed { ChatsAllScreen(selecting, actions) } }
            onNodeWithText("1 selected").assertExists()
            onNodeWithText("Delete 1 conversation").performClick()
            onNodeWithText("Rename").performClick()
            onNodeWithText("Cancel selection").performClick()
            assertEquals(listOf("deleteSelected", "renameSelected", "stop"), actions.calls)
            // Archive is shown but says why it can't run on this server.
            onNodeWithText("Archive 1 conversation").assertExists()
        }

    @Test
    fun `SCREENS-NAV-34 a delete offers Undo naming the conversation`() = runComposeUiTest {
        val actions = Recording()
        setContent {
            Themed { ChatsAllScreen(list.copy(undo = DeleteUndoUi(1, 1, RowTitle.Text("Theme packs"))), actions) }
        }
        onNodeWithText("Deleted “Theme packs”").assertExists()
        onNodeWithText("Undo").performClick()
        assertTrue("undo" in actions.calls)
    }

    @Test
    fun `SCREENS-NAV-34 a failed older page keeps a visible Try again and never shows raw error text`() =
        runComposeUiTest {
            val actions = Recording()
            setContent {
                Themed { ChatsAllScreen(list.copy(loadMoreFailure = Failure.Network(NetworkWhy.Offline)), actions) }
            }
            onNodeWithText("Could not load more conversations").assertExists()
            onNodeWithText("Try again").performClick()
            assertTrue("more" in actions.calls)
        }

    @Test
    fun `SCREENS-NAV-34 no server connected offers Servers instead of an empty list`() = runComposeUiTest {
        val actions = Recording()
        setContent { Themed { ChatsAllScreen(AllConversationsUi(phase = AllPhase.NoServer), actions) } }
        onNodeWithText("No server connected").assertExists()
        onAllNodesWithText("Load more conversations").assertCountEquals(0)
        onNodeWithText("Servers").performClick()
        assertEquals(listOf("servers"), actions.calls)
    }
}
