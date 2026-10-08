package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    @Test fun sameChatNameIsIsolatedBetweenOpenCodeAndTwoAgentProfiles() {
        val f = Fixture()
        f.host.chat("reply", true, 1000, true)
        f.host.agentChat("profile_a", "reply", true, 1000, true)
        f.host.agentChat("profile_b", "reply", true, 1000, true)
        assertEquals(3, f.host.diagnostics()["activeCount"])
        f.host.agentChat("profile_a", "reply", false, 0, false)
        assertEquals(2, f.host.diagnostics()["activeCount"])
        assertEquals(setOf("profile_b"), f.host.agentProfiles())
        f.host.chat("reply", false, 0, false)
        assertEquals(1, f.host.diagnostics()["activeCount"])
        f.host.agentChat("profile_b", "reply", false, 0, false)
        assertFalse(f.host.held)
        assertEquals(false, f.host.logicalWorkBusy())
    }
    @Test fun agentOffAfterHelperLossOnlyReleasesItsOwnProfileAndName() {
        val f = Fixture()
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { true }
        f.host.agentChat("a", "one", true, 1000, true)
        f.host.agentChat("a", "two", true, 1000, true)
        f.host.agentChat("b", "one", true, 1000, true)
        f.host.agentChat("a", "one", false, -1, false)
        assertEquals(mapOf("setup" to 1, "chat" to 2), f.host.diagnostics()["kinds"])
        f.host.agentChat("a", "two", false, 0, false)
        f.host.agentChat("b", "one", false, 0, false)
        assertTrue(f.host.held)
        assertEquals(mapOf("setup" to 1), f.host.diagnostics()["kinds"])
    }
    @Test fun serverLossRevokesOnlyOpenCodeScopeAndDoesNotCloseAgentReplies() {
        val f = Fixture()
        f.host.chat("reply", true, 1000, true)
        f.host.agentChat("a", "reply", true, 1000, true)
        f.host.serverGone()
        assertEquals(1, f.host.diagnostics()["activeCount"])
        assertEquals(true, f.host.agentChat("a", "reply", true, 1000, true)["held"])
        assertEquals(true, f.host.chat("reply", true, 1000, true)["capped"])
    }
    @Test fun helperLossClosesOnlyMatchingProfileWithoutRevivingItsRememberedRun() {
        val f = Fixture()
        f.host.chat("reply", true, 1000, true)
        f.host.agentChat("a", "reply", true, 1000, true)
        f.host.agentChat("b", "reply", true, 1000, true)
        f.host.helperGone("a")
        assertEquals(2, f.host.diagnostics()["activeCount"])
        assertEquals(setOf("a", "b"), f.host.agentProfiles())
        assertEquals(true, f.host.agentChat("a", "reply", true, 1000, true)["capped"])
        assertEquals(true, f.host.agentChat("b", "reply", true, 1000, true)["held"])
        assertEquals(true, f.host.chat("reply", true, 1000, true)["held"])
        f.host.agentChat("a", "reply", false, 0, false)
        assertEquals(true, f.host.agentChat("a", "reply", true, 1000, true)["held"])
    }
    @Test fun foregroundStopRevokesBothChatScopesWithoutReleasingSetup() {
        val f = Fixture()
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { true }
        f.host.chat("reply", true, 1000, true)
        f.host.agentChat("a", "reply", true, 1000, true)
        f.host.revokeForegroundWork()
        assertEquals(mapOf("setup" to 1), f.host.diagnostics()["kinds"])
        assertEquals(true, f.host.chat("reply", true, 1000, true)["capped"])
        assertEquals(true, f.host.agentChat("a", "reply", true, 1000, true)["capped"])
        assertFalse(f.host.foregroundHeld)
    }
    @Test fun expiredAgentReplyCannotRenewOrReadmitUntilMatchingOff() {
        val f = Fixture()
        f.host.agentChat("a", "reply", true, 1000, true)
        f.now = 1000
        f.pulse()
        assertFalse(f.host.held)
        assertEquals(true, f.host.logicalWorkBusy())
        assertEquals(true, f.host.agentChat("a", "reply", true, 1000, true)["capped"])
        f.host.agentChat("b", "reply", false, 0, false)
        assertEquals(true, f.host.agentChat("a", "reply", true, 1000, true)["capped"])
        f.host.agentChat("a", "reply", false, 0, false)
        assertEquals(true, f.host.agentChat("a", "reply", true, 1000, true)["held"])
    }
    @Test fun overlappingAgentProfilesCannotExtendContinuousHardCap() {
        val f = Fixture(2000)
        f.host.agentChat("a", "reply", true, 1500, true)
        f.now = 1000
        f.host.agentChat("b", "reply", true, 1500, true)
        f.host.agentChat("a", "reply", true, 1500, true)
        f.now = 2000
        f.pulse()
        assertFalse(f.host.held)
        assertEquals(true, f.host.agentChat("a", "reply", true, 1000, true)["capped"])
        assertEquals(true, f.host.agentChat("b", "reply", true, 1000, true)["capped"])
        assertEquals(true, f.host.logicalWorkBusy())
    }
    @Test fun agentScopeRequiresValidOpaqueIdentityAndAnAdmittedHelper() {
        val f = Fixture()
        for (profile in listOf("", "a.b", "a/b", "a:b", "a".repeat(81))) {
            assertEquals(false, f.host.agentChat(profile, "reply", true, 1000, true)["held"])
        }
        for (name in listOf("", "a/b", "a".repeat(81))) {
            assertEquals(false, f.host.agentChat("a", name, true, 1000, true)["held"])
        }
        assertEquals(false, f.host.agentChat("a", "reply", true, 1000, false)["held"])
        assertEquals(false, f.host.agentChat("a", "reply", true, 0, true)["held"])
        assertTrue(f.host.agentProfiles().isEmpty())
        assertEquals(false, f.host.logicalWorkBusy())
        assertEquals(true, f.host.agentChat("a".repeat(80), "b".repeat(80), true, 1000, true)["held"])
    }
    @Test fun helperDeniedDuringExistingReplyClosesRatherThanReadmitsItsToken() {
        val f = Fixture()
        f.host.agentChat("a", "reply", true, 1000, true)
        assertEquals(false, f.host.agentChat("a", "reply", true, 1000, false)["held"])
        assertFalse(f.host.held)
        assertEquals(true, f.host.agentChat("a", "reply", true, 1000, true)["capped"])
        assertEquals(true, f.host.logicalWorkBusy())
    }
    @Test fun logicalCapacityIsSharedAcrossBothScopesAndNativeOwnersIncludingClosedRuns() {
        val f = Fixture()
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { true }
        repeat(63) { f.host.chat("reply.$it", true, 1000, true) }
        repeat(64) { f.host.agentChat("a", "reply.$it", true, 1000, true) }
        assertEquals(128, f.host.diagnostics()["activeCount"])
        assertEquals(false, f.host.agentChat("b", "extra", true, 1000, true)["held"])
        f.host.adopt("extra", WorkLeases.Kind.SIGN_IN) { true }
        assertEquals(128, f.host.diagnostics()["activeCount"])
        f.host.revokeForegroundWork()
        assertEquals(1, f.host.diagnostics()["activeCount"])
        assertTrue(f.host.authorizeForegroundWork(f.host.foregroundGeneration()))
        assertEquals(false, f.host.agentChat("b", "extra", true, 1000, true)["held"])
        f.host.agentChat("b", "reply.0", false, 0, false)
        assertEquals(false, f.host.agentChat("b", "extra", true, 1000, true)["held"])
        f.host.agentChat("a", "reply.0", false, 0, false)
        assertEquals(true, f.host.agentChat("b", "extra", true, 1000, true)["held"])
    }
    @Test fun returnedProfileSnapshotCannotChangeInternalOwners() {
        val f = Fixture()
        f.host.agentChat("a", "reply", true, 1000, true)
        val original = f.host.agentProfiles()
        f.host.agentChat("b", "reply", true, 1000, true)
        assertEquals(setOf("a"), original)
        assertEquals(setOf("a", "b"), f.host.agentProfiles())
    }
    @Test fun terminalProtectionRaceCannotExceedCombinedLogicalOwnerLimit() {
        val f = Fixture()
        repeat(127) { f.host.chat("reply.$it", true, 1000, true) }
        f.terminals = mapOf(1 to false)
        f.protect = { f.host.agentChat("a", "reply", true, 1000, true); true }
        f.pulse()
        assertEquals(128, f.host.diagnostics()["activeCount"])
        assertEquals(mapOf("chat" to 128), f.host.diagnostics()["kinds"])
        // Once an actual logical slot ends, the still-live terminal can be observed.
        f.host.chat("reply.0", false, 0, false)
        f.protect = { true }
        f.pulse()
        assertEquals(128, f.host.diagnostics()["activeCount"])
        assertEquals(1, (f.host.diagnostics()["kinds"] as Map<*, *>)["terminal"])
    }
    @Test fun logicalIdleRequiresConfirmedNoWorkRatherThanRemainingCpuHolds() {
        val f = Fixture()
        assertEquals(false, f.host.logicalWorkBusy())
        var live = true
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { live }
        assertEquals(true, f.host.logicalWorkBusy())
        live = false // No pulse has released its CPU token yet.
        assertTrue(f.host.held)
        assertEquals(false, f.host.logicalWorkBusy())
    }
    @Test fun cappedAndRevokedSetupRemainsLogicallyBusyUntilActualCompletion() {
        val f = Fixture(2000)
        var live = true
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { live }
        f.now = 2000
        f.pulse()
        assertFalse(f.host.held)
        assertEquals(true, f.host.logicalWorkBusy())
        f.host.revokeSetupWork()
        assertEquals(true, f.host.logicalWorkBusy())
        live = false
        assertEquals(false, f.host.logicalWorkBusy())
    }
    @Test fun cappedOpenCodeChatRemainsLogicallyBusyUntilExplicitOff() {
        val f = Fixture()
        f.host.chat("reply", true, 1000, true)
        f.now = 1000
        assertFalse(f.host.held)
        assertEquals(true, f.host.logicalWorkBusy())
        f.host.serverGone()
        assertEquals(true, f.host.logicalWorkBusy())
        f.host.chat("reply", false, 0, false)
        assertEquals(false, f.host.logicalWorkBusy())
    }
    @Test fun unknownOwnerOrTerminalSnapshotCannotProveIdle() {
        val f = Fixture()
        f.snapshotUnavailable = true
        assertNull(f.host.logicalWorkBusy())
        f.snapshotUnavailable = false
        f.host.adopt("setup", WorkLeases.Kind.SETUP) { error("unavailable") }
        assertNull(f.host.logicalWorkBusy())
        f.host.release("setup")
        assertEquals(false, f.host.logicalWorkBusy())
    }
    @Test fun positivelyBusyEvidenceWinsOverAnUnknownIndependentSource() {
        val f = Fixture()
        f.host.adopt("unknown", WorkLeases.Kind.SETUP) { error("unavailable") }
        f.host.adopt("live", WorkLeases.Kind.SIGN_IN) { true }
        f.snapshotUnavailable = true
        assertEquals(true, f.host.logicalWorkBusy())
        f.host.release("live")
        f.snapshotUnavailable = false
        f.terminals = mapOf(17 to false)
        assertEquals(true, f.host.logicalWorkBusy())
        f.terminals = emptyMap()
        f.host.chat("reply", true, 1000, true)
        assertEquals(true, f.host.logicalWorkBusy())
    }
    @Test fun liveTerminalIsLogicallyBusyBeforeLeaseObservationAndAfterCap() {
        val f = Fixture(2000)
        f.terminals = mapOf(1 to false)
        assertFalse(f.host.held)
        assertEquals(true, f.host.logicalWorkBusy())
        f.pulse()
        f.now = 2000
        f.pulse()
        assertFalse(f.host.held)
        assertEquals(true, f.host.logicalWorkBusy())
        f.terminals = emptyMap()
        assertEquals(false, f.host.logicalWorkBusy())
    }
    @Test fun ownerReplacementDuringLivenessReadCannotProveIdleOrUseStaleBusyEvidence() {
        val f = Fixture()
        f.host.adopt("setup", WorkLeases.Kind.SETUP) {
            f.host.release("setup")
            f.host.adopt("setup", WorkLeases.Kind.SETUP) { false }
            true
        }
        assertNull(f.host.logicalWorkBusy())
        assertEquals(false, f.host.logicalWorkBusy())
    }
    @Test fun newlyAdoptedOwnerDuringTerminalReadMakesIdleUnknownUntilResampled() {
        val f = Fixture()
        f.terminalProvider = {
            f.host.adopt("setup", WorkLeases.Kind.SETUP) { false }
            emptyMap()
        }
        assertNull(f.host.logicalWorkBusy())
        f.terminalProvider = null
        assertEquals(false, f.host.logicalWorkBusy())
    }
    @Test fun logicalLivenessAndTerminalCallbacksRunOutsideHostGuard() {
        val f = Fixture()
        val ownerReleased = CountDownLatch(1)
        val chatReleased = CountDownLatch(1)
        f.host.adopt("setup", WorkLeases.Kind.SETUP) {
            Thread({ f.host.release("setup"); ownerReleased.countDown() }, "fixture-release-owner")
                .apply { isDaemon = true; start() }
            check(ownerReleased.await(1, TimeUnit.SECONDS))
            false
        }
        assertNull(f.host.logicalWorkBusy()) // The captured owner was removed.
        f.terminalProvider = {
            Thread({ f.host.chat("absent", false, 0, false); chatReleased.countDown() }, "fixture-release-chat")
                .apply { isDaemon = true; start() }
            check(chatReleased.await(1, TimeUnit.SECONDS))
            emptyMap()
        }
        assertEquals(false, f.host.logicalWorkBusy())
        assertEquals(0L, ownerReleased.count)
        assertEquals(0L, chatReleased.count)
    }
    @Test fun foregroundRevocationDeniesFreshChatIdsInBothScopesEvenWithRunningHelpers() {
        val f = Fixture()
        assertEquals(0L, f.host.foregroundGeneration())
        f.host.revokeForegroundWork()
        assertEquals(1L, f.host.foregroundGeneration())
        assertEquals(mapOf("held" to false, "capped" to false), f.host.chat("fresh", true, 1000, true))
        assertEquals(mapOf("held" to false, "capped" to false), f.host.agentChat("a", "fresh", true, 1000, true))
        assertFalse(f.host.held)
        assertTrue(f.host.agentProfiles().isEmpty())
        assertEquals(false, f.host.logicalWorkBusy())
    }
    @Test fun exactGenerationAuthorizationAllowsNewRunsButNeverRevivesRevokedNames() {
        val f = Fixture()
        f.host.chat("old", true, 1000, true)
        f.host.agentChat("a", "old", true, 1000, true)
        f.host.revokeForegroundWork()
        assertTrue(f.host.authorizeForegroundWork(f.host.foregroundGeneration()))
        assertEquals(mapOf("held" to false, "capped" to true), f.host.chat("old", true, 1000, true))
        assertEquals(mapOf("held" to false, "capped" to true), f.host.agentChat("a", "old", true, 1000, true))
        assertEquals(true, f.host.chat("new", true, 1000, true)["held"])
        assertEquals(true, f.host.agentChat("a", "new", true, 1000, true)["held"])
        assertEquals(2, f.host.diagnostics()["activeCount"])
    }
    @Test fun launchCapturedBeforeStopCannotAuthorizeAfterRevocation() {
        val f = Fixture()
        val captured = f.host.foregroundGeneration()
        f.host.revokeForegroundWork()
        assertFalse(f.host.authorizeForegroundWork(captured))
        assertEquals(false, f.host.chat("fresh", true, 1000, true)["held"])
        assertEquals(false, f.host.agentChat("a", "fresh", true, 1000, true)["held"])
        assertFalse(f.host.held)
    }
    @Test fun everyRevocationInvalidatesEarlierAuthorizedGeneration() {
        val f = Fixture()
        f.host.revokeForegroundWork()
        val first = f.host.foregroundGeneration()
        assertTrue(f.host.authorizeForegroundWork(first))
        assertEquals(true, f.host.agentChat("a", "first", true, 1000, true)["held"])
        f.host.revokeForegroundWork()
        assertFalse(f.host.authorizeForegroundWork(first))
        assertEquals(false, f.host.agentChat("a", "second", true, 1000, true)["held"])
        assertTrue(f.host.authorizeForegroundWork(f.host.foregroundGeneration()))
        assertEquals(true, f.host.agentChat("a", "second", true, 1000, true)["held"])
    }
    @Test fun offStillEndsExactLogicalRunsWhileChatAdmissionIsClosed() {
        val f = Fixture()
        f.host.chat("reply", true, 1000, true)
        f.host.agentChat("a", "reply", true, 1000, true)
        f.host.revokeForegroundWork()
        assertEquals(true, f.host.logicalWorkBusy())
        f.host.chat("reply", false, 0, false)
        assertEquals(true, f.host.logicalWorkBusy())
        f.host.agentChat("a", "reply", false, 0, false)
        assertEquals(false, f.host.logicalWorkBusy())
        assertEquals(false, f.host.agentChat("a", "reply", true, 1000, true)["held"])
    }
    @Test fun zeroHoldClosureRetainsBothExistingLogicalKeysWithoutCpuOrAdmission() {
        val f = Fixture()
        f.host.chat("reply", true, 1000, true)
        f.host.agentChat("a", "reply", true, 1000, true)
        assertEquals(false, f.host.agentChat("a", "reply", true, 0, true)["held"])
        assertEquals(1, f.host.diagnostics()["activeCount"])
        assertEquals(false, f.host.chat("reply", true, 0, true)["held"])
        assertFalse(f.host.held)
        assertEquals(true, f.host.logicalWorkBusy())
        assertEquals(setOf("a"), f.host.agentProfiles())
        f.host.revokeForegroundWork()
        f.host.agentChat("a", "reply", true, 0, false)
        f.host.chat("reply", true, 0, false)
        assertEquals(true, f.host.logicalWorkBusy())
        assertFalse(f.host.held)
        assertTrue(f.host.authorizeForegroundWork(f.host.foregroundGeneration()))
        assertEquals(true, f.host.agentChat("a", "reply", true, 1000, true)["capped"])
        assertEquals(true, f.host.chat("reply", true, 1000, true)["capped"])
        f.host.agentChat("a", "reply", false, 0, false)
        f.host.chat("reply", false, 0, false)
        assertEquals(false, f.host.logicalWorkBusy())
    }
}
