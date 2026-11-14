package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.math.roundToInt

/** The rule every text part marks by: the query, literally, ignoring case, not overlapping, trimmed. */
class KitFindMarksTest {
    @Test
    fun `A2-02 matches are literal, case-blind and do not overlap`() {
        val marks = KitFindMarks("  aa ")
        assertEquals(listOf(0 until 2, 4 until 6), marks.ranges("aaa AA a"))
        assertEquals(emptyList(), KitFindMarks("   ").ranges("anything"))
        assertEquals(emptyList(), KitFindMarks("longer than the text").ranges("short"))
    }

    @Test
    fun `A2-02 every match is tinted and only the current one is stronger`() {
        val text = AnnotatedString("find Find FIND")
        val out = text.withFindMarks(KitFindMarks("find", current = 1), Color.Red)
        // A colour keeps its alpha in 8 bits, so compare in whole percent.
        val percents = out.spanStyles.map { (it.item.background.alpha * 100).roundToInt() }
        assertEquals(listOf(18, 38, 18), percents)
        assertEquals(listOf(0, 5, 10), out.spanStyles.map { it.start })
    }

    @Test
    fun `A2-02 the ordinal counts matches that came before in the same part`() {
        val out = AnnotatedString("find").withFindMarks(KitFindMarks("find", current = 3), Color.Red, firstOrdinal = 3)
        assertEquals(listOf(38), out.spanStyles.map { (it.item.background.alpha * 100).roundToInt() })
    }

    @Test
    fun `A2-02 closed Find or no match leaves the text as it was`() {
        val text = AnnotatedString("nothing here")
        assertSame(text, text.withFindMarks(null, Color.Red))
        assertSame(text, text.withFindMarks(KitFindMarks("zzz"), Color.Red))
        assertSame(text, text.withFindMarks(KitFindMarks(""), Color.Red))
    }
}
