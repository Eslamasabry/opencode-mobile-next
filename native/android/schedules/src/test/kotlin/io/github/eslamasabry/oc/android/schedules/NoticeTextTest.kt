package io.github.eslamasabry.oc.android.schedules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoticeTextTest {
    @Test
    fun `the finished notice leads with the reply's first line, one line, capped, no key-like text`() {
        assertEquals("All 42 tests pass.", NoticeText.line("\n  All 42 tests pass.\nDetails follow", 100))
        assertEquals("Token … rotated", NoticeText.line("Token sk-abcdefghijklmnopqrstuv rotated", 100))
        assertEquals("abcd…", NoticeText.line("abcdefghij", 5))
        assertNull(NoticeText.line("   \n ", 100))
    }
}
