package io.github.eslamasabry.oc.ui.kit.rb.o09

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.ui.kit.row.KitFeedItemRules
import io.github.eslamasabry.oc.ui.kit.row.KitSliverRowGroupRules
import io.github.eslamasabry.oc.ui.kit.state.KIT_DETAILS_PREVIEW_LINES
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsRules
import io.github.eslamasabry.oc.ui.kit.state.KitLastKnownRules
import io.github.eslamasabry.oc.ui.kit.state.KitMiniTableRow
import io.github.eslamasabry.oc.ui.kit.state.KitMiniTableRules
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules of order 09's parts, each against its Dart file at FLUTTER_REF. */
@OptIn(RedactorApi::class)
class RowsBRulesTest {
    private fun v(label: String, value: String, copyable: Boolean = true) = KitTechnicalValue(label, Redacted(value), copyable)

    @Test fun `B-KIT-FOLD-001 an empty fold draws nothing and blank text counts as empty`() {
        assertTrue(KitDetailsRules.isEmpty(emptyList(), emptyList(), null, hasChild = false))
        assertTrue(KitDetailsRules.isEmpty(emptyList(), emptyList(), "  \n", hasChild = false))
        assertFalse(KitDetailsRules.isEmpty(emptyList(), emptyList(), null, hasChild = true))
        assertFalse(KitDetailsRules.isEmpty(emptyList(), listOf("Check the server."), null, hasChild = false))
    }

    @Test fun `B-KIT-FOLD-002 a value shows once under the first label that carried it`() {
        val unique = KitDetailsRules.unique(listOf(v("Address", "a:1"), v("Host", "a:1"), v("Branch", "main")))
        assertEquals(listOf("Address", "Branch"), unique.map { it.label })
    }

    @Test fun `B-KIT-FOLD-003 Copy all shows for two copyable values or any text`() {
        assertFalse(KitDetailsRules.showsCopyAll(listOf(v("A", "1"), v("B", "2", copyable = false)), null))
        assertTrue(KitDetailsRules.showsCopyAll(listOf(v("A", "1"), v("B", "2")), null))
        assertTrue(KitDetailsRules.showsCopyAll(emptyList(), "HTTP 502"))
    }

    @Test fun `B-KIT-FOLD-004 Copy all copies label lines, a blank line, then the text, and never a secret`() {
        assertEquals("A: 1\nB: 2\n\nHTTP 502", KitDetailsRules.copyAll(listOf(v("A", "1"), v("B", "2"), v("C", "3", false)), "HTTP 502"))
        val copied = KitDetailsRules.copyAll(emptyList(), "Authorization: Bearer FAKETOKENFAKETOKEN0123456789")
        assertFalse("FAKETOKENFAKETOKEN0123456789" in copied)
    }

    @Test fun `B-KIT-FOLD-005 the raw text shows its first 12 lines until Show all, whole in the sheet`() {
        assertEquals(12, KIT_DETAILS_PREVIEW_LINES)
        val text = (1..30).joinToString("\n") { "line $it" }
        val (head, tail) = KitDetailsRules.preview(text, whole = false)
        assertEquals(12, head.split('\n').size)
        assertEquals("line 13", tail!!.split('\n').first())
        assertNull(KitDetailsRules.preview(text, whole = true).second)
        assertNull(KitDetailsRules.preview("a\nb", whole = false).second)
    }

    @Test fun `B-KIT-FOLD-006 Copy the value lowers the first letter but keeps an acronym`() {
        assertEquals("address", KitDetailsRules.lowerFirst("Address"))
        assertEquals("URL", KitDetailsRules.lowerFirst("URL"))
        assertEquals("", KitDetailsRules.lowerFirst(""))
    }

    @Test fun `B-KIT-TABLE-001 a table draws at most 6 columns and 20 rows, cells wrap at 200 dp`() {
        assertEquals(6, KitMiniTableRules.MAX_COLUMNS)
        assertEquals(20, KitMiniTableRules.MAX_ROWS)
        assertEquals(200f, KitMiniTableRules.MAX_CELL_WIDTH)
        assertEquals(24f + 24f + 1f, KitMiniTableRules.columnWidth(23.2f, 24f))
        assertEquals(200f, KitMiniTableRules.columnWidth(500f, 24f))
    }

    @Test fun `B-KIT-TABLE-002 a column of numbers aligns to the end`() {
        val rows = listOf(KitMiniTableRow(listOf("a.dart", "12", "3.5 MB")), KitMiniTableRow(listOf("b.dart", "−4", "40%")))
        assertFalse(KitMiniTableRules.numeric(0, rows))
        assertTrue(KitMiniTableRules.numeric(1, rows))
        assertTrue(KitMiniTableRules.numeric(2, rows))
        assertFalse(KitMiniTableRules.numeric(3, rows))
        assertFalse(KitMiniTableRules.numeric(1, emptyList()))
    }

    @Test fun `B-KIT-TABLE-003 a roomy place stretches the columns, a tight one scrolls`() {
        assertEquals(2f, KitMiniTableRules.stretch(100f, 200f))
        assertEquals(1f, KitMiniTableRules.stretch(300f, 200f))
    }

    @Test fun `B-KIT-FEED-001 the meta line never breaks before a dot or inside a time`() {
        assertEquals(" · ", KitFeedItemRules.DOT)
        assertEquals("2 h ago", KitFeedItemRules.time("2 h ago"))
        assertFalse(KitFeedItemRules.large(1.3f))
        assertTrue(KitFeedItemRules.large(1.31f))
    }

    @Test fun `B-KIT-FEED-002 the row reads as one sentence title agent state project time`() {
        assertEquals("Fix, Claude Code, Needs you, alpha, 2 h ago", KitFeedItemRules.spoken("Fix", "Claude Code", "Needs you", "alpha", "2 h ago"))
        assertEquals("Fix, now", KitFeedItemRules.spoken("Fix", null, null, "", "now"))
    }

    @Test fun `B-KIT-FEED-003 the Working sweep runs across the word and mirrors in right to left`() {
        assertEquals(-0.3f, KitFeedItemRules.sweepCentre(0f, rtl = false), 1e-6f)
        assertEquals(1.3f, KitFeedItemRules.sweepCentre(1f, rtl = false), 1e-6f)
        assertEquals(1.3f, KitFeedItemRules.sweepCentre(0f, rtl = true), 1e-6f)
        assertEquals(0.18f, KitFeedItemRules.SWEEP_HALF)
    }

    @Test fun `B-KIT-LAST-001 the label says Refreshing only while a fresh read runs`() {
        assertEquals("Updated 12m ago · Refreshing", KitLastKnownRules.label("Updated 12m ago", true) { "$it · Refreshing" })
        assertEquals("Updated 12m ago", KitLastKnownRules.label("Updated 12m ago", false) { "$it · Refreshing" })
    }

    @Test fun `B-KIT-SLIVER-001 built-later rows assume icons, a bare panel reaches back by space4`() {
        assertTrue(KitSliverRowGroupRules.leads(null, itemCount = 3, childrenLead = false))
        assertFalse(KitSliverRowGroupRules.leads(null, itemCount = 0, childrenLead = false))
        assertFalse(KitSliverRowGroupRules.leads(false, itemCount = 3, childrenLead = true))
        assertTrue(KitSliverRowGroupRules.leads(false, itemCount = 0, childrenLead = false, track = true))
        assertEquals(16f, KitSliverRowGroupRules.reach(bare = true, sideMargin = 16f))
        assertEquals(8f, KitSliverRowGroupRules.reach(bare = true, sideMargin = 8f))
        assertEquals(0f, KitSliverRowGroupRules.reach(bare = false, sideMargin = 16f))
    }
}
