package io.github.eslamasabry.oc.feature.team

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamState
import io.github.eslamasabry.oc.feature.team.presentation.BoardColumn
import io.github.eslamasabry.oc.feature.team.presentation.TeamBoardActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamPresent
import io.github.eslamasabry.oc.feature.team.presentation.TeamProjectActions
import io.github.eslamasabry.oc.feature.team.ui.TeamBoardScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamHomeScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamProjectScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** The real team screens with fake state, found by visible text (Flutter team_projects_screen wording). */
@OptIn(ExperimentalTestApi::class)
class TeamScreensUiTest {
    private class Recorder : TeamHomeActions, TeamProjectActions, TeamBoardActions {
        val calls = mutableListOf<String>()

        override fun back() {
            calls += "back"
        }

        override fun openProject(id: String) {
            calls += "project $id"
        }

        override fun openRequest(projectId: String, requestId: String) {
            calls += "request $projectId $requestId"
        }

        override fun retry() {
            calls += "retry"
        }

        override fun resumeProject(projectId: String) {
            calls += "resumeProject $projectId"
        }

        override fun answer(requestId: String, text: String) {
            calls += "answer $requestId $text"
        }

        override fun askStop() {
            calls += "askStop"
        }

        override fun acceptMilestone(milestoneId: String) {
            calls += "accept $milestoneId"
        }

        override fun openBoard(milestone: String?) {
            calls += "board $milestone"
        }

        override fun showGraph(graph: Boolean) {
            calls += "graph $graph"
        }

        override fun filterMilestone(id: String?) {
            calls += "milestone $id"
        }

        override fun filterRepo(id: String?) {
            calls += "repo $id"
        }

        override fun filterServer(id: String?) {
            calls += "server $id"
        }

        override fun selectColumn(column: BoardColumn) {
            calls += "column $column"
        }
    }

    @Composable
    private fun Themed(locale: String = "en", content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = locale,
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp).height(900.dp)) { content() }
            }
        }
    }

    private val ready = TeamState(TeamPhase.Ready, workspace = team(shop()))

    @Test
    fun `B-TEAM-040 the team page shows the ask and the project, and each opens`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Themed { TeamHomeScreen(TeamPresent.home(ready, NOW), actions) } }
        onNodeWithText("AI Team").assertIsDisplayed()
        onNodeWithText("Milestone 2 of 2 · 1 working").assertIsDisplayed()
        onNodeWithText("$1.50 of $5 today").assertIsDisplayed()
        onNodeWithText("Which card provider?").performClick()
        onNodeWithText("Milestone 2 of 2 · 1 working").performClick()
        assertEquals(listOf("request shop q1", "project shop"), actions.calls)
    }

    @Test
    fun `B-TEAM-041 last-known projects say so and offer another try`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Themed { TeamHomeScreen(TeamPresent.home(ready.copy(stale = true), NOW), actions) } }
        onNodeWithText("Last known state").assertIsDisplayed()
        onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), actions.calls)
    }

    @Test
    fun `B-TEAM-042 the phone's team not answering says it is off`() = runComposeUiTest {
        setContent { Themed { TeamHomeScreen(TeamPresent.home(TeamState(TeamPhase.PhoneOff), NOW), Recorder()) } }
        onNodeWithText("AI Team is off").assertIsDisplayed()
        onNodeWithText("It needs a quick safety check, for example after an app update.").assertExists()
    }

    @Test
    fun `B-TEAM-043 the project shows goal, milestones and lanes, and a milestone opens its board`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Themed { TeamProjectScreen(TeamPresent.project(ready, "shop", NOW), actions) } }
        onNodeWithText("Sell shoes").assertIsDisplayed()
        onNodeWithText("Needs your decision · Frontend on This phone").assertExists()
        onNodeWithText("Lanes 1/2 busy").assertExists()
        onNodeWithText("Frontend · Pay form").assertExists()
        onNodeWithText("Desk · Not reachable · last known tasks").assertExists()
        onNodeWithText("2 Pay").performClick()
        assertEquals(listOf("board m2"), actions.calls)
    }

    @Test
    fun `B-TEAM-044 the board shows Flutter's lanes and switches to dependencies`() = runComposeUiTest {
        val actions = Recorder()
        val state = TeamPresent.board(ready, "shop", TeamPresent.BoardFilters(column = BoardColumn.Working))
        setContent { Themed { TeamBoardScreen(state, actions) } }
        onNodeWithText("Pay form", useUnmergedTree = true).assertExists()
        onNodeWithText("Dependencies").performClick()
        assertEquals(listOf("graph true"), actions.calls)
    }

    @Test
    fun `B-TEAM-045 the team page reads in Arabic`() = runComposeUiTest {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
        try {
            setContent { Themed("ar") { TeamHomeScreen(TeamPresent.home(TeamState(TeamPhase.PhoneOff), NOW), Recorder()) } }
            onNodeWithText("فريق الذكاء الاصطناعي متوقف").assertIsDisplayed()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `B-TEAM-046 Answer asks for the answer and sends it`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Themed { TeamProjectScreen(TeamPresent.project(ready, "shop", NOW), actions) } }
        onNodeWithText("Answer").performClick()
        onNodeWithText("Your answer").assertExists()
        onNode(hasSetTextAction()).performTextInput("Stripe")
        onAllNodesWithText("Answer").onLast().performClick()
        assertEquals(listOf("answer q1 Stripe"), actions.calls)
    }

    @Test
    fun `B-TEAM-047 the project menu stops the project only after asking`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Themed { TeamProjectScreen(TeamPresent.project(ready, "shop", NOW), actions) } }
        onNodeWithContentDescription("Project menu").performClick()
        onNodeWithText("Pause project").assertExists()
        onNodeWithText("Stop project").performClick()
        assertEquals(listOf("askStop"), actions.calls)
    }

    @Test
    fun `B-TEAM-048 an interrupted project offers to resume from the team page`() = runComposeUiTest {
        val actions = Recorder()
        val state = TeamState(TeamPhase.Ready, workspace = team(shop().copy(status = "interrupted", requests = emptyList())))
        setContent { Themed { TeamHomeScreen(TeamPresent.home(state, NOW), actions) } }
        onNodeWithText("Shop: work was interrupted, tap to resume").performClick()
        assertEquals(listOf("resumeProject shop"), actions.calls)
    }
}
