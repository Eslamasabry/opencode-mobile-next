package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.*
import org.junit.Test

class NativeWorkLeaseHostTest {
    private class Fixture(val cap: Long = 21_600_000, hasWake: Boolean = true) {
        data class Queued(val due: Long, val task: () -> Unit, var cancelled: Boolean = false)
        var now = 0L
        var terminals = emptyMap<Int, Boolean>()
        var snapshotUnavailable = false
        var terminalProvider: (() -> Map<Int, Boolean>)? = null
        var protection = true
        var unavailable = false
        var changes = 0
        var protectionCalls = 0
        var protect: (() -> Boolean)? = null
        val queued = mutableListOf<Queued>()
        var acquisitions = 0
        val wake = object : NativeWorkLeaseHost.Wake {
            var expires = 0L
            override val isHeld: Boolean get() = now < expires
            override fun acquire(timeoutMs: Long) {
                if (unavailable) throw SecurityException()
                acquisitions++; expires = now + timeoutMs
            }
            override fun release() { expires = now }
        }
        val host = NativeWorkLeaseHost(WorkLeases({ now }, maxLifetimeMs = cap), { now }, if (hasWake) wake else null,
            {
                if (snapshotUnavailable) throw IllegalStateException()
                terminalProvider?.invoke() ?: terminals
            }, { protectionCalls++; protect?.invoke() ?: protection }, { changes++ }, { delay, task ->
                val pending = Queued(now + delay, task)
                queued.add(pending)
                val cancel: () -> Unit = { pending.cancelled = true }
                cancel
            })
        fun pulse() { host.pulse() }
        fun next(): Queued = queued.filter { !it.cancelled }.minByOrNull { it.due }
            ?: error("No pending fake callback")
        fun runNext() {
            val pending = next()
            queued.remove(pending)
            check(pending.due >= now)
            now = pending.due
            pending.task()
        }
    }
    @Test fun chatOffCannotReleaseIndependentSetup() {
        val f = Fixture(); f.host.adopt("setup", WorkLeases.Kind.SETUP) { true }
        assertEquals(true, f.host.chat("chat.one", true, 1000, true)["held"])
        f.host.chat("chat.one", false, 1000, true)
        assertTrue(f.host.held); assertEquals(1, f.host.diagnostics()["activeCount"])
        f.host.release("setup"); assertFalse(f.host.held)
    }
    @Test fun stoppingServerCannotReleaseSignInOrSetup() {
        val f = Fixture(); f.host.adopt("sign", WorkLeases.Kind.SIGN_IN) { true }
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { true }
        f.host.chat("chat.one", true, 1000, true); f.host.serverGone()
        assertTrue(f.host.held); assertEquals(2, f.host.diagnostics()["activeCount"])
    }
    @Test fun separateChatOwnersReleaseOnlyTheirOwnToken() {
        val f = Fixture(); f.host.chat("chat.one", true, 1000, true); f.host.chat("chat.two", true, 2000, true)
        f.host.chat("chat.one", false, 1000, true); assertTrue(f.host.held)
        f.now = 2000; f.pulse(); assertFalse(f.host.held)
    }
    @Test fun expiredNamedRunCannotBeRevivedUntilExplicitIdle() {
        val f = Fixture(); f.host.chat("chat.one", true, 1000, true); f.now = 1000; f.pulse()
        assertEquals(mapOf("held" to false, "capped" to true), f.host.chat("chat.one", true, 1000, true))
        f.host.chat("chat.one", false, 1000, true)
        assertEquals(true, f.host.chat("chat.one", true, 1000, true)["held"])
    }
    @Test fun nativeContinuousCapStopsRenewals() {
        val f = Fixture(2000); f.host.chat("chat.one", true, 1500, true)
        f.now = 1000; f.host.chat("chat.one", true, 1500, true)
        f.now = 2000; f.pulse(); assertFalse(f.host.held)
        assertEquals(true, f.host.chat("chat.one", true, 1000, true)["capped"])
    }
    @Test fun serviceTimeoutRevokesForegroundWorkPreservingSeparateSetup() {
        val f = Fixture(); f.host.adopt("setup", WorkLeases.Kind.SETUP) { true }
        f.host.adopt("sign", WorkLeases.Kind.SIGN_IN) { true }
        f.terminals = mapOf(1 to false); f.pulse()
        f.host.chat("chat.one", true, 1000, true); f.host.revokeForegroundWork()
        assertEquals(1, f.host.diagnostics()["activeCount"]); assertFalse(f.host.foregroundHeld)
        f.now = 300001; f.pulse(); assertEquals(1, f.host.diagnostics()["activeCount"])
    }
    @Test fun terminalExitEndsLastHoldAndUpdatesServiceLifetime() {
        val f = Fixture(); f.terminals = mapOf(1 to false); f.pulse()
        assertTrue(f.host.held); assertTrue(f.host.foregroundHeld)
        f.terminals = emptyMap(); f.pulse(); assertFalse(f.host.held); assertFalse(f.host.foregroundHeld)
        assertEquals(2, f.changes)
    }
    @Test fun terminalSignInHasItsOwnKindWithoutIdleServerHold() {
        val f = Fixture(); f.terminals = mapOf(1 to true, 2 to false); f.pulse()
        assertEquals(mapOf("sign_in" to 1, "terminal" to 1), f.host.diagnostics()["kinds"])
        f.terminals = mapOf(2 to false); f.pulse(); assertTrue(f.host.held)
    }
    @Test fun rejectedTerminalProtectionNeverGrantsOrReacquiresSameSession() {
        val f = Fixture(); f.protection = false; f.terminals = mapOf(1 to false); f.pulse()
        assertFalse(f.host.held); f.protection = true; f.now = 300001; f.pulse(); assertFalse(f.host.held)
        f.terminals = emptyMap(); f.pulse(); f.terminals = mapOf(2 to false); f.pulse(); assertTrue(f.host.held)
    }
    @Test fun timeoutDuringTerminalProtectionCannotRearmItsHold() {
        val f = Fixture(); f.terminals = mapOf(1 to false)
        f.protect = { f.host.revokeForegroundWork(); true }
        f.pulse(); assertFalse(f.host.held)
    }
    @Test fun wakePermissionFailureDoesNotExposeErrorOrReviveExistingRun() {
        val f = Fixture(); f.unavailable = true
        assertEquals(mapOf("held" to false, "capped" to false), f.host.chat("chat.one", true, 1000, true))
        f.unavailable = false; assertEquals(false, f.host.chat("chat.one", true, 1000, true)["held"])
    }
    @Test fun finishedOwnerReleasesWithoutAnotherDartCall() {
        val f = Fixture(); var live = true; f.host.adopt("setup", WorkLeases.Kind.SETUP) { live }
        live = false; f.pulse(); assertFalse(f.host.held)
    }
    @Test fun idleServerAndInvalidLeaseNeverHoldCpu() {
        val f = Fixture(); assertFalse(f.host.held)
        assertEquals(false, f.host.chat("chat.one", true, 1000, false)["held"])
        assertEquals(false, f.host.chat("secret/name", true, 1000, true)["held"])
        assertEquals(false, f.host.chat("chat.one", true, -1, true)["held"])
        assertFalse(f.host.held)
    }
    @Test fun terminalObservationPreemptsPendingChatExpiryAndCancelledCallbackIsInert() {
        val f = Fixture()
        f.host.chat("chat.one", true, 500, true)
        f.runNext() // Initial 100ms pulse leaves only the chat expiry at 500ms.
        val obsolete = f.next()
        assertEquals(500L, obsolete.due)
        f.terminals = mapOf(1 to false)
        f.host.observeTerminalsSoon()
        assertTrue(obsolete.cancelled)
        assertEquals(200L, f.next().due)
        assertEquals(0, f.protectionCalls)
        f.runNext()
        assertEquals(1, f.protectionCalls)
        assertTrue(f.host.foregroundHeld)
        assertEquals(1, (f.host.diagnostics()["kinds"] as Map<*, *>)["terminal"])
        val replacement = f.next()
        val changes = f.changes
        val acquisitions = f.acquisitions
        // A cancelled runnable may already be queued at its former due time.
        f.now = 500L
        obsolete.task()
        assertEquals(changes, f.changes)
        assertEquals(acquisitions, f.acquisitions)
        assertEquals(1, f.protectionCalls)
        assertSame(replacement, f.next())
        assertTrue(f.host.held)
    }
    @Test fun newNativeOwnerPreemptsChatExpiryAndRenewsBeforeItsHoldExpires() {
        val f = Fixture()
        f.host.chat("chat.one", true, 800_000, true)
        f.runNext()
        val obsolete = f.next()
        assertEquals(800_000L, obsolete.due)
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { true }
        assertTrue(obsolete.cancelled)
        assertEquals(200L, f.next().due)
        f.runNext()
        // Periodic owner pulses renew before the first 15-minute timeout.
        for (step in 1..4) {
            f.now = 100L + step * 300_000L
            f.pulse()
            assertTrue(f.host.held)
            assertEquals(1, (f.host.diagnostics()["kinds"] as Map<*, *>)["setup"])
        }
    }
    @Test fun unknownTerminalSnapshotCannotReviveCappedSameSessionOrRepeatProtection() {
        val f = Fixture(2_000)
        f.terminals = mapOf(1 to false)
        f.pulse()
        assertEquals(1, f.protectionCalls)
        f.now = 2_000L
        f.pulse()
        assertFalse(f.host.held)
        f.snapshotUnavailable = true
        f.now = 2_500L
        f.pulse()
        f.snapshotUnavailable = false
        f.now = 3_000L
        f.pulse()
        assertFalse(f.host.held)
        assertFalse(f.host.foregroundHeld)
        assertEquals(1, f.protectionCalls)
        // Only a successful absence observation ends the logical session.
        f.terminals = emptyMap()
        f.pulse()
        f.terminals = mapOf(2 to false)
        f.pulse()
        assertTrue(f.host.held)
        assertEquals(2, f.protectionCalls)
    }
    @Test fun unknownLiveTerminalSnapshotClosesHoldWithoutReadmittingItsSession() {
        val f = Fixture()
        f.terminals = mapOf(1 to true)
        f.pulse()
        assertTrue(f.host.held)
        f.snapshotUnavailable = true
        f.pulse()
        assertFalse(f.host.held)
        f.snapshotUnavailable = false
        f.now = 300_001L
        f.pulse()
        assertFalse(f.host.held)
        assertFalse(f.host.foregroundHeld)
        assertEquals(1, f.protectionCalls)
    }
    @Test fun unknownNativeOwnerLivenessClosesAndRetainsItsLogicalOwnerUntilExplicitEnd() {
        val f = Fixture()
        var unknown = false
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { if (unknown) error("unavailable") else true }
        assertTrue(f.host.held)
        unknown = true
        f.pulse()
        assertFalse(f.host.held)
        unknown = false
        f.now = 300_001L
        f.pulse()
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { true }
        assertFalse(f.host.held)
        f.host.release("setup")
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { true }
        assertTrue(f.host.held)
    }
    @Test fun missingWakeCannotGrantCpuOrForegroundEligibilityForAnyWorkKind() {
        val f = Fixture(hasWake = false)
        f.host.chat("chat.one", true, 1_000, true)
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { true }
        f.host.adopt("sign", WorkLeases.Kind.SIGN_IN) { true }
        f.terminals = mapOf(1 to false)
        f.pulse()
        assertFalse(f.host.held)
        assertFalse(f.host.foregroundHeld)
        assertEquals(0, f.host.diagnostics()["activeCount"])
        f.now = 300_001L
        f.pulse()
        assertFalse(f.host.foregroundHeld)
    }
    @Test fun refusedWakeDoesNotLeaveForegroundEligibilityOrReviveRememberedOwners() {
        val f = Fixture()
        f.unavailable = true
        f.terminals = mapOf(1 to true, 2 to false)
        f.host.chat("chat.one", true, 1_000, true)
        f.host.adopt("sign", WorkLeases.Kind.SIGN_IN) { true }
        f.pulse()
        assertFalse(f.host.held)
        assertFalse(f.host.foregroundHeld)
        assertEquals(0, f.host.diagnostics()["activeCount"])
        f.unavailable = false
        f.now = 300_001L
        f.pulse()
        f.host.chat("chat.one", true, 1_000, true)
        f.host.adopt("sign", WorkLeases.Kind.SIGN_IN) { true }
        assertFalse(f.host.held)
        assertFalse(f.host.foregroundHeld)
        assertEquals(1, f.protectionCalls)
    }
    @Test fun setupServiceLossRevokesJobAndInstallerWhileIndependentChatRemains() {
        val f = Fixture()
        f.host.adopt("setup-job", WorkLeases.Kind.SETUP) { true }
        f.host.adopt("installer", WorkLeases.Kind.SETUP) { true }
        f.host.chat("chat.one", true, 900_000, true)
        f.host.revokeSetupWork()
        assertTrue(f.host.held)
        assertTrue(f.host.foregroundHeld)
        assertEquals(mapOf("chat" to 1), f.host.diagnostics()["kinds"])
        f.now = 300_001L
        f.pulse()
        assertEquals(mapOf("chat" to 1), f.host.diagnostics()["kinds"])
        f.host.adopt("setup-job", WorkLeases.Kind.SETUP) { true }
        assertEquals(1, f.host.diagnostics()["activeCount"])
        f.host.chat("chat.one", false, 1_000, true)
        assertFalse(f.host.held)
    }
    @Test fun automaticallyRenewedSetupAndSignInStopAtSixHourContinuousCap() {
        val f = Fixture()
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { true }
        f.host.adopt("sign", WorkLeases.Kind.SIGN_IN) { true }
        for (step in 1..71) {
            f.now = step * 300_000L
            f.pulse()
            assertTrue(f.host.held)
            assertEquals(2, f.host.diagnostics()["activeCount"])
        }
        f.now = 21_600_000L
        f.pulse()
        assertFalse(f.host.held)
        assertFalse(f.host.foregroundHeld)
        f.now += 300_000L
        f.pulse()
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { true }
        f.host.adopt("sign", WorkLeases.Kind.SIGN_IN) { true }
        assertEquals(0, f.host.diagnostics()["activeCount"])
    }
}
