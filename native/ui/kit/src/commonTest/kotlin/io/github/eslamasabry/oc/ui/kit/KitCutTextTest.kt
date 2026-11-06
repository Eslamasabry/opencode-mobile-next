package io.github.eslamasabry.oc.ui.kit

import kotlin.test.Test
import kotlin.test.assertEquals

class KitCutTextTest {
    @Test
    fun `a cut title ends at a whole word with an ellipsis`() {
        assertEquals("Fix the login flow…", kitEndAtWord("Fix the login flow on"))
        assertEquals("Fix the login…", kitEndAtWord("Fix the login flow. "))
    }
}
