package io.github.eslamasabry.oc.ui.kit.frame

import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import kotlin.test.Test
import kotlin.test.assertEquals

/** kit_page_heading.dart `_buildContent`: the quiet line's role, tone and chevron. */
class KitPageHeadingTest {
    @Test
    fun `B-KIT-PH-001 a switcher or plain line is secondary body words with a down chevron`() {
        assertEquals(KitPageHeadingLineLook(TypeRole.Body, TextTone.Secondary, chevronForward = false), kitPageHeadingLineLook(false))
    }

    @Test
    fun `B-KIT-PH-002 a needs-you line is amber at label weight and opens forward`() {
        assertEquals(KitPageHeadingLineLook(TypeRole.Label, TextTone.Attention, chevronForward = true), kitPageHeadingLineLook(true))
    }
}
