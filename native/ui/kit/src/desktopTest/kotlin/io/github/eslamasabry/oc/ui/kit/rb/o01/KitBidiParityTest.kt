package io.github.eslamasabry.oc.ui.kit.rb.o01

import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/kit_bidi.dart at 8302de537. */
class KitBidiParityTest {
    private val lri = "⁦"
    private val fsi = "⁨"
    private val pdi = "⁩"

    @Test fun isolatesWrapTheValue() {
        assertEquals("${lri}git push$pdi", KitBidi.ltr("git push"))
        assertEquals("${fsi}Sara$pdi", KitBidi.auto("Sara"))
    }

    @Test fun emptyTextIsLeftAlone() {
        assertEquals("", KitBidi.ltr(""))
        assertEquals("", KitBidi.auto(""))
    }

    @Test fun isolatedTextIsNeverNested() {
        assertEquals("${lri}a$pdi", KitBidi.ltr("${lri}a$pdi"))
        assertEquals("${fsi}a$pdi", KitBidi.ltr("${fsi}a$pdi"))
        assertEquals("${lri}a$pdi", KitBidi.auto("${lri}a$pdi"))
        assertEquals("${fsi}a$pdi", KitBidi.auto("${fsi}a$pdi"))
    }

    @Test fun halfIsolatedTextIsWrapped() {
        assertEquals("$lri${lri}a$lri$pdi".let { it }, KitBidi.ltr("${lri}a$lri"))
    }
}
