package io.github.eslamasabry.oc.engine.entry

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** ADR §9.7 order and 16 §5.3 `app_open` (plan 15 S2-ENTRY finish line). */
class AppBootstrapTest {
    private class Steps(val safeMode: Boolean = false) : BootstrapSteps {
        val fence = CompletableDeferred<Unit>()

        override fun installCrashHandler() = Unit

        override fun initRedactor() = Unit

        override fun crashLoopGuard(): Boolean = safeMode

        override fun buildGraph() = Unit

        override suspend fun startupFence() = fence.await()

        override suspend fun warmPrefs() = Unit

        override suspend fun startReportCapture() = Unit
    }

    private class Gate : GateSignal {
        override val open = MutableStateFlow(false)
    }

    private fun TestScope.boot(steps: Steps, gate: Gate, opens: MutableList<Unit>): AppBootstrap =
        AppBootstrap(steps, gate, { opens += Unit }, backgroundScope, StandardTestDispatcher(testScheduler))

    @Test
    fun `B-ANDR-073 start-up runs the ADR 9-7 steps in order and emits app_open once, only after the gate opens`() =
        runTest {
            val steps = Steps()
            val gate = Gate()
            val opens = mutableListOf<Unit>()
            val boot = boot(steps, gate, opens)
            boot.onCreate()
            boot.onCreate() // a second call is ignored
            assertEquals(listOf("crashHandler", "redactor", "crashLoopGuard", "graph"), boot.trail.value)
            runCurrent()
            assertEquals(BootstrapPhase.Starting, boot.phase.value) // the fence is still running

            gate.open.value = true
            runCurrent()
            assertEquals(0, opens.size) // gate open but bootstrap not Ready

            steps.fence.complete(Unit)
            runCurrent()
            assertEquals(BootstrapPhase.Ready, boot.phase.value)
            assertEquals(1, opens.size)
            assertEquals(
                listOf("crashHandler", "redactor", "crashLoopGuard", "graph", "startupFence", "warmPrefs"),
                boot.trail.value.take(6),
            )
            gate.open.value = false
            runCurrent()
            gate.open.value = true
            runCurrent()
            assertEquals(1, opens.size) // once per cold start
        }

    @Test
    fun `B-ANDR-073 safe mode never opens the app or emits app_open`() =
        runTest {
            val gate = Gate().also { it.open.value = true }
            val opens = mutableListOf<Unit>()
            val boot = boot(Steps(safeMode = true), gate, opens)
            boot.onCreate()
            runCurrent()
            assertEquals(BootstrapPhase.SafeMode, boot.phase.value)
            assertEquals(0, opens.size)
        }
}
