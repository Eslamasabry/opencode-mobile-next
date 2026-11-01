package io.github.eslamasabry.oc.engine.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IdentityTest {
    @Test
    fun `B-ANLY-009 the install id is random, kept for thirty days and then replaced by an unrelated one`() {
        val ids = CountingIds()
        val first = InstallIdPolicy.current(null, nowMs = 0, ids = ids)
        assertSame(first, InstallIdPolicy.current(first, nowMs = 30 * DAY_MS - 1, ids = ids))
        val second = InstallIdPolicy.current(first, nowMs = 30 * DAY_MS, ids = ids)
        assertNotEquals(first.id, second.id)
        assertEquals(30 * DAY_MS, second.createdAtMs)
    }

    @Test
    fun `B-ANLY-009 a clock moved backwards rotates the install id`() {
        val ids = CountingIds()
        val first = InstallIdPolicy.current(null, nowMs = 10 * DAY_MS, ids = ids)
        assertNotEquals(first.id, InstallIdPolicy.current(first, nowMs = 9 * DAY_MS, ids = ids).id)
    }

    @Test
    fun `B-ANLY-009 the secure id source gives 128-bit lowercase hex, different every time`() {
        val drawn = (1..200).map { SecureIdSource.newId() }
        drawn.forEach { assertTrue(Regex("^[0-9a-f]{32}$").matches(it), it) }
        assertEquals(drawn.size, drawn.toSet().size)
    }

    @Test
    fun `B-ANLY-009 the session id lasts while events keep coming and rotates after sixty idle minutes`() {
        val sessions = SessionTracker(CountingIds())
        val a = sessions.sessionAt(0)
        assertEquals(a, sessions.sessionAt(59 * 60_000L))
        assertEquals(a, sessions.sessionAt(118 * 60_000L))
        val b = sessions.sessionAt(178 * 60_000L)
        assertNotEquals(a, b)
        sessions.forget()
        assertNotEquals(b, sessions.sessionAt(178 * 60_000L + 1))
    }
}
