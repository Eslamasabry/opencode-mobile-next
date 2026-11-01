package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStatusPolicyTest {
    private val ticket = mapOf<String, Any?>("profileID" to "p", "scopeToken" to "scope",
        "sessionID" to "s", "fingerprint" to "fingerprint", "runMarker" to "user-1", "startedAt" to 1L)
    private val current = ticket + mapOf("busy" to true, "requestID" to "")

    @Test fun scheduledOffsetsAreThirtySecondsTwoMinutesAndTenMinutes() {
        var elapsed = 0L
        assertEquals(listOf(30_000L, 120_000L, 600_000L), WidgetStatusPolicy.delaysMs.map { elapsed += it; elapsed })
        assertEquals(15_000L, WidgetStatusPolicy.READ_BUDGET_MS)
    }
    @Test fun idleStaleAndUnknownResultsStopImmediately() {
        for (status in listOf("idle", "stale", "accepted", null)) {
            for (attempt in 0..2) assertFalse(WidgetStatusPolicy.continues(status, attempt))
        }
    }
    @Test fun busyAndFailedReadsHaveOnlyThreeAttempts() {
        for (status in listOf("busy", "unavailable")) {
            assertTrue(WidgetStatusPolicy.continues(status, 0))
            assertTrue(WidgetStatusPolicy.continues(status, 1))
            assertFalse(WidgetStatusPolicy.continues(status, 2))
            assertFalse(WidgetStatusPolicy.continues(status, -1))
            assertFalse(WidgetStatusPolicy.continues(status, 3))
        }
    }
    @Test fun queuedOrFailedIdlePatchRetainsLaterChecks() {
        // An idle response alone cannot remove the ticket while the main-thread
        // snapshot commit has not completed, including a timed-out callback.
        for ((completed, patched) in listOf(false to false, false to true, true to false)) {
            val outcome = WidgetStatusPolicy.settledStatus("idle", completed, patched)
            assertEquals("unavailable", outcome)
            assertTrue(WidgetStatusPolicy.continues(outcome, 0))
            assertTrue(WidgetStatusPolicy.continues(outcome, 1))
            assertFalse(WidgetStatusPolicy.continues(outcome, 2))
        }
    }
    @Test fun persistedIdlePatchStopsTheChainImmediately() {
        val outcome = WidgetStatusPolicy.settledStatus("idle", true, true)
        assertEquals("idle", outcome)
        for (attempt in 0..2) assertFalse(WidgetStatusPolicy.continues(outcome, attempt))
    }
    @Test fun osRestartSkipsItsReadWithoutDiscardingLaterCheckpoints() {
        assertTrue(WidgetStatusPolicy.mayRead(0))
        for (restart in listOf(1, 2, 10)) {
            assertFalse(WidgetStatusPolicy.mayRead(restart))
            assertTrue(WidgetStatusPolicy.continues("unavailable", 0))
            assertTrue(WidgetStatusPolicy.continues("unavailable", 1))
            assertFalse(WidgetStatusPolicy.continues("unavailable", 2))
        }
    }
    @Test fun completedBusyAndStaleKeepTheirDistinctContinuationRules() {
        assertEquals("busy", WidgetStatusPolicy.settledStatus("busy", true, false))
        assertTrue(WidgetStatusPolicy.continues(WidgetStatusPolicy.settledStatus("busy", true, false), 0))
        assertEquals("stale", WidgetStatusPolicy.settledStatus("stale", true, false))
        assertFalse(WidgetStatusPolicy.continues(WidgetStatusPolicy.settledStatus("stale", true, false), 0))
    }
    @Test fun unchangedBusyTurnCanBeReadWithoutAnActionButton() {
        assertTrue(WidgetStatusPolicy.matches(ticket, current + ("onDemandActions" to emptyList<String>())))
    }
    @Test fun profileScopeEndpointAndNewTurnInvalidateOldWork() {
        for (field in listOf("profileID", "scopeToken", "sessionID", "fingerprint", "runMarker", "startedAt")) {
            assertFalse(field, WidgetStatusPolicy.matches(ticket, current + (field to "new")))
        }
    }
    @Test fun newPermissionIdleAndMissingRunIdentityCannotBePatched() {
        assertFalse(WidgetStatusPolicy.matches(ticket, current + ("requestID" to "new-permission")))
        assertFalse(WidgetStatusPolicy.matches(ticket, current + ("busy" to false)))
        assertFalse(WidgetStatusPolicy.matches(ticket - "runMarker", current - "runMarker"))
    }
}
