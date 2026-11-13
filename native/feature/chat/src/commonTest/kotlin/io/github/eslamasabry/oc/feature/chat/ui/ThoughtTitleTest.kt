package io.github.eslamasabry.oc.feature.chat.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RC1 device pass (picture g4): a one-line thought "Simple math: 17*23 = 391." read "1723 = 391" on its line, because
 * the title stripped every `*`, `_`, backtick and `#`. The title now reads the line as Markdown does.
 */
class ThoughtTitleTest {
    @Test
    fun `RC1-MD-003 a thought's line keeps marks that are not Markdown pairs and drops real ones`() {
        assertEquals("Simple math: 17*23 = 391.", thoughtTitle("Simple math: 17*23 = 391."))
        assertEquals("call a_b_c", thoughtTitle("call a_b_c"))
        assertEquals("2*3*4 = 24", thoughtTitle("2*3*4 = 24"))
        assertEquals("an emphasis here", thoughtTitle("an *emphasis* here"))
        assertEquals("Planning the edit", thoughtTitle("\n## **Planning the edit**\nThen the rest"))
    }
}
