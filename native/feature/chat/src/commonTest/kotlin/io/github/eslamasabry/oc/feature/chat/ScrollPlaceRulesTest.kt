package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.ui.ScrollPlace
import io.github.eslamasabry.oc.feature.chat.ui.ScrollPlaceRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** PLACE-1: a reader's place is a row key and the offset into it, never a list position. */
class ScrollPlaceRulesTest {
    private val keys = listOf("n3", "n2", "n1", "k", "o1", "o2", "o3", "o4", "o5", "o6", "o7", "o8", "o9", "o10")

    @Test
    fun `PLACE-1 the place holds the row's key, the offset and the next eight older keys`() {
        val place = ScrollPlaceRules.capture(keys, firstVisible = 3, offset = 17)!!
        assertEquals("k", place.key)
        assertEquals(17, place.offset)
        assertEquals(listOf("o1", "o2", "o3", "o4", "o5", "o6", "o7", "o8"), place.older)
    }

    @Test
    fun `PLACE-1 rows that arrived below while away move the index but the same row comes back at the same offset`() {
        val place = ScrollPlaceRules.capture(keys, firstVisible = 3, offset = 17)!!
        val later = listOf("m2", "m1") + keys
        assertEquals(5 to 17, ScrollPlaceRules.target(place, later))
    }

    @Test
    fun `PLACE-1 a row that is gone falls back to the nearest earlier row that remains`() {
        val place = ScrollPlaceRules.capture(keys, firstVisible = 3, offset = 17)!!
        val without = keys - setOf("k", "o1")
        assertEquals(without.indexOf("o2") to 0, ScrollPlaceRules.target(place, without))
        assertNull(ScrollPlaceRules.target(place, listOf("x", "y")))
    }

    @Test
    fun `PLACE-1 nothing to capture on an empty list`() {
        assertNull(ScrollPlaceRules.capture(emptyList(), 0, 0))
        assertEquals(0, ScrollPlace("k", 0, emptyList()).older.size)
    }
}
