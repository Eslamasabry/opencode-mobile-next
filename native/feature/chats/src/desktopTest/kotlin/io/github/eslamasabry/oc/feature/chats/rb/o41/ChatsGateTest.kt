package io.github.eslamasabry.oc.feature.chats.rb.o41

import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.engine.agents.feed.ProjectSummary
import io.github.eslamasabry.oc.feature.chats.RecordingChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/** Real Conversations UI with Flutter chats_home_golden_test.dart's scene words and states. */
class ChatsGateTest {
    private val actions = RecordingChatsActions()
    private fun row(id: String, title: String, project: String, status: RowStatus, age: RelativeAge) =
        ConversationRowUi(id, ConversationRef(ProfileId("p1"), SessionId(id)), RowTitle.Text(title), project, status, age)
    private fun mixedState() = ChatsUi(
        phase = ChatsPhase.List, needsYou = 2, running = 1,
        today = persistentListOf(
            row("login", "Fix the login bug", "alpha", RowStatus.NeedsYou, RelativeAge.Minutes(2)),
            row("database", "Which database should we use", "gamma", RowStatus.NeedsYou, RelativeAge.Minutes(40)),
            row("dark", "Add dark mode", "beta", RowStatus.Running, RelativeAge.Minutes(9)),
            row("build", "Explain the build", "alpha", RowStatus.Done, RelativeAge.Hours(3)),
        ),
        earlier = persistentListOf(
            row("rename", "Rename the settings page and move its tests next to it", "beta", RowStatus.None, RelativeAge.Days(3)),
            row("release", "Plan the release notes", "gamma", RowStatus.None, RelativeAge.Days(9)),
        ),
        allCount = 6,
        projectChoices = persistentListOf(ProjectSummary("/alpha", "alpha", 2, 0, 0), ProjectSummary("/beta", "beta", 2, 1, 0), ProjectSummary("/gamma", "gamma", 2, 0, 0)),
    )
    private fun shot(name: String, state: ChatsUi) = FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen) { ChatsScreen(state, actions) }
    @Test fun mixed() { shot("chats_home_mixed", mixedState()) }
    @Test fun mixedWide() { shot("chats_home_mixed_1280x800", mixedState()) }
    @Test fun projectSheet() { shot("chats_home_sheet", mixedState().copy(sheet = ChatsHomeSheet.Projects)) }
    @Test fun empty() { shot("chats_home_empty", ChatsUi(phase = ChatsPhase.Empty)) }
    @Test fun emptyWide() { shot("chats_home_empty_1280x800", ChatsUi(phase = ChatsPhase.Empty)) }
}
