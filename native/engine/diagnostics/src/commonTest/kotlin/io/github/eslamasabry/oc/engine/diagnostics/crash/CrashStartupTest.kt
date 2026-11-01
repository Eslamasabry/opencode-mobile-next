package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitReason
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CrashStartupTest {
    private val clock = TestClock()
    private val diagnostics = Diagnostics(Redactor(), clock)
    private val now get() = clock.now().toEpochMilliseconds()

    private fun TestScope.startup(source: CrashStartupSource) =
        CrashStartup(source, diagnostics, clock, backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)))

    private fun consented(): FakeCrashStore = FakeCrashStore(CrashFile.Consent to "${now - 1_000}")

    /** Fails the test if anything reaches the ring before readiness settled. */
    private fun TestScope.watchRingBefore(startup: () -> CrashStartup?): () -> Boolean {
        var early = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            diagnostics.errors.collect { if (it.isNotEmpty() && startup()?.ready?.isCompleted != true) early = true }
        }
        return { early }
    }

    @Test
    fun `B-SEC-045 slow opening outlives the launch budget and resolves readiness later`() =
        runTest {
            val store = FakeCrashStore()
            val reply = CompletableDeferred<Outcome<CrashStartupEvidence>>()
            var opens = 0
            val crash = startup { opens++; reply.await() }
            val readiness = crash.ready
            val launch = crash.start()
            advanceTimeBy(CrashStartup.LAUNCH_BUDGET)
            runCurrent()
            assertTrue(launch.isCompleted)
            assertNull(launch.await())
            assertFalse(readiness.isCompleted)
            assertNull(crash.current.value)
            assertSame(launch, crash.start())
            advanceTimeBy(50)
            reply.complete(Outcome.Ok(CrashStartupEvidence(store)))
            val controller = assertNotNull(readiness.await())
            assertFalse(controller.enabled)
            assertSame(controller, crash.current.value)
            assertEquals(1, opens)
        }

    @Test
    fun `B-SEC-045 the launch wait returns at 300 ms`() =
        runTest {
            val store = FakeCrashStore()
            val reply = CompletableDeferred<Outcome<CrashStartupEvidence>>()
            val launch = startup { reply.await() }.start()
            advanceTimeBy(299)
            runCurrent()
            assertFalse(launch.isCompleted)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(launch.isCompleted)
            assertNull(launch.await())
            assertTrue(store.files.isEmpty())
        }

    @Test
    fun `B-SEC-045 a reply beyond the launch budget becomes available later`() =
        runTest {
            val store = FakeCrashStore()
            val crash =
                startup {
                    delay(CrashStartup.LAUNCH_BUDGET.inWholeMilliseconds + 50)
                    Outcome.Ok(CrashStartupEvidence(store))
                }
            assertNull(crash.start().await())
            val controller = assertNotNull(crash.ready.await())
            assertSame(controller, crash.current.value)
            assertFalse(controller.enabled)
        }

    @Test
    fun `B-SEC-045 a never-answering platform bounds launch while readiness stays pending`() =
        runTest {
            val store = FakeCrashStore()
            val reply = CompletableDeferred<Outcome<CrashStartupEvidence>>()
            val crash = startup { reply.await() }
            val readyBefore = crash.ready
            val launch = crash.start()
            advanceTimeBy(299)
            runCurrent()
            assertFalse(launch.isCompleted)
            advanceTimeBy(1)
            runCurrent()
            assertNull(launch.await())
            assertFalse(readyBefore.isCompleted)
            assertSame(readyBefore, crash.ready)
            assertNull(crash.current.value)
            crash.capture(IllegalStateException("synthetic-provider-secret"))
            val entry = diagnostics.errors.value.single()
            assertEquals("Invalid state", entry.message)
            assertEquals("", entry.stack)
            assertFalse("synthetic-provider-secret" in diagnostics.reportText())
            assertTrue(store.files.isEmpty())
        }

    @Test
    fun `B-SEC-045 a late opening failure resolves readiness to null`() =
        runTest {
            val reply = CompletableDeferred<Outcome<CrashStartupEvidence>>()
            val crash = startup { reply.await() }
            val readiness = crash.ready
            val launch = crash.start()
            advanceTimeBy(CrashStartup.LAUNCH_BUDGET)
            runCurrent()
            assertNull(launch.await())
            reply.completeExceptionally(IllegalStateException("synthetic-provider-secret"))
            assertNull(readiness.await())
            assertNull(crash.current.value)

            crash.reset()
            val failed = startup { Outcome.Failed(Failure.Device()) }
            assertNull(failed.start().await())
            assertNull(failed.ready.await())
        }

    @Test
    fun `B-SEC-045 a late opening keeps explicit persisted consent`() =
        runTest {
            val store = consented()
            val consent = store.files[CrashFile.Consent]
            val reply = CompletableDeferred<Outcome<CrashStartupEvidence>>()
            val crash = startup { reply.await() }
            val launch = crash.start()
            advanceTimeBy(CrashStartup.LAUNCH_BUDGET)
            runCurrent()
            assertNull(launch.await())
            reply.complete(Outcome.Ok(CrashStartupEvidence(store)))
            val controller = assertNotNull(crash.ready.await())
            assertTrue(controller.enabled)
            assertSame(controller, crash.current.value)
            assertEquals(consent, store.files[CrashFile.Consent])
            assertEquals(setOf(CrashFile.Consent), store.files.keys)
        }

    @Test
    fun `B-SEC-045 a reset drops an old reply after the new store attached`() =
        runTest {
            val staleStore = FakeCrashStore()
            val store = FakeCrashStore()
            val oldReply = CompletableDeferred<Outcome<CrashStartupEvidence>>()
            val oldCall = CompletableDeferred<Unit>()
            var handler: suspend () -> Outcome<CrashStartupEvidence> = {
                oldCall.complete(Unit)
                oldReply.await()
            }
            val crash = startup { handler() }
            val oldReady = crash.ready
            val oldLaunch = crash.start()
            oldCall.await()
            crash.reset()
            assertNull(oldReady.await())
            val newReady = crash.ready
            handler = { Outcome.Ok(CrashStartupEvidence(store)) }
            val controller = assertNotNull(crash.start().await())
            assertSame(controller, newReady.await())
            oldReply.complete(Outcome.Ok(CrashStartupEvidence(staleStore)))
            assertNull(oldLaunch.await())
            runCurrent()
            assertSame(controller, crash.current.value)
            assertSame(controller, crash.ready.await())
            assertEquals(0, staleStore.touched)
        }

    @Test
    fun `B-SEC-045 late restored records replay exactly once after real readiness`() =
        runTest {
            val store = consented()
            val reply = CompletableDeferred<Outcome<CrashStartupEvidence>>()
            var opens = 0
            var crash: CrashStartup? = null
            val early = watchRingBefore { crash }
            val started = startup { opens++; reply.await() }
            crash = started
            val launch = started.start()
            advanceTimeBy(CrashStartup.LAUNCH_BUDGET)
            runCurrent()
            assertNull(launch.await())
            val exits =
                listOf(
                    ExitRecord(reason = ExitReason.CRASH_NATIVE, timestampMs = now, description = "synthetic-provider-secret"),
                    ExitRecord(reason = ExitReason.ANR, timestampMs = now),
                )
            reply.complete(Outcome.Ok(CrashStartupEvidence(store, exits = exits)))
            val controller = assertNotNull(started.ready.await())
            assertEquals(2, controller.savedCount)
            assertSame(controller, started.current.value)
            runCurrent()
            assertEquals(listOf("crash.native", "crash.anr"), diagnostics.errors.value.map { it.source })
            assertSame(launch, started.start())
            runCurrent()
            assertEquals(2, diagnostics.errors.value.size)
            assertEquals(1, opens)
            assertFalse(early())
            assertFalse("synthetic-provider-secret" in store.files[CrashFile.Records]!!)
        }

    @Test
    fun `B-SEC-045 a reset after readiness cancels queued restored notifications`() =
        runTest {
            val store = consented()
            val exits = listOf(ExitRecord(reason = ExitReason.CRASH_NATIVE, timestampMs = now))
            val crash = startup { Outcome.Ok(CrashStartupEvidence(store, exits = exits)) }
            val ready = crash.ready
            crash.start()
            // Reset the moment readiness settles, before the queued replay runs.
            var controller: CrashDiagnostics? = null
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                controller = ready.await()
                crash.reset()
            }
            runCurrent()
            assertEquals(1, controller!!.savedCount)
            assertNull(crash.current.value)
            assertTrue(diagnostics.isEmpty)
            assertFalse(controller!!.state.value.available)
        }

    @Test
    fun `B-SEC-045 a reset during replay prevents further stale notifications`() =
        runTest {
            val store = consented()
            val exits = listOf(ExitRecord(reason = ExitReason.CRASH_NATIVE, timestampMs = now), ExitRecord(reason = ExitReason.ANR, timestampMs = now))
            val crash = startup { Outcome.Ok(CrashStartupEvidence(store, exits = exits)) }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                diagnostics.errors.collect { if (it.isNotEmpty()) crash.reset() }
            }
            crash.start()
            runCurrent()
            assertEquals(listOf("crash.native"), diagnostics.errors.value.map { it.source })
            assertNull(crash.current.value)
        }

    @Test
    fun `B-SEC-045 a timely reply makes opted-in capture available`() =
        runTest {
            val store = consented()
            val crash = startup { Outcome.Ok(CrashStartupEvidence(store)) }
            val controller = assertNotNull(crash.start().await())
            assertTrue(controller.enabled)
            assertSame(controller, crash.current.value)
        }

    @Test
    fun `B-SEC-045 background startup imports only private categories into diagnostics`() =
        runTest {
            val store = consented()
            val jvm = NativeCrashRecord.from(rawCrash() + mapOf("timestamp" to now - 500, "exceptionClass" to "java.lang.SecurityException", "message" to UNSAFE))
            var crash: CrashStartup? = null
            val early = watchRingBefore { crash }
            val started = startup { Outcome.Ok(CrashStartupEvidence(store, platformCrash = jvm, exits = listOf(ExitRecord(reason = ExitReason.ANR, timestampMs = now)))) }
            crash = started
            val controller = assertNotNull(started.start().await())
            assertEquals(2, controller.savedCount)
            started.capture(IllegalStateException("synthetic-provider-secret"))
            assertEquals(3, controller.savedCount)
            runCurrent()
            assertEquals(
                setOf("crash.platform: Permission denied", "crash.anr: Android reported that the app stopped responding", "crash.platform: Invalid state"),
                diagnostics.errors.value.map { "${it.source}: ${it.message}" }.toSet(),
            )
            assertFalse(early())
            val saved = store.files[CrashFile.Records]!!
            assertFalse("synthetic-provider-secret" in saved)
            assertFalse("fake-secret-code" in saved)
            assertFalse("synthetic-provider-secret" in diagnostics.reportText())
        }

    @Test
    fun `B-SEC-045 clearing before the import notification cannot restore erased entries`() =
        runTest {
            val store = consented()
            val exits = listOf(ExitRecord(reason = ExitReason.CRASH_NATIVE, timestampMs = now))
            val crash = startup { Outcome.Ok(CrashStartupEvidence(store, exits = exits)) }
            val ready = crash.ready
            var cleared: Boolean? = null
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cleared = ready.await()!!.clear() }
            val controller = assertNotNull(crash.start().await())
            runCurrent()
            assertEquals(true, cleared)
            assertEquals(0, controller.savedCount)
            assertTrue(diagnostics.isEmpty)
            assertTrue(controller.enabled)
        }
}
