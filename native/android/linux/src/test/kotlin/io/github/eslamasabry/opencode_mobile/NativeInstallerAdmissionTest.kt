package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInstallerAdmissionTest {
    private val boot = "11111111-1111-1111-1111-111111111111"
    private val rootfs = "a".repeat(64)
    private fun process(pid: Int, parent: Int = 1, session: Int = pid, ticks: Long = 10) =
        RuntimeProcessIdentity(pid, ticks, parent, session, session)
    private fun prepared(operation: InstallerOperation = InstallerOperation.CHECK) = InstallerTicket(
        "b".repeat(64), rootfs, InstallerTarget.entries.toSet(), operation,
        NativeRuntimeReceipt(boot, "c".repeat(64), 7, null, null, emptyList()),
    )
    private fun committed(operation: InstallerOperation = InstallerOperation.CHECK) =
        NativeInstallerOwnership.committed(prepared(operation), process(20), process(21, 20))

    private class Slot(var ticket: InstallerTicket?) {
        var current = true
        var ownerLive = false
        var proofs = 0
        var clears = 0
        var saveSucceeds = true
        val seen = mutableListOf<InstallerTicket>()
        fun reclaim(proof: (InstallerTicket) -> Boolean = { true }) = NativeInstallerAdmission.reclaim(
            NativeInstallerAdmission.Claim(ticket, ownerLive), { current }, { ticket },
            { proofs++; seen.add(it); proof(it) },
            { candidate ->
                clears++
                assertEquals(ticket, candidate)
                if (saveSucceeds) ticket = null
                saveSucceeds
            },
        )
    }

    @Test fun deadAllTargetCheckAndInstallReclaimAfterTwoExactEmptyProofs() {
        for (operation in InstallerOperation.entries) {
            val original = committed(operation)
            val slot = Slot(original)
            val absence = mutableListOf<Int>()
            assertTrue(slot.reclaim { ticket ->
                NativeInstallerOwnership.drainPlan(ticket, rootfs, boot, emptyList(), emptySet(),
                    { absence.add(it); true }, { _, _ -> false }).server.isEmpty()
            })
            assertEquals(null, slot.ticket)
            assertEquals(listOf(original, original), slot.seen)
            assertEquals(listOf(20, 21, 20, 21), absence)
            assertEquals(1, slot.clears)
        }
    }
    @Test fun failedPreparedLaunchReclaimsOnlyAfterTwoCompleteEmptyInventories() {
        val slot = Slot(prepared())
        assertTrue(slot.reclaim { ticket ->
            NativeInstallerOwnership.drainPlan(ticket, rootfs, boot, emptyList(), emptySet(),
                { error("prepared ticket has no PID to probe") }, { _, _ -> false }).server.isEmpty()
        })
        assertEquals(2, slot.proofs)
        assertEquals(1, slot.clears)
    }
    @Test fun absentTicketDoesNotInspectOrClearAnything() {
        val slot = Slot(null)
        assertFalse(slot.reclaim())
        assertEquals(0, slot.proofs)
        assertEquals(0, slot.clears)
    }
    @Test fun livingCachedOwnerCannotBeReclaimedEvenWithAnEmptyInventory() {
        val slot = Slot(committed()).apply { ownerLive = true }
        assertFalse(slot.reclaim())
        assertEquals(0, slot.proofs)
        assertEquals(0, slot.clears)
    }
    @Test fun staleCachedOwnerCannotInspectOrClearAnotherOwner() {
        val slot = Slot(committed()).apply { current = false }
        assertFalse(slot.reclaim())
        assertEquals(0, slot.proofs)
        assertEquals(0, slot.clears)
    }
    @Test fun ticketReplacedBeforeFirstProofIsPreserved() {
        val old = committed()
        val next = old.copy(id = "d".repeat(64))
        var cleared = false
        var proofs = 0
        assertFalse(NativeInstallerAdmission.reclaim(NativeInstallerAdmission.Claim(old, false), { true }, { next },
            { proofs++; true }, { cleared = true; true }))
        assertEquals(0, proofs)
        assertFalse(cleared)
    }
    @Test fun ownerChangedDuringFirstProofPreservesTicket() {
        val slot = Slot(committed())
        assertFalse(slot.reclaim { slot.current = false; true })
        assertEquals(0, slot.clears)
        assertEquals(1, slot.proofs)
    }
    @Test fun ownerChangedDuringSecondProofPreservesTicket() {
        val slot = Slot(committed())
        assertFalse(slot.reclaim {
            if (slot.proofs == 2) { slot.current = false }
            true
        })
        assertEquals(0, slot.clears)
        assertEquals(2, slot.proofs)
    }
    @Test fun replacementDuringEitherProofIsNeverCleared() {
        for (changedAt in 1..2) {
            val slot = Slot(committed())
            val replacement = slot.ticket!!.copy(id = "d".repeat(64))
            assertFalse(slot.reclaim {
                if (slot.proofs == changedAt) { slot.ticket = replacement }
                true
            })
            assertSame(replacement, slot.ticket)
            assertEquals(0, slot.clears)
        }
    }
    @Test fun anyFailedQuiescenceProofPreservesTicket() {
        for (failedAt in 1..2) {
            val slot = Slot(committed())
            val original = slot.ticket
            assertFalse(slot.reclaim { slot.proofs != failedAt })
            assertSame(original, slot.ticket)
            assertEquals(0, slot.clears)
        }
    }
    @Test fun failedDurableClearDoesNotClaimReclaimed() {
        val slot = Slot(committed()).apply { saveSucceeds = false }
        val original = slot.ticket
        assertFalse(slot.reclaim())
        assertEquals(2, slot.proofs)
        assertEquals(1, slot.clears)
        assertSame(original, slot.ticket)
    }
    @Test fun callbackFailuresPropagateWithoutClearingAuthority() {
        val slot = Slot(committed())
        val error = IllegalStateException("ownershipUnknown")
        assertSame(error, assertThrows(IllegalStateException::class.java) { slot.reclaim { throw error } })
        assertEquals(0, slot.clears)
    }
    @Test fun survivingObservedOrphanAndNonceChildBlockReclaimWithoutSignals() {
        val orphan = process(22, session = 21)
        val observed = committed().copy(observed = listOf(orphan))
        for ((ticket, nonceMatch) in listOf(observed to false, committed() to true)) {
            val slot = Slot(ticket)
            assertFalse(slot.reclaim { candidate ->
                NativeInstallerOwnership.drainPlan(candidate, rootfs, boot, listOf(orphan), emptySet(),
                    { true }, { _, _ -> nonceMatch }).server.isEmpty()
            })
            assertEquals(0, slot.clears)
        }
    }
    @Test fun unknownSameUidInventoryCannotBecomeQuiescence() {
        val slot = Slot(committed())
        assertThrows(IllegalArgumentException::class.java) {
            slot.reclaim { ticket ->
                NativeInstallerOwnership.drainPlan(ticket, rootfs, boot, listOf(process(99)), emptySet(),
                    { true }, { _, _ -> false }).server.isEmpty()
            }
        }
        assertEquals(0, slot.clears)
    }
    @Test fun reusedRootLeaderAndObservedPidNeverGrantQuiescence() {
        for (identity in listOf(process(20), process(21, 20), process(22))) {
            val ticket = committed().copy(observed = if (identity.pid == 22) listOf(identity) else emptyList())
            val slot = Slot(ticket)
            assertThrows(IllegalArgumentException::class.java) {
                slot.reclaim { candidate ->
                    NativeInstallerOwnership.drainPlan(candidate, rootfs, boot,
                        listOf(identity.copy(startTicks = 11)), emptySet(), { true }, { _, _ -> false })
                        .server.isEmpty()
                }
            }
            assertEquals(0, slot.clears)
        }
    }
    @Test fun missingProcWithoutExactEsrchRefusesInsteadOfDeletingTicket() {
        val slot = Slot(committed())
        assertThrows(IllegalArgumentException::class.java) {
            slot.reclaim { ticket ->
                NativeInstallerOwnership.drainPlan(ticket, rootfs, boot, emptyList(), emptySet(),
                    { false }, { _, _ -> false }).server.isEmpty()
            }
        }
        assertEquals(0, slot.clears)
    }
    @Test fun changedBootOrRootfsKeepsTicketForStrictColdRecovery() {
        for ((generation, currentBoot) in listOf("d".repeat(64) to boot, rootfs to boot.replace('1', '2'))) {
            val slot = Slot(committed())
            assertThrows(IllegalArgumentException::class.java) {
                slot.reclaim { ticket ->
                    NativeInstallerOwnership.drainPlan(ticket, generation, currentBoot, emptyList(), emptySet(),
                        { true }, { _, _ -> false }).server.isEmpty()
                }
            }
            assertEquals(0, slot.clears)
        }
    }
    @Test fun preparedTicketCannotClearUnknownFailedLaunchSurvivor() {
        val slot = Slot(prepared())
        assertThrows(IllegalArgumentException::class.java) {
            slot.reclaim { ticket ->
                NativeInstallerOwnership.drainPlan(ticket, rootfs, boot, listOf(process(99)), emptySet(),
                    { true }, { _, _ -> false }).server.isEmpty()
            }
        }
        assertEquals(0, slot.clears)
    }
}
