package io.github.eslamasabry.oc.ui.design

import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.type.TextTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutTokensTest {
    @Test
    fun `B-KIT-029 window classes switch at 600 840 and 1200 and short is under 480`() {
        assertEquals(WindowClass.Compact, WindowClass.of(599.9f))
        assertEquals(WindowClass.Medium, WindowClass.of(600f))
        assertEquals(WindowClass.Medium, WindowClass.of(839f))
        assertEquals(WindowClass.Expanded, WindowClass.of(840f))
        assertEquals(WindowClass.Expanded, WindowClass.of(1199f))
        assertEquals(WindowClass.Large, WindowClass.of(1200f))
        assertTrue(WindowClass.isShort(479f))
        assertFalse(WindowClass.isShort(480f))
        assertEquals(WindowClass.Medium, WindowClass.modal(1280f, 400f))
        assertEquals(WindowClass.Large, WindowClass.modal(1280f, 800f))
    }

    @Test
    fun `B-KIT-029 every cap role has the Flutter cap and alignment`() {
        val expected =
            mapOf(
                CapRole.Conversation to 700, CapRole.Reading to 720, CapRole.List to 960, CapRole.Sheet to 640,
                CapRole.Confirm to 560, CapRole.Dialog to 480, CapRole.Popover to 320, CapRole.Toast to 480,
                CapRole.State to 440, CapRole.Lane to 400, CapRole.Control to 400, CapRole.Chip to 320, CapRole.Glyph to 96,
            )
        for ((role, cap) in expected) assertEquals(cap, role.capDp, role.name)
        for (r in listOf(CapRole.Fill, CapRole.Wrapper, CapRole.Scope)) assertNull(r.capDp)
        for (r in listOf(CapRole.Popover, CapRole.Toast, CapRole.Lane, CapRole.Chip)) assertEquals(CapAlign.Start, r.defaultAlign)
        for (r in listOf(CapRole.Reading, CapRole.List, CapRole.State, CapRole.Glyph)) assertEquals(CapAlign.Center, r.defaultAlign)
    }

    @Test
    fun `B-KIT-029 the sidebar grows with text up to 400 or a third of the window`() {
        assertEquals(296f, Layout.sidebarWidth(1f, 1600f))
        assertEquals(384.8f, Layout.sidebarWidth(1.3f, 1600f), 0.01f)
        assertEquals(400f, Layout.sidebarWidth(2f, 1600f))
        assertEquals(300f, Layout.sidebarWidth(2f, 900f), 0.01f)
        assertEquals(296f, Layout.sidebarWidth(2f, 600f)) // never under the list pane
    }

    @Test
    fun `B-KIT-029 panes follow the body - the detail keeps 360 dp after the list and the side`() {
        assertTrue(Layout.showsDetail(WindowClass.Expanded, short = false, bodyWidthDp = 840f))
        assertFalse(Layout.showsDetail(WindowClass.Medium, short = false, bodyWidthDp = 700f))
        assertFalse(Layout.showsDetail(WindowClass.Expanded, short = true, bodyWidthDp = 915f))
        assertFalse(Layout.showsDetail(WindowClass.Expanded, short = false, bodyWidthDp = 840f - 296f)) // a sidebar takes 296
        assertTrue(Layout.showsDetail(WindowClass.Expanded, short = false, bodyWidthDp = 544f, listInBody = false))
        assertTrue(Layout.showsSide(WindowClass.Large, short = false, bodyWidthDp = 1280f)) // 1280 − 296 − 340 = 644
        assertFalse(Layout.showsSide(WindowClass.Large, short = false, bodyWidthDp = 1280f - 296f)) // 348 < 360
        assertFalse(Layout.showsSide(WindowClass.Expanded, short = false, bodyWidthDp = 1199f))
    }

    @Test
    fun `B-KIT-035 every status tone has a word tone and a glyph and failure is never red`() {
        assertEquals(TextTone.Primary, StatusTone.Failure.text)
        assertEquals(TextTone.Attention, StatusTone.Attention.text)
        assertEquals(StatusTone.entries.size, StatusTone.entries.map { it.glyph }.toSet().size)
    }
}
