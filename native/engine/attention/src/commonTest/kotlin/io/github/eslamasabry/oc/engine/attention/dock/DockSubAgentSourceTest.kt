package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.SubAgentsRead
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SubAgent
import io.github.eslamasabry.oc.core.model.SubAgentOpen
import io.github.eslamasabry.oc.core.model.SubAgentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** The dock's children come from the one provider-neutral sub-agent source: live events and backfill reads. */
class DockSubAgentSourceTest {
    private val p = ProfileId("p1")
    private val a = SessionId("a")

    private fun ref(n: String) = ConversationRef(p, SessionId(n))

    private fun upserted(id: String, parent: SessionId?, forkOf: SessionId? = null) =
        GatewayEvent.SessionUpserted(Session(SessionId(id), "t-$id", parentId = parent, forkOf = forkOf))

    @Test
    fun `a live child links to its parent, a fork never does`() {
        assertEquals(listOf(DockEvent.Link(ref("c1"), a)), SubAgentChildSource.links(p, upserted("c1", a)))
        assertTrue(SubAgentChildSource.links(p, upserted("f1", a, forkOf = a)).isEmpty(), "a fork is not a sub-agent")
        assertTrue(SubAgentChildSource.links(p, upserted("top", null)).isEmpty())
        assertTrue(DockFeed.events(p, upserted("f1", a, forkOf = a)).none { it is DockEvent.Link })
    }

    @Test
    fun `a backfill read names each sub-agent and hangs it under its parent`() {
        val read =
            SubAgentsRead(
                a,
                persistentListOf(
                    SubAgent(SessionId("c1"), a, "explore", "Look around", SubAgentState.Working, SubAgentOpen.Conversation(SessionId("c1"), false)),
                    SubAgent(SessionId("c2"), a, "Review", "Review", SubAgentState.Done, SubAgentOpen.Conversation(SessionId("c2"), false)),
                ),
            )
        assertEquals(
            listOf(
                DockEvent.Meta(ref("c1"), "Look around", "explore"),
                DockEvent.Link(ref("c1"), a),
                DockEvent.Meta(ref("c2"), "Review", null),
                DockEvent.Link(ref("c2"), a),
            ),
            SubAgentChildSource.events(p, read),
        )
        // Fed to the model, a running child that existed before the app connected groups under its parent.
        val ui =
            (SubAgentChildSource.events(p, read) + DockEvent.RunStarted(ConversationRef(p, a)) + DockEvent.RunStarted(ref("c1")))
                .fold(DockModel.empty()) { m, e -> m.reduce(e) }
                .ui
        assertEquals(listOf(ConversationRef(p, a)), ui.entries.map { it.ref })
        assertEquals(listOf(ref("c1")), ui.entries.single().children.map { it.ref })
        assertEquals("explore", ui.entries.single().children.single().name)
    }
}
