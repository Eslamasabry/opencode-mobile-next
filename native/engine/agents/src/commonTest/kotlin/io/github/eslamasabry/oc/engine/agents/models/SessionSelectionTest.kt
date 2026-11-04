package io.github.eslamasabry.oc.engine.agents.models

import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionSelectionTest {
    private fun json(text: String) = AgentJson.parseOrNull(text) as JsonObject

    private fun ref(id: String) = ModelRef("p", id)

    private fun session(model: String = "one", agent: String = "build", variant: String = "") =
        SessionSelection(ref(model), variant, agent)

    /** Applies one wire event the way the controller does; non-selection and malformed events are ignored. */
    private fun SessionSelections.event(type: String, data: String): SessionSelections =
        SelectionEvent.parse(type, json(data))?.let { apply(it).selections } ?: this

    @Test
    fun `B-PROTO-102 v2 reads retain variants and distinguish inherited from unknown`() {
        val mapped = SessionSelection.fromWireSession(
            json("""{"id":"a","model":{"providerID":"p","id":"one","variant":"high"},"agent":"plan"}"""),
        )
        assertEquals("high", mapped.variant)
        assertEquals("", SessionSelection.fromWireSession(json("""{"id":"a","model":{"providerID":"p","id":"one","variant":"default"}}""")).variant)
        val selections = SessionSelections().hydrate("a", SessionSelection.fromWireSession(json("""{"id":"a"}""")))
        assertNull(selections.modelFor("a"))
        assertEquals("", selections.agentFor("a"))
        assertEquals("", selections.variantFor("a"))
        assertTrue(selections.selectionFor("a").modelKnown)
        assertFalse(selections.selectionFor("missing").modelKnown)
    }

    @Test
    fun `B-PROTO-102 live selection replaces variant and patches only the matching session`() {
        var s = SessionSelections()
            .hydrate("a", session(variant = "high"))
            .hydrate("b", session())
        s = s.event("session.model.selected", """{"sessionID":"a","model":{"providerID":"p","id":"other"}}""")
        s = s.event("session.agent.selected", """{"sessionID":"a","agent":"plan"}""")
        s = s.event("session.renamed", """{"sessionID":"a","title":"Renamed"}""")
        assertEquals("p/other", s.modelFor("a")!!.wireName)
        assertEquals("", s.variantFor("a"))
        assertEquals("plan", s.agentFor("a"))
        assertEquals("p/one", s.modelFor("b")!!.wireName)
        assertEquals("build", s.agentFor("b"))
        // Only selection events are selection changes; a move keeps the chat's choice.
        assertNull(SelectionEvent.parse("session.renamed", json("""{"sessionID":"a","title":"Renamed"}""")))
        s = s.event("session.moved", """{"sessionID":"a","projectID":"new-project","location":{"directory":"/other"}}""")
        assertEquals("p/other", s.modelFor("a")!!.wireName)
        // A composite id in an event is sent bare.
        s = s.event("session.model.selected", """{"sessionID":"b","model":{"providerID":"p","id":"p/two","variant":"low"}}""")
        assertEquals("p/two", s.modelFor("b")!!.wireName)
        assertEquals("low", s.variantFor("b"))
    }

    @Test
    fun `B-PROTO-102 a model event cannot establish an unhydrated agent or clear with malformed data`() {
        val applied = SessionSelections().apply(
            SelectionEvent.parse("session.model.selected", json("""{"sessionID":"unloaded","model":{"providerID":"p","id":"remote"}}"""))!!,
        )
        var s = applied.selections
        assertTrue(applied.needsRefresh)
        assertFalse(s.selectionFor("unloaded").agentKnown)
        assertNull(SelectionEvent.parse("session.model.selected", json("""{"sessionID":"unloaded"}""")))
        assertNull(SelectionEvent.parse("session.model.selected", json("""{"sessionID":"unloaded","model":{"id":"x"}}""")))
        assertNull(SelectionEvent.parse("session.agent.selected", json("""{"sessionID":"unloaded","agent":""}""")))
        s = s.event("session.model.selected", """{"sessionID":"unloaded"}""")
        assertEquals("p/remote", s.modelFor("unloaded")!!.wireName)
        s = s.hydrate("unloaded", session(model = "remote", agent = "plan"))
        assertEquals("plan", s.agentFor("unloaded"))
    }

    @Test
    fun `B-PROTO-102 a deleted session is not revived by a late event`() {
        val s = SessionSelections().hydrate("a", session()).delete("a")
            .event("session.agent.selected", """{"sessionID":"a","agent":"plan"}""")
        assertFalse(s.selectionFor("a").modelKnown)
    }
}
