package io.github.eslamasabry.oc.feature.phonesetup

import io.github.eslamasabry.oc.feature.phonesetup.presentation.RingCostPart
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupRingCost
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupTransport
import kotlin.test.Test
import kotlin.test.assertEquals

/** Parity audit 2026-11-08 row 460 (Flutter phone_setup_progress_screen.dart `_costLine`). */
class SetupRingCostTest {
    @Test
    fun `AUDIT-460 the time left comes first, then Wi-Fi or mobile data`() {
        assertEquals(
            listOf(RingCostPart.Minutes(4), RingCostPart.Wifi),
            SetupRingCost.parts(230, lastStep = false, downloading = true, transport = SetupTransport.Wifi),
        )
        assertEquals(
            listOf(RingCostPart.Minutes(2), RingCostPart.MobileData),
            SetupRingCost.parts(100, lastStep = false, downloading = false, transport = SetupTransport.Mobile),
        )
    }

    @Test
    fun `AUDIT-460 under a minute is said only on the last step, and never zero minutes`() {
        assertEquals(listOf(RingCostPart.UnderMinute), SetupRingCost.parts(40, lastStep = true, downloading = false, transport = SetupTransport.Unknown))
        assertEquals(listOf(RingCostPart.Minutes(1)), SetupRingCost.parts(20, lastStep = false, downloading = false, transport = SetupTransport.Other))
    }

    @Test
    fun `AUDIT-460 the network alone is said only while something downloads`() {
        assertEquals(listOf(RingCostPart.MobileData), SetupRingCost.parts(null, lastStep = false, downloading = true, transport = SetupTransport.Mobile))
        assertEquals(emptyList(), SetupRingCost.parts(null, lastStep = false, downloading = false, transport = SetupTransport.Wifi))
        assertEquals(emptyList(), SetupRingCost.parts(null, lastStep = false, downloading = true, transport = SetupTransport.Unknown))
    }
}
