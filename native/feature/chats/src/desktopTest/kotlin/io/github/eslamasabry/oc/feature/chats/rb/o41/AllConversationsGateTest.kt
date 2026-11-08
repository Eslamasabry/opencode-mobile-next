package io.github.eslamasabry.oc.feature.chats.rb.o41

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chats.presentation.AllConversationsUi
import io.github.eslamasabry.oc.feature.chats.presentation.AllPhase
import io.github.eslamasabry.oc.feature.chats.presentation.AllRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllActions
import io.github.eslamasabry.oc.feature.chats.presentation.MoveHereUi
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.feature.chats.ui.ChatsAllScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/**
 * All conversations (Flutter GlobalSessionsScreen) with the scene of the `work_global_sessions_*` goldens: two projects,
 * opencode-mobile (in use) and marketing-site, four active conversations and one archived one.
 */
class AllConversationsGateTest {
    private object Actions : ChatsAllActions {
        override fun back() {}
        override fun search(query: String) {}
        override fun toggleRunning() {}
        override fun toggleArchived() {}
        override fun chooseProject(directory: String) {}
        override fun clearFilters() {}
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

    private fun row(id: String, title: String, project: String, running: Boolean = false, archived: Boolean = false) =
        AllRowUi(
            key = "p1/$id",
            ref = ConversationRef(ProfileId("p1"), SessionId(id)),
            title = RowTitle.Text(title),
            project = project,
            server = null,
            running = running,
            archived = archived,
            selected = false,
            canArchive = true,
            canContinueHere = project != "opencode-mobile",
        )

    private val active =
        persistentListOf(
            row("s1", "Fix the wake lock on Android 15", "opencode-mobile", running = true),
            row("s2", "Voice model download resumes after a restart", "opencode-mobile"),
            row("s3", "New hero section and pricing table", "marketing-site"),
            row("s4", "Tighten the landing page copy", "marketing-site"),
        )

    private val loaded = AllConversationsUi(phase = AllPhase.List, rows = active, hasArchived = true)

    private fun shot(name: String, state: AllConversationsUi) =
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen) { ChatsAllScreen(state, Actions) }

    @Test fun loaded() { shot("work_global_sessions_loaded", loaded) }
    @Test fun loadedWide() { shot("work_global_sessions_loaded_1280x800", loaded) }

    @Test
    fun archived() {
        shot(
            "work_global_sessions_archived",
            loaded.copy(archived = true, rows = persistentListOf(row("s5", "Spike: offline queue on SQLite", "opencode-mobile", archived = true))),
        )
    }

    @Test fun empty() { shot("work_global_sessions_empty", AllConversationsUi(phase = AllPhase.Empty)) }

    @Test
    fun error() {
        shot("work_global_sessions_error", AllConversationsUi(phase = AllPhase.Failed, failure = Failure.Network()))
    }

    @Test
    fun moveConfirm() {
        shot(
            "work_global_sessions_move_confirm",
            loaded.copy(
                moveHere =
                    MoveHereUi(
                        key = "p1/s3",
                        title = RowTitle.Text("New hero section and pricing table"),
                        from = "marketing-site",
                        to = "opencode-mobile",
                        sessionWorking = false,
                        working = false,
                        failed = false,
                    ),
            ),
        )
    }
}
