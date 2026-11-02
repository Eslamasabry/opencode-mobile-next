package io.github.eslamasabry.oc.feature.servers

import io.github.eslamasabry.oc.feature.servers.presentation.ConnectMethod
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectMethodHint
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectMethods
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ported from test/connect_method_order_test.dart. */
class ConnectMethodsTest {
    private val browser = ConnectMethod(type = "oauth", id = "b", label = "ChatGPT Pro/Plus (browser)")
    private val headless = ConnectMethod(type = "oauth", id = "h", label = "ChatGPT Pro/Plus (headless)")
    private val key = ConnectMethod(type = "key", id = null, label = "Manually enter API Key")

    @Test
    fun `B-NAV-006 headless comes first, browser last, key in between`() {
        assertEquals(listOf(headless, key, browser), ConnectMethods.order(listOf(browser, key, headless)))
        assertEquals(listOf(headless, key, browser), ConnectMethods.order(listOf(key, browser, headless)))
    }

    @Test
    fun `B-NAV-006 hints say where each flow finishes`() {
        assertEquals(ConnectMethodHint.PasteCallbackUrl, ConnectMethods.hint(browser))
        assertEquals(ConnectMethodHint.WorksFromPhone, ConnectMethods.hint(headless))
        assertEquals(ConnectMethodHint.PasteApiKey, ConnectMethods.hint(key))
        assertTrue(ConnectMethods.needsServerBrowser(browser))
        assertFalse(ConnectMethods.needsServerBrowser(headless))
        assertFalse(ConnectMethods.needsServerBrowser(key))
    }
}
