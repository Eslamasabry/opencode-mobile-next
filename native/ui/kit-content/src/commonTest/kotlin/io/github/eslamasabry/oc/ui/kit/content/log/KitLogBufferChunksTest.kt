package io.github.eslamasabry.oc.ui.kit.content.log

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Dart `perf_memory_test` › log chunk boundaries preserve CRLF, blank lines and eviction counts. */
class KitLogBufferChunksTest {
    @Test
    fun `B-BUILD-024 log chunk boundaries preserve CRLF, blank lines and eviction counts`() {
        val random = Random(17)
        for (capacity in listOf(1, 3, 64)) {
            val buffer = KitLogBuffer(capacity)
            var accumulated = ""
            repeat(200) {
                val chunk = (0 until random.nextInt(40) + 1).joinToString("") { listOf("a", "b", "\r", "\n")[random.nextInt(4)] }
                accumulated += chunk
                buffer.appendText(chunk)
                val expected = accumulated.split('\n').toMutableList()
                if (expected.last().isEmpty()) expected.removeAt(expected.lastIndex)
                val tail = expected.drop(maxOf(0, expected.size - capacity)).map { it.removeSuffix("\r") }
                assertEquals(tail, buffer.lines.map { it.text })
                assertEquals(maxOf(0, expected.size - capacity), buffer.dropped)
            }
            val old = buffer.lines
            val oldText = old.map { it.text }
            buffer.replaceText("new\r\npartial")
            assertEquals(oldText, old.map { it.text }, "an earlier snapshot never changes")
            assertEquals("partial", buffer.lines.last().text)
            buffer.add(KitLogLine("explicit", KitLogLevel.Error))
            assertEquals("explicit", buffer.lines.last().text)
            assertEquals(KitLogLevel.Error, buffer.lines.last().level)
            buffer.clear()
            assertTrue(buffer.lines.isEmpty())
            assertEquals(0, buffer.dropped)
        }
    }
}
