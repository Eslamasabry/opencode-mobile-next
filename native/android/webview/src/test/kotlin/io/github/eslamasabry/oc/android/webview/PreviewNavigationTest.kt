package io.github.eslamasabry.oc.android.webview

import io.github.eslamasabry.oc.android.webview.PreviewNavigation.Navigate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** P12-FIX-PREVIEWQR: the in-app preview's navigation decisions (Codex review previewqr #6, #9). */
class PreviewNavigationTest {
    @Test
    fun `B-SEC-058 only a person's tap may ask to leave, scripts frames and redirects elsewhere are refused silently`() {
        assertEquals(Navigate.LoadHere, PreviewNavigation.navigate(sameOrigin = true, mainFrame = true, gesture = false))
        assertEquals(Navigate.AskToLeave, PreviewNavigation.navigate(sameOrigin = false, mainFrame = true, gesture = true))
        // `location.href = "https://elsewhere"` and an intercepted redirect carry no gesture: no prompt.
        assertEquals(Navigate.Refuse, PreviewNavigation.navigate(sameOrigin = false, mainFrame = true, gesture = false))
        assertEquals(Navigate.Refuse, PreviewNavigation.navigate(sameOrigin = false, mainFrame = false, gesture = true))
    }

    @Test
    fun `B-SEC-058 a redirect within the origin loads the page at its new address`() {
        assertEquals("http://127.0.0.1:5173/app/", PreviewNavigation.reloadAt("http://127.0.0.1:5173/", "http://127.0.0.1:5173/app/"))
        assertNull(PreviewNavigation.reloadAt("http://127.0.0.1:5173/", "http://127.0.0.1:5173/"))
    }
}
