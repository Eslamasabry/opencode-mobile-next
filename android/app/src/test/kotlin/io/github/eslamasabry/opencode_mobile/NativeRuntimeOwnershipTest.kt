package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.*
import org.junit.Test

class NativeRuntimeOwnershipTest {
    private val boot = "11111111-1111-1111-1111-111111111111"
    private val nonce = "a".repeat(64)
    private fun p(pid: Int, parent: Int = 1, sid: Int = pid, ticks: Long = 10) = RuntimeProcessIdentity(pid, ticks, parent, sid, sid)
    private fun receipt(other: List<RuntimeProcessIdentity> = emptyList()) = NativeRuntimeReceipt(boot, nonce, 1, p(20), p(21, 20), other)
    @Test fun coldWorkerCannotAdoptReplacementStartOrSpendItsBudget() {
        val worker = NativeRuntimeOwnership.ColdWorker(4, 7)
        val events = mutableListOf<String>()
        assertTrue(worker.runIfIdle(4, 7, false) { events.add("waiting") })
        // Person Stop then Start advances admission; the delayed worker must do no ownership I/O.
        assertFalse(worker.runIfIdle(6, 7, false) {
            events.addAll(listOf("read-owner", "drain", "reserve", "launch"))
        })
        assertEquals(listOf("waiting"), events)
    }
    @Test fun coldWorkerDoesNotDrainAnAlreadyLiveServer() {
        val worker = NativeRuntimeOwnership.ColdWorker(4, 7)
        var signals = 0
        assertFalse(worker.runIfIdle(4, 7, true) { signals++ })
        assertEquals(0, signals)
        assertTrue(worker.runIfIdle(4, 7, false) { signals++ })
        assertEquals(1, signals)
    }
    @Test fun staleColdWorkerCannotClearOrLaunchOverANewerSchedule() {
        val worker = NativeRuntimeOwnership.ColdWorker(4, 7)
        var scheduled = true
        var launches = 0
        assertFalse(worker.runIfIdle(4, 8, false) { launches++ })
        assertFalse(worker.runIfCurrent(4, 8) { scheduled = false })
        assertTrue(scheduled)
        assertEquals(0, launches)
        assertTrue(NativeRuntimeOwnership.ColdWorker(4, 8).runIfCurrent(4, 8) { scheduled = false })
        assertFalse(scheduled)
    }
    @Test fun revokedColdWorkerCannotOverwritePersonStopOrSystemTimeoutCopy() {
        val worker = NativeRuntimeOwnership.ColdWorker(4, 7)
        for (reason in listOf("stopped", "systemTimeout")) {
            var published = reason
            assertFalse(worker.runIfCurrent(5, 7) { published = "ownershipUnknown" })
            assertEquals(reason, published)
        }
    }
    @Test fun currentColdWorkerKeepsItsTicketAcrossRuntimeGenerationAllocation() {
        val worker = NativeRuntimeOwnership.ColdWorker(4, 7)
        var runtimeGeneration = 11L
        var launches = 0
        assertTrue(worker.runIfIdle(4, 7, false) { runtimeGeneration++ })
        assertTrue(worker.runIfIdle(4, 7, false) { launches++ })
        assertEquals(12L, runtimeGeneration)
        assertEquals(1, launches)
    }
    @Test fun manualUnboundStartDoesNotArmColdRestoration() {
        assertFalse(NativeRuntimeOwnership.manualRecipeEligible("profile", null, true, true, true))
        assertFalse(NativeRuntimeOwnership.manualRecipeEligible("profile", "old", true, true, true))
        assertTrue(NativeRuntimeOwnership.manualRecipeEligible("profile", "profile", true, true, true))
        assertFalse(NativeRuntimeOwnership.manualRecipeEligible("profile", "profile", false, true, true))
        assertFalse(NativeRuntimeOwnership.manualRecipeEligible("profile", "profile", true, false, true))
        assertFalse(NativeRuntimeOwnership.manualRecipeEligible("profile", "profile", true, true, false))
    }
    @Test fun unsupportedGateAllowsOnlyAnUnreleasedDrainedWantedManualFallback() {
        assertTrue(NativeRuntimeOwnership.manualFallbackAllowed(false, true, true))
        assertFalse(NativeRuntimeOwnership.manualFallbackAllowed(true, true, true))
        assertFalse(NativeRuntimeOwnership.manualFallbackAllowed(false, false, true))
        assertFalse(NativeRuntimeOwnership.manualFallbackAllowed(false, true, false))
    }
    @Test fun permitCannotBeWrittenBeforeDurableIdentityCommit() {
        val gate = NativeRuntimeGate()
        var wrote = false
        assertThrows(IllegalStateException::class.java) { gate.release { wrote = true } }
        assertFalse(wrote)
        assertFalse(gate.released)
        gate.identityCommitted(); gate.release { wrote = true }
        assertTrue(wrote); assertTrue(gate.released)
    }
    @Test fun partialPermitWriteFailureMustNotFallBackToAnUnbudgetedWorkload() {
        val gate = NativeRuntimeGate(); gate.identityCommitted()
        assertThrows(IllegalStateException::class.java) { gate.release { error("write failed") } }
        assertTrue(gate.released)
        assertFalse(NativeRuntimeOwnership.manualFallbackAllowed(gate.released, true, true))
    }
    @Test fun committedIdentityCannotReleaseTwice() {
        val gate = NativeRuntimeGate(); gate.identityCommitted(); gate.release { }
        assertThrows(IllegalStateException::class.java) { gate.release { } }
    }
    @Test fun stickyRequiresValidDurableRecipeOwnershipAndBudget() {
        assertTrue(NativeRuntimeOwnership.stickyAllowed(true, true, true, 0, false))
        assertFalse(NativeRuntimeOwnership.stickyAllowed(false, true, true, 0, true))
        assertFalse(NativeRuntimeOwnership.stickyAllowed(true, false, true, 0, true))
        assertFalse(NativeRuntimeOwnership.stickyAllowed(true, true, false, 0, true))
        assertFalse(NativeRuntimeOwnership.stickyAllowed(true, true, true, 4, true))
        assertFalse(NativeRuntimeOwnership.stickyAllowed(true, true, true, 3, false))
        assertTrue(NativeRuntimeOwnership.stickyAllowed(true, true, true, 3, true))
    }
    @Test fun staleStopCannotCommitOrDrainTheReplacementManualGeneration() {
        assertTrue(NativeRuntimeOwnership.revocationMayCommit(1, 1))
        assertFalse(NativeRuntimeOwnership.revocationMayCommit(1, 2))
        assertFalse(NativeRuntimeOwnership.revocationMayCommit(null, 2))
        assertFalse(NativeRuntimeOwnership.revocationMayCommit(0, 0))
    }
    @Test fun denialDrainsOnlyPriorProcessReceiptAndNeverANewerManualStart() {
        assertTrue(NativeRuntimeOwnership.denialMayDrain(10, 10, false))
        assertFalse(NativeRuntimeOwnership.denialMayDrain(11, 10, false))
        assertFalse(NativeRuntimeOwnership.denialMayDrain(10, 10, true))
        assertFalse(NativeRuntimeOwnership.denialMayDrain(0, 10, false))
    }
    @Test fun mismatchedLeaderIdentityRefusesSignalsEvenWhenNonceStillMatches() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeRuntimeOwnership.plan(receipt(), boot, listOf(p(21, 20, ticks = 99)), emptySet()) { _, _ -> true }
        }
    }
    @Test fun mismatchedRootIdentityRefusesSignalsBeforeSelectingAnyChildren() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeRuntimeOwnership.plan(receipt(), boot, listOf(p(20, ticks = 99), p(21, 20)), emptySet(),
                requireCompleteInventory = false, nonceMatches = { _, _ -> true })
        }
    }
    @Test fun exactKernelIdentityRejectsPidReuse() { assertFalse(p(20).sameProcess(p(20, ticks = 11))) }
    @Test fun statParserHandlesSpacesAndClosingParenthesesInName() {
        val fields = mutableListOf("S", "1", "20", "20")
        repeat(15) { fields.add("0") }; fields.add("123")
        assertEquals(123L, RuntimeProcessIdentity.stat("20 (a ) b) ${fields.joinToString(" ")}").startTicks)
    }
    @Test fun zeroSessionKernelIdentitySurvivesStatAndDurableReceiptRead() {
        val fields = mutableListOf("S", "7", "373", "0")
        repeat(15) { fields.add("0") }; fields.add("12345")
        val identity = RuntimeProcessIdentity.stat("42 (app process) ${fields.joinToString(" ")}")
        assertEquals(0, identity.session)
        assertEquals(373, identity.group)
        assertEquals(identity, RuntimeProcessIdentity.read(identity.map()))
    }
    @Test fun negativeSessionAndZeroGroupStillRejectKernelIdentity() {
        assertThrows(IllegalArgumentException::class.java) { RuntimeProcessIdentity(42, 12345, 7, 373, -1) }
        assertThrows(IllegalArgumentException::class.java) { RuntimeProcessIdentity(42, 12345, 7, 0, 0) }
        val value = mapOf("pid" to 42, "startTicks" to 12345L, "parent" to 7, "group" to 373, "session" to -1)
        assertThrows(IllegalArgumentException::class.java) { RuntimeProcessIdentity.read(value) }
    }
    @Test fun zeroSessionGenericRootNeverWeakensTheCommittedGateLeaderProof() {
        val root = RuntimeProcessIdentity(20, 10, 1, 373, 0)
        val leader = RuntimeProcessIdentity(21, 10, 20, 21, 0)
        assertThrows(IllegalArgumentException::class.java) { NativeRuntimeReceipt(boot, nonce, 1, root, leader, emptyList()) }
        assertEquals(root, NativeRuntimeReceipt(boot, nonce, 1, root, p(21, 20), emptyList()).root)
    }
    @Test fun recordedOtherRuntimeAndItsCurrentChildrenAreExcluded() {
        val other = p(40)
        val plan = NativeRuntimeOwnership.plan(receipt(listOf(other)), boot,
            listOf(p(20), p(21, 20), other, p(41, 40)), emptySet()) { pid, _ -> pid == 21 }
        assertEquals(setOf(40, 41), plan.other)
        assertEquals(setOf(20, 21), plan.server.map { it.pid }.toSet())
    }
    @Test fun deadLeaderOrphanRequiresInheritedNonce() {
        val plan = NativeRuntimeOwnership.plan(receipt(), boot, listOf(p(22, sid = 21)), emptySet()) { _, n -> n == nonce }
        assertEquals(22, plan.server.single().pid)
    }
    @Test fun explicitStopSelectsOnlyProvenServerAndLeavesUnknownRuntimeAlone() {
        val plan = NativeRuntimeOwnership.plan(receipt(), boot, listOf(p(20), p(21, 20), p(99)),
            emptySet(), requireCompleteInventory = false, nonceMatches = { pid, _ -> pid == 21 })
        assertEquals(setOf(20, 21), plan.server.map { it.pid }.toSet())
    }
    @Test fun unknownSameUidProcessCannotBeKilled() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeRuntimeOwnership.plan(receipt(), boot, listOf(p(99)), emptySet()) { _, _ -> false }
        }
    }
    @Test fun orphanFromUnrecordedOtherRootFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeRuntimeOwnership.plan(receipt(listOf(p(40))), boot, listOf(p(41)), emptySet()) { _, _ -> false }
        }
    }
    @Test fun reusedOtherPidDoesNotBecomeAnExclusion() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeRuntimeOwnership.plan(receipt(listOf(p(40))), boot, listOf(p(40, ticks = 11)), emptySet()) { _, _ -> false }
        }
    }
    @Test fun bootMismatchFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeRuntimeOwnership.plan(receipt(), boot.replace('1', '2'), emptyList(), emptySet()) { _, _ -> true }
        }
    }
    @Test fun preparedGateDoesNotProveUnknownChildOwnership() {
        val prepared = NativeRuntimeReceipt(boot, nonce, 1, null, null, emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            NativeRuntimeOwnership.plan(prepared, boot, listOf(p(20)), emptySet()) { _, _ -> true }
        }
    }
}
