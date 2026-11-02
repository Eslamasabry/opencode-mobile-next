package io.github.eslamasabry.opencode_mobile.graph

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CoordinatorPickerSafetyTest {
    @Test
    fun `B-CHAT-010 growing document reads only one overflow byte then closes`() {
        var consumed = 0
        var closed = false
        val source = object : ByteArrayInputStream(ByteArray(1024 * 1024)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                super.read(bytes, offset, length).also { if (it > 0) consumed += it }
            override fun close() { closed = true; super.close() }
        }
        assertFailsWith<DocumentRead.TooLarge> { DocumentRead.atMost(source, 5) }
        assertEquals(6, consumed)
        assertTrue(closed)
    }

    @Test
    fun `B-STATE-101 cancelled picker retains its slot until its system result is drained`() = runTest {
        val requests = PickRequests<String>()
        val old = assertNotNull(requests.begin())
        requests.retire(old)
        assertNull(requests.begin(), "A new launch cannot overtake the old system result")
        requests.resolve(old, "late old document")
        assertNull(old.await())
        val next = assertNotNull(requests.begin())
        requests.resolve(next, "new document")
        assertEquals("new document", next.await())
    }
}
