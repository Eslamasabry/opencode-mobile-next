package io.github.eslamasabry.oc.feature.about.gestures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The hidden-gesture ledger (test/gesture_audit_test.dart:58,72). The files behind the evidence: desktopTest. */
class HiddenGesturesTest {
    @Test
    fun `B-NAV-019 every hidden gesture has a visible equivalent and an evidence place`() {
        assertTrue(HiddenGestures.all.isNotEmpty())
        assertEquals(emptyList(), HiddenGestures.problems())
        val longPresses = HiddenGestures.all.filter { "long-press" in it.gesture }.map { it.element }
        assertTrue("chats-all-row-long-press" in longPresses)
        assertTrue("chat-prompt-long-press" in longPresses)
    }

    @Test
    fun `B-NAV-019 a row without a visible equivalent or with a vague evidence fails the ledger`() {
        val good = HiddenGestures.all.first()
        val problems =
            HiddenGestures.problems(
                listOf(
                    good.copy(element = "a", visibleEquivalent = " "),
                    good.copy(element = "b", evidence = "the chats screen"),
                    good.copy(element = "c"),
                    good.copy(element = "c"),
                ),
            )
        assertEquals(
            listOf(
                "chats-all / a: visibleEquivalent",
                "chats-all / b: \"the chats screen\" is not native/<file>.kt:<line>",
                "chats-all / c: listed twice",
            ),
            problems,
        )
    }
}
