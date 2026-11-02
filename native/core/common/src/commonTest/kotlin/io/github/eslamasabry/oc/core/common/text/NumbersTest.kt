package io.github.eslamasabry.oc.core.common.text

import kotlin.test.Test
import kotlin.test.assertEquals

class NumbersTest {
    private val arabic = NumberSymbols(zeroDigit = '٠', grouping = '٬', decimal = '٫', minus = '-')
    private val persian = NumberSymbols(zeroDigit = '۰', grouping = '٬', decimal = '٫')

    @Test
    fun `B-L10N-012 whole numbers use the phone's digits and thousands separator`() {
        assertEquals("0", Numbers.format(0, NumberSymbols.Latin))
        assertEquals("12,345", Numbers.format(12345, NumberSymbols.Latin))
        assertEquals("١٢٬٣٤٥", Numbers.format(12345, arabic))
        assertEquals("۱,۰۰۰,۰۰۰".replace(',', '٬'), Numbers.format(1_000_000, persian))
        assertEquals("-1,000", Numbers.format(-1000, NumberSymbols.Latin))
        assertEquals("999", Numbers.format(999L, NumberSymbols.Latin))
        assertEquals("1234", Numbers.format(1234, NumberSymbols.Latin.copy(grouping = null)))
        assertEquals("-9,223,372,036,854,775,808", Numbers.format(Long.MIN_VALUE, NumberSymbols.Latin))
    }

    @Test
    fun `B-L10N-012 decimals round half up and keep the phone's separator`() {
        assertEquals("1.50", Numbers.decimal(1.5, 2, NumberSymbols.Latin))
        assertEquals("١٫٥٠", Numbers.decimal(1.5, 2, arabic))
        assertEquals("1,234.57", Numbers.decimal(1234.567, 2, NumberSymbols.Latin))
        assertEquals("-0.5", Numbers.decimal(-0.5, 1, NumberSymbols.Latin))
        assertEquals("0.00", Numbers.decimal(-0.001, 2, NumberSymbols.Latin))
        assertEquals("3", Numbers.decimal(2.6, 0, NumberSymbols.Latin))
    }

    @Test
    fun `B-L10N-012 digits in built text follow the phone and other characters stay`() {
        assertEquals("١٢:٣٠", Numbers.digits("12:30", arabic))
        assertEquals("v2.1", Numbers.digits("v2.1", NumberSymbols.Latin))
    }

    @Test
    fun `pinned symbols win over the phone reading`() {
        Numbers.pinned = arabic
        try {
            assertEquals("٣", Numbers.format(3))
        } finally {
            Numbers.pinned = null
        }
    }
}
