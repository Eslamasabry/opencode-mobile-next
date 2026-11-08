package io.github.eslamasabry.oc.feature.chats.rb.o41

import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.engine.agents.feed.ProjectSummary
import io.github.eslamasabry.oc.feature.chats.RecordingChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlin.test.Test
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.stopRowSemantics
import io.github.eslamasabry.oc.ui.words.res.workRunawayHelperInProject
import io.github.eslamasabry.oc.ui.words.res.workRunawaySeeRunning
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import org.jetbrains.compose.resources.stringResource

/**
 * Real Conversations UI with test/goldens/chats_home_golden_test.dart's scene words and states (Flutter `_source`): six
 * conversations at 2026-10-03 12:00, the projects alpha (dart, git), beta (node) and gamma, and two more in temp and home
 * folders for the project sheet. Flutter's ChatsHomeScreen draws no bar (the shell's bar sits above it,
 * chats_home_screen.dart:32), so neither does the page here (`shellBar = false`).
 *
 * `chats_home_leftover_*`: the "leftover node process" line is the shell's runaway watcher's status (Flutter
 * `leftoverNoticeLine`, chats_host.dart:578-603), which the app hands the home as its status line ([ChatsScreen]'s
 * `appStatus`); the case builds that status as the watcher would. The `chats_new_*` start screen is in
 * NewConversationGateTest.
 */
class ChatsGateTest {
    private val actions = RecordingChatsActions()

    private fun row(id: String, title: String, project: String, status: RowStatus, minutesAgo: Long, directory: String = "/root/projects/$project") =
        ConversationRowUi(
            id,
            ConversationRef(ProfileId("p1"), SessionId(id)),
            RowTitle.Text(title),
            project,
            status,
            updatedAtMs = NOW - minutesAgo * MINUTE,
            directory = directory,
        )

    private val login = row("a", "Fix the login bug", "alpha", RowStatus.NeedsYou, 2)
    private val database = row("e", "Which database should we use", "gamma", RowStatus.NeedsYou, 40)
    private val dark = row("b", "Add dark mode", "beta", RowStatus.Running, 9)
    private val build = row("c", "Explain the build", "alpha", RowStatus.Done, 3 * 60)
    private val rename = row("d", "Rename the settings page and move its tests next to it", "beta", RowStatus.None, 3 * DAY)
    private val release = row("f", "Plan the release notes", "gamma", RowStatus.None, 9 * DAY)

    private val projects =
        persistentListOf(
            ProjectSummary("/root/projects/alpha", "alpha", 2, 0, 0),
            ProjectSummary("/root/projects/beta", "beta", 2, 1, 0),
            ProjectSummary("/root/projects/gamma", "gamma", 2, 0, 0),
        )

    private fun mixedState() =
        ChatsUi(
            phase = ChatsPhase.List,
            needsYou = 2,
            running = 1,
            today = persistentListOf(login, database, dark, build),
            earlier = persistentListOf(rename, release),
            allCount = 6,
            nowMs = NOW,
            projectChoices = projects,
            projectNeedsYou = persistentSetOf("/root/projects/alpha", "/root/projects/gamma"),
            projectFacts =
                persistentMapOf(
                    "/root/projects/alpha" to HomeProjectFacts(HomeProjectKind.Dart, git = true),
                    "/root/projects/beta" to HomeProjectFacts(HomeProjectKind.Node),
                ),
        )

    private fun shot(name: String, state: ChatsUi, status: (@Composable () -> KitStatus)? = null) =
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen) {
            ChatsScreen(state, actions, appStatus = status?.invoke(), shellBar = false)
        }

    // Flutter's scene (chats_home_golden_test.dart:140-153): helper node, project beta, busy for 12 min.
    @Composable
    private fun leftoverStatus() =
        KitStatus(
            KitStatusKind.Work,
            stringResource(Res.string.workRunawayHelperInProject, "node", "beta", "12 min"),
            id = "work:leftover",
            icon = KitIcons.model,
            action = KitLineAction(stringResource(Res.string.stopRowSemantics, "node"), {}),
            onDismiss = {},
            dismissLabel = stringResource(Res.string.workspaceDismissNotice),
            more = listOf(KitLineAction(stringResource(Res.string.workRunawaySeeRunning), {})),
        )

    @Test fun leftover() { shot("chats_home_leftover", mixedState()) { leftoverStatus() } }
    @Test fun leftoverWide() { shot("chats_home_leftover_1280x800", mixedState()) { leftoverStatus() } }

    // G1, G2: the list under its heading and chips, rows one hairline apart.
    @Test fun mixed() { shot("chats_home_mixed", mixedState()) }
    @Test fun mixedWide() { shot("chats_home_mixed_1280x800", mixedState()) }

    // Running only (Flutter initialFilter: running).
    private fun runningState() =
        mixedState().copy(
            filters = ChatsFilters(running = true),
            today = persistentListOf(dark),
            earlier = persistentListOf(),
        )
    @Test fun running() { shot("chats_home_running", runningState()) }
    @Test fun runningWide() { shot("chats_home_running_1280x800", runningState()) }

    // G4: the project sheet over the list, with the Other folders row: Flutter's scene adds a scratch idea in /tmp and
    // home notes in /root (test/goldens/chats_home_golden_test.dart:97-116), which the list behind the sheet shows too, in the feed's order (as the golden lists them).
    private val scratch = row("g", "Scratch idea", "tmp", RowStatus.None, 2 * DAY, directory = "/tmp/scratch")
    private val homeNotes = row("h", "Home notes", "Home", RowStatus.None, 4 * DAY, directory = "/root")
    private fun sheetState() =
        mixedState().copy(
            earlier = persistentListOf(rename, release, scratch, homeNotes),
            allCount = 8,
            otherFoldersCount = 2,
            sheet = ChatsHomeSheet.Projects,
        )
    @Test fun projectSheet() { shot("chats_home_sheet", sheetState()) }
    @Test fun projectSheetWide() { shot("chats_home_sheet_1280x800", sheetState()) }

    // G3: the calm first-run page.
    @Test fun empty() { shot("chats_home_empty", ChatsUi(phase = ChatsPhase.Empty, nowMs = NOW)) }
    @Test fun emptyWide() { shot("chats_home_empty_1280x800", ChatsUi(phase = ChatsPhase.Empty, nowMs = NOW)) }

    // G3: Running on alpha with nothing running there (Flutter "chats-empty-running").
    private fun noMatchState() =
        mixedState().copy(
            phase = ChatsPhase.NoMatch,
            filters = ChatsFilters(projectDirectory = "/root/projects/alpha", running = true),
            today = persistentListOf(),
            earlier = persistentListOf(),
        )
    @Test fun noMatch() { shot("chats_home_nomatch", noMatchState()) }
    @Test fun noMatchWide() { shot("chats_home_nomatch_1280x800", noMatchState()) }

    private companion object {
        /** 2026-10-03 12:00 UTC, Flutter's `_now`. */
        const val NOW = 1_791_028_800_000L
        const val MINUTE = 60_000L
        const val DAY = 24 * 60L
    }
}
