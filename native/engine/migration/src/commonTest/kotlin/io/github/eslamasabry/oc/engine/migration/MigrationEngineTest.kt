package io.github.eslamasabry.oc.engine.migration

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.migration.BlockReason
import io.github.eslamasabry.oc.core.data.migration.GateState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationEngineTest {
    private class FakeRunner : MigrationRunnerPort {
        override val progress = MutableStateFlow<MigrationProgress>(MigrationProgress.Waiting)
        var runs = 0
        var retries = 0
        var acks = 0
        var freshCalls = 0
        var restoreCalls = 0
        var throwOnRun = false
        var onRun: suspend () -> Unit = {}
        var onRetry: suspend () -> Unit = {}

        override suspend fun run() {
            runs++
            if (throwOnRun) error("boom with a secret: hunter2")
            onRun()
        }

        override suspend fun retry() {
            retries++
            onRetry()
        }

        override suspend fun acknowledgeNotices() {
            acks++
            val open = progress.value as? MigrationProgress.Open ?: return
            progress.value = open.copy(reenter = emptyList(), deferred = emptySet())
        }

        override suspend fun startFresh(): Boolean {
            freshCalls++
            progress.value = MigrationProgress.Open(firstNativeLaunch = false)
            return true
        }

        override suspend fun restorePreUpdateData(): Boolean {
            restoreCalls++
            return true
        }
    }

    private fun TestScope.engine(runner: FakeRunner, names: Map<String, String> = emptyMap()) =
        MigrationEngine(runner, { names[it] }, backgroundScope)

    @Test
    fun `B-MIGR-010 the gate stays closed while the runner works and opens only when it reports Open`() = runTest {
        val runner = FakeRunner()
        val finish = CompletableDeferred<Unit>()
        runner.onRun = {
            runner.progress.value = MigrationProgress.Running("M2")
            finish.await()
            runner.progress.value = MigrationProgress.Open(firstNativeLaunch = true)
        }
        val engine = engine(runner)
        val writer = async { engine.gate.awaitWritable() }
        engine.start()
        runCurrent()
        assertEquals(GateState.Running("M2"), engine.gate.state.value)
        assertFalse(writer.isCompleted, "no store may write while the migration runs")
        finish.complete(Unit)
        runCurrent()
        assertTrue(writer.isCompleted)
        assertIs<GateState.Open>(engine.gate.state.value).also { assertTrue(it.firstNativeLaunch) }
    }

    @Test
    fun `B-MIGR-010 a runner that throws blocks safely with Try again instead of crashing or waiting forever`() = runTest {
        val runner = FakeRunner().apply { throwOnRun = true }
        val engine = engine(runner)
        engine.start()
        runCurrent()
        val blocked = assertIs<MigrationProgress.Blocked>(engine.view.value.progress)
        assertEquals(MigrationBlock.StepCrashed, blocked.reason)
        assertEquals("runner", blocked.step)
        assertEquals(GateState.Blocked(BlockReason.StepCrashed), engine.gate.state.value)
    }

    @Test
    fun `B-MIGR-010 Try again resumes the runner and the gate opens after a block`() = runTest {
        val runner = FakeRunner()
        runner.onRun = { runner.progress.value = MigrationProgress.Blocked(MigrationBlock.WriteRefused, "M3") }
        runner.onRetry = { runner.progress.value = MigrationProgress.Open(firstNativeLaunch = true) }
        val engine = engine(runner)
        engine.start()
        runCurrent()
        assertEquals(GateState.Blocked(BlockReason.WriteRefused), engine.gate.state.value)
        engine.retry()
        runCurrent()
        assertEquals(1, runner.retries)
        assertIs<GateState.Open>(engine.gate.state.value)
    }

    @Test
    fun `B-MIGR-010 two Try again taps while one runs reach the runner once`() = runTest {
        val runner = FakeRunner()
        val release = CompletableDeferred<Unit>()
        runner.onRun = { runner.progress.value = MigrationProgress.Blocked(MigrationBlock.StepCrashed, "M2") }
        runner.onRetry = { release.await() }
        val engine = engine(runner)
        engine.start()
        runCurrent()
        launch { engine.retry() }
        launch { engine.retry() }
        runCurrent()
        assertTrue(engine.view.value.acting)
        release.complete(Unit)
        runCurrent()
        assertEquals(1, runner.retries)
        assertFalse(engine.view.value.acting)
    }

    @Test
    fun `B-MIGR-004 a fresh install with nothing to move opens at once without the first-launch notices`() = runTest {
        val runner = FakeRunner()
        runner.onRun = { runner.progress.value = MigrationProgress.Open(firstNativeLaunch = false) }
        val engine = engine(runner)
        engine.start()
        runCurrent()
        assertEquals(GateState.Open(firstNativeLaunch = false, notices = io.github.eslamasabry.oc.core.data.migration.MigrationNotices()), engine.gate.state.value)
    }

    @Test
    fun `B-MIGR-006 the re-enter list names each server and carries ids only, then is acknowledged once`() = runTest {
        val runner = FakeRunner()
        runner.onRun = {
            runner.progress.value = MigrationProgress.Open(
                firstNativeLaunch = true,
                reenter = listOf(ReentryItem("7", ReentryNeed.Password), ReentryItem(null, ReentryNeed.GitHubSignIn)),
            )
        }
        val engine = engine(runner, names = mapOf("7" to "Home server"))
        engine.start()
        runCurrent()
        assertEquals(mapOf("7" to "Home server"), engine.view.value.names)
        val gate = assertIs<GateState.Open>(engine.gate.state.value)
        assertEquals(listOf(ProfileId("7")), gate.notices.reenter)
        engine.acknowledgeNotices()
        runCurrent()
        assertEquals(1, runner.acks)
        assertTrue(assertIs<MigrationProgress.Open>(engine.view.value.progress).reenter.isEmpty())
    }

    @Test
    fun `B-MIGR-011 nothing from a thrown runner error reaches what the gate shows`() = runTest {
        val runner = FakeRunner().apply { throwOnRun = true }
        val engine = engine(runner)
        engine.start()
        runCurrent()
        assertFalse(engine.view.value.toString().contains("hunter2"))
        assertFalse(engine.gate.state.value.toString().contains("hunter2"))
    }

    @Test
    fun `B-MIGR-010 Start fresh and Restore run only when the block offers them`() = runTest {
        val runner = FakeRunner()
        runner.onRun = { runner.progress.value = MigrationProgress.Blocked(MigrationBlock.PrefsUnreadable, "M1") }
        val engine = engine(runner)
        engine.start()
        runCurrent()
        engine.startFresh()
        engine.restorePreUpdateData()
        assertEquals(0, runner.freshCalls)
        assertEquals(0, runner.restoreCalls)
        runner.progress.value =
            MigrationProgress.Blocked(MigrationBlock.PrefsUnreadable, "M1", startFreshAvailable = true)
        runCurrent()
        engine.startFresh()
        runCurrent()
        assertEquals(1, runner.freshCalls)
        assertIs<GateState.Open>(engine.gate.state.value)
    }

    @Test
    fun `B-MIGR-005 once open the gate never closes again`() = runTest {
        val runner = FakeRunner()
        runner.onRun = { runner.progress.value = MigrationProgress.Open(firstNativeLaunch = true) }
        val engine = engine(runner)
        engine.start()
        runCurrent()
        runner.progress.value = MigrationProgress.Running("M2")
        runCurrent()
        assertIs<GateState.Open>(engine.gate.state.value)
    }

    @Test
    fun `B-MIGR-009 the deferred-features notice is acknowledged once it was shown and no sheet is waiting`() = runTest {
        val runner = FakeRunner()
        runner.onRun = {
            runner.progress.value = MigrationProgress.Open(firstNativeLaunch = true, deferred = setOf(DeferredFeature.AiTeam))
        }
        val engine = engine(runner)
        engine.start()
        runCurrent()
        engine.acknowledgeDeferredNotice()
        runCurrent()
        assertEquals(1, runner.acks)
    }

    @Test
    fun `B-MIGR-010 the engine starts the runner once however often start is called`() = runTest {
        val runner = FakeRunner()
        val engine = engine(runner)
        engine.start()
        engine.start()
        runCurrent()
        assertEquals(1, runner.runs)
    }
}
