package io.github.eslamasabry.oc.ui.kit.status

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which status a slot shows (kit_status_slot.dart:143-154 at 8302de537). */
class KitStatusSlotTest {
    private val offline = KitStatus(KitStatusKind.Connection, "Offline")
    private val update = KitStatus(KitStatusKind.Update, "Update ready")
    private val risky = KitStatus(KitStatusKind.RiskySwitch, "Auto-approve is on")
    private val work = KitStatus(KitStatusKind.Work, "Working")

    @Test fun `B-KIT-R10-040 the highest kind wins across app-wide, own and contributed`() {
        assertEquals(offline, kitStatusShown(listOf(update, offline), work, listOf(risky)))
        assertEquals(risky, kitStatusShown(listOf(update), work, listOf(null, risky)))
        assertNull(kitStatusShown(emptyList(), null, listOf(null)))
    }

    @Test fun `B-KIT-R10-041 ties keep app-wide first, then own, then contributions in order`() {
        val own = KitStatus(KitStatusKind.Update, "own")
        val second = KitStatus(KitStatusKind.Update, "second")
        assertEquals(update, kitStatusShown(listOf(update), own, listOf(second)))
        assertEquals(own, kitStatusShown(emptyList(), own, listOf(second)))
    }

    @Test fun `B-KIT-R10-042 omit leaves out app-wide conditions only`() {
        assertEquals(risky, kitStatusShown(listOf(offline), null, listOf(risky), omit = setOf(KitStatusKind.Connection)))
        val ownOffline = KitStatus(KitStatusKind.Connection, "own offline")
        assertEquals(ownOffline, kitStatusShown(listOf(offline), ownOffline, emptyList(), omit = setOf(KitStatusKind.Connection)))
    }
}
