package io.github.eslamasabry.oc.feature.team

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.team.TeamFinding
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamProject
import io.github.eslamasabry.oc.engine.team.TeamRequest
import io.github.eslamasabry.oc.engine.team.TeamState
import io.github.eslamasabry.oc.engine.team.TeamTask
import io.github.eslamasabry.oc.feature.team.presentation.BoardColumn
import io.github.eslamasabry.oc.feature.team.presentation.EditorKind
import io.github.eslamasabry.oc.feature.team.presentation.RefusalUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamBoardActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamEditorActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamEditorRules
import io.github.eslamasabry.oc.feature.team.presentation.TeamEditorUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamIntroUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamPresent
import io.github.eslamasabry.oc.feature.team.presentation.TeamProjectActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamServer as IntroServer
import io.github.eslamasabry.oc.feature.team.presentation.TeamServerKind
import io.github.eslamasabry.oc.feature.team.presentation.TeamServersActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamServersPresent
import io.github.eslamasabry.oc.feature.team.presentation.TeamTaskActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamTimelineActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamTimelinePresent
import io.github.eslamasabry.oc.feature.team.presentation.TimelineFilter
import io.github.eslamasabry.oc.feature.team.ui.TeamBoardScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamEditorScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamHomeScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamProjectScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamServersScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamTaskScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamTimelineScreen
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone
import kotlin.test.Test

/** Audit capture only (throwaway): the AI Team screens, each in a few states. Fixtures are built inside the content so one bad fixture costs one shot. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val m = "team"

    /** Shoots kept out of the default run to bound CI time; empty this set to capture them too. */
    private val skip = setOf(
        "home/loading", "home/ready-empty", "home/computer-team", "home/blocked",
        "off/unknown-server", "off/this-phone-turning-on", "off/computer",
        "project/stale", "project/paused", "project/not-found",
        "board/backlog", "board/filtered-milestone", "board/not-found",
        "task/merged", "task/queued", "task/not-found",
        "editor/plan", "editor/restoring", "editor/settings",
        "servers/not-found", "timeline/problems",
    )

    private fun shot(
        screen: String,
        state: String,
        advanceMs: Long = 1_000,
        setup: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {},
        content: @Composable (dark: Boolean) -> Unit,
    ) {
        if ("$screen/$state" in skip) return
        Audit.shoot(m, screen, state, advanceMs, setup, content)
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitChatEnvironment(Redactor()).Provide { content() }
        }

    private class HomeA : TeamHomeActions {
        override fun back() {}
        override fun openProject(id: String) {}
        override fun openRequest(projectId: String, requestId: String) {}
        override fun resumeProject(projectId: String) {}
        override fun retry() {}
    }

    private class ProjectA : TeamProjectActions {
        override fun back() {}
        override fun openBoard(milestone: String?) {}
        override fun retry() {}
    }

    private class BoardA : TeamBoardActions {
        override fun back() {}
        override fun showGraph(graph: Boolean) {}
        override fun filterMilestone(id: String?) {}
        override fun filterRepo(id: String?) {}
        override fun filterServer(id: String?) {}
        override fun selectColumn(column: BoardColumn) {}
    }

    private class TaskA : TeamTaskActions {
        override fun back() {}
        override fun answer(requestId: String, text: String) {}
        override fun resume() {}
        override fun message(text: String) {}
        override fun retry() {}
        override fun dismissRefusal() {}
    }

    private class EditorA : TeamEditorActions {
        override fun back() {}
        override fun setText(key: String, text: String) {}
        override fun save() {}
    }

    private class ServersA : TeamServersActions {
        override fun back() {}
        override fun retry() {}
        override fun openTask(taskId: String) {}
        override fun editLanes(serverId: String?) {}
        override fun saveLanes(serverId: String, lanes: Int) {}
        override fun chooseMove(taskId: String, serverId: String) {}
        override fun moveWithNote(note: String?) {}
        override fun restartElsewhere(yes: Boolean) {}
        override fun dismissRefusal() {}
    }

    private class TimelineA : TeamTimelineActions {
        override fun back() {}
        override fun filter(f: TimelineFilter) {}
        override fun openTask(taskId: String) {}
    }

    private val ready get() = TeamState(TeamPhase.Ready, workspace = team(shop()), health = READY_HEALTH)

    private fun longShop(): TeamProject =
        shop().copy(
            name = "A project whose name is far too long to fit on a single line of a phone screen",
            specDraft = shop().specDraft.copy(goal = "Sell shoes online with a checkout, receipts, refunds, a warehouse feed and weekly reports for the whole company"),
        )

    // ---------------------------------------------------------------- home

    @Test
    fun home() = Audit.guard("${m}__home__build") {
        val a = HomeA()
        shot("home", "loading") { Themed { TeamHomeScreen(TeamHomeUi(phase = TeamPhase.Loading, loading = true), a) } }
        shot("home", "ready") { Themed { TeamHomeScreen(TeamPresent.home(ready, NOW), a) } }
        shot("home", "ready-empty") {
            Themed { TeamHomeScreen(TeamPresent.home(TeamState(TeamPhase.Ready, workspace = team(), health = READY_HEALTH), NOW), a) }
        }
        shot("home", "ready-many-long") {
            Themed {
                TeamHomeScreen(
                    TeamPresent.home(
                        TeamState(
                            TeamPhase.Ready,
                            workspace = team(
                                longShop(),
                                TeamProject(id = "old", name = "Old", status = "done", simulated = false),
                                TeamProject(id = "p", name = "Paused", status = "paused", simulated = false),
                            ),
                            health = READY_HEALTH,
                        ),
                        NOW,
                    ),
                    a,
                )
            }
        }
        shot("home", "stale") { Themed { TeamHomeScreen(TeamPresent.home(ready.copy(stale = true), NOW), a) } }
        shot("home", "blocked") { Themed { TeamHomeScreen(TeamPresent.home(ready.copy(health = BLOCKED_HEALTH), NOW), a) } }
        shot("home", "interrupted") {
            Themed {
                TeamHomeScreen(
                    TeamPresent.home(TeamState(TeamPhase.Ready, workspace = team(shop().copy(status = "interrupted", requests = emptyList())), health = READY_HEALTH), NOW),
                    a,
                )
            }
        }
        shot("home", "phone-not-answering") { Themed { TeamHomeScreen(TeamPresent.home(TeamState(TeamPhase.PhoneOff), NOW), a) } }
        shot("home", "computer-team") { Themed { TeamHomeScreen(TeamPresent.home(TeamState(TeamPhase.Computer), NOW), a) } }
    }

    @Test
    fun off() = Audit.guard("${m}__off__build") {
        val a = HomeA()
        fun off(intro: TeamIntroUi) = TeamHomeUi(phase = TeamPhase.Off, intro = intro)
        shot("off", "unknown-server") { Themed { TeamHomeScreen(off(TeamIntroUi()), a) } }
        shot("off", "this-phone") {
            Themed { TeamHomeScreen(off(TeamIntroUi(IntroServer(TeamServerKind.ThisPhone, "This phone"), canTurnOn = true)), a) }
        }
        shot("off", "this-phone-turning-on") {
            Themed { TeamHomeScreen(off(TeamIntroUi(IntroServer(TeamServerKind.ThisPhone, "This phone"), canTurnOn = true, turningOn = true)), a) }
        }
        shot("off", "turn-on-failed") {
            Themed {
                TeamHomeScreen(
                    off(TeamIntroUi(IntroServer(TeamServerKind.ThisPhone, "This phone"), canTurnOn = true, failure = Failure.Network(NetworkWhy.Offline))),
                    a,
                )
            }
        }
        shot("off", "computer") {
            Themed { TeamHomeScreen(off(TeamIntroUi(IntroServer(TeamServerKind.Computer, "studio-pc"))), a) }
        }
        shot("off", "computer-guide") {
            Themed { TeamHomeScreen(off(TeamIntroUi(IntroServer(TeamServerKind.Computer, "studio-pc"), guide = true)), a) }
        }
    }

    // ---------------------------------------------------------------- project, board, task

    @Test
    fun project() = Audit.guard("${m}__project__build") {
        val a = ProjectA()
        shot("project", "running") { Themed { TeamProjectScreen(TeamPresent.project(ready, "shop", NOW), a) } }
        shot("project", "long-names") {
            Themed {
                TeamProjectScreen(
                    TeamPresent.project(TeamState(TeamPhase.Ready, workspace = team(longShop()), health = READY_HEALTH), "shop", NOW),
                    a,
                )
            }
        }
        shot("project", "stale") { Themed { TeamProjectScreen(TeamPresent.project(ready.copy(stale = true), "shop", NOW), a) } }
        shot("project", "interrupted") {
            Themed {
                TeamProjectScreen(
                    TeamPresent.project(TeamState(TeamPhase.Ready, workspace = team(shop().copy(status = "interrupted", requests = emptyList())), health = READY_HEALTH), "shop", NOW),
                    a,
                )
            }
        }
        shot("project", "paused") {
            Themed {
                TeamProjectScreen(
                    TeamPresent.project(TeamState(TeamPhase.Ready, workspace = team(shop().copy(status = "paused", requests = emptyList())), health = READY_HEALTH), "shop", NOW),
                    a,
                )
            }
        }
        shot("project", "not-found") { Themed { TeamProjectScreen(TeamPresent.project(ready, "missing", NOW), a) } }
    }

    @Test
    fun board() = Audit.guard("${m}__board__build") {
        val a = BoardA()
        shot("board", "working") {
            Themed { TeamBoardScreen(TeamPresent.board(ready, "shop", TeamPresent.BoardFilters(column = BoardColumn.Working)), a) }
        }
        shot("board", "backlog") {
            Themed { TeamBoardScreen(TeamPresent.board(ready, "shop", TeamPresent.BoardFilters(column = BoardColumn.Backlog)), a) }
        }
        shot("board", "dependencies") {
            Themed { TeamBoardScreen(TeamPresent.board(ready, "shop", TeamPresent.BoardFilters(graph = true)), a) }
        }
        shot("board", "filtered-milestone") {
            Themed { TeamBoardScreen(TeamPresent.board(ready, "shop", TeamPresent.BoardFilters(milestone = "m1")), a) }
        }
        shot("board", "not-found") { Themed { TeamBoardScreen(TeamPresent.board(ready, "missing", TeamPresent.BoardFilters()), a) } }
    }

    @Test
    fun task() = Audit.guard("${m}__task__build") {
        val a = TaskA()
        shot("task", "running-with-question") { Themed { TeamTaskScreen(TeamPresent.task(ready, "shop", "t2", NOW), a) } }
        shot("task", "merged") { Themed { TeamTaskScreen(TeamPresent.task(ready, "shop", "t1", NOW), a) } }
        shot("task", "queued") { Themed { TeamTaskScreen(TeamPresent.task(ready, "shop", "t3", NOW), a) } }
        shot("task", "review") { Themed { TeamTaskScreen(TeamPresent.task(ready, "shop", "t5", NOW), a) } }
        shot("task", "findings") {
            Themed {
                val t = TeamTask(
                    "t", "Pay form", status = "review",
                    findings = listOf(
                        TeamFinding("f1", "critical", text = "Card number logged"),
                        TeamFinding("f2", "minor", text = "A very long finding text that goes on and on about a style problem in the pay form component"),
                    ),
                )
                val p = TeamProject(id = "a", simulated = false, tasks = listOf(t), requests = listOf(TeamRequest("q", "findings", "Review the findings", taskId = "t")))
                TeamTaskScreen(TeamPresent.task(TeamState(TeamPhase.Ready, workspace = team(p), health = READY_HEALTH), "a", "t", NOW, setOf("f1")), a)
            }
        }
        shot("task", "refused") {
            Themed { TeamTaskScreen(TeamPresent.task(ready, "shop", "t2", NOW).copy(refusal = RefusalUi("conflict", "message")), a) }
        }
        shot("task", "not-found") { Themed { TeamTaskScreen(TeamPresent.task(ready, "shop", "missing", NOW), a) } }
    }

    // ---------------------------------------------------------------- editors and project pages

    private fun editor(kind: EditorKind, withProject: Boolean, refusal: RefusalUi? = null): TeamEditorUi {
        val ws = team(shop())
        val project = if (withProject) shop() else null
        val (form, _) = TeamEditorRules.initial(kind, ws, project) { "x" }
        return TeamEditorUi(
            kind,
            phase = TeamPhase.Ready,
            form = form,
            restoring = false,
            servers = ws.servers.toImmutableList(),
            refusal = refusal,
        )
    }

    @Test
    fun editors() = Audit.guard("${m}__editor__build") {
        val a = EditorA()
        shot("editor", "new-project") { Themed { TeamEditorScreen(editor(EditorKind.Create, false), a) } }
        shot("editor", "new-project-refused") {
            Themed { TeamEditorScreen(editor(EditorKind.Create, false, RefusalUi("repository_empty", "createProject")), a) }
        }
        shot("editor", "quick-task") { Themed { TeamEditorScreen(editor(EditorKind.Quick, false), a) } }
        shot("editor", "defaults") { Themed { TeamEditorScreen(editor(EditorKind.Defaults, false), a) } }
        shot("editor", "roles") { Themed { TeamEditorScreen(editor(EditorKind.Roles, false), a) } }
        shot("editor", "spec") { Themed { TeamEditorScreen(editor(EditorKind.Spec, true), a) } }
        shot("editor", "plan") { Themed { TeamEditorScreen(editor(EditorKind.Plan, true), a) } }
        shot("editor", "settings") { Themed { TeamEditorScreen(editor(EditorKind.Settings, true), a) } }
        shot("editor", "restoring") {
            Themed { TeamEditorScreen(editor(EditorKind.Create, false).copy(restoring = true, phase = TeamPhase.Loading), a) }
        }
    }

    @Test
    fun projectPages() = Audit.guard("${m}__pages__build") {
        shot("servers", "ready") { Themed { TeamServersScreen(TeamServersPresent.present(ready, "shop"), ServersA()) } }
        shot("servers", "not-found") { Themed { TeamServersScreen(TeamServersPresent.present(ready, "missing"), ServersA()) } }
        shot("timeline", "all") {
            Themed { TeamTimelineScreen(TeamTimelinePresent.present(ready, "shop", TimelineFilter.All, NOW, TimeZone.UTC), TimelineA()) }
        }
        shot("timeline", "problems") {
            Themed { TeamTimelineScreen(TeamTimelinePresent.present(ready, "shop", TimelineFilter.Problems, NOW, TimeZone.UTC), TimelineA()) }
        }
    }
}
