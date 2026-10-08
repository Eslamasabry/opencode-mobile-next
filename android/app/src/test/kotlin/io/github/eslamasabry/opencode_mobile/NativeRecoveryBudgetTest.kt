package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NativeRecoveryBudgetTest {
    @Test fun nativeLaunchDoesNotNotifyAnAlreadyEmptySetAfterFinalReservation() {
        assertFalse(NativeRecoveryBudget.shouldRemoveBeforeLaunch(nativeOwned = true, tracked = false))
        assertTrue(NativeRecoveryBudget.shouldRemoveBeforeLaunch(nativeOwned = true, tracked = true))
        assertTrue(NativeRecoveryBudget.shouldRemoveBeforeLaunch(nativeOwned = false, tracked = false))
        assertTrue(NativeRecoveryBudget.shouldRemoveBeforeLaunch(nativeOwned = false, tracked = true))
    }

    @Test
    fun readingAndSavingAnUnusedBudgetDoesNotSpendAnAttempt() {
        val budget = NativeRecoveryBudget.read(legacyBudget())

        assertEquals(0, budget.attempts)
        assertFalse(budget.pending)
        assertEquals(0L, budget.revision)
        assertEquals(budget, NativeRecoveryBudget.read(budget.map()))
    }

    @Test
    fun threeReservationsAreTheLifetimeLimitEvenAfterReceiptsFinish() {
        var budget = NativeRecoveryBudget()
        repeat(3) { index ->
            budget = budget.reserve(at = 100L + index, generation = 7L, id = "event-$index")
            assertEquals(index + 1, budget.attempts)
            assertEquals((index + 1).toLong(), budget.revision)
            assertTrue(budget.pending)
            assertEquals("event-$index", budget.eventId)
            assertEquals(7L, budget.recoveryGeneration)
            rejects { budget.reserve(at = 200L, generation = 7L, id = "duplicate") }
            budget = NativeRecoveryBudget.read(budget.copy(
                pending = false, eventId = null, recoveryGeneration = null,
            ).map())
        }

        rejects { budget.reserve(at = 300L, generation = 8L, id = "fourth") }
        assertEquals(3, budget.attempts)
    }

    @Test
    fun migrationRetainsSpentAttemptsAndScheduledRetryWithoutSpendingAgain() {
        val migrated = NativeRecoveryBudget.read(legacyBudget(
            "attempts" to 2,
            "nextAt" to 12_345L,
        ))

        assertEquals(2, migrated.attempts)
        assertEquals(12_345L, migrated.nextAt)
        assertFalse(migrated.pending)
        assertEquals(migrated, NativeRecoveryBudget.read(migrated.map()))
        val reserved = migrated.reserve(at = 12_345L, generation = 4L, id = "last")
        assertEquals(3, reserved.attempts)
        assertNull(reserved.nextAt)
        assertNull(reserved.confirmedAt)
    }

    @Test
    fun migratedPendingReceiptCannotReserveAgain() {
        val migrated = NativeRecoveryBudget.read(legacyBudget(
            "attempts" to 2,
            "pending" to true,
            "eventId" to "existing-receipt",
            "recoveryGeneration" to 11L,
        ))

        assertEquals(2, migrated.attempts)
        assertTrue(migrated.pending)
        assertEquals("existing-receipt", migrated.eventId)
        assertEquals(11L, migrated.recoveryGeneration)
        assertEquals(migrated, NativeRecoveryBudget.read(migrated.map()))
        rejects { migrated.reserve(at = 100L, generation = 11L, id = "duplicate") }
    }

    @Test
    fun migratedConfirmedReceiptRetainsIdentityTimeAndSpentAttempts() {
        val migrated = NativeRecoveryBudget.read(legacyBudget(
            "attempts" to 3,
            "pending" to true,
            "eventId" to "confirmed-receipt",
            "recoveryGeneration" to 12L,
            "confirmedAt" to 98_765L,
        ))

        assertEquals(3, migrated.attempts)
        assertTrue(migrated.pending)
        assertEquals("confirmed-receipt", migrated.eventId)
        assertEquals(12L, migrated.recoveryGeneration)
        assertEquals(98_765L, migrated.confirmedAt)
        assertEquals(migrated, NativeRecoveryBudget.read(migrated.map()))
        rejects { migrated.reserve(at = 100_000L, generation = 12L, id = "duplicate") }
    }

    @Test
    fun nativeRevisionSurvivesPersistenceAndAdvancesOnReservation() {
        val restored = NativeRecoveryBudget.read(legacyBudget("revision" to 6L))
        val reserved = restored.reserve(at = 100L, generation = 3L, id = "receipt")

        assertEquals(7L, reserved.revision)
        assertEquals(reserved, NativeRecoveryBudget.read(reserved.map()))
    }

    @Test
    fun missingRequiredFieldsAndUnsupportedSchemaFailClosed() {
        for (key in listOf("version", "attempts", "pending")) {
            rejects(key) { NativeRecoveryBudget.read(legacyBudget().toMutableMap().apply { remove(key) }) }
        }
        for (version in listOf<Any?>(null, 0, 2, "1", 1.0, true)) {
            rejects("version=$version") { NativeRecoveryBudget.read(legacyBudget("version" to version)) }
        }
        for (attempts in listOf<Any?>(null, -1, 4, Long.MAX_VALUE, "2", 2.0, true)) {
            rejects("attempts=$attempts") { NativeRecoveryBudget.read(legacyBudget("attempts" to attempts)) }
        }
        for (pending in listOf<Any?>(null, "false", 0)) {
            rejects("pending=$pending") { NativeRecoveryBudget.read(legacyBudget("pending" to pending)) }
        }
    }

    @Test
    fun corruptOptionalFieldsAndIncompleteReceiptsFailClosed() {
        for (key in listOf("nextAt", "recoveryGeneration", "confirmedAt", "revision")) {
            for (value in listOf<Any>("1", 1.0, true)) {
                rejects("$key=$value") { NativeRecoveryBudget.read(legacyBudget(key to value)) }
            }
        }
        rejects { NativeRecoveryBudget.read(legacyBudget("revision" to -1L)) }
        rejects { NativeRecoveryBudget.read(legacyBudget("eventId" to 123)) }
        rejects { NativeRecoveryBudget.read(legacyBudget("confirmedAt" to 123L)) }
        for (event in listOf<Any?>(null, "", 123)) {
            rejects("eventId=$event") {
                NativeRecoveryBudget.read(legacyBudget("pending" to true,
                    "eventId" to event, "recoveryGeneration" to 1L))
            }
        }
        rejects {
            NativeRecoveryBudget.read(legacyBudget("pending" to true, "eventId" to "receipt"))
        }
    }

    @Test
    fun everyRecognizedSupervisionRequiresBothRecoveryBehaviors() {
        for (supervision in listOf("high", "balanced", "autonomous")) {
            assertTrue(NativeRecoveryBudget.policyAllows(policy(supervision = supervision)))
            assertFalse(NativeRecoveryBudget.policyAllows(policy(supervision = supervision,
                behaviors = mapOf("restartPhoneServer" to false, "pollRestartHealth" to true))))
            assertFalse(NativeRecoveryBudget.policyAllows(policy(supervision = supervision,
                behaviors = mapOf("restartPhoneServer" to true, "pollRestartHealth" to false))))
        }
    }

    @Test
    fun missingCorruptAndUnknownPolicyNeverGrantsRecovery() {
        assertFalse(NativeRecoveryBudget.policyAllows(null))
        assertFalse(NativeRecoveryBudget.policyAllows(emptyMap<Any, Any>()))
        for (supervision in listOf<Any?>(null, "future", "HIGH", 1)) {
            assertFalse(NativeRecoveryBudget.policyAllows(policy(supervision = supervision)))
        }
        for (version in listOf<Any?>(null, 0, 2, "1", 1.0, true)) {
            assertFalse(NativeRecoveryBudget.policyAllows(policy(version = version)))
        }
        for (behaviors in listOf<Any?>(null, true, "enabled", emptyMap<String, Boolean>(),
            mapOf("restartPhoneServer" to true), mapOf("pollRestartHealth" to true),
            mapOf("restartPhoneServer" to "true", "pollRestartHealth" to true),
            mapOf("restartPhoneServer" to true, "pollRestartHealth" to 1))) {
            assertFalse(NativeRecoveryBudget.policyAllows(policy(behaviors = behaviors)))
        }
    }

    @Test
    fun migrationRequiresAnExplicitVersionTwoNativeAuthorityMarker() {
        assertTrue(NativeRecoveryBudget.migrationAllows(
            mapOf("version" to 2, "nativeAuthority" to true),
        ))
        assertFalse(NativeRecoveryBudget.migrationAllows(null))
        assertFalse(NativeRecoveryBudget.migrationAllows(emptyMap<Any, Any>()))
        assertFalse(NativeRecoveryBudget.migrationAllows(mapOf("version" to 2)))
        assertFalse(NativeRecoveryBudget.migrationAllows(mapOf("nativeAuthority" to true)))
        for (version in listOf<Any?>(null, 0, 1, 3, 2L, 2.0, 2.0f, "2", true)) {
            assertFalse("version=$version", NativeRecoveryBudget.migrationAllows(
                mapOf("version" to version, "nativeAuthority" to true),
            ))
        }
        for (authority in listOf<Any?>(null, false, "true", 1)) {
            assertFalse("nativeAuthority=$authority", NativeRecoveryBudget.migrationAllows(
                mapOf("version" to 2, "nativeAuthority" to authority),
            ))
        }
    }

    @Test
    fun admissionRequiresMatchingGenerationAndEveryCurrentPermission() {
        assertTrue(AdmissionCase().allowed())
        assertFalse("disabled binding", AdmissionCase(enabled = false).allowed())
        assertFalse("server no longer wanted", AdmissionCase(wanted = false).allowed())
        assertFalse("user stopped server", AdmissionCase(userStopped = true).allowed())
        assertFalse("stale generation", AdmissionCase(generation = 6L).allowed())
        assertFalse("expected generation changed", AdmissionCase(expectedGeneration = 8L).allowed())
        assertFalse("invalid migration marker", AdmissionCase(markerValid = false).allowed())
        assertFalse("policy revoked", AdmissionCase(policyAllowed = false).allowed())
    }

    @Test
    fun runningServerAlwaysKeepsForegroundEvenWithoutRecoveryAdmission() {
        assertTrue(NativeRecoveryBudget.keepsForeground(
            running = true, scheduled = false, admitted = false, attempts = 3,
        ))
    }

    @Test
    fun stoppedServerKeepsForegroundOnlyForAnAdmittedScheduledUnspentRetry() {
        for (attempts in 0..2) {
            assertTrue(NativeRecoveryBudget.keepsForeground(
                running = false, scheduled = true, admitted = true, attempts = attempts,
            ))
        }
        for (attempts in listOf(-1, 3, 4)) {
            assertFalse(NativeRecoveryBudget.keepsForeground(
                running = false, scheduled = true, admitted = true, attempts = attempts,
            ))
        }
        assertFalse(NativeRecoveryBudget.keepsForeground(
            running = false, scheduled = true, admitted = false, attempts = 0,
        ))
        assertFalse(NativeRecoveryBudget.keepsForeground(
            running = false, scheduled = false, admitted = true, attempts = 0,
        ))
    }

    @Test
    fun manualLiveProofForTheBoundProfileCanResetWithoutAutomationPermission() {
        assertTrue(ManualResetCase().allowed())
    }

    @Test
    fun manualResetRejectsUnboundStaleStoppedPausedOrNativeOwnedProof() {
        assertFalse("wrong requested profile", ManualResetCase(requestedProfile = "other").allowed())
        assertFalse("wrong bound profile", ManualResetCase(boundProfile = "other").allowed())
        assertFalse("missing bound profile", ManualResetCase(boundProfile = null).allowed())
        assertFalse("missing manual generation", ManualResetCase(manualGeneration = null).allowed())
        assertFalse("stale manual generation", ManualResetCase(manualGeneration = 6L).allowed())
        assertFalse("changed current generation", ManualResetCase(currentGeneration = 8L).allowed())
        assertFalse("paused profile", ManualResetCase(resumed = false).allowed())
        assertFalse("server no longer wanted", ManualResetCase(wanted = false).allowed())
        assertFalse("user stopped server", ManualResetCase(userStopped = true).allowed())
        assertFalse("native recovery owns launch", ManualResetCase(nativeOwned = true).allowed())
        assertFalse("server is not running", ManualResetCase(running = false).allowed())
    }

    @Test
    fun archiveStoresConfirmedReceiptsAndLeavesUnconfirmedAttemptsOut() {
        val pending = NativeRecoveryBudget().reserve(at = 100L, generation = 1L, id = "receipt")
        assertEquals(emptyList<NativeRecoveryBudget>(), NativeRecoveryBudget.archiveConfirmed(
            emptyList(), pending,
        ))
        val confirmed = pending.copy(confirmedAt = 200L)
        val archived = NativeRecoveryBudget.archiveConfirmed(emptyList(), confirmed)
        assertEquals(listOf(confirmed), archived)
        assertEquals(archived, NativeRecoveryBudget.archiveConfirmed(archived, NativeRecoveryBudget()))
    }

    @Test
    fun repeatedManualCyclesRetainEveryConfirmedReceiptAndRefuseOverflow() {
        var archive = emptyList<NativeRecoveryBudget>()
        val receipts = mutableListOf<NativeRecoveryBudget>()
        repeat(16) { cycle ->
            val receipt = NativeRecoveryBudget().reserve(
                at = cycle.toLong(), generation = cycle.toLong(), id = "cycle-$cycle",
            ).copy(confirmedAt = 1_000L + cycle)
            receipts += receipt
            archive = NativeRecoveryBudget.archiveConfirmed(archive, receipt)
                .map { NativeRecoveryBudget.read(it.map()) }
            assertEquals(receipts, archive)
        }
        val overflow = NativeRecoveryBudget().reserve(
            at = 17L, generation = 17L, id = "cycle-16",
        ).copy(confirmedAt = 2_000L)

        rejects("seventeenth confirmed receipt") {
            NativeRecoveryBudget.archiveConfirmed(archive, overflow)
        }
        assertEquals(receipts, archive)
        assertEquals("cycle-0", archive.first().eventId)
        assertEquals("cycle-15", archive.last().eventId)
    }

    @Test
    fun duplicateEventIsIdempotentEvenAtCapacityAndPreservesOriginalReceipt() {
        val archive = List(16) { index ->
            NativeRecoveryBudget(attempts = 1, pending = true, eventId = "receipt-$index",
                recoveryGeneration = index.toLong(), confirmedAt = 100L + index)
        }
        val duplicate = archive[5].copy(confirmedAt = 9_999L, revision = 8L)

        assertEquals(archive, NativeRecoveryBudget.archiveConfirmed(archive, duplicate))
        assertEquals(105L, NativeRecoveryBudget.archiveConfirmed(archive, duplicate)[5].confirmedAt)
    }

    @Test
    fun overCapacityStoredArchiveFailsClosedBeforeIgnoringDuplicatesOrPendingReceipts() {
        val corruptArchive = List(17) { index ->
            NativeRecoveryBudget(attempts = 1, pending = true, eventId = "receipt-$index",
                recoveryGeneration = index.toLong(), confirmedAt = 100L + index)
        }

        rejects("corrupt archive with duplicate") {
            NativeRecoveryBudget.archiveConfirmed(corruptArchive, corruptArchive.first())
        }
        rejects("corrupt archive with unconfirmed attempt") {
            NativeRecoveryBudget.archiveConfirmed(corruptArchive, NativeRecoveryBudget())
        }
    }

    private data class ManualResetCase(
        val resumed: Boolean = true,
        val wanted: Boolean = true,
        val userStopped: Boolean = false,
        val boundProfile: String? = "profile",
        val requestedProfile: String = "profile",
        val manualGeneration: Long? = 7L,
        val currentGeneration: Long = 7L,
        val running: Boolean = true,
        val nativeOwned: Boolean = false,
    ) {
        fun allowed(): Boolean = NativeRecoveryBudget.manualResetAllowed(
            resumed = resumed,
            wanted = wanted,
            userStopped = userStopped,
            boundProfile = boundProfile,
            requestedProfile = requestedProfile,
            manualGeneration = manualGeneration,
            currentGeneration = currentGeneration,
            running = running,
            nativeOwned = nativeOwned,
        )
    }

    private data class AdmissionCase(
        val enabled: Boolean = true,
        val wanted: Boolean = true,
        val userStopped: Boolean = false,
        val generation: Long = 7L,
        val expectedGeneration: Long = 7L,
        val markerValid: Boolean = true,
        val policyAllowed: Boolean = true,
    ) {
        fun allowed(): Boolean = NativeRecoveryBudget.admitted(
            enabled = enabled,
            wanted = wanted,
            userStopped = userStopped,
            generation = generation,
            expectedGeneration = expectedGeneration,
            markerValid = markerValid,
            policyAllowed = policyAllowed,
        )
    }

    private fun legacyBudget(vararg changes: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "version" to 1, "attempts" to 0, "nextAt" to null, "pending" to false,
        "eventId" to null, "recoveryGeneration" to null, "confirmedAt" to null,
    ) + changes

    private fun policy(
        version: Any? = 1,
        supervision: Any? = "high",
        behaviors: Any? = mapOf("restartPhoneServer" to true, "pollRestartHealth" to true),
    ): Map<String, Any?> = mapOf(
        "version" to version, "supervision" to supervision, "behaviors" to behaviors,
    )

    private fun rejects(label: String = "corrupt budget", action: () -> Unit) {
        try {
            action()
        } catch (_: IllegalStateException) {
            return
        }
        fail("Accepted $label")
    }
}
