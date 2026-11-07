package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import io.github.eslamasabry.oc.ui.kit.content.qr.KitScannerFlow
import io.github.eslamasabry.oc.ui.kit.content.qr.KitScannerOpened
import io.github.eslamasabry.oc.ui.kit.content.qr.KitScannerPhase
import io.github.eslamasabry.oc.ui.kit.content.qr.scannerTextMax
import io.github.eslamasabry.oc.ui.kit.content.qr.scannerWindowSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** lib/ui/kit/kit_scanner.dart at 8302de537: `_Viewfinder.side`, the text block's room and `_KitScannerState`'s rules. */
class KitScannerParityTest {
    @Test fun window_side_is_240_capped_by_gutters_and_margins_in_whole_dp() {
        assertEquals(240f, scannerWindowSide(412f, 700f, 16f, 24f))
        assertEquals(200f, scannerWindowSide(232.5f, 700f, 16f, 24f))
        assertEquals(101f, scannerWindowSide(412f, 149.9f, 16f, 24f))
        assertEquals(0f, scannerWindowSide(20f, 700f, 16f, 24f))
    }

    @Test fun text_block_scrolls_before_the_frame_drops_below_160_plus_margins() {
        assertEquals(592f, scannerTextMax(800f)) // 800 - (160 + 48)
        assertEquals(150f, scannerTextMax(300f)) // half the height wins
    }

    @Test fun starts_then_scans() {
        val flow = KitScannerFlow()
        val n = flow.start()
        assertEquals(KitScannerPhase.Starting, flow.phase)
        assertEquals(KitScannerOpened.Scanning, flow.opened(n, failed = false))
        assertEquals(KitScannerPhase.Scanning, flow.phase)
    }

    @Test fun failure_is_reported_and_draws_nothing() {
        val flow = KitScannerFlow()
        assertEquals(KitScannerOpened.Failed, flow.opened(flow.start(), failed = true))
        assertEquals(KitScannerPhase.Failed, flow.phase)
    }

    @Test fun a_stale_start_changes_nothing() {
        val flow = KitScannerFlow()
        val first = flow.start()
        val second = flow.start()
        assertEquals(KitScannerOpened.Ignore, flow.opened(first, failed = true))
        assertEquals(KitScannerPhase.Starting, flow.phase)
        assertEquals(KitScannerOpened.Scanning, flow.opened(second, failed = false))
        flow.dispose()
        assertEquals(KitScannerOpened.Ignore, flow.opened(second, failed = false))
    }

    @Test fun accepted_code_latches() {
        val flow = KitScannerFlow()
        flow.opened(flow.start(), failed = false)
        var seen = 0
        assertFalse(flow.code("") { seen++; true })
        assertFalse(flow.code("nope") { seen++; false })
        assertTrue(flow.code("yes") { seen++; true })
        assertFalse(flow.code("again") { seen++; true })
        assertEquals(2, seen)
        assertTrue(flow.handled)
    }

    @Test fun no_code_is_offered_unless_scanning() {
        val flow = KitScannerFlow()
        flow.start()
        assertFalse(flow.code("x") { true })
        assertFalse(flow.handled)
    }

    @Test fun pauses_while_away_and_restarts_on_return() {
        val flow = KitScannerFlow()
        val n = flow.start()
        assertTrue(flow.away())
        assertEquals(KitScannerPhase.Paused, flow.phase)
        assertEquals(KitScannerOpened.Stop, flow.opened(n, failed = false))
        assertEquals(KitScannerPhase.Paused, flow.phase)
        assertFalse(flow.away())
        assertTrue(flow.back())
        assertEquals(KitScannerOpened.Scanning, flow.opened(flow.start(), failed = false))
    }

    @Test fun nothing_restarts_after_a_code_was_accepted() {
        val flow = KitScannerFlow()
        flow.opened(flow.start(), failed = false)
        flow.code("ok") { true }
        assertFalse(flow.away()) // the camera is already stopped
        assertEquals(KitScannerPhase.Paused, flow.phase)
        assertFalse(flow.back())
    }
}
