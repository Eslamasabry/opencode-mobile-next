package io.github.eslamasabry.oc.feature.team

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.TeamFinding
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamProject
import io.github.eslamasabry.oc.engine.team.TeamRequest
import io.github.eslamasabry.oc.engine.team.TeamState
import io.github.eslamasabry.oc.engine.team.TeamTask
import io.github.eslamasabry.oc.feature.team.presentation.EditorKind
import io.github.eslamasabry.oc.feature.team.presentation.EditorProblem
import io.github.eslamasabry.oc.feature.team.presentation.ProjectConfirm
import io.github.eslamasabry.oc.feature.team.presentation.RefusalUi
import io.github.eslamasabry.oc.feature.team.presentation.TaskConfirm
import io.github.eslamasabry.oc.feature.team.presentation.TeamEditorActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamEditorRules
import io.github.eslamasabry.oc.feature.team.presentation.TeamEditorUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamPresent
import io.github.eslamasabry.oc.feature.team.presentation.TeamProjectActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamTaskActions
import io.github.eslamasabry.oc.feature.team.ui.TeamEditorScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamHomeScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamProjectScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamTaskScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals

/** The new team pages with fake state, found by Flutter's words. */
@OptIn(ExperimentalTestApi::class)
class TeamFinishUiTest {
    private class Calls : TeamHomeActions, TeamProjectActions, TeamTaskActions, TeamEditorActions {
        val calls = mutableListOf<String>()

        override fun back() {
            calls += "back"
        }

        override fun openProject(id: String) {
            calls += "project $id"
        }

        override fun openRequest(projectId: String, requestId: String) = Unit

        override fun resumeProject(projectId: String) = Unit

        override fun retry() = Unit

        override fun dismissRefusal() = Unit

        override fun openEditor(kind: EditorKind, projectId: String) {
            calls += "editor ${kind.wire}"
        }

        override fun openEditor(kind: EditorKind) {
            calls += "editor ${kind.wire}"
        }

        override fun openBoard(milestone: String?) = Unit

        override fun answer(requestId: String, text: String) = Unit

        override fun resume() = Unit

        override fun message(text: String) = Unit

        override fun ask(confirm: ProjectConfirm) {
            calls += "ask $confirm"
        }

        override fun ask(confirm: TaskConfirm) {
            calls += "ask $confirm"
        }

        override fun setText(key: String, text: String) = Unit

        override fun save() {
            calls += "save"
        }

        override fun toggleFinding(id: String, on: Boolean) {
            calls += "tick $id $on"
        }

        override fun fixSelected() {
            calls += "fix"
        }

        override fun pause() = Unit

        override fun confirm() = Unit

        override fun cancelConfirm() = Unit
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
                io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment(io.github.eslamasabry.oc.core.security.Redactor()).Provide {
                    Box(Modifier.width(412.dp).height(900.dp)) { content() }
                }
            }
        }
    }

    private val ready = TeamState(TeamPhase.Ready, workspace = team(shop()), health = READY_HEALTH)

    @Test
    fun `B-TEAM-130 the team page starts a project or a quick task and opens defaults and roles`() = runComposeUiTest {
        val calls = Calls()
        setContent { Themed { TeamHomeScreen(TeamPresent.home(ready, NOW), calls) } }
        onNodeWithText("Protected by Android's file protection").assertExists()
        onNodeWithText("New project").performClick()
        onNodeWithText("Give a quick task").performClick()
        onNodeWithTag("team-defaults").performClick()
        onNodeWithTag("team-roles").performClick()
        assertEquals(listOf("editor create", "editor quick", "editor defaults", "editor roles"), calls.calls)
    }

    @Test
    fun `B-TEAM-131 an engine that cannot run work says so and offers no New project`() = runComposeUiTest {
        setContent { Themed { TeamHomeScreen(TeamPresent.home(ready.copy(health = BLOCKED_HEALTH), NOW), Calls()) } }
        onNodeWithTag("team-execution-blocked").assertExists()
        onNodeWithText("New project").assertDoesNotExist()
    }

    @Test
    fun `B-TEAM-132 New project's Start planning waits, with the reason, until the goal and a repo are there`() = runComposeUiTest {
        val calls = Calls()
        val (form, _) = TeamEditorRules.initial(EditorKind.Create, team(), null) { "x" }
        val ui = TeamEditorUi(EditorKind.Create, phase = TeamPhase.Ready, form = form, restoring = false, servers = team().servers.toImmutableList())
        setContent { Themed { TeamEditorScreen(ui, calls) } }
        onNodeWithText("New project").assertIsDisplayed()
        onNodeWithText("Start planning").assertIsNotEnabled()
        onNodeWithText("Add a goal and at least one repo.").assertExists()
    }

    @Test
    fun `B-TEAM-133 a refused start says it in plain words with the code under details`() = runComposeUiTest {
        val (form, _) = TeamEditorRules.initial(EditorKind.Create, team(), null) { "x" }
        val ui =
            TeamEditorUi(
                EditorKind.Create,
                phase = TeamPhase.Ready,
                form = form,
                restoring = false,
                refusal = RefusalUi("repository_empty", "createProject"),
            )
        setContent { Themed { TeamEditorScreen(ui, Calls()) } }
        onNodeWithTag("team-refusal").assertExists()
        onNodeWithText("Details").assertExists()
    }

    @Test
    fun `B-TEAM-134 the project page offers Delete project and its pages`() = runComposeUiTest {
        val calls = Calls()
        setContent { Themed { TeamProjectScreen(TeamPresent.project(ready, "shop", NOW), calls) } }
        onNode(hasScrollAction()).performScrollToNode(hasText("Delete project Shop"))
        onNodeWithText("Timeline").assertExists()
        onNodeWithText("Servers").assertExists()
        onNodeWithText("Delete project Shop").performClick()
        assertEquals(listOf("ask Delete"), calls.calls)
    }

    @Test
    fun `B-TEAM-135 a task's findings can be ticked and fixed`() = runComposeUiTest {
        val calls = Calls()
        val t = TeamTask("t", "Pay form", status = "review", findings = listOf(TeamFinding("f1", "critical", text = "Card number logged")))
        val p = TeamProject(id = "a", simulated = false, tasks = listOf(t), requests = listOf(TeamRequest("q", "findings", "Review the findings", taskId = "t")))
        val state = TeamState(TeamPhase.Ready, workspace = team(p), health = READY_HEALTH)
        setContent { Themed { TeamTaskScreen(TeamPresent.task(state, "a", "t", NOW, setOf("f1")), calls) } }
        onNode(hasScrollAction()).performScrollToNode(hasText("Fix selected"))
        onNodeWithText("Fix selected").performClick()
        assertEquals(listOf("fix"), calls.calls)
    }
}
