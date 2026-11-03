package io.github.eslamasabry.oc.core.common.text

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneNumbersTest {
    private fun <T> withFormatLocale(tag: String, block: () -> T): T {
        val before = Locale.getDefault(Locale.Category.FORMAT)
        Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag(tag))
        try {
            return block()
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, before)
        }
    }

    @Test
    fun `06-12_1-8 the phone's number setting chooses the digits`() {
        assertEquals("1,234", withFormatLocale("en-US") { Numbers.format(1234) })
        assertEquals("١٬٢٣٤", withFormatLocale("ar-EG-u-nu-arab") { Numbers.format(1234) })
        assertEquals("1,234", withFormatLocale("ar-EG-u-nu-latn") { Numbers.format(1234) }.replace('٬', ','))
    }
}
