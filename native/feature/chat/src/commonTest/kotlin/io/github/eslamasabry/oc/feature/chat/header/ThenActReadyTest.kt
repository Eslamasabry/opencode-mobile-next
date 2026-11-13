package io.github.eslamasabry.oc.feature.chat.header

import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderRules
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A list row's Share waits for the page's capabilities, or the confirm is dropped and nothing happens. */
class ThenActReadyTest {
    @Test
    fun `Share waits until the menu offers it, other acts run at once`() {
        val beforeCapabilities = listOf(HeaderAction.Find, HeaderAction.Details)
        assertFalse(HeaderRules.thenActReady(HeaderAction.Share, beforeCapabilities))
        assertTrue(HeaderRules.thenActReady(HeaderAction.Details, beforeCapabilities))
        assertTrue(HeaderRules.thenActReady(HeaderAction.Share, beforeCapabilities + HeaderAction.Share))
        assertFalse(HeaderRules.thenActReady(null, beforeCapabilities))
    }
}
