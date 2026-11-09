package io.github.eslamasabry.oc.feature.team

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.team.SetupPhase
import io.github.eslamasabry.oc.engine.team.SetupProblem
import io.github.eslamasabry.oc.engine.team.SetupServerState
import io.github.eslamasabry.oc.engine.team.SetupState
import io.github.eslamasabry.oc.engine.team.SetupStep
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.feature.team.presentation.PhoneOffKind
import io.github.eslamasabry.oc.feature.team.presentation.PhoneOffUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamHostFacts
import io.github.eslamasabry.oc.feature.team.presentation.TeamSettingsActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamSettingsUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamSetupActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamSetupPresent
import io.github.eslamasabry.oc.feature.team.ui.TeamHomeScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamSettingsScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamSetupScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The turn-on page, the phone-off page and Team settings with fake state, found by Flutter's words
 * (team_phone_setup_screen.dart, team_page.dart `PhoneTeamOffPage`, team_settings_screen.dart).
 */
@OptIn(ExperimentalTestApi::class)
class TeamSetupUiTest {
    private class Calls : TeamSetupActions, TeamHomeActions, TeamSettingsActions {
        val calls = mutableListOf<String>()

        override fun back() {
            calls += "back"
        }

        override fun start() {
            calls += "start"
        }

        override fun answer(yes: Boolean) {
            calls += "answer $yes"
        }

        override fun openTeam() {
            calls += "openTeam"
        }

        override fun openSetup() {
            calls += "openSetup"
        }

        override fun openAgents() {
            calls += "agents"
        }

        override fun openDetails() {
            calls += "details"
        }

        override fun askTurnOff() {
            calls += "askTurnOff"
        }

        override fun cancelTurnOff() {
            calls += "cancelTurnOff"
        }

        override fun confirmTurnOff() {
            calls += "confirmTurnOff"
        }

        override fun openProject(id: String) = Unit

        override fun openRequest(projectId: String, requestId: String) = Unit

        override fun resumeProject(projectId: String) = Unit

        override fun retry() = Unit
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
                KitChatEnvironment(Redactor()).Provide { Box(Modifier.width(412.dp).height(900.dp)) { content() } }
            }
        }
    }

    private val redactor = Redactor()

    private fun present(state: SetupState, now: Long = 12_000L) = TeamSetupPresent.present(state, now) { redactor.details(it) }

    @Test
    fun `B-AGENT-294 a run shows its four steps, the reply waiting with its time so far`() = runComposeUiTest {
        val state =
            SetupState(phase = SetupPhase.Running, step = SetupStep.Reply, beganAtMs = mapOf(SetupStep.Reply to 2_000L))
        setContent { Themed { TeamSetupScreen(present(state), Calls()) } }
        onNodeWithText("Turn on AI Team").assertIsDisplayed()
        onNodeWithText("Let your current reply finish").assertExists()
        onNodeWithText("Finishing your current reply… · 10 s so far").assertExists()
        onNodeWithText("Stop OpenCode and close terminals").assertExists()
        onNodeWithText("Checking this phone is safe for the team").assertExists()
        onNodeWithText("Start OpenCode again, protected").assertExists()
    }

    @Test
    fun `B-AGENT-294 the one question stops OpenCode only on the yes`() = runComposeUiTest {
        val calls = Calls()
        val state =
            SetupState(
                phase = SetupPhase.Confirming,
                step = SetupStep.Stop,
                beganAtMs = mapOf(SetupStep.Reply to 0L, SetupStep.Stop to 1_000L),
                tookMs = mapOf(SetupStep.Reply to 1_000L),
            )
        setContent { Themed { TeamSetupScreen(present(state), calls) } }
        onNodeWithText("Stop OpenCode briefly?").assertIsDisplayed()
        onNodeWithText("Stop and continue").performClick()
        assertEquals(listOf("answer true"), calls.calls)
    }

    @Test
    fun `B-AGENT-043 Not now answers no`() = runComposeUiTest {
        val calls = Calls()
        val state = SetupState(phase = SetupPhase.Confirming, step = SetupStep.Stop, beganAtMs = mapOf(SetupStep.Stop to 1_000L))
        setContent { Themed { TeamSetupScreen(present(state), calls) } }
        onNodeWithText("Not now").performClick()
        assertEquals(listOf("answer false"), calls.calls)
    }

    @Test
    fun `B-AGENT-043 a failure says what happened, where OpenCode is, and offers Start again`() = runComposeUiTest {
        val calls = Calls()
        val state =
            SetupState(
                phase = SetupPhase.Failed,
                failedStep = SetupStep.Check,
                problem = SetupProblem.Unsafe,
                details = "boundary_proof_failed:native.signal-31",
                serverState = SetupServerState.BackOn,
                terminalsClosed = true,
                tookMs = mapOf(SetupStep.Reply to 1_000L, SetupStep.Stop to 3_000L),
                beganAtMs = mapOf(SetupStep.Check to 5_000L),
            )
        setContent { Themed { TeamSetupScreen(present(state), calls) } }
        onNodeWithText("The team stays off").assertIsDisplayed()
        onNodeWithTag("team-setup-failed").assertExists()
        onNodeWithText("The safety test was closed by Android (signal 31) before it could finish.").assertExists()
        onNodeWithText("Start again").performClick()
        assertEquals(listOf("start"), calls.calls)
    }

    @Test
    fun `B-AGENT-043 a no ends calmly and keeps the steps`() = runComposeUiTest {
        val state =
            SetupState(
                phase = SetupPhase.Failed,
                failedStep = SetupStep.Stop,
                problem = SetupProblem.Declined,
                details = "declined",
                tookMs = mapOf(SetupStep.Reply to 1_000L),
            )
        setContent { Themed { TeamSetupScreen(present(state), Calls()) } }
        onNodeWithText("Nothing changed").assertIsDisplayed()
        onNodeWithText("Start again").assertExists()
    }

    @Test
    fun `B-AGENT-294 done says the team is ready and New project goes back to the team`() = runComposeUiTest {
        val calls = Calls()
        val state =
            SetupState(
                phase = SetupPhase.Done,
                boundaryTier = "proot",
                tookMs = SetupStep.entries.associateWith { 2_000L },
                skipped = setOf(SetupStep.Stop),
            )
        setContent { Themed { TeamSetupScreen(present(state), calls) } }
        onNodeWithText("AI Team is ready").assertIsDisplayed()
        onNodeWithText("Protected by this phone's Linux sandbox").assertExists()
        onNodeWithText("New project").performClick()
        assertEquals(listOf("openTeam"), calls.calls)
    }

    @Test
    fun `B-AGENT-294 the phone-off page joins a check that is going, and a waiting one offers Review`() = runComposeUiTest {
        val calls = Calls()
        setContent {
            Themed {
                TeamHomeScreen(TeamHomeUi(phase = TeamPhase.PhoneOff, phoneOff = PhoneOffUi(PhoneOffKind.Working, 2, 4)), calls)
            }
        }
        onNodeWithText("Checking AI Team on this phone").assertIsDisplayed()
        onNodeWithText("Step 2 of 4").assertExists()
        onNodeWithText("Turn on AI Team on this phone").performClick()
        assertEquals(listOf("openSetup"), calls.calls)
    }

    @Test
    fun `B-AGENT-294 a check waiting for the person says so`() = runComposeUiTest {
        val calls = Calls()
        setContent {
            Themed { TeamHomeScreen(TeamHomeUi(phase = TeamPhase.PhoneOff, phoneOff = PhoneOffUi(PhoneOffKind.Waiting)), calls) }
        }
        onNodeWithText("AI Team needs to restart OpenCode").assertIsDisplayed()
        onNodeWithText("Review and choose when").performClick()
        assertEquals(listOf("openSetup"), calls.calls)
    }

    private val settings =
        TeamSettingsUi(
            phase = TeamPhase.Ready,
            roles = 3,
            canCheck = true,
            host =
                TeamHostFacts(
                    provider = redactor.details("phoneEngine"),
                    version = redactor.details("1.0.0"),
                    address = redactor.details("http://127.0.0.1:4098"),
                    protection = "landlock",
                ),
        )

    @Test
    fun `B-AGENT-294 Team settings lists the agents, the check, the details and Turn off last`() = runComposeUiTest {
        val calls = Calls()
        setContent { Themed { TeamSettingsScreen(settings, calls) } }
        onNodeWithText("Team settings").assertIsDisplayed()
        onNodeWithText("Agents · 3 roles").performClick()
        onNodeWithText("Turn on AI Team on this phone").performClick()
        onNodeWithText("Technical details").performClick()
        onNodeWithText("Turn off the AI Team").performClick()
        assertEquals(listOf("agents", "openSetup", "details", "askTurnOff"), calls.calls)
    }

    @Test
    fun `B-AGENT-295 Turn off asks first with a strong confirm and a Keep`() = runComposeUiTest {
        val calls = Calls()
        setContent { Themed { TeamSettingsScreen(settings.copy(confirmingOff = true), calls) } }
        onNodeWithText("Turn off AI Team?").assertIsDisplayed()
        onNodeWithText("Its tasks and settings, and your projects, stay").assertExists()
        onNodeWithText("Keep").performClick()
        onNodeWithText("Turn off AI Team").performClick()
        assertEquals(listOf("cancelTurnOff", "confirmTurnOff"), calls.calls)
    }

    @Test
    fun `B-AGENT-043 a team that could not be stopped says it is still on`() = runComposeUiTest {
        setContent { Themed { TeamSettingsScreen(settings.copy(offFailed = true), Calls()) } }
        onNodeWithText("Couldn’t stop the team on this phone, so it is still on. Try again.").assertIsDisplayed()
    }

    @Test
    fun `B-AGENT-294 Technical details shows where it runs and the raw values`() = runComposeUiTest {
        setContent { Themed { TeamSettingsScreen(settings.copy(detailsOpen = true), Calls()) } }
        onNodeWithTag("team-settings-host-sheet").assertExists()
        onNodeWithTag("team-host-disclaimer").assertExists()
        onNodeWithText("Protected by Android's file protection").assertExists()
    }
}
