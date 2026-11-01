package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WorkLeasesTest {
    private var now = 0L
    private fun registry(hold: Long = 100L, lifetime: Long = 1_000L, capacity: Int = 128) =
        WorkLeases({ now }, hold, lifetime, capacity)

    @Test fun overlappingWorkSurvivesOneOwnerReleaseAndLastReleaseEndsHold() {
        val leases = registry()
        val chat = leases.acquire(WorkLeases.Kind.CHAT, 60L)!!
        val setup = leases.acquire(WorkLeases.Kind.SETUP, 90L)!!
        assertEquals(2, leases.snapshot().activeCount)
        assertEquals(90L, leases.snapshot().holdForMs)
        assertEquals(60L, leases.snapshot().nextExpiryInMs)
        assertTrue(leases.release(chat))
        assertTrue(leases.contains(setup))
        assertEquals(1, leases.snapshot().activeCount)
        assertTrue(leases.release(setup))
        assertEquals(0, leases.snapshot().activeCount)
        assertEquals(0L, leases.snapshot().holdForMs)
        assertNull(leases.snapshot().nextExpiryInMs)
    }

    @Test fun positiveHoldsClampToMaximumButInvalidHoldsNeverAdmitOrRenew() {
        val leases = registry()
        assertNull(leases.acquire(WorkLeases.Kind.CHAT, 0L))
        assertNull(leases.acquire(WorkLeases.Kind.CHAT, -1L))
        val token = leases.acquire(WorkLeases.Kind.CHAT, Long.MAX_VALUE)!!
        assertEquals(100L, leases.snapshot().holdForMs)
        assertFalse(leases.renew(token, 0L))
        assertFalse(leases.renew(token, Long.MIN_VALUE))
        assertEquals(100L, leases.snapshot().holdForMs)
        now = 50L
        assertTrue(leases.renew(token, Long.MAX_VALUE))
        assertEquals(100L, leases.snapshot().holdForMs)
    }

    @Test fun renewalCannotResetOriginalLifetimeAndExactDeadlineExpires() {
        val leases = registry(lifetime = 250L)
        val token = leases.acquire(WorkLeases.Kind.TERMINAL, 100L)!!
        now = 90L
        assertTrue(leases.renew(token, 100L))
        now = 180L
        assertTrue(leases.renew(token, 100L))
        assertEquals(70L, leases.snapshot().holdForMs)
        now = 249L
        assertTrue(leases.renew(token, 100L))
        assertEquals(1L, leases.snapshot().holdForMs)
        now = 250L
        assertFalse(leases.contains(token))
        assertFalse(leases.renew(token, 100L))
        assertFalse(leases.release(token))
    }

    @Test fun overlappingNewAcquisitionsCannotExtendContinuousAggregateCap() {
        val leases = registry(hold = 1_000L, lifetime = 250L)
        val first = leases.acquire(WorkLeases.Kind.CHAT, 1_000L)!!
        now = 100L
        val second = leases.acquire(WorkLeases.Kind.SIGN_IN, 1_000L)!!
        assertTrue(leases.release(first))
        now = 200L
        val third = leases.acquire(WorkLeases.Kind.SETUP, 1_000L)!!
        assertEquals(50L, leases.snapshot().holdForMs)
        assertTrue(leases.renew(second, 1_000L))
        assertTrue(leases.renew(third, 1_000L))
        now = 250L
        assertEquals(0, leases.snapshot().activeCount)
        assertFalse(leases.renew(second, 1_000L))
        assertFalse(leases.renew(third, 1_000L))
    }

    @Test fun expiredShortHoldCannotBeRevivedEvenBeforeHardDeadline() {
        val leases = registry()
        val token = leases.acquire(WorkLeases.Kind.SIGN_IN, 10L)!!
        now = 10L
        assertFalse(leases.renew(token, 100L))
        assertFalse(leases.contains(token))
        assertEquals(0, leases.snapshot().activeCount)
    }

    @Test fun releaseClearAndExpiryNeverReuseTokensOrLetOldReleaseAffectNewWork() {
        val leases = registry()
        val first = leases.acquire(WorkLeases.Kind.CHAT, 10L)!!
        assertTrue(leases.release(first))
        val second = leases.acquire(WorkLeases.Kind.SETUP, 10L)!!
        leases.clear()
        val third = leases.acquire(WorkLeases.Kind.SIGN_IN, 10L)!!
        now = 10L
        assertFalse(leases.renew(third, 10L))
        val fourth = leases.acquire(WorkLeases.Kind.TERMINAL, 10L)!!
        assertEquals(4, setOf(first, second, third, fourth).size)
        for (old in listOf(first, second, third)) {
            assertFalse(leases.release(old))
            assertFalse(leases.renew(old, 100L))
        }
        assertTrue(leases.contains(fourth))
    }

    @Test fun genuineIdleAllowsAFreshAggregateWindow() {
        val leases = registry(hold = 1_000L, lifetime = 250L)
        val first = leases.acquire(WorkLeases.Kind.CHAT, 1_000L)!!
        now = 100L
        assertTrue(leases.release(first))
        now = 200L
        val next = leases.acquire(WorkLeases.Kind.SETUP, 1_000L)!!
        assertNotEquals(first, next)
        assertEquals(250L, leases.snapshot().holdForMs)
    }

    @Test fun kindReleasePreservesOtherWorkAndAccurateIndependentCounts() {
        val leases = registry()
        leases.acquire(WorkLeases.Kind.CHAT, 100L)
        leases.acquire(WorkLeases.Kind.CHAT, 100L)
        val setup = leases.acquire(WorkLeases.Kind.SETUP, 100L)!!
        val signIn = leases.acquire(WorkLeases.Kind.SIGN_IN, 100L)!!
        val terminal = leases.acquire(WorkLeases.Kind.TERMINAL, 100L)!!
        assertEquals(2, leases.snapshot().counts[WorkLeases.Kind.CHAT])
        leases.releaseKind(WorkLeases.Kind.CHAT)
        assertEquals(3, leases.snapshot().activeCount)
        assertEquals(0, leases.snapshot().counts[WorkLeases.Kind.CHAT])
        assertTrue(listOf(setup, signIn, terminal).all { leases.contains(it) })
    }

    @Test fun capacityIsBoundedAndExpiredSlotsBecomeAvailable() {
        val leases = registry(capacity = 2)
        leases.acquire(WorkLeases.Kind.CHAT, 10L)
        val setup = leases.acquire(WorkLeases.Kind.SETUP, 100L)!!
        assertNull(leases.acquire(WorkLeases.Kind.TERMINAL, 100L))
        now = 10L
        assertNotNull(leases.acquire(WorkLeases.Kind.TERMINAL, 100L))
        assertTrue(leases.contains(setup))
        assertEquals(2, leases.snapshot().activeCount)
    }

    @Test fun defaultCapacityAdmitsOnly128Holds() {
        val leases = registry()
        repeat(128) { assertNotNull(leases.acquire(WorkLeases.Kind.TERMINAL, 100L)) }
        assertNull(leases.acquire(WorkLeases.Kind.SETUP, 100L))
        assertEquals(128, leases.snapshot().activeCount)
    }

    @Test fun overflowingLifetimeNeverSaturatesToAnUnboundedDeadline() {
        val leases = registry()
        now = Long.MAX_VALUE - 999L
        assertNull(leases.acquire(WorkLeases.Kind.SETUP, 1L))
        assertEquals(0, leases.snapshot().activeCount)
    }

    @Test fun exactMaximumDeadlineRemainsBoundedAndExpiresNormally() {
        val leases = registry(hold = 1_000L, lifetime = 1_000L)
        now = Long.MAX_VALUE - 1_000L
        val token = leases.acquire(WorkLeases.Kind.SETUP, Long.MAX_VALUE)!!
        assertEquals(1_000L, leases.snapshot().holdForMs)
        now = Long.MAX_VALUE - 1L
        assertTrue(leases.renew(token, Long.MAX_VALUE))
        assertEquals(1L, leases.snapshot().holdForMs)
        now = Long.MAX_VALUE
        assertFalse(leases.renew(token, Long.MAX_VALUE))
        assertNull(leases.acquire(WorkLeases.Kind.CHAT, 1L))
    }

    @Test fun exhaustedTokenCounterRefusesInsteadOfWrappingOrReusing() {
        val leases = registry()
        // Simulate unreachable-in-practice counter exhaustion without allocating
        // Long.MAX_VALUE leases. This checks the fail-closed boundary only.
        val counter = WorkLeases::class.java.getDeclaredField("lastToken")
        counter.isAccessible = true
        counter.setLong(leases, Long.MAX_VALUE - 1L)
        assertEquals(Long.MAX_VALUE, leases.acquire(WorkLeases.Kind.CHAT, 100L))
        leases.clear()
        assertNull(leases.acquire(WorkLeases.Kind.SETUP, 100L))
    }

    @Test fun forwardClockJumpExpiresEveryOldHoldWithoutRenewal() {
        val leases = registry()
        val token = leases.acquire(WorkLeases.Kind.CHAT, 100L)!!
        now = 100_000L
        assertEquals(0, leases.snapshot().activeCount)
        assertFalse(leases.renew(token, 100L))
    }

    @Test fun backwardClockClosesHoldsAndRefusesAdmissionUntilCaughtUp() {
        val leases = registry()
        now = 100L
        val token = leases.acquire(WorkLeases.Kind.CHAT, 100L)!!
        now = 99L
        assertEquals(0, leases.snapshot().activeCount)
        assertFalse(leases.renew(token, 100L))
        assertNull(leases.acquire(WorkLeases.Kind.SETUP, 100L))
        now = 100L
        val next = leases.acquire(WorkLeases.Kind.SETUP, 100L)!!
        assertNotEquals(token, next)
        assertFalse(leases.renew(token, 100L))
    }

    @Test fun negativeAndUnavailableClocksNeverExposeErrorsOrKeepHolds() {
        var unavailable = false
        val leases = WorkLeases({ if (unavailable) error("clock failure") else now }, 100L, 1_000L)
        val token = leases.acquire(WorkLeases.Kind.SIGN_IN, 100L)!!
        unavailable = true
        assertEquals(0, leases.snapshot().activeCount)
        assertFalse(leases.contains(token))
        assertNull(leases.acquire(WorkLeases.Kind.CHAT, 100L))
        unavailable = false
        now = -1L
        assertNull(leases.acquire(WorkLeases.Kind.SETUP, 100L))
        now = 0L
        assertNotNull(leases.acquire(WorkLeases.Kind.SETUP, 100L))
        assertFalse(leases.renew(token, 100L))
    }

    @Test fun snapshotCountsCannotMutateRegistryOrPreviouslyReturnedSnapshots() {
        val leases = registry()
        leases.acquire(WorkLeases.Kind.CHAT, 100L)
        val before = leases.snapshot()
        assertThrows(UnsupportedOperationException::class.java) {
            (before.counts as MutableMap<WorkLeases.Kind, Int>)[WorkLeases.Kind.CHAT] = 99
        }
        leases.releaseKind(WorkLeases.Kind.CHAT)
        assertEquals(1, before.counts[WorkLeases.Kind.CHAT])
        assertEquals(0, leases.snapshot().counts[WorkLeases.Kind.CHAT])
    }

    @Test fun invalidRegistryBoundsRefuseConstruction() {
        for (hold in listOf(0L, -1L)) {
            assertThrows(IllegalArgumentException::class.java) { WorkLeases({ 0L }, maxHoldMs = hold) }
        }
        for (lifetime in listOf(0L, -1L)) {
            assertThrows(IllegalArgumentException::class.java) { WorkLeases({ 0L }, maxLifetimeMs = lifetime) }
        }
        for (capacity in listOf(0, -1, 129)) {
            assertThrows(IllegalArgumentException::class.java) { WorkLeases({ 0L }, maxLeases = capacity) }
        }
    }

    @Test fun concurrentAdmissionsAndReleasesNeverExceedCapacityOrReuseTokens() {
        val leases = WorkLeases({ 0L }, 100L, 1_000L, 16)
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val admitted = (1..64).map {
                pool.submit<Long?> { start.await(); leases.acquire(WorkLeases.Kind.CHAT, 100L) }
            }
            start.countDown()
            val tokens = admitted.mapNotNull { it.get(5, TimeUnit.SECONDS) }
            assertEquals(16, tokens.size)
            assertEquals(tokens.size, tokens.toSet().size)
            assertEquals(16, leases.snapshot().activeCount)
            val released = tokens.map { token -> pool.submit<Boolean> { leases.release(token) } }
            assertTrue(released.all { it.get(5, TimeUnit.SECONDS) })
            assertEquals(0, leases.snapshot().activeCount)
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
