package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports test/agent_handoff_links_test.dart. */
class HandoffLinksTest {
    private fun prefs() = FakeAgentPrefs(mapOf("oc.profiles" to """[{"id":"phone"},{"id":"claude"},{"id":"other"}]"""))

    private fun link(
        id: String = "handoff-1",
        source: String = "phone",
        target: String = "claude",
        title: String = "Fix the parser",
        sourceSession: String = "session-source",
        targetSession: String = "session-target",
        sourceOrigin: HandoffOrigin = HandoffOrigin.Server,
        targetOrigin: HandoffOrigin = HandoffOrigin.Server,
    ) = HandoffLink(
        id = id,
        sourceProfileId = source,
        sourceSessionId = sourceSession,
        sourceOrigin = sourceOrigin,
        sourceTitle = title,
        targetProfileId = target,
        targetSessionId = targetSession,
        targetOrigin = targetOrigin,
        targetAgent = "Claude Code",
        projectDirectory = "/projects/parser",
        handoffPath = "/projects/parser/.handoff/session-source.md",
    )

    private fun saved(outcome: Outcome<HandoffLinkSave>) = assertEquals(HandoffLinkSave.Saved.ok(), outcome)

    private fun refused(reason: HandoffLinkRefusal, outcome: Outcome<HandoffLinkSave>) =
        assertEquals(HandoffLinkSave.Refused(reason).ok(), outcome)

    private fun HandoffLinks.of(profile: String, session: String, origin: HandoffOrigin? = null) =
        forSession(profile, session, origin) ?: error("links unavailable")

    @Test
    fun `B-STATE-001 one durable record supplies both conversation directions after restart`() = runTest {
        val prefs = prefs()
        val store = HandoffLinks(prefs)
        saved(store.save(link()))
        assertEquals("Claude Code", store.of("phone", "session-source").single().targetAgent)
        assertEquals("Fix the parser", store.of("claude", "session-target").single().sourceTitle)
        assertTrue("oc.agentHandoffs.phone" in prefs.values)
        assertFalse("oc.agentHandoffs.claude" in prefs.values)

        val restarted = HandoffLinks(FakeAgentPrefs(prefs.values))
        assertEquals("handoff-1", restarted.of("claude", "session-target").single().id)
        assertTrue(restarted.of("other", "session-target").isEmpty())
    }

    @Test
    fun `B-SEC-003 redacts display metadata and rejects secret-bearing identifiers`() = runTest {
        val prefs = prefs()
        val store = HandoffLinks(prefs)
        saved(store.save(link(title = "API_KEY=synthetic-private-value")))
        assertFalse((prefs.values["oc.agentHandoffs.phone"] as String).contains("synthetic-private-value"))
        refused(HandoffLinkRefusal.Invalid, store.save(link(id = "token=synthetic-private-value")))
        assertFalse((prefs.values["oc.agentHandoffs.phone"] as String).contains("synthetic-private-value"))
    }

    @Test
    fun `B-STATE-001 same-profile handoff has one link and saves are idempotent`() = runTest {
        val store = HandoffLinks(prefs())
        val same = link(target = "phone")
        saved(store.save(same))
        saved(store.save(same))
        assertEquals(1, store.of("phone", "session-source").size)
        assertEquals(1, store.of("phone", "session-target").size)
        refused(HandoffLinkRefusal.Conflict, store.save(link(target = "phone", title = "Different work")))
    }

    @Test
    fun `B-STATE-001 runtime origins distinguish identical session IDs under one parent`() = runTest {
        val prefs = prefs()
        val store = HandoffLinks(prefs)
        saved(store.save(link(id = "server-link", target = "phone", sourceSession = "shared", targetSession = "server-target")))
        saved(
            store.save(
                link(
                    id = "agent-link",
                    target = "phone",
                    sourceSession = "shared",
                    targetSession = "agent-target",
                    sourceOrigin = HandoffOrigin.PhoneAgent,
                    targetOrigin = HandoffOrigin.PhoneAgent,
                ),
            ),
        )
        assertEquals("server-link", store.of("phone", "shared", HandoffOrigin.Server).single().id)
        assertEquals("agent-link", store.of("phone", "shared", HandoffOrigin.PhoneAgent).single().id)
        assertTrue(store.of("phone", "agent-target", HandoffOrigin.Server).isEmpty())
        assertEquals(2, store.of("phone", "shared").size)

        val restarted = HandoffLinks(FakeAgentPrefs(prefs.values))
        val persisted = restarted.of("phone", "agent-target", HandoffOrigin.PhoneAgent).single()
        assertEquals(HandoffOrigin.PhoneAgent, persisted.sourceOrigin)
        assertEquals(HandoffOrigin.PhoneAgent, persisted.targetOrigin)
        saved(store.removeProfile("phone"))
        assertFalse("oc.agentHandoffs.phone" in prefs.values)
    }

    @Test
    fun `B-STATE-001 cross-origin links use the durable parent without a transient profile row`() = runTest {
        val store = HandoffLinks(prefs())
        saved(store.save(link(target = "phone", targetOrigin = HandoffOrigin.PhoneAgent)))
        assertEquals(HandoffOrigin.PhoneAgent, store.of("phone", "session-source", HandoffOrigin.Server).single().targetOrigin)
        assertEquals(HandoffOrigin.Server, store.of("phone", "session-target", HandoffOrigin.PhoneAgent).single().sourceOrigin)
        assertTrue(store.of("phone", "session-target", HandoffOrigin.Server).isEmpty())
    }

    @Test
    fun `B-STATE-001 concurrent saves keep each conversation link`() = runTest {
        val store = HandoffLinks(prefs())
        listOf(
            async { store.save(link(id = "one")) },
            async { store.save(link(id = "two")) },
            async { store.save(link(id = "three", source = "other")) },
        ).awaitAll().forEach(::saved)
        assertEquals(2, store.of("phone", "session-source").size)
        assertEquals(3, store.of("claude", "session-target").size)
    }

    @Test
    fun `B-STATE-001 deleting target removes references from surviving source profiles`() = runTest {
        val prefs = prefs()
        val store = HandoffLinks(prefs)
        saved(store.save(link()))
        saved(store.save(link(id = "unrelated", target = "other")))
        saved(store.removeProfile("claude"))
        assertEquals("unrelated", store.of("phone", "session-source").single().id)
        assertFalse((prefs.values["oc.agentHandoffs.phone"] as String).contains("claude"))
        refused(HandoffLinkRefusal.ProfileUnavailable, store.save(link()))
    }

    @Test
    fun `B-STATE-001 deleting source removes its canonical key`() = runTest {
        val prefs = prefs()
        val store = HandoffLinks(prefs)
        saved(store.save(link()))
        saved(store.removeProfile("phone"))
        assertFalse("oc.agentHandoffs.phone" in prefs.values)
        assertTrue(store.of("claude", "session-target").isEmpty())
    }

    @Test
    fun `B-STATE-001 refused persistence reports failure and leaves nothing stored`() = runTest {
        val prefs = prefs()
        val store = HandoffLinks(prefs)
        prefs.refuseWrites = true
        assertIs<Outcome.Failed>(store.save(link()))
        assertFalse("oc.agentHandoffs.phone" in prefs.values)
        prefs.refuseWrites = false
        saved(store.save(link()))
        assertEquals(1, store.of("phone", "session-source").size)
    }

    @Test
    fun `B-STATE-001 refused cross-profile removal remains retryable and is reported`() = runTest {
        val prefs = prefs()
        val store = HandoffLinks(prefs)
        saved(store.save(link()))
        prefs.refuseWrites = true
        assertIs<Outcome.Failed>(store.removeProfile("claude"))
        assertContains(prefs.values["oc.agentHandoffs.phone"] as String, "claude")
        prefs.refuseWrites = false
        saved(store.removeProfile("claude"))
        assertFalse("oc.agentHandoffs.phone" in prefs.values)
    }

    @Test
    fun `B-STATE-001 a closed profile admits no new link until deletion is cancelled`() = runTest {
        val prefs = prefs()
        val store = HandoffLinks(prefs)
        store.closeProfile("claude")
        assertFalse(store.admitsProfiles("phone", "claude"))
        assertNull(store.forSession("claude", "session-target"))
        refused(HandoffLinkRefusal.ProfileUnavailable, store.save(link()))
        store.cancelDeletion("claude")
        saved(store.save(link(id = "fresh")))
        assertEquals("fresh", store.of("phone", "session-source").single().id)
    }

    @Test
    fun `B-STATE-001 corrupt stored links fail closed instead of replacing history`() = runTest {
        val prefs = prefs()
        prefs.values["oc.agentHandoffs.phone"] = "{broken"
        val store = HandoffLinks(prefs)
        assertNull(store.forSession("phone", "session-source"))
        refused(HandoffLinkRefusal.Unreadable, store.save(link()))
        assertEquals("{broken", prefs.values["oc.agentHandoffs.phone"])
        saved(store.removeProfile("phone"))
        assertFalse("oc.agentHandoffs.phone" in prefs.values)
    }
}
