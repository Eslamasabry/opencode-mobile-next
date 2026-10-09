package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeIdleReturnNotificationTest {
    private class Fixture {
        var shows = 0
        var removals = 0
        var succeeds = true
        var showFails = false
        var removeFails = false
        val policy = NativeIdleReturnNotification({
            shows++
            if (showFails) throw AssertionError("synthetic callback failure")
            succeeds
        }, {
            removals++
            if (removeFails) throw AssertionError("synthetic callback failure")
        })
    }

    private fun stopped() = NativeIdleReturnNotification.State(
        available = true, owner = "server_owner-1", generation = 7, idleStopped = true,
        wanted = true, userStopped = false, foreground = false, serverRunning = false)

    @Test fun backgroundIdleStopShowsOneReturnNotification() {
        val f = Fixture()
        assertTrue(f.policy.update(stopped()))
        assertEquals(1, f.shows)
        assertEquals(0, f.removals)
    }

    @Test fun repeatedSameOwnerGenerationDoesNotShowAgain() {
        val f = Fixture()
        repeat(3) { assertTrue(f.policy.update(stopped())) }
        assertEquals(1, f.shows)
    }

    @Test fun newGenerationShowsAgainForSameOwner() {
        val f = Fixture()
        assertTrue(f.policy.update(stopped()))
        assertTrue(f.policy.update(stopped().copy(generation = 8)))
        assertEquals(2, f.shows)
        assertTrue(f.policy.update(stopped().copy(generation = 8)))
        assertEquals(2, f.shows)
    }

    @Test fun newOwnerDoesNotReuseAnotherOwnersNotificationKey() {
        val f = Fixture()
        assertTrue(f.policy.update(stopped()))
        assertTrue(f.policy.update(stopped().copy(owner = "other")))
        assertEquals(2, f.shows)
    }

    @Test fun foregroundReturnRemovesNotificationAndAllowsLaterIdleShow() {
        val f = Fixture()
        assertTrue(f.policy.update(stopped()))
        assertFalse(f.policy.update(stopped().copy(foreground = true)))
        assertEquals(1, f.removals)
        assertTrue(f.policy.update(stopped()))
        assertEquals(2, f.shows)
    }

    @Test fun unavailableReceiptClearsEvenBeforeAnyShow() {
        val f = Fixture()
        assertFalse(f.policy.update(stopped().copy(available = false)))
        assertEquals(1, f.removals)
        assertEquals(0, f.shows)
    }

    @Test fun explicitStopAndLostWantedIntentEachClearNotification() {
        val f = Fixture()
        assertTrue(f.policy.update(stopped()))
        assertFalse(f.policy.update(stopped().copy(userStopped = true)))
        assertFalse(f.policy.update(stopped().copy(wanted = false)))
        assertEquals(2, f.removals)
        assertEquals(1, f.shows)
    }

    @Test fun runningServerOrConsumedIdleStopCannotShowReturnNotice() {
        val f = Fixture()
        assertFalse(f.policy.update(stopped().copy(serverRunning = true)))
        assertFalse(f.policy.update(stopped().copy(idleStopped = false)))
        assertEquals(2, f.removals)
        assertEquals(0, f.shows)
    }

    @Test fun invalidOrAbsentOwnerCannotShowNotification() {
        val f = Fixture()
        val invalid = listOf(null, "", "a".repeat(81), "owner/path", "owner.name", "owner ", "ع", "owner\n")
        for (owner in invalid) assertFalse(f.policy.update(stopped().copy(owner = owner)))
        assertEquals(0, f.shows)
        assertEquals(invalid.size, f.removals)
        assertTrue(f.policy.update(stopped().copy(owner = "a".repeat(80))))
    }

    @Test fun nonpositiveIdleGenerationCannotShowNotification() {
        val f = Fixture()
        for (generation in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertFalse(f.policy.update(stopped().copy(generation = generation)))
        }
        assertEquals(0, f.shows)
        assertEquals(3, f.removals)
        assertTrue(f.policy.update(stopped().copy(generation = Long.MAX_VALUE)))
    }

    @Test fun stalePlatformNotificationIsRemovedWithoutAnInMemoryKey() {
        val f = Fixture()
        f.policy.clear()
        f.policy.clear()
        assertFalse(f.policy.update(stopped().copy(foreground = true)))
        assertEquals(3, f.removals)
        assertEquals(0, f.shows)
    }

    @Test fun refusedShowRetriesAndThenDeduplicatesAfterSuccess() {
        val f = Fixture()
        f.succeeds = false
        assertFalse(f.policy.update(stopped()))
        assertFalse(f.policy.update(stopped()))
        assertEquals(2, f.shows)
        f.succeeds = true
        assertTrue(f.policy.update(stopped()))
        assertTrue(f.policy.update(stopped()))
        assertEquals(3, f.shows)
    }

    @Test fun throwingShowOrGenerationReplacementDoesNotPoisonRetry() {
        val f = Fixture()
        assertTrue(f.policy.update(stopped()))
        f.showFails = true
        assertFalse(f.policy.update(stopped().copy(generation = 8)))
        assertFalse(f.policy.update(stopped().copy(generation = 8)))
        f.showFails = false
        assertTrue(f.policy.update(stopped().copy(generation = 8)))
        assertEquals(4, f.shows)
    }

    @Test fun throwingRemoveStillClearsKeyAndPermitsSameGenerationShow() {
        val f = Fixture()
        assertTrue(f.policy.update(stopped()))
        f.removeFails = true
        assertFalse(f.policy.update(stopped().copy(foreground = true)))
        f.policy.clear()
        assertEquals(2, f.removals)
        assertTrue(f.policy.update(stopped()))
        assertEquals(2, f.shows)
    }
}
