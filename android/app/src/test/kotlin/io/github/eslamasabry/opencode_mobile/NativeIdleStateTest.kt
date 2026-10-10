package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeIdleStateTest {
    private class Fixture(initial: NativeIdleState.Snapshot? = NativeIdleState.Snapshot(enabled = true)) {
        val writes = mutableListOf<NativeIdleState.Snapshot>()
        var reject = false
        var throws = false
        val state = NativeIdleState(initial) { value ->
            writes.add(value)
            if (throws) error("synthetic private persistence failure")
            !reject
        }
    }

    @Test fun serverCrashBeforeHelperCompletionRestoresOwedServerMarkerWithoutNewToken() {
        val f = Fixture()
        val token = f.state.beginStop("server", "helper", true)!!
        assertTrue(f.state.markServerResumed("server", token))
        assertFalse(f.state.snapshot().stopped)
        assertTrue(f.state.markServerLost("server", token))
        assertTrue(f.state.snapshot().stopped)
        assertTrue(f.state.snapshot().helperStopped)
        assertEquals(token, f.state.snapshot().generation)
        assertTrue(f.state.resumeAllowed("server", token))
        assertFalse(f.state.complete("server", token, true))
    }

    @Test fun serverCrashCannotReviveRevokedOrCompletedHelperIntent() {
        val f = Fixture()
        val token = f.state.beginStop("server", null, false)!!
        assertTrue(f.state.markServerResumed("server", token))
        assertTrue(f.state.complete("server", token, false))
        assertFalse(f.state.markServerLost("server", token))
        assertFalse(f.state.snapshot().stopped)
        assertTrue(f.state.revoke())
        assertFalse(f.state.markServerLost("server", token))
        assertEquals(0L, f.state.snapshot().generation)
    }

    @Test fun disabledDefaultDoesNotStopOrPersistAnything() {
        val f = Fixture(NativeIdleState.Snapshot())
        assertTrue(f.state.available)
        assertNull(f.state.beginStop("server", null, false))
        assertEquals(NativeIdleState.Snapshot(), f.state.snapshot())
        assertTrue(f.writes.isEmpty())
    }

    @Test fun nullInitialStateRemainsUnavailableAndCannotBeRepairedByConfiguration() {
        val f = Fixture(null)
        assertFalse(f.state.available)
        assertEquals(NativeIdleState.Snapshot(), f.state.snapshot())
        assertFalse(f.state.configure(true, 5))
        assertNull(f.state.beginStop("server", null, false))
        assertFalse(f.state.revoke())
        assertFalse(f.state.resumeAllowed("server", 1))
        assertFalse(f.state.markServerResumed("server", 1))
        assertFalse(f.state.complete("server", 1, true))
        assertFalse(f.state.clearCompletedHelperGate("server", 1))
        assertTrue(f.writes.isEmpty())
    }

    @Test fun invalidInitialSnapshotAlsoRefusesAdmissionWithoutPersistence() {
        for (initial in listOf(
            NativeIdleState.Snapshot(counter = -1),
            NativeIdleState.Snapshot(idleMinutes = 0),
            NativeIdleState.Snapshot(stopped = true),
            NativeIdleState.Snapshot(counter = 1, generation = 1),
        )) {
            val f = Fixture(initial)
            assertFalse(f.state.available)
            assertNull(f.state.beginStop("server", null, false))
            assertTrue(f.writes.isEmpty())
        }
    }

    @Test fun changedConfigurationPersistsAndIncrementsButIdenticalIsIdempotent() {
        val f = Fixture(NativeIdleState.Snapshot())
        assertTrue(f.state.configure(false, 5))
        assertTrue(f.writes.isEmpty())
        assertTrue(f.state.configure(true, 60))
        assertEquals(NativeIdleState.Snapshot(enabled = true, idleMinutes = 60, counter = 1), f.state.snapshot())
        assertTrue(f.state.configure(true, 60))
        assertEquals(1, f.writes.size)
        for (minutes in listOf(Int.MIN_VALUE, 0, 61, Int.MAX_VALUE)) assertFalse(f.state.configure(false, minutes))
        assertEquals(1, f.writes.size)
        assertTrue(f.state.available)
    }

    @Test fun stopCapturesExactOwnerAndPreviouslyLiveHelperWithPositiveDurableGeneration() {
        val f = Fixture()
        val generation = f.state.beginStop("server", "helper", true)!!
        assertEquals(1L, generation)
        assertEquals(NativeIdleState.Snapshot(enabled = true, counter = 1, generation = 1,
            stopped = true, helperStopped = true, owner = "server", helper = "helper"), f.state.snapshot())
        assertEquals(f.state.snapshot(), f.writes.single())
        assertTrue(f.state.resumeAllowed("server", generation))
        assertFalse(f.state.resumeAllowed("other", generation))
        assertFalse(f.state.resumeAllowed("server", 0))
        assertFalse(f.state.resumeAllowed("server", generation + 1))
    }

    @Test fun invalidOwnerOrHelperBindingDoesNotAdvanceDurableCounter() {
        val f = Fixture()
        for (owner in listOf("", "server/other", "a".repeat(81))) assertNull(f.state.beginStop(owner, null, false))
        assertNull(f.state.beginStop("server", null, true))
        assertNull(f.state.beginStop("server", "helper", false))
        assertNull(f.state.beginStop("server", "helper/path", true))
        assertNull(f.state.beginStop("server", "a".repeat(81), true))
        assertEquals(0L, f.state.snapshot().counter)
        assertTrue(f.writes.isEmpty())
        assertNotNull(f.state.beginStop("a".repeat(80), "b".repeat(80), true))
    }

    @Test fun serverResumeRetainsExactHelperMarkerUntilHelperAcknowledgment() {
        val f = Fixture()
        val generation = f.state.beginStop("server", "helper", true)!!
        assertTrue(f.state.markServerResumed("server", generation))
        val saved = f.state.snapshot()
        assertFalse(saved.stopped)
        assertTrue(saved.helperStopped)
        assertEquals("helper", saved.helper)
        assertEquals(generation, saved.generation)
        assertTrue(f.state.resumeAllowed("server", generation))
        assertTrue(f.state.markServerResumed("server", generation))
        assertEquals(2, f.writes.size)
    }

    @Test fun helperAcknowledgmentCannotClearAnUnresumedServerMarker() {
        val f = Fixture()
        val generation = f.state.beginStop("server", "helper", true)!!
        assertFalse(f.state.complete("server", generation, true))
        assertTrue(f.state.snapshot().stopped)
        assertEquals(1, f.writes.size)
    }

    @Test fun priorLiveHelperMustBeRunningBeforeCompletionClearsAllMarkers() {
        val f = Fixture()
        val generation = f.state.beginStop("server", "helper", true)!!
        f.state.markServerResumed("server", generation)
        val prior = f.state.snapshot()
        assertFalse(f.state.complete("server", generation, false))
        assertEquals(prior, f.state.snapshot())
        assertTrue(f.state.complete("server", generation, true))
        assertEquals(NativeIdleState.Snapshot(enabled = true, counter = generation), f.state.snapshot())
        assertFalse(f.state.resumeAllowed("server", generation))
        assertFalse(f.state.complete("server", generation, true))
        assertFalse(f.state.clearCompletedHelperGate("server", generation))
    }

    @Test fun noPriorHelperRetainsCompletedGenerationForOneFreshHelperGate() {
        val f = Fixture()
        val generation = f.state.beginStop("server", null, false)!!
        f.state.markServerResumed("server", generation)
        assertTrue(f.state.complete("server", generation, false))
        assertFalse(f.state.snapshot().stopped)
        assertFalse(f.state.snapshot().helperStopped)
        assertEquals("server", f.state.snapshot().owner)
        assertEquals(generation, f.state.snapshot().generation)
        assertFalse(f.state.resumeAllowed("server", generation))
        assertTrue(f.state.complete("server", generation, true))
        assertEquals(2, f.writes.size)
    }

    @Test fun completedHelperGateClearsOnceAndAdvancesDurableCounter() {
        val f = Fixture()
        val generation = f.state.beginStop("server", null, false)!!
        f.state.markServerResumed("server", generation)
        f.state.complete("server", generation, false)
        assertTrue(f.state.clearCompletedHelperGate("server", generation))
        assertEquals(NativeIdleState.Snapshot(enabled = true, counter = generation + 1), f.state.snapshot())
        assertFalse(f.state.clearCompletedHelperGate("server", generation))
        assertFalse(f.state.markServerResumed("server", generation))
        assertFalse(f.state.complete("server", generation, true))
    }

    @Test fun completedHelperGateCannotClearPendingServerOrPriorHelperWork() {
        val f = Fixture()
        val generation = f.state.beginStop("server", "helper", true)!!
        assertFalse(f.state.clearCompletedHelperGate("server", generation))
        f.state.markServerResumed("server", generation)
        assertFalse(f.state.clearCompletedHelperGate("server", generation))
        assertTrue(f.state.snapshot().helperStopped)
    }

    @Test fun policyChangeInvalidatesAwaitedTokenWithoutErasingStoppedIntent() {
        val f = Fixture()
        val old = f.state.beginStop("server", "helper", true)!!
        assertTrue(f.state.configure(false, 45))
        val saved = f.state.snapshot()
        assertEquals(old + 1, saved.counter)
        assertEquals(saved.counter, saved.generation)
        assertFalse(saved.enabled)
        assertEquals(45, saved.idleMinutes)
        assertTrue(saved.stopped && saved.helperStopped)
        assertEquals("server", saved.owner)
        assertEquals("helper", saved.helper)
        assertFalse(f.state.resumeAllowed("server", old))
        assertFalse(f.state.markServerResumed("server", old))
        assertFalse(f.state.complete("server", old, true))
        assertTrue(f.state.resumeAllowed("server", saved.generation))
        assertNull(f.state.beginStop("server", null, false))
    }

    @Test fun identicalPolicyPreservesPendingTokenAndCompletedHelperGate() {
        val f = Fixture()
        val generation = f.state.beginStop("server", null, false)!!
        assertTrue(f.state.configure(true, 5))
        assertTrue(f.state.resumeAllowed("server", generation))
        f.state.markServerResumed("server", generation)
        f.state.complete("server", generation, false)
        val before = f.state.snapshot()
        assertTrue(f.state.configure(true, 5))
        assertEquals(before, f.state.snapshot())
        assertEquals(2, f.writes.size)
    }

    @Test fun changedPolicyAlsoInvalidatesCompletedHelperGateAcknowledgments() {
        val f = Fixture()
        val old = f.state.beginStop("server", null, false)!!
        f.state.markServerResumed("server", old)
        f.state.complete("server", old, false)
        f.state.configure(false, 1)
        val next = f.state.snapshot().generation
        assertEquals(old + 1, next)
        assertFalse(f.state.clearCompletedHelperGate("server", old))
        assertTrue(f.state.clearCompletedHelperGate("server", next))
    }

    @Test fun explicitRevokeClearsOwnersMarkersAndInvalidatesEveryOldAcknowledgment() {
        val f = Fixture()
        val old = f.state.beginStop("server", "helper", true)!!
        assertTrue(f.state.revoke())
        assertEquals(NativeIdleState.Snapshot(enabled = true, counter = old + 1), f.state.snapshot())
        assertFalse(f.state.resumeAllowed("server", old))
        assertFalse(f.state.markServerResumed("server", old))
        assertFalse(f.state.complete("server", old, true))
        assertTrue(f.state.revoke())
        assertEquals(old + 2, f.state.snapshot().counter)
        val fresh = f.state.beginStop("server", null, false)!!
        assertTrue(fresh > old + 2)
    }

    @Test fun wrongOwnerAndStaleGenerationNeverWriteAcknowledgments() {
        val f = Fixture()
        val generation = f.state.beginStop("server", null, false)!!
        for ((owner, token) in listOf("other" to generation, "server" to 0L, "server" to generation + 1)) {
            assertFalse(f.state.markServerResumed(owner, token))
            assertFalse(f.state.complete(owner, token, true))
            assertFalse(f.state.clearCompletedHelperGate(owner, token))
        }
        assertEquals(1, f.writes.size)
        assertTrue(f.state.snapshot().stopped)
    }

    @Test fun coldReadPreservesPendingHelperAndCompletedFreshHelperGate() {
        for (helperWasLive in listOf(false, true)) {
            val f = Fixture()
            val generation = f.state.beginStop("server", if (helperWasLive) "helper" else null, helperWasLive)!!
            val restored = Fixture(NativeIdleState.Snapshot.read(f.state.snapshot().map()))
            assertTrue(restored.state.resumeAllowed("server", generation))
            restored.state.markServerResumed("server", generation)
            if (helperWasLive) assertFalse(restored.state.complete("server", generation, false))
            else {
                restored.state.complete("server", generation, false)
                val completed = Fixture(NativeIdleState.Snapshot.read(restored.state.snapshot().map()))
                assertTrue(completed.state.clearCompletedHelperGate("server", generation))
            }
        }
    }

    @Test fun strictWireReadAllowsJsonIntegralTypesWithoutLosingLongCounter() {
        val f = Fixture()
        f.state.beginStop("server", null, false)
        val wire = f.state.snapshot().map().toMutableMap()
        wire["version"] = 1L
        wire["counter"] = 1
        wire["generation"] = 1
        wire["idleMinutes"] = 5L
        assertEquals(f.state.snapshot(), NativeIdleState.Snapshot.read(wire))
        val large = NativeIdleState.Snapshot(enabled = true, counter = Long.MAX_VALUE - 1)
        assertEquals(large, NativeIdleState.Snapshot.read(large.map()))
    }

    @Test fun strictWireReadRejectsMissingUnknownWrongVersionAndWrongTypedFields() {
        val base = NativeIdleState.Snapshot().map()
        val malformed = mutableListOf<Map<String, Any?>>(base - "helper", base + ("extra" to false),
            base + ("version" to 2), base + ("version" to "1"))
        for ((key, value) in listOf("counter" to 0.0, "generation" to true, "enabled" to "false",
            "idleMinutes" to 5.0, "stopped" to 0, "helperStopped" to null, "owner" to 7)) {
            malformed.add(base + (key to value))
        }
        for (wire in malformed) {
            val error = assertThrows(IllegalArgumentException::class.java) { NativeIdleState.Snapshot.read(wire) }
            assertEquals("state unavailable", error.message)
        }
    }

    @Test fun strictWireReadRejectsContradictoryOwnershipAndGenerationShapes() {
        val base = NativeIdleState.Snapshot().map()
        for (wire in listOf(
            base + ("counter" to -1L),
            base + ("idleMinutes" to 61),
            base + ("generation" to -1L),
            base + ("stopped" to true),
            base + ("owner" to "server"),
            base + mapOf("counter" to 2L, "generation" to 1L, "owner" to "server"),
            base + mapOf("counter" to 1L, "generation" to 1L, "owner" to "server/path"),
            base + mapOf("counter" to 1L, "generation" to 1L, "owner" to "server", "helper" to "helper"),
            base + mapOf("counter" to 1L, "generation" to 1L, "owner" to "server", "helperStopped" to true),
        )) assertThrows(IllegalArgumentException::class.java) { NativeIdleState.Snapshot.read(wire) }
    }

    @Test fun failedPersistFreezesOldSnapshotAndEverySubsequentAdmission() {
        val f = Fixture()
        val generation = f.state.beginStop("server", "helper", true)!!
        val before = f.state.snapshot()
        f.reject = true
        assertFalse(f.state.markServerResumed("server", generation))
        assertEquals(before, f.state.snapshot())
        assertFalse(f.state.available)
        assertFalse(f.state.resumeAllowed("server", generation))
        f.reject = false
        assertFalse(f.state.configure(true, 1))
        assertFalse(f.state.revoke())
        assertNull(f.state.beginStop("server", null, false))
        assertFalse(f.state.complete("server", generation, true))
        assertEquals(2, f.writes.size)
    }

    @Test fun thrownPersistCannotExposeItsErrorOrRepairTheCounter() {
        val f = Fixture()
        f.throws = true
        assertNull(f.state.beginStop("server", null, false))
        assertFalse(f.state.available)
        assertEquals(0L, f.state.snapshot().counter)
        f.throws = false
        assertFalse(f.state.configure(true, 1))
        assertEquals(1, f.writes.size)
    }

    @Test fun persistenceFailureForAnyDurableTransitionFailsClosed() {
        val transitions: List<(NativeIdleState, Long) -> Boolean> = listOf(
            { state, _ -> state.configure(false, 1) },
            { state, _ -> state.revoke() },
            { state, generation -> state.complete("server", generation, true) },
        )
        for (transition in transitions) {
            val f = Fixture()
            val generation = f.state.beginStop("server", "helper", true)!!
            f.state.markServerResumed("server", generation)
            val before = f.state.snapshot()
            f.reject = true
            assertFalse(transition(f.state, generation))
            assertEquals(before, f.state.snapshot())
            assertFalse(f.state.available)
            assertFalse(f.state.resumeAllowed("server", generation))
        }
        val f = Fixture()
        val generation = f.state.beginStop("server", null, false)!!
        f.state.markServerResumed("server", generation)
        f.reject = true
        assertFalse(f.state.clearCompletedHelperGate("server", generation))
        assertFalse(f.state.available)
    }

    @Test fun counterOverflowRefusesStopRevokePolicyChangeAndGateConsumption() {
        val initial = NativeIdleState.Snapshot(enabled = true, counter = Long.MAX_VALUE,
            generation = Long.MAX_VALUE, owner = "server")
        val operations: List<(NativeIdleState) -> Boolean> = listOf(
            { it.beginStop("server", null, false) != null },
            { it.revoke() },
            { it.configure(false, 1) },
            { it.clearCompletedHelperGate("server", Long.MAX_VALUE) },
        )
        for (operation in operations) {
            val f = Fixture(initial)
            assertFalse(operation(f.state))
            assertFalse(f.state.available)
            assertEquals(initial, f.state.snapshot())
            assertFalse(f.state.markServerResumed("server", Long.MAX_VALUE))
            assertTrue(f.writes.isEmpty())
        }
    }

    @Test fun maximumPositiveGenerationRoundTripsWithoutWrapping() {
        val f = Fixture(NativeIdleState.Snapshot(enabled = true, counter = Long.MAX_VALUE - 1))
        assertEquals(Long.MAX_VALUE, f.state.beginStop("server", null, false))
        assertTrue(f.state.resumeAllowed("server", Long.MAX_VALUE))
        assertEquals(f.state.snapshot(), NativeIdleState.Snapshot.read(f.state.snapshot().map()))
        assertFalse(f.state.revoke())
        assertFalse(f.state.resumeAllowed("server", Long.MAX_VALUE))
    }

    @Test fun snapshotAndSerializedMapCannotMutateTheOwnedState() {
        val f = Fixture()
        f.state.beginStop("server", "helper", true)
        val original = f.state.snapshot()
        val wire = original.map().toMutableMap()
        wire["owner"] = "other"
        wire["counter"] = 100L
        val changedCopy = original.copy(owner = "other")
        assertEquals("other", changedCopy.owner)
        assertEquals(original, f.state.snapshot())
        assertEquals(original, f.writes.single())
        f.state.markServerResumed("server", original.generation)
        assertTrue(original.stopped)
        assertFalse(f.state.snapshot().stopped)
    }
}
