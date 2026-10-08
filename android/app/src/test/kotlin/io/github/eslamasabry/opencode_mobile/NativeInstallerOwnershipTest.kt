package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.*
import org.junit.Test

class NativeInstallerOwnershipTest {
    private val boot = "11111111-1111-1111-1111-111111111111"
    private val rootfs = "a".repeat(64)
    private val nonce = "b".repeat(64)
    private fun p(pid: Int, parent: Int = 1, session: Int = pid, ticks: Long = 10) =
        RuntimeProcessIdentity(pid, ticks, parent, session, session)
    private fun prepared() = InstallerTicket("c".repeat(64), rootfs, setOf(InstallerTarget.CLAUDE),
        InstallerOperation.INSTALL, NativeRuntimeReceipt(boot, nonce, 7, null, null, emptyList()))
    private fun ticket(observed: List<RuntimeProcessIdentity> = emptyList()) =
        NativeInstallerOwnership.committed(prepared(), p(20), p(21, 20)).copy(observed = observed)
    private fun plan(ticket: InstallerTicket = ticket(), inventory: List<RuntimeProcessIdentity> = emptyList(),
        readers: Set<Int> = emptySet(), gone: (Int) -> Boolean = { true }, matches: (Int, String) -> Boolean = { _, _ -> false }) =
        NativeInstallerOwnership.drainPlan(ticket, rootfs, boot, inventory, readers, gone, matches)
    private fun safeReject(action: () -> Unit) {
        val error = assertThrows(IllegalArgumentException::class.java) { action() }
        assertEquals("ownershipUnknown", error.message)
    }

    @Test fun strictTicketRoundTripsOnlyLifecycleMetadata() {
        val original = ticket(listOf(p(22, 21)))
        assertEquals(original, InstallerTicket.read(original.map()))
        assertEquals(prepared(), InstallerTicket.read(prepared().map()))
        assertEquals(1, original.map()["version"])
    }
    @Test fun scriptOutputCredentialsAndUnknownSchemaAreRejected() {
        for (key in listOf("script", "password", "output", "params")) {
            safeReject { InstallerTicket.read(ticket().map() + (key to "private-value")) }
        }
        for (version in listOf<Any>(0, 2, 1.0, "1", true)) {
            safeReject { InstallerTicket.read(ticket().map() + ("version" to version)) }
        }
        safeReject { InstallerTicket.read(ticket().map() - "observed") }
    }
    @Test fun identifiersRootfsAndBootRequireTheirExactFormats() {
        for (bad in listOf("", "A".repeat(64), "a".repeat(63), "../root")) {
            safeReject { prepared().copy(id = bad) }
            safeReject { prepared().copy(rootfsGeneration = bad) }
        }
        safeReject { prepared().copy(ownership = prepared().ownership.copy(boot = "-".repeat(36))) }
    }
    @Test fun typedTargetsAndOperationRejectUnknownEmptyAndDuplicateData() {
        for (targets in listOf(emptyList<String>(), listOf("CLAUDE", "CLAUDE"), listOf("custom"))) {
            safeReject { InstallerTicket.read(ticket().map() + ("targets" to targets)) }
        }
        safeReject { InstallerTicket.read(ticket().map() + ("operation" to "SCRIPT")) }
    }
    @Test fun observedIdentitiesAreBoundedUniqueAndNotPreparedOrOtherRuntime() {
        safeReject { ticket(List(129) { p(it + 100) }) }
        safeReject { ticket(listOf(p(22), p(22))) }
        safeReject { prepared().copy(observed = listOf(p(22))) }
        val other = p(40)
        safeReject { ticket().copy(ownership = ticket().ownership.copy(other = listOf(other)), observed = listOf(other)) }
        safeReject { ticket(listOf(p(20, ticks = 11))) }
    }
    @Test fun gateCommitRequiresPreparedReceiptAndStrictSessionLeader() {
        assertFalse(ticket().ownership.prepared)
        safeReject { NativeInstallerOwnership.committed(ticket(), p(20), p(21)) }
        safeReject { NativeInstallerOwnership.committed(prepared(), p(20), p(20)) }
        safeReject { NativeInstallerOwnership.committed(prepared(), p(20), p(21, session = 20)) }
    }
    @Test fun rootfsOrBootChangeRefusesBeforeNonceOrSignalSelection() {
        var inspected = false
        safeReject { NativeInstallerOwnership.drainPlan(ticket(), "d".repeat(64), boot,
            listOf(p(20), p(21, 20)), emptySet(), { false }) { _, _ -> inspected = true; true } }
        assertFalse(inspected)
        safeReject { NativeInstallerOwnership.drainPlan(ticket(), rootfs, boot.replace('1', '2'),
            listOf(p(20), p(21, 20)), emptySet(), { false }) { _, _ -> inspected = true; true } }
        assertFalse(inspected)
    }
    @Test fun exactObservedOrphanCanBeDrainedAfterReparentingAndNonceLoss() {
        val recorded = p(22, 21, session = 21)
        val reparented = recorded.copy(parent = 1)
        val selected = plan(ticket(listOf(recorded)), listOf(reparented, p(23, 22, session = 21)))
        assertEquals(setOf(22, 23), selected.server.map { it.pid }.toSet())
    }
    @Test fun reusedObservedPidRefusesEvenWhenMarkedAsAReader() {
        safeReject { plan(ticket(listOf(p(22, 21))), listOf(p(22, ticks = 11)), setOf(22)) }
    }
    @Test fun mismatchedRootOrLeaderRefusesBeforeDescendantSelection() {
        safeReject { plan(inventory = listOf(p(20, ticks = 11), p(21, 20))) }
        safeReject { plan(inventory = listOf(p(20), p(21, 20, ticks = 11))) }
    }
    @Test fun unknownInventoryAndPreparedSurvivorsCannotGrantQuiescence() {
        safeReject { plan(inventory = listOf(p(99))) }
        safeReject { plan(prepared(), listOf(p(20), p(21, 20))) }
        assertTrue(plan(prepared()).server.isEmpty())
    }
    @Test fun missingRecordedProcessMustBeIndependentlyConfirmedGone() {
        safeReject { plan(gone = { false }) }
        safeReject { plan(ticket(listOf(p(22))), readers = setOf(22), gone = { it != 22 }) }
        safeReject { plan(gone = { throw IllegalStateException("private-value") }) }
        val probes = mutableListOf<Int>()
        assertTrue(plan(ticket(listOf(p(20), p(22))), gone = { probes.add(it); true }).server.isEmpty())
        assertEquals(listOf(20, 21, 22), probes)
    }
    @Test fun visibleReceiptNeverNeedsAbsenceProbeAndReuseRefusesBeforeAnyProbe() {
        val probes = mutableListOf<Int>()
        assertEquals(2, plan(inventory = listOf(p(20), p(21, 20)), gone = { probes.add(it); false }).server.size)
        safeReject { plan(inventory = listOf(p(21, 20, ticks = 11)), gone = { probes.add(it); true }) }
        assertTrue(probes.isEmpty())
    }
    @Test fun completeCurrentTreeDrainsRootLast() {
        val selected = plan(inventory = listOf(p(20), p(21, 20), p(22, 21)))
        assertEquals(setOf(20, 21, 22), selected.server.map { it.pid }.toSet())
        assertEquals(20, selected.server.last().pid)
    }
    @Test fun nonceProvesAnOtherwiseUnobservedOrphanSession() {
        val selected = plan(inventory = listOf(p(22, session = 21))) { _, value -> value == nonce }
        assertEquals(22, selected.server.single().pid)
        safeReject { plan(inventory = listOf(p(22, session = 21))) }
    }
    @Test fun knownOtherRuntimeAndReadersAreExcludedFromSignals() {
        val other = p(40)
        val original = ticket().copy(ownership = ticket().ownership.copy(other = listOf(other)))
        val selected = plan(original, listOf(p(20), p(21, 20), other, p(41, 40), p(50)), setOf(50))
        assertEquals(setOf(40, 41), selected.other)
        assertEquals(setOf(20, 21), selected.server.map { it.pid }.toSet())
    }
    @Test fun readerInstallerCollisionRefusesInsteadOfKillingReader() {
        safeReject { plan(inventory = listOf(p(20), p(21, 20)), readers = setOf(21)) }
        safeReject { plan(ticket(listOf(p(22))), listOf(p(22)), setOf(22)) }
    }
    @Test fun completeInventoryIsRequiredEvenAfterOwnedParentAndChildrenExit() {
        assertTrue(plan().server.isEmpty())
        assertFalse(plan(ticket(listOf(p(22, 21))), listOf(p(22))).server.isEmpty())
        safeReject { plan(inventory = listOf(p(99))) }
    }
    @Test fun duplicateInventoryCannotHideReusedPid() {
        safeReject { plan(inventory = listOf(p(20), p(20, ticks = 11))) }
    }
    @Test fun newlyTrackedLiveServerAllowsCheckCompletionWithoutSignalingPeer() {
        val durable = ticket()
        val before = durable.map()
        val server = p(50)
        val child = p(51, 50, session = 50)
        val current = listOf(server, child)
        val live = NativeInstallerOwnership.withCurrentRuntimePeers(durable, listOf(server), current)
        val finished = plan(live, current)
        assertTrue(finished.server.isEmpty())
        assertEquals(setOf(50, 51), finished.other)
        assertEquals(before, durable.map())
        // The live exclusion is never promoted to authority for a later cold process.
        safeReject { plan(durable, current) }
    }
    @Test fun livePeerProofRequiresPresentExactKernelStartIdentity() {
        val peer = p(50)
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(ticket(), listOf(peer), listOf(peer.copy(startTicks = 11))) }
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(ticket(), listOf(peer), emptyList()) }
    }
    @Test fun peerDedupUsesCurrentKernelShapeWithoutTrustingStaleMetadata() {
        val peer = p(50, parent = 2)
        val kernel = peer.copy(parent = 1, group = 373, session = 0)
        val live = NativeInstallerOwnership.withCurrentRuntimePeers(ticket(), listOf(peer, peer), listOf(kernel))
        assertEquals(listOf(kernel), live.ownership.other)
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(ticket(), listOf(peer),
            listOf(peer.copy(session = 21))) }
    }
    @Test fun livePeerCannotReplacePreviouslyRecordedOtherPidWithANewProcess() {
        val old = p(50)
        val durable = ticket().copy(ownership = ticket().ownership.copy(other = listOf(old)))
        val reused = old.copy(startTicks = 11)
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(durable, listOf(reused), listOf(reused)) }
    }
    @Test fun livePeersCannotHideRootLeaderOrObservedInstallerProcesses() {
        for (peer in listOf(p(20), p(21, 20), p(22))) {
            safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(ticket(listOf(p(22))), listOf(peer), listOf(peer)) }
        }
    }
    @Test fun livePeersCannotHideUnobservedInstallerDescendants() {
        val child = p(23, 21, session = 23)
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(ticket(), listOf(child),
            listOf(p(20), p(21, 20), child)) }
    }
    @Test fun installerSessionOrphanCannotBecomeALivePeerWithoutNonceInspection() {
        val orphan = p(23, session = 21)
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(ticket(), listOf(orphan), listOf(orphan)) }
    }
    @Test fun reusedObservationRefusesBeforeAnyLivePeerAdmission() {
        val reused = p(22, ticks = 11)
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(ticket(listOf(p(22))), emptyList(), listOf(reused)) }
    }
    @Test fun boundedLivePeerProofRefusesOverflowDuplicateInventoryAndPreparedAdmission() {
        val peer = p(50)
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(ticket(), List(129) { peer }, listOf(peer)) }
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(ticket(), listOf(peer), listOf(peer, peer)) }
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(prepared(), listOf(peer), listOf(peer)) }
        val others = (100..227).map { p(it) }
        val durable = ticket().copy(ownership = ticket().ownership.copy(other = others))
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(durable, listOf(peer), listOf(peer)) }
    }
    @Test fun livePeerAdmissionDoesNotRelaxUnknownInventoryOrAndroidReaderCollision() {
        val peer = p(50)
        val unknown = p(99)
        val live = NativeInstallerOwnership.withCurrentRuntimePeers(ticket(), listOf(peer), listOf(peer, unknown))
        safeReject { plan(live, listOf(peer, unknown)) }
        val owned = p(20)
        val withOwned = NativeInstallerOwnership.withCurrentRuntimePeers(ticket(), listOf(peer), listOf(peer, owned))
        safeReject { plan(withOwned, listOf(peer, owned), setOf(20)) }
    }
    @Test fun pendingServerStopAdmitsFreshTrackedPeersOnlyInTheLivePlan() {
        val durable = ticket().ownership.copy(other = listOf(p(40)))
        val before = durable.map()
        val peer = p(50)
        val child = p(51, 50, session = 50)
        val inventory = listOf(p(20), p(21, 20), peer, child)
        val live = NativeInstallerOwnership.withCurrentRuntimePeers(durable, emptyList(), listOf(peer), inventory)
        val drain = NativeRuntimeOwnership.plan(live, boot, inventory, emptySet()) { _, _ -> false }
        assertEquals(setOf(20, 21), drain.server.map { it.pid }.toSet())
        assertEquals(setOf(50, 51), drain.other)
        val completed = NativeRuntimeOwnership.plan(live, boot, listOf(peer, child), emptySet()) { _, _ -> false }
        assertTrue(completed.server.isEmpty())
        assertEquals(before, durable.map())
        safeReject { NativeRuntimeOwnership.plan(durable, boot, inventory, emptySet()) { _, _ -> false } }
    }
    @Test fun receiptPeerCannotHideAPendingServerDescendantOrLeader() {
        val durable = ticket().ownership
        val child = p(23, 21, session = 23)
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(durable, emptyList(), listOf(child),
            listOf(p(20), p(21, 20), child)) }
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(durable, emptyList(), listOf(p(21, 20)),
            listOf(p(20), p(21, 20))) }
    }
    @Test fun receiptPeerRejectsReusedTrackedIdentityAndRecordedOtherCollision() {
        val peer = p(50)
        val reused = peer.copy(startTicks = 11)
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(ticket().ownership, emptyList(),
            listOf(peer), listOf(reused)) }
        val durable = ticket().ownership.copy(other = listOf(peer))
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(durable, emptyList(),
            listOf(reused), listOf(reused)) }
    }
    @Test fun receiptObservedProcessesCannotBecomePeerExemptions() {
        val observed = p(22)
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(ticket().ownership, listOf(observed),
            listOf(observed), listOf(observed)) }
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(ticket().ownership, listOf(observed),
            emptyList(), listOf(observed.copy(startTicks = 11))) }
    }
    @Test fun receiptOverloadRetainsObservedSchemaAndBoundGuards() {
        val durable = ticket().ownership
        val peer = p(50)
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(durable, List(129) { p(it + 100) },
            emptyList(), emptyList()) }
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(durable, listOf(peer, peer),
            emptyList(), emptyList()) }
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(durable.copy(other = listOf(peer)), listOf(peer),
            emptyList(), emptyList()) }
        safeReject { NativeInstallerOwnership.withCurrentRuntimePeers(prepared().ownership, listOf(peer),
            emptyList(), emptyList()) }
    }
    @Test fun componentMappingUsesShippedIdsAndConservativeUnknownCoverage() {
        assertEquals(setOf(InstallerTarget.OPENCODE1, InstallerTarget.OPENCODE2), NativeInstallerOwnership.targetsForComponent("opencode"))
        assertEquals(setOf(InstallerTarget.PASEO), NativeInstallerOwnership.targetsForComponent("agent-paseo"))
        assertEquals(setOf(InstallerTarget.CLAUDE), NativeInstallerOwnership.targetsForComponent("agent-claude"))
        assertEquals(InstallerTarget.entries.toSet(), NativeInstallerOwnership.targetsForComponent("node"))
        assertEquals(InstallerTarget.entries.toSet(), NativeInstallerOwnership.targetsForComponent("unknown"))
    }
}
