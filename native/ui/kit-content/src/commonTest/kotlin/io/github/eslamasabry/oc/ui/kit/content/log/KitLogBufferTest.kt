package io.github.eslamasabry.oc.ui.kit.content.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Dart `kit_log_panel_test` › 8 KitLogBuffer. */
class KitLogBufferTest {
    @Test
    fun `B-KIT-021 the buffer splits chunks, keeps a partial line, drops and counts`() {
        val buffer = KitLogBuffer(capacity = 3)
        buffer.appendText("a\nb")
        assertEquals(listOf("a", "b"), buffer.lines.map { it.text })
        buffer.appendText("c\n")
        assertEquals(listOf("a", "bc"), buffer.lines.map { it.text })
        buffer.appendText("d\ne\nf\n")
        assertEquals(listOf("d", "e", "f"), buffer.lines.map { it.text })
        assertEquals(2, buffer.dropped)
        buffer.replaceText("x\ny")
        assertEquals(listOf("x", "y"), buffer.lines.map { it.text })
        assertEquals(0, buffer.dropped)
        buffer.clear()
        assertTrue(buffer.lines.isEmpty())
    }

    @Test
    fun `B-KIT-021 added lines past capacity are dropped and counted, CR LF reads as one break`() {
        val buffer = KitLogBuffer(capacity = 5)
        repeat(1245) { buffer.add(KitLogLine("l $it")) }
        assertEquals(1240, buffer.dropped)
        assertEquals("l 1244", buffer.lines.last().text)
        buffer.clear()
        buffer.appendText("one\r\ntwo\r\n", KitLogLevel.Error)
        assertEquals(listOf("one", "two"), buffer.lines.map { it.text })
        assertTrue(buffer.lines.all { it.level == KitLogLevel.Error })
    }
}
