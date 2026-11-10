@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phone

import androidx.compose.foundation.Indication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleReadiness
import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleReport
import io.github.eslamasabry.oc.engine.runtime.recovery.PreviousExitKind
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneExitKind
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneExitNotice
import kotlin.time.Instant
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerHeadline
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerLine
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerTone
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitCardKind
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveStepKind
import io.github.eslamasabry.oc.feature.phone.presentation.KeepRunningActions
import io.github.eslamasabry.oc.feature.phone.presentation.KeepRunningUi
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneAct
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessKind
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessRole
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessRowUi
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneDetailsUi
import io.github.eslamasabry.oc.feature.phone.presentation.RunningRowUi
import io.github.eslamasabry.oc.feature.phone.presentation.RunningActions
import io.github.eslamasabry.oc.feature.phone.presentation.RunningUi
import io.github.eslamasabry.oc.feature.phone.presentation.ServerRow
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneActions
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneAnswer
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneUi
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveStepUi
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneMaker
import io.github.eslamasabry.oc.feature.phone.ui.KeepRunningScreen
import io.github.eslamasabry.oc.feature.phone.ui.RunningScreen
import io.github.eslamasabry.oc.feature.phone.ui.ThisPhoneScreen
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/** The three pages render the Flutter words for their states and route taps to their intents. */
class PhoneScreensTest {
    private object TestPlatform : KitPlatform {
        override val name: String = "test"
        override val family: KitPlatformFamily = KitPlatformFamily.Desktop
        override val signals: StateFlow<KitSystemSignals> = MutableStateFlow(KitSystemSignals())

        override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

        override fun haptic(kind: KitHaptic) = Unit

        override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) = KitTheme(platform = TestPlatform, localeTag = "en", stillFrame = true, content = content)

    private class Calls : ThisPhoneActions, RunningActions, KeepRunningActions {
        val seen = mutableListOf<String>()

        override fun act(act: PhoneAct) { seen += "act $act" }
        override fun stopServer() { seen += "stop" }
        override fun askRestart() { seen += "askRestart" }
        override fun askUpdate() { seen += "askUpdate" }
        override fun confirm() { seen += "confirm" }
        override fun dismiss() { seen += "dismiss" }
        override fun openRunning() { seen += "openRunning" }
        override fun openKeepRunning() { seen += "openKeepRunning" }
        override fun back() { seen += "back" }
        override fun shown(visible: Boolean) { seen += "shown $visible" }
        override fun retry() { seen += "retry" }
        override fun details(pid: Int) { seen += "details $pid" }
        override fun askStop(pid: Int) { seen += "askStop $pid" }
        override fun confirmStop() { seen += "confirmStop" }
        override fun openChildLimitSettings() { seen += "openChildLimit" }
        override fun hideChildLimit() { seen += "hideChildLimit" }
        override fun resumed() { seen += "resumed" }
        override fun open(step: KeepAliveStepKind) { seen += "open $step" }
        override fun dismissRecovery() { seen += "dismissRecovery" }
        override fun refreshStorage() { seen += "refreshStorage" }
        override fun measureStorage() { seen += "measureStorage" }
    }

    @Test
    fun `B-LINUX-250 This phone says the server stopped by itself and offers Restart now`() =
        runComposeUiTest {
            val calls = Calls()
            val ui =
                ThisPhoneUi(
                    answer =
                        ThisPhoneAnswer(AnswerTone.Off, AnswerHeadline.StoppedItself, primary = PhoneAct.RestartNow),
                    // The page's rows show once OpenCode is installed here, and Running on this phone once there is
                    // a reading (ThisPhoneScreen: `installed`, and the row's count/status guard).
                    details = PhoneDetailsUi(installed = true),
                    running = RunningRowUi(count = 3),
                )
            setContent { Themed { ThisPhoneScreen(ui, calls) } }
            onNodeWithText("The server stopped by itself").assertExists()
            onNodeWithText("Restart now").performClick()
            onNodeWithText("Running on this phone").performClick()
            onNodeWithText("Keep running in the background").performClick()
            assertEquals(listOf("act RestartNow", "openRunning", "openKeepRunning"), calls.seen.filterNot { it.startsWith("shown") })
        }

    @Test
    fun `P-115 after Android stops the app This phone says it once, on the rows' start edge`() =
        runComposeUiTest {
            val calls = Calls()
            val ui =
                ThisPhoneUi(
                    answer = ThisPhoneAnswer(AnswerTone.Off, AnswerHeadline.StoppedItself, primary = PhoneAct.RestartNow),
                    details = PhoneDetailsUi(installed = true),
                    exitNotice = PhoneExitNotice(PhoneExitKind.Killed, Instant.fromEpochMilliseconds(0), recoveryAllowed = true),
                    lifecycle =
                        LifecycleReport(
                            readiness = LifecycleReadiness.Stopped,
                            exitKind = PreviousExitKind.Killed,
                            hadServices = true,
                        ),
                )
            // A phone-wide column: in a wide window the kit centres the notice in its reading width.
            setContent { Themed { Box(Modifier.width(412.dp)) { ThisPhoneScreen(ui, calls) } } }
            onNodeWithText("Android stopped the app while OpenCode was running").assertExists()
            // The recovery line repeated the exit notice ("Android stopped the app. OpenCode is stopped.").
            onAllNodesWithText("Android stopped the app. OpenCode is stopped.").assertCountEquals(0)
            onAllNodesWithTag("kit-notice").assertCountEquals(1)
            // The info mark starts on the gutter (16 dp), the rows' edge, not on the screen edge.
            onNodeWithTag("kit-notice").assertLeftPositionInRootIsEqualTo(16.dp)
        }

    @Test
    fun `plan 07 F01 a running server's rows name their object`() =
        runComposeUiTest {
            val calls = Calls()
            val ui =
                ThisPhoneUi(
                    answer = ThisPhoneAnswer(AnswerTone.Ok, AnswerHeadline.Running, AnswerLine.Storage(410L * 1024 * 1024)),
                    serverRows = persistentListOf(ServerRow.Stop, ServerRow.Restart),
                )
            setContent { Themed { ThisPhoneScreen(ui, calls) } }
            onNodeWithText("Everything is running").assertExists()
            onNodeWithText("Takes ⁦410 MB⁩ of storage").assertExists()
            onNodeWithText("Stop OpenCode on this phone").performClick()
            // Stop asks Flutter's question first (6bcea0edb); the stop is sent from its confirm.
            onNodeWithText("Stop OpenCode on this phone?").assertExists()
            onAllNodesWithText("Stop OpenCode on this phone").onLast().performClick()
            onNodeWithText("Restart OpenCode on this phone").performClick()
            assertEquals(listOf("stop", "askRestart"), calls.seen.filterNot { it.startsWith("shown") })
        }

    @Test
    fun `B-LINUX-143 Running on this phone offers Stop only on unprotected rows, and the card hides`() =
        runComposeUiTest {
            val calls = Calls()
            // Both rows need attention (the o48 rule: only stoppable work needing attention gets an inline Stop);
            // only the unprotected one may offer it.
            val server = ProcessRowUi(10, "OpenCode", ProcessKind.OpenCode, null, false, ProcessRole.ServerRoot, heavy = true)
            var ui by
                mutableStateOf(
                    RunningUi(
                        loading = false,
                        rows = persistentListOf(server, ProcessRowUi(11, "node", ProcessKind.Other, 12, true, null)),
                        count = 12,
                        childLimit = ChildLimitCardKind.OpenSettings,
                    )
                )
            setContent { Themed { RunningScreen(ui, calls) } }
            onAllNodesWithText("Stop").assertCountEquals(1)
            onNodeWithText("Let agents run more programs at once").assertExists()
            onNodeWithText("Stop").performClick()
            onNodeWithTag("child-limit-open", useUnmergedTree = true).performClick()
            onNodeWithTag("child-limit-hide", useUnmergedTree = true).performClick()
            assertEquals(listOf("askStop 11", "openChildLimit", "hideChildLimit"), calls.seen.filterNot { it.startsWith("shown") })
            // The protected row says where it is controlled, in its details.
            ui = ui.copy(selected = server)
            waitForIdle()
            onNodeWithText("It runs a server. Stop the server from This phone.").assertExists()
        }

    @Test
    fun `B-ANDR-007 Keep running words each step for the maker and opens Android's screen`() =
        runComposeUiTest {
            val calls = Calls()
            val ui =
                KeepRunningUi(
                    loaded = true,
                    maker = PhoneMaker.Nubia,
                    steps =
                        persistentListOf(
                            KeepAliveStepUi(KeepAliveStepKind.LockInRecents, done = false, opens = null),
                            KeepAliveStepUi(KeepAliveStepKind.Battery, done = true, opens = null),
                        ),
                    swipeWarning = true,
                )
            setContent { Themed { KeepRunningScreen(ui, calls) } }
            onNodeWithText("Lock it in Recent apps").assertExists()
            onNodeWithText("Allowed").assertExists()
            onAllNodesWithText("Open").assertCountEquals(0)
            onNodeWithText(
                "On Android 15 and newer, Android allows background syncing for about 6 hours a day, even with everything " +
                    "here allowed. After that the app pauses in the background until you open it.",
            ).assertExists()
        }
}
