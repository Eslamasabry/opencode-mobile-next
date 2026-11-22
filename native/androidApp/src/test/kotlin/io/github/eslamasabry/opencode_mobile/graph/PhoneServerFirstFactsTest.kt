package io.github.eslamasabry.opencode_mobile.graph

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S29-a: a stored own stop is in the very first facts, before any status read. */
class PhoneServerFirstFactsTest {
    @Test
    fun `B-HOME-501 the first facts carry the stored own stop, so the first frame reads stopped`() {
        val first = firstServerFacts(storedStop = true)
        assertTrue(first.stoppedByPerson)
        assertNull(first.status, "no status read yet: nothing reads connected")
        assertFalse(first.starting)
    }

    @Test
    fun `B-HOME-502 without a stored stop the first facts are the plain empty ones`() {
        assertFalse(firstServerFacts(storedStop = false).stoppedByPerson)
    }
}
