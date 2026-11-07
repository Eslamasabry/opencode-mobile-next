package io.github.eslamasabry.oc.ui.kit.rb.o13

import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KIT_ARRIVAL_HOLD_MS
import io.github.eslamasabry.oc.ui.kit.motion.KIT_ARRIVAL_WASH_ALPHA
import io.github.eslamasabry.oc.ui.kit.motion.KitArrivalRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** lib/ui/kit/kit_arrival.dart at 8302de537: the request is claimed once, by its own row only; hold and wash. */
class KitArrivalParityTest {
    @Test fun `a request is claimed by its own row once`() {
        val request = KitArrivalRequest("effects-vibration")
        assertFalse(request.claim("effects-glass"), "another row never claims it")
        assertTrue(request.claim("effects-vibration"))
        assertFalse(request.claim("effects-vibration"), "a rebuild or the row built anew never repeats the arrival")
    }

    @Test fun `a wrong id does not use the request up`() {
        val request = KitArrivalRequest("a")
        assertFalse(request.claim("b"))
        assertTrue(request.claim("a"))
    }

    @Test fun `hold, wash and fade follow the Dart constants`() {
        assertEquals(2400, KIT_ARRIVAL_HOLD_MS) // kit_arrival.dart:76
        assertEquals(.18f, KIT_ARRIVAL_WASH_ALPHA) // kit_arrival.dart:80
        assertEquals(900, FlutterKitMotion.ENTRANCE_MS) // the fade (KitMotion.entrance), kit_arrival.dart:145
    }
}
