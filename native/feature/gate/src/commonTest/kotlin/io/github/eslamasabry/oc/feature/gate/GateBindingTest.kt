package io.github.eslamasabry.oc.feature.gate

import io.github.eslamasabry.oc.engine.migration.DeferredFeature
import io.github.eslamasabry.oc.engine.migration.MigrationBlock
import io.github.eslamasabry.oc.engine.migration.MigrationEngine
import io.github.eslamasabry.oc.engine.migration.MigrationProgress
import io.github.eslamasabry.oc.engine.migration.MigrationRunnerPort
import io.github.eslamasabry.oc.engine.migration.MigrationView
import io.github.eslamasabry.oc.engine.migration.ReentryItem
import io.github.eslamasabry.oc.engine.migration.ReentryNeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** P12-MIGRATION: what the engine knows → what the gate draws, and the gate's taps → the engine and the platform. */
@OptIn(ExperimentalCoroutinesApi::class)
class GateBindingTest {
    @Test
    fun `B-MIGR-010 a blocked migration shows its reason, the step id as Details and only the ways out that exist`() {
        val input = gateInputOf(MigrationView(MigrationProgress.Blocked(MigrationBlock.StepCrashed, step = "M2")))
        val blocked = assertIs<GateInput.Blocked>(input)
        assertEquals(GateBlockReason.StepCrashed, blocked.reason)
        assertEquals("M2 · StepCrashed", blocked.detail)
        val rows = GatePresenter.rowsFor(blocked.reason, blocked.restoreAvailable, blocked.startFreshAvailable)
        assertEquals(listOf(GateRow.TryAgain, GateRow.Report), rows, "no row that would do nothing")
    }

    @Test
    fun `B-MIGR-010 Start fresh shows once the runner offers it`() {
        val input = gateInputOf(
            MigrationView(MigrationProgress.Blocked(MigrationBlock.PrefsUnreadable, "M1", startFreshAvailable = true)),
        ) as GateInput.Blocked
        assertEquals(
            listOf(GateRow.TryAgain, GateRow.StartFresh, GateRow.Report),
            GatePresenter.rowsFor(input.reason, input.restoreAvailable, input.startFreshAvailable),
        )
    }

    @Test
    fun `B-MIGR-010 low space keeps the needed bytes for the plain sentence`() {
        val input = gateInputOf(MigrationView(MigrationProgress.Blocked(MigrationBlock.LowSpace, "S0", neededBytes = 5L shl 20)))
        assertEquals(5L shl 20, assertIs<GateInput.Blocked>(input).neededBytes)
    }

    @Test
    fun `B-MIGR-006 re-enter rows name their server, fall back to no name, and GitHub is app-wide`() {
        val view = MigrationView(
            MigrationProgress.Open(
                firstNativeLaunch = true,
                reenter = listOf(
                    ReentryItem("1", ReentryNeed.Password),
                    ReentryItem("2", ReentryNeed.Token),
                    ReentryItem(null, ReentryNeed.GitHubSignIn),
                ),
                deferred = setOf(DeferredFeature.Memory),
            ),
            names = mapOf("1" to "Home"),
        )
        val open = assertIs<GateInput.Open>(gateInputOf(view))
        assertEquals(
            listOf(
                GateReentry("1", "Home", GateReentryKind.Password),
                GateReentry("2", null, GateReentryKind.Token),
                GateReentry(null, "GitHub", GateReentryKind.GitHubSignIn),
            ),
            open.reenter,
        )
        assertTrue(open.deferredDataFound)
        assertTrue(open.firstNativeLaunch)
    }

    @Test
    fun `B-MIGR-010 waiting and running map to the waiting screens`() {
        assertEquals(GateInput.Closed(localeKnown = true), gateInputOf(MigrationView(MigrationProgress.Waiting)))
        assertEquals(GateInput.Running("M3"), gateInputOf(MigrationView(MigrationProgress.Running("M3"))))
    }

    @Test
    fun `B-MIGR-011 Report a problem shares the step and reason only`() = runTest {
        val progress = MutableStateFlow<MigrationProgress>(MigrationProgress.Blocked(MigrationBlock.WriteRefused, "M3"))
        val runner = object : MigrationRunnerPort {
            override val progress = progress
            override suspend fun run() = Unit
            override suspend fun retry() = Unit
            override suspend fun acknowledgeNotices() = Unit
            override suspend fun startFresh() = false
            override suspend fun restorePreUpdateData() = false
        }
        val engine = MigrationEngine(runner, { null }, backgroundScope)
        engine.start()
        runCurrent()
        val shared = mutableListOf<String?>()
        val platform = object : GatePlatform {
            override fun reportProblem(detail: String?) { shared += detail }
            override fun openStorageSettings() = Unit
            override fun openReentry(item: GateReentry) = Unit
        }
        EngineGateActions(engine, platform).reportProblem()
        assertEquals(listOf<String?>("M3 · WriteRefused"), shared)
        assertFalse(shared.single()!!.contains("/"), "no path")
    }
}
