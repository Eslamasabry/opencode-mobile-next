package io.github.eslamasabry.oc.ui.kitchat.rb.o20

import io.github.eslamasabry.oc.ui.kitchat.composer.KitSteerBoxPhase
import io.github.eslamasabry.oc.ui.kitchat.composer.KitSteerBoxRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** lib/ui/kit/kit_steer_box.dart: who may type, dictate, send and close, by phase and block. */
class KitSteerBoxRulesTest {
    @Test fun `B-KIT-2010 limits are the Dart constants`() {
        assertEquals(8000, KitSteerBoxRules.MAX_LENGTH) // kit_steer_box.dart:92
        assertEquals(3..5, KitSteerBoxRules.FIELD_LINES) // kit_steer_box.dart:145 maxLines 5; kit_field.dart:713 starts a multiline field at min(cap, 3)
    }

    @Test fun `B-KIT-2011 send needs ready, no block and words after trimming`() {
        assertTrue(KitSteerBoxRules.canSend(KitSteerBoxPhase.Ready, null, " hi "))
        assertFalse(KitSteerBoxRules.canSend(KitSteerBoxPhase.Ready, null, "   "))
        assertFalse(KitSteerBoxRules.canSend(KitSteerBoxPhase.Ready, "Waiting", "hi"))
        KitSteerBoxPhase.entries.filter { it != KitSteerBoxPhase.Ready }.forEach {
            assertFalse(KitSteerBoxRules.canSend(it, null, "hi"), it.name)
        }
    }

    @Test fun `B-KIT-2012 the field takes typing while idle or dictating`() {
        assertTrue(KitSteerBoxRules.fieldEnabled(KitSteerBoxPhase.Ready, null))
        assertTrue(KitSteerBoxRules.fieldEnabled(KitSteerBoxPhase.Dictating, null))
        assertFalse(KitSteerBoxRules.fieldEnabled(KitSteerBoxPhase.Ready, "blocked"))
        assertFalse(KitSteerBoxRules.fieldEnabled(KitSteerBoxPhase.Sending, null))
        assertFalse(KitSteerBoxRules.fieldEnabled(KitSteerBoxPhase.Sent, null))
    }

    @Test fun `B-KIT-2013 close is off only while sending`() {
        KitSteerBoxPhase.entries.forEach { assertEquals(it != KitSteerBoxPhase.Sending, KitSteerBoxRules.canClose(it), it.name) }
    }
}
