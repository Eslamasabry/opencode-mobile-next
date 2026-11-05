package io.github.eslamasabry.oc.ui.design.rb.o02

import kotlin.test.*
import io.github.eslamasabry.oc.ui.design.layout.*

class KitLayoutParityTest {
    @Test fun constantsMatchKitLayout() {
        assertEquals(listOf(600,840,1200,480), listOf(Layout.MEDIUM_FROM,Layout.EXPANDED_FROM,Layout.LARGE_FROM,Layout.SHORT_HEIGHT))
        assertEquals(listOf(700,720,960,640,560,480,320,200,480,440,400,400,320,96), listOf(Layout.CONVERSATION_WIDTH,Layout.READING_WIDTH,Layout.LIST_WIDTH,Layout.SHEET_MAX_WIDTH,Layout.CONFIRM_WIDTH,Layout.DIALOG_WIDTH,Layout.POPOVER_MAX_WIDTH,Layout.POPOVER_MIN_WIDTH,Layout.UNDO_MAX_WIDTH,Layout.STATE_MAX_WIDTH,Layout.LANE_MAX_WIDTH,Layout.CONTROL_MAX_WIDTH,Layout.CHIP_MAX_WIDTH,Layout.GLYPH_MAX_WIDTH))
        assertEquals(listOf(296,700,360,340,80,400,340), listOf(Layout.PANE_LIST_WIDTH,Layout.PANE_DETAIL_MAX_WIDTH,Layout.PANE_DETAIL_MIN_WIDTH,Layout.PANE_SIDE_WIDTH,Layout.RAIL_WIDTH,Layout.SIDEBAR_MAX_WIDTH,Layout.PAIR_ROW_MIN_WIDTH))
        assertEquals(1f/3f, Layout.SIDEBAR_MAX_SHARE)
    }
    @Test fun boundaryWidthsAndShortModals() {
        for ((width, expected) in listOf(0f to WindowClass.Compact,599.99f to WindowClass.Compact,600f to WindowClass.Medium,839.99f to WindowClass.Medium,840f to WindowClass.Expanded,1199.99f to WindowClass.Expanded,1200f to WindowClass.Large)) assertEquals(expected,WindowClass.of(width))
        assertEquals(listOf(false,false,true,true), WindowClass.entries.map { it.isWide })
        assertTrue(WindowClass.isShort(479.99f)); assertFalse(WindowClass.isShort(480f))
        assertEquals(WindowClass.Medium, WindowClass.modal(1280f,479f))
        assertEquals(WindowClass.Large, WindowClass.modal(1280f,480f))
        assertEquals(WindowClass.Compact, WindowClass.modal(400f,300f))
    }
    @Test fun sidebarHonorsTextScaleCapAndMinimum() {
        assertEquals(296f, Layout.sidebarWidth(1f,1600f))
        assertEquals(400f, Layout.sidebarWidth(2f,1600f))
        assertEquals(300f, Layout.sidebarWidth(2f,900f))
        assertEquals(296f, Layout.sidebarWidth(2f,600f))
        assertEquals(296f, Layout.sidebarWidth(.5f,1600f))
    }
}
