package io.github.eslamasabry.oc.feature.onboarding

import androidx.compose.foundation.Indication
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.feature.onboarding.presentation.OpeningGateActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.OpeningGateUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.OpeningGateViewModel
import io.github.eslamasabry.oc.feature.onboarding.presentation.OpeningPhase
import io.github.eslamasabry.oc.feature.onboarding.presentation.OpeningSnapshot
import io.github.eslamasabry.oc.feature.onboarding.presentation.OpeningWorld
import io.github.eslamasabry.oc.feature.onboarding.ui.OpeningGateScreen
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.TestTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private class TestKitPlatform : KitPlatform {
    override val name: String = "test"
    override val family: KitPlatformFamily = KitPlatformFamily.Desktop
    override val signals: MutableStateFlow<KitSystemSignals> = MutableStateFlow(KitSystemSignals())
    override val isDebug: Boolean = true

    override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

    override fun haptic(kind: KitHaptic) {}

    override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null
}

@Composable
private fun Themed(content: @Composable () -> Unit) {
    KitTheme(
        appearance = KitAppearance(motion = MotionLevel.Off),
        platform = TestKitPlatform(),
        localeTag = "en",
        stillFrame = true,
        content = content,
    )
}

private class FakeWorld(phase: OpeningPhase) : OpeningWorld {
    val calls = mutableListOf<String>()
    var resetResult: Outcome<Unit> = Unit.ok()
    override val phase = MutableStateFlow(OpeningSnapshot(phase, 0))

    fun move(next: OpeningPhase) {
        phase.value = OpeningSnapshot(next, phase.value.generation + 1)
    }

    override suspend fun retry() {
        calls += "retry"
    }

    override suspend fun startFresh(): Outcome<Unit> {
        calls += "startFresh"
        return resetResult
    }

    override fun reportProblem() {
        calls += "report"
    }
}

private class RecordingGateActions : OpeningGateActions {
    val calls = mutableListOf<String>()

    override fun retry() {
        calls += "retry"
    }

    override fun reportProblem() {
        calls += "report"
    }

    override fun askStartFresh() {
        calls += "ask"
    }

    override fun confirmStartFresh(confirmationId: Long?) {
        calls += "confirm"
    }

    override fun cancelStartFresh() {
        calls += "cancel"
    }
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class OpeningGateTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `SCREENS-NAV-01 the gate waits, then opens, with one clock per waiting phase`() {
        val time = TestTimeSource()
        val world = FakeWorld(OpeningPhase.Opening)
        val vm = OpeningGateViewModel(world, time)
        val first = assertIs<OpeningGateUi.Waiting>(vm.state.value)
        assertEquals(false, first.updating)
        world.move(OpeningPhase.Updating)
        val updating = assertIs<OpeningGateUi.Waiting>(vm.state.value)
        assertEquals(true, updating.updating)
        world.move(OpeningPhase.Ready)
        assertSame(OpeningGateUi.Open, vm.state.value)
        world.move(OpeningPhase.SafeMode)
        assertSame(OpeningGateUi.Open, vm.state.value, "safe mode owns its own page (A02)")
    }

    @Test
    fun `SCREENS-NAV-01 Start fresh asks first, then resets, a failed reset says so and Try again returns`() {
        val world = FakeWorld(OpeningPhase.Failed)
        val vm = OpeningGateViewModel(world)
        assertEquals(OpeningGateUi.Failed(confirming = false), vm.state.value)
        vm.confirmStartFresh((vm.state.value as? OpeningGateUi.Failed)?.confirmationId)
        assertEquals(emptyList(), world.calls, "nothing is removed without the question")
        vm.askStartFresh()
        assertEquals(OpeningGateUi.Failed(confirming = true, confirmationId = 1), vm.state.value)
        vm.cancelStartFresh()
        assertEquals(OpeningGateUi.Failed(confirming = false), vm.state.value)

        world.resetResult = Failure.StorageRefused().failed()
        vm.askStartFresh()
        vm.confirmStartFresh((vm.state.value as? OpeningGateUi.Failed)?.confirmationId)
        assertSame(OpeningGateUi.ResetFailed, vm.state.value)
        vm.retry()
        assertEquals(listOf("startFresh", "retry"), world.calls)
        assertEquals(OpeningGateUi.Failed(confirming = false), vm.state.value)
        vm.reportProblem()
        assertEquals("report", world.calls.last())
    }

    @Test
    fun `SCREENS-NAV-01 finding 6 recovery revokes destructive confirmation`() {
        val world = FakeWorld(OpeningPhase.Failed)
        val vm = OpeningGateViewModel(world)
        vm.askStartFresh()
        val stale = assertIs<OpeningGateUi.Failed>(vm.state.value).confirmationId
        world.move(OpeningPhase.Ready)
        vm.confirmStartFresh(stale)
        assertEquals(
            emptyList(),
            world.calls,
            "a stale confirmation must not delete recovered sign-ins",
        )
        world.move(OpeningPhase.Failed)
        assertEquals(OpeningGateUi.Failed(confirming = false), vm.state.value)
        vm.confirmStartFresh((vm.state.value as? OpeningGateUi.Failed)?.confirmationId)
        assertEquals(emptyList(), world.calls, "a new failure requires a new question")
    }

    @Test
    fun `SCREENS-NAV-01 finding 6 a later failure does not resurrect consent`() {
        val world = FakeWorld(OpeningPhase.Failed)
        val vm = OpeningGateViewModel(world)
        vm.askStartFresh()
        val stale = assertIs<OpeningGateUi.Failed>(vm.state.value).confirmationId
        world.move(OpeningPhase.Opening)
        world.move(OpeningPhase.Failed)
        assertEquals(OpeningGateUi.Failed(confirming = false), vm.state.value)
        vm.confirmStartFresh((vm.state.value as? OpeningGateUi.Failed)?.confirmationId)
        assertEquals(emptyList(), world.calls)
        vm.askStartFresh()
        vm.confirmStartFresh(stale)
        assertEquals(emptyList(), world.calls, "an old callback cannot answer a new question")
        vm.confirmStartFresh((vm.state.value as? OpeningGateUi.Failed)?.confirmationId)
        assertEquals(listOf("startFresh"), world.calls)
    }

    @Test
    fun `SCREENS-NAV-01 finding 6 rapid recovery cannot reuse a conflated failed phase`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val world = FakeWorld(OpeningPhase.Failed)
            val vm = OpeningGateViewModel(world)
            testScheduler.runCurrent()
            vm.askStartFresh()
            testScheduler.runCurrent()
            val stale = assertIs<OpeningGateUi.Failed>(vm.state.value).confirmationId
            // Both changes occur before the collector gets its next turn.
            world.move(OpeningPhase.Ready)
            world.move(OpeningPhase.Failed)
            vm.confirmStartFresh(stale)
            testScheduler.runCurrent()
            assertEquals(emptyList(), world.calls)
            assertEquals(OpeningGateUi.Failed(confirming = false), vm.state.value)
        }

    @Test
    fun `SCREENS-NAV-01 the opening words show on the first frame`() = runComposeUiTest {
        setContent {
            Themed {
                OpeningGateScreen(
                    OpeningGateUi.Waiting(false, TestTimeSource().markNow()),
                    RecordingGateActions(),
                )
            }
        }
        onNodeWithText("Opening…").assertExists()
        onNodeWithText("Reading your saved servers.").assertExists()
    }

    @Test
    fun `SCREENS-NAV-01 a failed start offers Try again, Report a problem and Start fresh in plain words`() =
        runComposeUiTest {
            val actions = RecordingGateActions()
            setContent { Themed { OpeningGateScreen(OpeningGateUi.Failed(), actions) } }
            onNodeWithText("Can't read saved servers").assertExists()
            onNodeWithText("Try again").performClick()
            onNodeWithText("Report a problem").performClick()
            onNodeWithText("Start fresh").performClick()
            assertEquals(listOf("retry", "report", "ask"), actions.calls)
        }

    @Test
    fun `SCREENS-NAV-01 Start fresh is a question that names the act`() = runComposeUiTest {
        val actions = RecordingGateActions()
        setContent {
            Themed { OpeningGateScreen(OpeningGateUi.Failed(confirming = true), actions) }
        }
        onNodeWithText("Remove saved sign-ins?").assertExists()
        onNodeWithText("Remove saved sign-ins").performClick()
        assertEquals(listOf("confirm"), actions.calls)
    }
}
