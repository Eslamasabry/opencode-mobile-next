package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.feature.chats.ui.AllConversationsPagingTail
import io.github.eslamasabry.oc.feature.chats.ui.ChatsAllScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class AllConversationsScreenTest {
    private class Actions : ChatsAllActions {
        val calls = mutableListOf<String>()

        override fun back() {}

        override fun search(query: String) {
            calls += "search:$query"
        }

        override fun toggleRunning() {
            calls += "running"
        }

        override fun toggleArchived() {
            calls += "archive-filter"
        }

        override fun chooseProject(directory: String) {
            calls += "project:$directory"
        }

        override fun clearFilters() {
            calls += "clear-filters"
        }

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

        override fun retry() {
            calls += "retry"
        }

        override fun dismissProblem() {}

        override fun openServers() {}
    }

    @Test
    fun `B12 active empty state offers refresh`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                appearance = KitAppearance(motion = MotionLevel.Off),
            ) {
                ChatsAllScreen(AllConversationsUi(phase = AllPhase.Empty), actions)
            }
        }
        onNodeWithText("Refresh").performClick()
        assertEquals(listOf("retry"), actions.calls)
    }

    @Test
    fun `B12 paging control yields to loading and returns after the read`() = runComposeUiTest {
        var state by
            androidx.compose.runtime.mutableStateOf(
                AllConversationsUi(phase = AllPhase.List, canLoadMore = true)
            )
        var calls = 0
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                appearance = KitAppearance(motion = MotionLevel.Off),
            ) {
                AllConversationsPagingTail(state) { calls++ }
            }
        }
        onNodeWithText("Load more conversations").performClick()
        assertEquals(1, calls)
        runOnIdle { state = state.copy(loadingMore = true) }
        onNodeWithText("Load more conversations").assertDoesNotExist()
        runOnIdle { state = state.copy(loadingMore = false) }
        onNodeWithText("Load more conversations").assertExists()
        runOnIdle { state = state.copy(canLoadMore = false) }
        onNodeWithText("Load more conversations").assertDoesNotExist()
    }

    @Test
    fun `B12 failed paging retains retry even without an exposed cursor`() = runComposeUiTest {
        var calls = 0
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                appearance = KitAppearance(motion = MotionLevel.Off),
            ) {
                AllConversationsPagingTail(
                    AllConversationsUi(
                        phase = AllPhase.NoMatch,
                        loadMoreFailure = Failure.Unexpected(),
                    )
                ) {
                    calls++
                }
            }
        }
        onNodeWithText("Could not load more conversations").assertExists()
        onNodeWithText("Try again").performClick()
        assertEquals(1, calls)
    }

    @Test
    fun `B12 project choices move to the search menu and archived remains a visible segment`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    appearance = KitAppearance(motion = MotionLevel.Off),
                ) {
                    ChatsAllScreen(
                        AllConversationsUi(
                            phase = AllPhase.List,
                            projects =
                                persistentListOf(
                                    ProjectChipUi("/one", "one", false),
                                    ProjectChipUi("/two", "two", false),
                                ),
                        ),
                        actions,
                    )
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

    @Test
    fun `B12 clearing an archived search leaves the other filters alone`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                appearance = KitAppearance(motion = MotionLevel.Off),
            ) {
                ChatsAllScreen(
                    AllConversationsUi(
                        phase = AllPhase.NoMatch,
                        archived = true,
                        query = "checkout",
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("No archived conversation has that title. Try a shorter search.")
            .assertExists()
        onNodeWithText("Clear search").performClick()
        assertEquals(listOf("search:"), actions.calls)
    }
}
