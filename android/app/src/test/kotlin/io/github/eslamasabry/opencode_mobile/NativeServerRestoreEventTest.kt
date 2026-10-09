package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeServerRestoreEventTest {
    private val boot = "11111111-1111-1111-1111-111111111111"
    private val nextBoot = "22222222-2222-2222-2222-222222222222"
    private val recipe = NativeServerRecipe("owner", "openCode2", 2202, "a".repeat(64))
    private fun process(pid: Int, ticks: Long = 10) = RuntimeProcessIdentity(pid, ticks, 1, pid, pid)
    private fun receipt() = NativeRuntimeReceipt(boot, "b".repeat(64), 1, process(20), process(21), emptyList())

    @Test fun onlyExactProtectedSystemActionsAreRecognized() {
        assertEquals(NativeServerRestoreEvent.BOOT_COMPLETED,
            NativeServerRestoreEvent.fromAction("android.intent.action.BOOT_COMPLETED"))
        assertEquals(NativeServerRestoreEvent.PACKAGE_REPLACED,
            NativeServerRestoreEvent.fromAction("android.intent.action.MY_PACKAGE_REPLACED"))
        for (action in listOf(null, "", "android.intent.action.PACKAGE_REPLACED",
            "android.intent.action.LOCKED_BOOT_COMPLETED", "android.intent.action.BOOT_COMPLETED ", "boot")) {
            assertNull(NativeServerRestoreEvent.fromAction(action))
        }
    }

    private fun admitted(
        native: Boolean = true, available: Boolean = true,
        idle: NativeIdleState.Snapshot = NativeIdleState.Snapshot(),
        owner: String? = "owner", restoreOwner: String? = "owner", attempts: Int = 0,
    ) = NativeServerEventPolicy.admitted(native, available, idle, owner, restoreOwner, "owner", attempts)

    @Test fun eventAdmissionRequiresNativeAuthorityDisabledIdleAndUnspentBudget() {
        assertTrue(admitted())
        assertFalse(admitted(native = false))
        assertFalse(admitted(available = false))
        assertFalse(admitted(idle = NativeIdleState.Snapshot(enabled = true)))
        for (owner in listOf(null, "other")) {
            assertFalse(admitted(owner = owner))
            assertFalse(admitted(restoreOwner = owner))
        }
        for (attempts in listOf(-1, 3, 4)) assertFalse(admitted(attempts = attempts))
        assertTrue(admitted(attempts = 2))
    }

    @Test fun completedHelperGateIsPreservedButOwedOrForeignHelperNeverAuthorizesBoot() {
        val completed = NativeIdleState.Snapshot(counter = 5, generation = 5, owner = "owner")
        assertTrue(admitted(idle = completed))
        assertEquals(5L, completed.generation)
        assertFalse(admitted(idle = completed.copy(stopped = true)))
        assertFalse(admitted(idle = completed.copy(helperStopped = true, helper = "helper")))
        assertFalse(admitted(idle = completed.copy(owner = "previous")))
    }

    @Test fun bootRequiresExactRecipeAndPackageUpdatePromotesOnlyIncreasingVersion() {
        val bootEvent = NativeServerRestoreEvent.BOOT_COMPLETED
        val update = NativeServerRestoreEvent.PACKAGE_REPLACED
        assertEquals(recipe, NativeServerEventPolicy.recipeForEvent(bootEvent, recipe, 2202, recipe.rootfsGeneration))
        assertNull(NativeServerEventPolicy.recipeForEvent(bootEvent, recipe, 2203, recipe.rootfsGeneration))
        assertEquals(recipe, NativeServerEventPolicy.recipeForEvent(update, recipe, 2202, recipe.rootfsGeneration))
        val promoted = NativeServerEventPolicy.recipeForEvent(update, recipe, 2203, recipe.rootfsGeneration)
        assertNotNull(promoted)
        assertEquals(recipe.copy(packageVersion = 2203), promoted)
        assertFalse(recipe.compatible(2203, recipe.rootfsGeneration))
        for (version in listOf(-1L, 0L, 2201L)) {
            assertNull(NativeServerEventPolicy.recipeForEvent(update, recipe, version, recipe.rootfsGeneration))
        }
        for (event in NativeServerRestoreEvent.entries) {
            assertNull(NativeServerEventPolicy.recipeForEvent(event, recipe, 2203, "c".repeat(64)))
        }
    }

    @Test fun changedBootProvesOnlyAbsenceAndNeverAdoptsReusedOldPids() {
        val old = receipt()
        // A new Android process can reuse an old server PID; it is only a registered app process now.
        assertTrue(NativeServerEventPolicy.crossBootQuiescent(old, nextBoot,
            listOf(process(20, ticks = 900)), setOf(20), 20))
        assertEquals(boot, old.boot)
        assertEquals(10L, old.root!!.startTicks)
        assertFalse(NativeServerEventPolicy.crossBootQuiescent(old, boot, listOf(process(40)), setOf(40), 40))
        assertFalse(NativeServerEventPolicy.crossBootQuiescent(old.copy(root = null, leader = null), nextBoot,
            listOf(process(40)), setOf(40), 40))
        assertFalse(NativeServerEventPolicy.crossBootQuiescent(old, nextBoot,
            listOf(process(40), process(41)), setOf(40), 40))
        assertFalse(NativeServerEventPolicy.crossBootQuiescent(old, nextBoot, null, setOf(40), 40))
        assertFalse(NativeServerEventPolicy.crossBootQuiescent(old, nextBoot, emptyList(), setOf(40), 40))
        assertFalse(NativeServerEventPolicy.crossBootQuiescent(old, nextBoot, listOf(process(40)), emptySet(), 40))
    }

    @Test fun dispatchTicketCannotBeCopiedOrAdoptANewerOwnerIdlePolicyOrRuntime() {
        fun newTicket() = NativeServerRestoreTicket(NativeServerRestoreEvent.PACKAGE_REPLACED,
            recipe, recipe, receipt(), 4, 5, 6, boot, 7, NativeRecoveryBudget(attempts = 1))
        val ticket = newTicket()
        fun current(
            active: NativeServerRestoreTicket? = ticket, revision: Long = 4,
            generation: Long = 5, schedule: Long = 6, idleCounter: Long = 7,
            currentBoot: String = boot, version: Long = 2202, rootfs: String = recipe.rootfsGeneration,
        ) = NativeServerEventPolicy.ticketCurrent(ticket, active, revision, generation, schedule,
            idleCounter, currentBoot, version, rootfs)
        assertTrue(current())
        assertFalse(current(active = null))
        assertFalse(current(active = newTicket()))
        assertFalse(current(revision = 5))
        assertFalse(current(generation = 6))
        assertFalse(current(schedule = 7))
        // Enabling and then disabling idle still revokes the originally prepared dispatch.
        assertFalse(current(idleCounter = 9))
        assertFalse(current(currentBoot = nextBoot))
        assertFalse(current(version = 2203))
        assertFalse(current(rootfs = "d".repeat(64)))
        assertEquals(NativeRecoveryBudget(attempts = 1), ticket.budget)
    }

    @Test fun nullIntentCleanupCanOnlyJoinTheSameServerOnlyReceipt() {
        val expected = receipt()
        fun allowed(owner: String? = "owner", includesOther: Boolean = false,
            pending: NativeRuntimeReceipt? = expected) = NativeServerEventPolicy.pendingDrainCompatible(
                owner, "owner", includesOther, pending, expected)
        assertTrue(allowed(owner = null, pending = null))
        assertTrue(allowed())
        assertFalse(allowed(owner = "other"))
        assertFalse(allowed(includesOther = true))
        assertFalse(allowed(pending = null))
        assertFalse(allowed(pending = expected.copy(generation = 2)))
        assertFalse(allowed(pending = expected.copy(nonce = "c".repeat(64))))
        assertFalse(allowed(pending = expected.copy(boot = nextBoot)))
        assertFalse(allowed(pending = expected.copy(root = process(20, ticks = 11))))
        assertFalse(allowed(pending = expected.copy(root = null, leader = null)))
        assertFalse(allowed(pending = expected.copy(other = listOf(process(30)))))
    }
}
