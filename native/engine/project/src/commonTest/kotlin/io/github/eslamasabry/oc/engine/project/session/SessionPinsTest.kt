package io.github.eslamasabry.oc.engine.project.session

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionPinsTest {
    private val a = ProfileId("a")
    private val b = ProfileId("b")

    @Test
    fun `B-STATE-114 pins survive reload and serialize writes without crossing profile or location`() = runTest {
        val disk = SessionTestDisk()
        val store = storeOver(disk, StandardTestDispatcher(testScheduler))
        val lane = WriterLane("pins")
        val pins = SessionPinStore(store, lane)
        val scope = PinScope.of("/one", null)
        val other = PinScope.of("/two", null)
        listOf(
            async { pins.setPinned(a, scope, SessionId("ses_first"), true) },
            async { pins.setPinned(a, scope, SessionId("ses_second"), true) },
            async { pins.setPinned(a, scope, SessionId("ses_first"), false) },
            async { pins.setPinned(a, other, SessionId("ses_elsewhere"), true) },
            async { pins.setPinned(b, scope, SessionId("ses_other_server"), true) },
        ).awaitAll().forEach { assertEquals(Outcome.Ok(Unit), it) }

        val reloaded = SessionPinStore(storeOver(SessionTestDisk(disk.persisted()), StandardTestDispatcher(testScheduler)), lane)
        assertEquals(setOf(SessionId("ses_second")), reloaded.ids(a, scope))
        assertEquals(setOf(SessionId("ses_elsewhere")), reloaded.ids(a, other))
        assertEquals(setOf(SessionId("ses_other_server")), reloaded.ids(b, scope))
        assertEquals("[\"/one\",null]", scope.raw)

        // The standard profile sweep finds the key; after it, the deleted profile shows nothing and peers keep theirs.
        val doomed = ProfileKeyRule.scopedKeys("a", store.keys("oc."), setOf("a", "b"))
        assertTrue(SessionPinStore.keyFor(a) in doomed)
        assertTrue(SessionPinStore.keyFor(b) !in doomed)
        store.commit(lane) { doomed.forEach(::remove) }
        pins.forget(a)
        assertEquals(emptySet(), pins.ids(a, scope))
        assertEquals(setOf(SessionId("ses_other_server")), pins.ids(b, scope))
    }

    @Test
    fun `B-STATE-114 failed storage is not shown as saved and later writes recover`() = runTest {
        val disk = SessionTestDisk().apply { refuse = true }
        val pins = SessionPinStore(storeOver(disk, StandardTestDispatcher(testScheduler)), WriterLane("pins"))
        val scope = PinScope("scope")
        val failed = pins.setPinned(a, scope, SessionId("ses_one"), true)
        assertIs<Outcome.Failed>(failed)
        assertIs<Failure.StorageRefused>(failed.failure)
        assertEquals(emptySet(), pins.ids(a, scope))
        disk.refuse = false
        assertEquals(Outcome.Ok(Unit), pins.setPinned(a, scope, SessionId("ses_two"), true))
        assertEquals(setOf(SessionId("ses_two")), pins.ids(a, scope))
    }
}
