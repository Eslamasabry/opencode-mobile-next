package io.github.eslamasabry.oc.ui.kit.dock

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Background CPU 2026-11-14: a tucked dock shows only its nub's dots, so a Working or Thinking ring's comet and breath
 * must not run there (they kept an untouched Glass home drawing ~68 frames a second on the emulator).
 */
class DockRingLoopsTest {
    @Test
    fun `BG-CPU-005 a ring loops only while the dock is out and ambient loops are allowed`() {
        assertTrue(ringLoopsRun(ambientLoops = true, tucked = false), "out, loops allowed: the comet and breath run")
        assertFalse(ringLoopsRun(ambientLoops = true, tucked = true), "tucked to the nub: nothing loops")
        assertFalse(ringLoopsRun(ambientLoops = false, tucked = false), "reduced motion or a still frame: nothing loops")
        assertFalse(ringLoopsRun(ambientLoops = false, tucked = true))
    }
}
