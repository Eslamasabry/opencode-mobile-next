package io.github.eslamasabry.oc.ui.kit.rb.o08

import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.state.ReceiptRules
import io.github.eslamasabry.oc.ui.kit.state.ReceiptWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules of lib/ui/kit/kit_receipt.dart that the native receipt keeps. */
class KitReceiptTest {
    @Test
    fun `B-KIT-receipt a not-confirmed write is its own state and asks the person to act`() {
        assertEquals(ReceiptWords.NotConfirmed, ReceiptRules.words(KitReceiptState.NotConfirmed, unconfirmedTooLong = false))
        assertTrue(ReceiptRules.asksToAct(ReceiptWords.NotConfirmed))
        assertTrue(ReceiptRules.asksToAct(ReceiptWords.RefusedReason))
        assertFalse(ReceiptRules.asksToAct(ReceiptWords.Sent))
        assertFalse(ReceiptRules.asksToAct(ReceiptWords.Confirmed))
    }

    @Test
    fun `B-KIT-receipt a waiting write that turned slow reads not confirmed yet, sending too`() {
        assertEquals(ReceiptWords.NotConfirmed, ReceiptRules.words(KitReceiptState.Sending, unconfirmedTooLong = true))
        assertEquals(ReceiptWords.NotConfirmed, ReceiptRules.words(KitReceiptState.Sent, unconfirmedTooLong = true))
        assertEquals(ReceiptWords.Sending, ReceiptRules.words(KitReceiptState.Sending, unconfirmedTooLong = false))
    }

    @Test
    fun `B-KIT-receipt a blank reason or place falls back to the plain word`() {
        assertEquals(ReceiptWords.Refused, ReceiptRules.words(KitReceiptState.Refused("  "), false))
        assertEquals(ReceiptWords.AnsweredElsewhere, ReceiptRules.words(KitReceiptState.AnsweredElsewhere(""), false))
    }

    @Test
    fun `B-KIT-receipt Try again only where the person may act, Undo only when confirmed inside its window`() {
        assertTrue(ReceiptRules.retryShown(ReceiptWords.NotConfirmed, canRetry = true))
        assertFalse(ReceiptRules.retryShown(ReceiptWords.Confirmed, canRetry = true))
        assertFalse(ReceiptRules.retryShown(ReceiptWords.Sent, canRetry = true))
        assertFalse(ReceiptRules.retryShown(ReceiptWords.NotConfirmed, canRetry = false))
        assertTrue(ReceiptRules.undoShown(ReceiptWords.Confirmed, canUndo = true, windowOpen = true))
        assertFalse(ReceiptRules.undoShown(ReceiptWords.Confirmed, canUndo = true, windowOpen = false))
        assertFalse(ReceiptRules.undoShown(ReceiptWords.Sent, canUndo = true, windowOpen = true))
    }
}
