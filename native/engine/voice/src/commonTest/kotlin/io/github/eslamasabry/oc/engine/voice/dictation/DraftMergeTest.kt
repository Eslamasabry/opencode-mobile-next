package io.github.eslamasabry.oc.engine.voice.dictation

import kotlin.test.Test
import kotlin.test.assertEquals

class DraftMergeTest {
    @Test
    fun `B-MEDIA-024 a dictated draft merges around the current text selection`() {
        assertEquals("hello world", DraftMerge.merge("hello", -1, -1, "  world "))
        assertEquals("one two three", DraftMerge.merge("one three", 4, 4, "two"))
        assertEquals("one two three", DraftMerge.merge("one XX three", 4, 6, "two"))
        assertEquals("say hi there", DraftMerge.merge("say there", 3, 3, "hi"))
        assertEquals("word", DraftMerge.merge("", 0, 0, "word"))
        assertEquals("lead text", DraftMerge.merge("text", 0, 0, "lead"))
        assertEquals("keep\nnew", DraftMerge.merge("keep\n", -1, -1, "new"))
        assertEquals("same", DraftMerge.merge("same", 2, 2, "   "))
        assertEquals("ab cd", DraftMerge.merge("ab", 99, 120, "cd")) // offsets past the end clamp
    }
}
