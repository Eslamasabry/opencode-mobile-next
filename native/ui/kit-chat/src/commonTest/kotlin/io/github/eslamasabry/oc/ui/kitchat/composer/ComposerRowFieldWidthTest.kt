package io.github.eslamasabry.oc.ui.kitchat.composer

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * F1 (scoreboard 2026-11-07): the one-row field's width, from Flutter `kit_composer.dart:760-764` (`rowField`):
 * one 48 dp target per leading control plus the trailing one, the field's S2 end gap, its S3 start gap only when
 * nothing leads, and the box's S1 padding on both sides.
 */
class ComposerRowFieldWidthTest {
    @Test
    fun `F1 the plus and the round control leave the field the rest of the row`() {
        // 380 - 4 - 4 - 48 * 2 - 8 = 268
        assertEquals(268f, composerRowFieldWidth(380f, 48f, leadingTargets = 1))
    }

    @Test
    fun `F1 Stop beside the plus takes one more target`() {
        assertEquals(220f, composerRowFieldWidth(380f, 48f, leadingTargets = 2))
    }

    @Test
    fun `F1 nothing leading starts the field after the S3 gap`() {
        // 380 - 8 - 48 - 8 - 12 = 304
        assertEquals(304f, composerRowFieldWidth(380f, 48f, leadingTargets = 0))
    }
}
