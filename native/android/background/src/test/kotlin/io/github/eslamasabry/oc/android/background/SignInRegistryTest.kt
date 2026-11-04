package io.github.eslamasabry.oc.android.background

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// Ports test/agent_sign_in_foreground_test.dart (B-ANDR-001): the owner registry over a fake service port.
@OptIn(ExperimentalCoroutinesApi::class)
class SignInRegistryTest {
    private class FakeLease(private val log: MutableList<String>, private val refusal: ForegroundLoss?) : ForegroundLease {
        val events = MutableSharedFlow<ForegroundLoss>(extraBufferCapacity = 8)
        override var active = true

        override suspend fun ready(): LeaseAdmission = refusal?.let { LeaseAdmission.Refused(it) } ?: LeaseAdmission.Ready

        override val lost: Flow<ForegroundLoss> = events

        override suspend fun release(): Outcome<Unit> {
            if (!active) return Unit.ok()
            active = false
            log += "release"
            return Unit.ok()
        }
    }

    private class FakePort(private val refusal: ForegroundLoss? = null) : ForegroundLeasePort {
        val log = mutableListOf<String>()
        val leases = mutableListOf<FakeLease>()

        override fun reserve(): ForegroundLease {
            log += "reserve"
            return FakeLease(log, refusal).also { leases += it }
        }
    }

    private fun TestScope.registry() = SignInForegroundRegistry(backgroundScope)

    private fun TestScope.losses(lease: ForegroundLease): MutableList<ForegroundLoss> {
        val seen = mutableListOf<ForegroundLoss>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { lease.lost.collect { seen += it } }
        return seen
    }

    @Test
    fun `B-ANDR-001 a refused service keeps its code through the owner binding and is not a loss`() = runTest {
        val registry = registry()
        val port = FakePort(ForegroundLoss(ForegroundFailure.Unavailable, "service-not-started"))
        val binding = registry.bind("coded-refusal", port)
        val lease = binding.reserve()
        val losses = losses(lease)
        val admission = assertIs<LeaseAdmission.Refused>(lease.ready())
        assertEquals("service-not-started", admission.loss.code)
        runCurrent()
        assertEquals(0, losses.size, "a refusal is not a loss: only ready reports it")
        registry.unbind(binding)
    }

    @Test
    fun `B-ANDR-001 a running service that stops says what stopped it`() = runTest {
        val registry = registry()
        val port = FakePort()
        val binding = registry.bind("coded-loss", port)
        val lease = binding.reserve()
        assertEquals(LeaseAdmission.Ready, lease.ready())
        val losses = losses(lease)
        port.leases.single().events.emit(ForegroundLoss(ForegroundFailure.Paused, "android-time-limit"))
        runCurrent()
        assertEquals("android-time-limit", losses.single().code)
        assertEquals(ForegroundFailure.Paused, losses.single().reason)
        registry.unbind(binding)
    }

    @Test
    fun `B-ANDR-001 successive replacement cannot skip the oldest pending drain`() = runTest {
        val registry = registry()
        val first = registry.bind("foreground-chain", FakePort())
        val drain = CompletableDeferred<Unit>()
        first.addCleanup {
            drain.await()
            Unit.ok()
        }
        registry.bind("foreground-chain", FakePort())
        val lastPort = FakePort()
        val last = registry.bind("foreground-chain", lastPort)
        val lease = last.reserve()
        runCurrent()
        val beforeDrain = lastPort.log.toList()
        drain.complete(Unit)
        assertEquals(LeaseAdmission.Ready, lease.ready())
        registry.unbind(last)
        assertEquals(emptyList(), beforeDrain)
    }

    @Test
    fun `B-ANDR-001 a replacement waits for the old terminal drain and foreground release`() = runTest {
        val registry = registry()
        val oldPort = FakePort()
        val old = registry.bind("foreground-replacement", oldPort)
        val oldLease = old.reserve()
        assertEquals(LeaseAdmission.Ready, oldLease.ready())
        val drain = CompletableDeferred<Unit>()
        old.addCleanup {
            oldPort.log += "drain"
            drain.await()
            Unit.ok()
        }
        val nextPort = FakePort()
        val next = registry.bind("foreground-replacement", nextPort)
        val nextLease = next.reserve()
        runCurrent()
        assertEquals(listOf("reserve", "drain"), oldPort.log)
        assertEquals(emptyList(), nextPort.log)
        drain.complete(Unit)
        assertEquals(LeaseAdmission.Ready, nextLease.ready())
        assertEquals(listOf("reserve", "drain", "release"), oldPort.log)
        assertTrue(nextLease.active)
        registry.unbind(next)
    }

    @Test
    fun `B-ANDR-001 an unbind from an old owner cannot clear its replacement`() = runTest {
        val registry = registry()
        val old = registry.bind("foreground-stale", FakePort())
        val next = registry.bind("foreground-stale", FakePort())
        registry.unbind(old)
        assertSame(next, registry.bindingFor("foreground-stale"))
        assertTrue(next.current)
        registry.unbind(next)
    }

    @Test
    fun `B-ANDR-001 cancelling a reservation before the drain gate never starts a service`() = runTest {
        val registry = registry()
        val drain = CompletableDeferred<Unit>()
        val old = registry.bind("foreground-cancel", FakePort())
        old.addCleanup {
            drain.await()
            Unit.ok()
        }
        val port = FakePort()
        val next = registry.bind("foreground-cancel", port)
        val lease = next.reserve()
        lease.release()
        assertIs<LeaseAdmission.Refused>(lease.ready())
        drain.complete(Unit)
        runCurrent()
        assertEquals(emptyList(), port.log)
        registry.unbind(next)
    }

    @Test
    fun `B-ANDR-001 a failed cleanup blocks admission and remains retryable`() = runTest {
        val registry = registry()
        var fail = true
        val old = registry.bind("foreground-retry", FakePort())
        old.addCleanup {
            if (fail) throw IllegalStateException("private native text")
            Unit.ok()
        }
        assertIs<Outcome.Failed>(registry.unbind(old))
        fail = false
        assertIs<Outcome.Ok<Unit>>(registry.unbind(old))
        val next = registry.bind("foreground-retry", FakePort())
        val lease = next.reserve()
        assertEquals(LeaseAdmission.Ready, lease.ready())
        assertTrue(lease.active)
        registry.unbind(next)
    }
}
