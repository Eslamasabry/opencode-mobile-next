package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.feature.chats.presentation.AllConversationsRules
import io.github.eslamasabry.oc.feature.chats.presentation.AllModel
import io.github.eslamasabry.oc.feature.chats.presentation.ServerName
import io.github.eslamasabry.oc.feature.chats.presentation.SourcePages
import kotlin.test.Test
import kotlin.test.assertEquals

/** A row read from a saved list never sits among live rows, even when it is newer than some of them. */
class SavedRowsLastTest {
    @Test
    fun `saved only rows come after every live row`() {
        val live = ProfileId("live")
        val away = ProfileId("away")
        fun session(id: String, updated: Long) = Session(SessionId(id), time = SessionTime(createdMs = updated, updatedMs = updated))
        val rows =
            AllConversationsRules.build(
                    AllModel(
                        known = true,
                        servers = listOf(ServerName(live, "Phone"), ServerName(away, "Laptop")),
                        sources =
                            mapOf(
                                live to SourcePages(sessions = listOf(session("old", 1), session("older", 0)), loaded = true),
                                away to SourcePages(sessions = listOf(session("saved", 9)), preview = true),
                            ),
                    )
                )
                .rows
        assertEquals(listOf("old", "older", "saved"), rows.map { it.ref.sessionId.raw })
        assertEquals(listOf(false, false, true), rows.map { it.readOnly })
    }
}
