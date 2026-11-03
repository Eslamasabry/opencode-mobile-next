package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** P12-FIX-PREVIEWQR: a tapped reply link to a local dev server is the preview's (Codex review previewqr #5). */
class PreviewClaimTest {
    private val ref = ConversationRef(ProfileId("p1"), SessionId("ses_1"))

    @Test
    fun `B-CHAT-027 a tapped localhost link opens the preview on the conversation's server, path and query kept`() {
        assertEquals(PreviewRoute.Page(5173, false, ref, "/app?mode=demo"), PreviewRoute.Page.claim("http://localhost:5173/app?mode=demo", ref))
        assertEquals(PreviewRoute.Page(8443, true, null, "/"), PreviewRoute.Page.claim("https://127.0.0.1:8443", null))
    }

    @Test
    fun `B-CHAT-025 other links are not claimed and go on through the external link policy`() {
        listOf("https://example.com", "http://localhost/", "http://user:pw@localhost:3000", "ftp://localhost:21").forEach {
            assertNull(PreviewRoute.Page.claim(it, ref), it)
        }
    }
}
