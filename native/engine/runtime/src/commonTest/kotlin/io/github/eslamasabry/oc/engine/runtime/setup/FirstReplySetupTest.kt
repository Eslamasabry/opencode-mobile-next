package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Phase 0 (first reply): setup must never look hung, and a killed setup resumes from its failed step. */
@OptIn(RedactorApi::class)
class FirstReplySetupTest {
    private val linux = FakeSetupHost()
    private val clock = StepClock()

    private fun TestScope.engine(scope: CoroutineScope = backgroundScope) = SetupEngine(
        host = linux,
        registryPort = FixtureRegistry,
        words = fixtureWords,
        scope = scope,
        clock = clock,
        scriptPrelude = FixtureRegistry.PRELUDE,
        redact = { it },
        finisher = SetupFinisher { Unit.ok() },
        pollInterval = 5.milliseconds,
    )

    private fun TestScope.pumpUntil(done: () -> Boolean) {
        repeat(2_000) {
            if (done()) return
            advanceTimeBy(5)
            runCurrent()
        }
        assertTrue(done(), "condition never held")
    }

    private val now = 1_790_000_000_000L

    @Test
    fun `P0-SETUP-001 a step running past its limit is stopped and offers Try again`() = runTest {
        val engine = engine()
        engine.run(emptySet())
        linux.advance("essentials", mapOf("startedAt" to now - 25 * 60_000L))
        val watcher = backgroundScope.launch { engine.progress.collect { } }
        pumpUntil { engine.progress.value.state == SetupState.Failed }
        watcher.cancel()
        assertEquals(1, linux.cancels, "the runner is told to stop")
        assertEquals(SetupError.StepTimedOut("Git and SSH"), engine.progress.value.error)
        assertTrue(engine.progress.value.canContinue, "Try again is possible")
        engine.dispose()
    }

    @Test
    fun `P0-SETUP-002 a step inside its limit keeps running and shows its clock`() = runTest {
        val engine = engine()
        engine.run(emptySet())
        linux.advance("essentials", mapOf("startedAt" to now - 3 * 60_000L))
        linux.job!!["logTail"] = "Get:4 http://ports.ubuntu.com noble/main arm64 git\n"
        val watcher = backgroundScope.launch { engine.progress.collect { } }
        pumpUntil { engine.progress.value.activity != null }
        watcher.cancel()
        assertEquals(0, linux.cancels)
        assertEquals(SetupState.Running, engine.progress.value.state)
        assertTrue(engine.progress.value.stepElapsedSeconds!! in 170..190)
        assertEquals(SetupActivity.Downloading, engine.progress.value.activity)
        engine.dispose()
    }

    @Test
    fun `P0-SETUP-003 the last output line becomes a plain activity`() {
        assertEquals(SetupActivity.Unpacking, SetupActivity.fromLog("x\nUnpacking git (1:2.43) ...\n"))
        assertEquals(SetupActivity.Configuring, SetupActivity.fromLog("Setting up git (1:2.43) ...\n"))
        assertEquals(SetupActivity.Checking, SetupActivity.fromLog("Reading package lists...\n"))
        assertEquals(SetupActivity.Working, SetupActivity.fromLog("something odd\n"))
        assertNull(SetupActivity.fromLog("\n"))
    }

    @Test
    fun `P0-SETUP-004 after the app is killed mid-setup Continue resumes from the failed step, not from zero`() =
        runTest {
            linux.job = FakeSetupHost.jobFrom(RECORDED_INTERRUPTED)
            linux.checks += mapOf(
                "linux" to (true to "24.04.5"),
                "essentials" to (true to "2.43.0"),
                "python" to (true to "3.12.3"),
            )
            val engine = engine()
            engine.resume()
            for (id in listOf("linux", "essentials", "python")) assertTrue(linux.spec(id).skipped, "$id is not redone")
            assertFalse(linux.spec("node").skipped, "the step that was running is the one that restarts")
            assertFalse(linux.spec("opencode").skipped)
            engine.dispose()
        }
}
