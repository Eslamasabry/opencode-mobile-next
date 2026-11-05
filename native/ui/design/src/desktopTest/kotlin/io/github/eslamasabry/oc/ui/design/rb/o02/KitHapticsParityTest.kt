package io.github.eslamasabry.oc.ui.design.rb.o02

import kotlin.test.*
import io.github.eslamasabry.oc.ui.design.motion.*

class KitHapticsParityTest {
    @Test fun settingsWinForEverySemanticEvent() {
        assertEquals(listOf("Send","Done","Commit","LongPress","Select","ToggleOn","ToggleOff"),KitHaptic.entries.map { it.name })
        for (event in KitHaptic.entries) {
            assertTrue(event.allowed())
            assertFalse(event.allowed(enabled=false))
            assertFalse(event.allowed(contextEnabled=false))
            assertEquals(event != KitHaptic.Done,event.allowed(reduced=true))
        }
    }
}
