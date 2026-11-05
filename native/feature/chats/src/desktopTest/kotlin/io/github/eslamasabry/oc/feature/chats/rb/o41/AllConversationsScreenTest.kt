package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.feature.chats.ui.ChatsAllScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AllConversationsScreenTest {
    private class Actions : ChatsAllActions {
        val calls = mutableListOf<String>()
        override fun back() {}
        override fun search(query: String) { calls += "search:$query" }
        override fun toggleRunning() { calls += "running" }
        override fun toggleArchived() { calls += "archive-filter" }
        override fun chooseProject(directory: String) { calls += "project:$directory" }
        override fun clearFilters() { calls += "clear-filters" }
        override fun tapRow(key: String) {}
        override fun startSelecting(key: String?) {}
        override fun stopSelecting() {}
        override fun rename(key: String) {}
        override fun renameSelected() {}
        override fun finishRename(title: String?) {}
        override fun delete(key: String) {}
        override fun deleteSelected() {}
        override fun undoDelete() {}
        override fun commitDelete() {}
        override fun archive(key: String) {}
        override fun archiveSelected() {}
        override fun loadMore() {}
        override fun retry() {}
        override fun dismissProblem() {}
        override fun openServers() {}
    }

    @Test fun `B12 project choices move to the search menu and archived remains a visible segment`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                ChatsAllScreen(AllConversationsUi(phase = AllPhase.List, projects = persistentListOf(
                    ProjectChipUi("/one", "one", false), ProjectChipUi("/two", "two", false),
                )), actions)
            }
        }
        onNodeWithText("one").assertDoesNotExist()
        onNodeWithText("Active").assertExists()
        onNodeWithText("Archived").performClick()
        assertEquals(listOf("archive-filter"), actions.calls)
        onNodeWithContentDescription("Filter").performClick()
        onNodeWithText("two").performClick()
        assertEquals(listOf("archive-filter", "project:/two"), actions.calls)
    }

    @Test fun `B12 clearing an archived search leaves the other filters alone`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                ChatsAllScreen(AllConversationsUi(phase = AllPhase.NoMatch, archived = true, query = "checkout"), actions)
            }
        }
        onNodeWithText("No archived conversation has that title. Try a shorter search.").assertExists()
        onNodeWithText("Clear search").performClick()
        assertEquals(listOf("search:"), actions.calls)
    }
}
