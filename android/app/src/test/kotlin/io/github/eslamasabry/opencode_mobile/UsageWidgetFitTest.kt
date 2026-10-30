package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class UsageWidgetFitTest {
    private fun numbers(left: Int, detail: Boolean = true) =
        WidgetPlan(needsFix = false, hasNumbers = true, minLeft = left, hasDetail = detail)

    private fun fix() = WidgetPlan(needsFix = true, hasNumbers = false, minLeft = 0, hasDetail = false)

    @Test fun neverPicksMoreThanFitsTheHeight() {
        val plans = List(4) { numbers(90) }
        for (h in 60..400) {
            val picked = UsageWidgetFit.pick(plans, h, 6)
            val used = picked.sumOf { UsageWidgetFit.NUMBERS_DP + if (it.showDetail) UsageWidgetFit.DETAIL_DP else 0 }
            assertTrue("h=$h", used <= maxOf(0, h - UsageWidgetFit.FRAME_DP - UsageWidgetFit.SLACK_DP))
        }
    }

    @Test fun theDefaultSizeKeepsWholeRowsInsteadOfHalfOne() {
        // 110dp is the minimum size: header plus one whole block, no more.
        val picked = UsageWidgetFit.pick(List(3) { numbers(90) }, 110, 6)
        assertEquals(1, picked.size)
    }

    @Test fun keepsTheMostUrgentAndTheOriginalOrder() {
        val plans = listOf(numbers(95), numbers(10), numbers(50))
        val picked = UsageWidgetFit.pick(plans, 50 + 6 + 2 * UsageWidgetFit.NUMBERS_DP, 6)
        assertEquals(listOf(1, 2), picked.map { it.index })
    }

    @Test fun setupStillFitsWhenThereIsNoRoomForNumbers() {
        val plans = listOf(numbers(5), fix())
        val picked = UsageWidgetFit.pick(plans, 50 + 6 + UsageWidgetFit.SETUP_DP, 6)
        assertEquals(listOf(1), picked.map { it.index })
    }

    @Test fun compactDefaultShowsAvailableNumbersBeforeSetupRows() {
        val plans = listOf(fix(), numbers(62))
        val picked = UsageWidgetFit.pick(plans, 110, 6)
        assertEquals(listOf(1), picked.map { it.index })
    }

    @Test fun detailLinesOnlyWhereThereIsRoom() {
        val plans = listOf(numbers(10), numbers(20))
        val tight = UsageWidgetFit.pick(plans, 50 + 6 + 2 * UsageWidgetFit.NUMBERS_DP, 6)
        assertTrue(tight.none { it.showDetail })
        val roomy = UsageWidgetFit.pick(plans, 400, 6)
        assertTrue(roomy.all { it.showDetail })
        assertFalse(UsageWidgetFit.pick(listOf(numbers(10, detail = false)), 400, 6).single().showDetail)
    }

    @Test fun compactPlacementShowsTheTightestWindow() {
        assertEquals(listOf(1), UsageWidgetFit.columnIndices(listOf(62, 15), 120))
        assertEquals(listOf(0), UsageWidgetFit.columnIndices(listOf(15, 62), 179))
        assertEquals(listOf(0), UsageWidgetFit.columnIndices(listOf(15, 15), 120))
        assertTrue(UsageWidgetFit.columnIndices(emptyList(), 120).isEmpty())
    }

    @Test fun wideningRestoresBothWindowsInTheirPresentedOrder() {
        assertEquals(listOf(0, 1), UsageWidgetFit.columnIndices(listOf(62, 15), 180))
        assertEquals(listOf(0, 1), UsageWidgetFit.columnIndices(listOf(62, 15, 5), 300))
        assertEquals(listOf(0), UsageWidgetFit.columnIndices(listOf(62), 180))
    }

    @Test fun launcherMetadataPlacesAFixedTwoByTwo() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "android/app/src/main/res/xml/widget_usage_info.xml").isFile }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(
            File(root, "android/app/src/main/res/xml/widget_usage_info.xml")
        ).documentElement
        fun attr(name: String) = document.getAttributeNS("http://schemas.android.com/apk/res/android", name)
        assertEquals("2", attr("targetCellWidth"))
        assertEquals("2", attr("targetCellHeight"))
        assertEquals("${UsageWidgetFit.MIN_WIDTH_DP}dp", attr("minWidth"))
        assertEquals("${UsageWidgetFit.MIN_WIDTH_DP}dp", attr("maxResizeWidth"))
        // Usage shares the already qualified Sessions placement / Save / Cancel host.
        val sessions = factory.newDocumentBuilder().parse(
            File(root, "android/app/src/main/res/xml/widget_sessions_info.xml")
        ).documentElement
        assertEquals(
            sessions.getAttributeNS("http://schemas.android.com/apk/res/android", "configure"),
            attr("configure")
        )
    }

    @Test fun respectsTheRowLimit() {
        assertEquals(6, UsageWidgetFit.pick(List(9) { numbers(50) }, 2000, 6).size)
    }
}
