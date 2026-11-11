package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.feature.chats.presentation.AllConversationsRules
import io.github.eslamasabry.oc.feature.chats.presentation.AllModel
import io.github.eslamasabry.oc.feature.chats.presentation.RowAttention
import io.github.eslamasabry.oc.feature.chats.presentation.ServerName
import io.github.eslamasabry.oc.feature.chats.presentation.SourcePages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** H2 / M4: an All-conversations row carries the same state facts as the home row, Stopped included. */
class AllRowStatesTest {
    private val profile = ProfileId("p")

    private fun rows(attention: RowAttention) =
        AllConversationsRules.build(
                AllModel(
                    known = true,
                    servers = listOf(ServerName(profile, "Phone")),
                    sources = mapOf(profile to SourcePages(sessions = listOf(Session(SessionId("a"))), loaded = true)),
                    attention = mapOf(AllConversationsRules.key(profile, SessionId("a")) to attention),
                )
            )
            .rows

    @Test
    fun `a stopped run reads Stopped and not Unread result`() {
        val row = rows(RowAttention(unread = false, stopped = true)).single()
        assertTrue(row.stopped)
        assertFalse(row.unread)
        val both = rows(RowAttention(unread = true, stopped = true)).single()
        assertTrue(both.stopped)
        assertFalse(both.unread)
    }

    @Test
    fun `a finished unseen run reads Unread result, nothing else says nothing`() {
        val unread = rows(RowAttention(unread = true)).single()
        assertTrue(unread.unread)
        assertFalse(unread.stopped)
        val none = rows(RowAttention()).single()
        assertEquals(false, none.unread || none.stopped || none.needsYou)
    }
}
