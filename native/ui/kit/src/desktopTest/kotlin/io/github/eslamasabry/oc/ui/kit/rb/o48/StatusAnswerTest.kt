package io.github.eslamasabry.oc.ui.kit.rb.o48

import io.github.eslamasabry.oc.ui.kit.state.KitAnswerScale
import io.github.eslamasabry.oc.ui.kit.state.statusAnswerSize
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusAnswerTest {
    @Test fun `Solid uses the quiet large answer and firm schools retain the display size`() {
        assertEquals(32, statusAnswerSize(KitAnswerScale.Large, false))
        assertEquals(40, statusAnswerSize(KitAnswerScale.Large, true))
        assertEquals(28, statusAnswerSize(KitAnswerScale.Calm, false))
        assertEquals(24, statusAnswerSize(KitAnswerScale.Compact, true))
    }
}
