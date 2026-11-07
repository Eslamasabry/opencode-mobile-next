package io.github.eslamasabry.opencode_mobile

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** P-110: after Android kills the app (the camera in front), it reopens on the page it was on. */
class LastRouteTest {
    private val chat = ChatRoute(ConversationRef(ProfileId("p"), SessionId("s")))

    @Test
    fun `P-110 a shell that came back on home reopens the chat the person was in`() {
        assertEquals(chat, LastRoute.reopen(saved = chat, current = ChatsRoute.Home))
        // Before the shell published its top.
        assertEquals(chat, LastRoute.reopen(saved = chat, current = null))
    }

    @Test
    fun `P-110 a stack that restored itself, or a page a link opened, is left alone`() {
        assertNull(LastRoute.reopen(saved = chat, current = chat))
        assertNull(LastRoute.reopen(saved = chat, current = ChatsRoute.Attention))
        assertNull(LastRoute.reopen(saved = null, current = ChatsRoute.Home))
    }

    @Test
    fun `P-110 home is not a page to reopen, and a chat's one-shot acts never run twice`() {
        assertNull(LastRoute.reopen(saved = ChatsRoute.Home, current = ChatsRoute.Home))
        val once = chat.copy(requestId = "r1", thenAction = "Share")
        assertEquals(chat, LastRoute.reopen(saved = once, current = ChatsRoute.Home))
    }
}
