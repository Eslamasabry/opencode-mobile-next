package io.github.eslamasabry.oc.ui.shell.nav

import androidx.navigation3.runtime.NavKey
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import kotlin.test.Test
import kotlin.test.assertEquals

/** A sub-agent's chat goes back to the conversation that started it without stacking that conversation twice. */
class ShellBackToChatTest {
    private val parent = ConversationRef(ProfileId("p"), SessionId("ses_parent"))
    private val child = ConversationRef(ProfileId("p"), SessionId("ses_child"))

    private fun navigator(): ShellNavigator =
        ShellNavigator().also { it.attach(mutableListOf<NavKey>(ChatsRoute.Home)) }

    @Test
    fun `S30-CHAT-003 opened from the parent's card, going to the parent pops the sub-agent`() {
        val nav = navigator()
        nav.openChat(parent)
        nav.openChat(child)
        nav.backToChat(parent)
        assertEquals(listOf<AppRoute>(ChatsRoute.Home, ChatRoute(parent)), nav.snapshot())
    }

    @Test
    fun `S30-CHAT-003 opened from elsewhere, going to the parent opens it once`() {
        val nav = navigator()
        nav.openChat(child)
        nav.backToChat(parent)
        nav.backToChat(parent)
        assertEquals(listOf<AppRoute>(ChatsRoute.Home, ChatRoute(child), ChatRoute(parent)), nav.snapshot())
    }
}
