package io.github.eslamasabry.opencode_mobile

import io.github.eslamasabry.opencode_mobile.DisplayRefresh.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Motion lead 2026-11-07: the app asks for the display's fastest refresh mode, never a different resolution. */
class DisplayRefreshTest {
    private val fhd60 = Mode(1, 1080, 2400, 60f)
    private val fhd90 = Mode(2, 1080, 2400, 90f)
    private val fhd120 = Mode(3, 1080, 2400, 120f)
    private val qhd144 = Mode(4, 1440, 3200, 144f)

    @Test
    fun `MOT-REFRESH a 120 Hz phone running at 60 asks for its 120 Hz mode`() {
        assertEquals(fhd120, DisplayRefresh.fastest(listOf(fhd60, fhd90, fhd120), current = fhd60))
    }

    @Test
    fun `MOT-REFRESH a faster mode at another resolution is never picked`() {
        assertEquals(fhd90, DisplayRefresh.fastest(listOf(fhd60, fhd90, qhd144), current = fhd60))
    }

    @Test
    fun `MOT-REFRESH a 60 Hz-only display keeps its one mode`() {
        assertEquals(fhd60, DisplayRefresh.fastest(listOf(fhd60), current = fhd60))
    }

    @Test
    fun `MOT-REFRESH two modes at the same rate prefer the current one`() {
        val twin = Mode(9, 1080, 2400, 120f)
        assertEquals(fhd120, DisplayRefresh.fastest(listOf(twin, fhd120), current = fhd120))
    }

    @Test
    fun `MOT-REFRESH no modes at the current resolution means no request`() {
        assertNull(DisplayRefresh.fastest(listOf(qhd144), current = fhd60))
    }
}
