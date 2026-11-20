package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.QuestionPrompt
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class DockListTest {
    private val p = ProfileId("p1")
    private val q = ProfileId("p2")
    private fun ref(n: String, profile: ProfileId = p) = ConversationRef(profile, SessionId(n))
    private val a = ref("a")
    private val b = ref("b")
    private val c = ref("c")
    private val d = ref("d")
    private val e = ref("e")
    private var clock = 5_000L
    private val facts = DockFacts { clock }

    private fun permission(session: SessionId, id: String, kind: String, vararg meta: Pair<String, String>) =
        PermissionRequest(
            RequestId(id), session, kind, persistentListOf(), JsonObject(meta.associate { it.first to JsonPrimitive(it.second) }),
            persistentListOf(), null, false, null,
        )

    private fun question(session: SessionId, id: String, text: String) =
        PendingQuestion(RequestId(id), session, persistentListOf(QuestionPrompt("T", text, false, true, false, persistentListOf(), false)))

    /** Feeds one gateway event the way the wiring does: the facts first, then the reducer. */
    private fun DockModel.hear(event: GatewayEvent, profile: ProfileId = p): DockModel {
        facts.feed(profile, event)
        return DockFeed.events(profile, event).fold(this) { m, ev -> m.reduce(ev) }
    }

    private fun build(m: DockModel, newest: (ConversationRef) -> String? = { null }) = DockList.build(m.ui, facts, newest)

    @Test
    fun `the five states, progress and thinking are read from the rings`() {
        val m =
            DockModel.empty(cap = 20)
                .reduce(DockEvent.RunStarted(a)).reduce(DockEvent.Tasks(a, 3, 7))
                .hear(GatewayEvent.PermissionAsked(permission(b.sessionId, "r1", "edit", "filePath" to "/work/wait7.txt")))
                .reduce(DockEvent.RunStarted(c)).reduce(DockEvent.RunEnded(c, DockRunEnd.Failed))
                .reduce(DockEvent.RunStarted(d)).reduce(DockEvent.RunEnded(d, DockRunEnd.Succeeded))
                .reduce(DockEvent.RunStarted(e)).reduce(DockEvent.Reasoning(e, true))
        val rows = build(m).rows.associateBy { it.ref }
        assertEquals(DockListState.Working, rows.getValue(a).state)
        assertEquals(DockProgress(3, 7), rows.getValue(a).progress)
        assertFalse(rows.getValue(a).thinking)
        assertEquals(DockListState.NeedsYou, rows.getValue(b).state)
        assertEquals(DockListState.Failed, rows.getValue(c).state)
        assertEquals(DockListState.Done, rows.getValue(d).state, "finished and not opened")
        assertEquals(DockListState.Working, rows.getValue(e).state)
        assertTrue(rows.getValue(e).thinking)
        assertNull(rows.getValue(e).progress)
        val model = build(m)
        assertEquals(1, model.needsCount)
        assertEquals(1, model.failedCount)
        assertEquals(2, model.count, "the face's number is needs you plus failed")
    }

    @Test
    fun `a request's words are one short line with secrets masked`() {
        val secret = "sk-abcdefghijkl1234"
        val m =
            DockModel.empty(cap = 20)
                .hear(GatewayEvent.PermissionAsked(permission(a.sessionId, "r1", "edit", "filePath" to "/work/wait7.txt")))
                .hear(GatewayEvent.PermissionAsked(permission(b.sessionId, "r2", "bash", "command" to "npm install left-pad --token $secret")))
                .hear(GatewayEvent.QuestionAsked(question(c.sessionId, "r3", "Use the key $secret\nfor the deploy?")))
        val rows = build(m).rows.associateBy { it.ref }
        assertEquals(DockRequestWords(DockRequestKind.Permission, "r1", "edit", "wait7.txt", ""), rows.getValue(a).request)
        val run = rows.getValue(b).request!!
        assertEquals("run", run.action)
        assertEquals("npm install", run.target)
        val ask = rows.getValue(c).request!!
        assertEquals(DockRequestKind.Question, ask.kind)
        assertFalse(secret in ask.text, "the secret never reaches the list")
        assertTrue(ask.text.startsWith("Use the key") && "\n" !in ask.text)
        // Settled: the words go with the request.
        val after = m.hear(GatewayEvent.PermissionSettled(a.sessionId, RequestId("r1")))
        assertNull(facts.requestWords(a, DockRequestRef(DockRequestKind.Permission, "r1")))
        assertNull(build(after).rows.firstOrNull { it.ref == a }?.request)
    }

    @Test
    fun `a request read at reconnect has words too and replaces what the server no longer holds`() {
        facts.requestsRead(p, listOf(permission(a.sessionId, "r1", "read", "path" to "/x/notes.md")), emptyList())
        assertEquals("read", facts.requestWords(a, DockRequestRef(DockRequestKind.Permission, "r1"))?.action)
        facts.requestsRead(p, emptyList(), emptyList())
        assertNull(facts.requestWords(a, DockRequestRef(DockRequestKind.Permission, "r1")))
        facts.requestsRead(p, null, emptyList())
        assertNull(facts.requestWords(a, DockRequestRef(DockRequestKind.Permission, "r1")), "a failed read changes nothing")
    }

    @Test
    fun `a sub-agent has its own state, its request words and is capped with the count beyond`() {
        val kids = (1..6).map { ref("k$it") }
        var m = DockModel.empty(cap = 20).reduce(DockEvent.RunStarted(a))
        for (k in kids) m = m.reduce(DockEvent.Meta(k, "t", "explore", a.sessionId)).reduce(DockEvent.RunStarted(k))
        m = m.hear(GatewayEvent.PermissionAsked(permission(kids[0].sessionId, "rk", "bash", "command" to "git push")))
        val row = build(m).rows.single()
        assertEquals(DockListState.NeedsYou, row.state, "a sub-agent that needs you raises its parent")
        assertEquals(kids[0], row.causedBy)
        assertEquals("git push", row.request?.target, "the parent shows the sub-agent's words")
        assertEquals(DockModel.DEFAULT_CHILD_CAP, row.subAgents.size)
        assertEquals(6 - DockModel.DEFAULT_CHILD_CAP, row.subAgentsMore)
        val asking = row.subAgents.first { it.ref == kids[0] }
        assertEquals(DockListState.NeedsYou, asking.state)
        assertEquals("explore", asking.name)
        assertEquals("git push", asking.request?.target)
        assertTrue(row.subAgents.filter { it.ref != kids[0] }.all { it.state == DockListState.Working && it.request == null })
    }

    @Test
    fun `the server is named only when more than one server is listed`() {
        val one = DockModel.empty(cap = 20).reduce(DockEvent.ServerName(p, "Home")).reduce(DockEvent.RunStarted(a))
        assertNull(build(one).rows.single().serverName)
        assertFalse(build(one).severalServers)
        val two = one.reduce(DockEvent.ServerName(q, "Work")).reduce(DockEvent.RunStarted(ref("x", q)))
        val rows = build(two).rows.associateBy { it.ref }
        assertEquals("Home", rows.getValue(a).serverName)
        assertEquals("Work", rows.getValue(ref("x", q)).serverName)
        assertTrue(build(two).severalServers)
    }

    @Test
    fun `pinned, the open conversation and the newest line are on the row, the line masked`() {
        val m =
            DockModel.empty(cap = 20).reduce(DockEvent.PinsLoaded(p, emptyList()))
                .reduce(DockEvent.RunStarted(a)).reduce(DockEvent.RunStarted(b)).reduce(DockEvent.Pin(b)).reduce(DockEvent.Opened(a))
        val model = build(m) { if (it == b) "Done. password=hunter2 is set\nnext line" else null }
        val rows = model.rows.associateBy { it.ref }
        assertTrue(rows.getValue(b).pinned)
        assertFalse(rows.getValue(a).pinned)
        assertTrue(rows.getValue(a).isOpen)
        assertFalse(rows.getValue(b).isOpen)
        val line = rows.getValue(b).newestLine!!
        assertFalse("hunter2" in line)
        assertNull(rows.getValue(a).newestLine)
    }

    @Test
    fun `last activity is when the stream last showed it, else the home list's time`() {
        facts.listed(p, listOf(DockRecent(a.sessionId, "A", 1_000L), DockRecent(b.sessionId, "B", 2_000L)))
        assertEquals(1_000L, facts.lastActivity(a))
        clock = 9_000L
        facts.feed(p, GatewayEvent.RunStarted(a.sessionId))
        assertEquals(9_000L, facts.lastActivity(a))
        assertEquals(2_000L, facts.lastActivity(b))
        assertNull(facts.lastActivity(c))
        val m = DockModel.empty(cap = 20).reduce(DockEvent.RunStarted(a))
        assertEquals(9_000L, build(m).rows.single().lastActivityMs)
        facts.forget(a)
        assertNull(facts.lastActivity(a))
    }

    @Test
    fun `a session record seen on connect is not activity and a conversation from two hours ago keeps its age`() {
        val twoHoursAgo = 100_000_000L - 2 * 60 * 60_000L
        clock = 100_000_000L
        facts.listed(p, listOf(DockRecent(a.sessionId, "A", twoHoursAgo)))
        facts.feed(p, GatewayEvent.SessionUpserted(Session(a.sessionId, "A", time = SessionTime(updatedMs = twoHoursAgo))))
        facts.feed(p, GatewayEvent.SessionUpserted(Session(b.sessionId, "B")))
        assertEquals(twoHoursAgo, facts.lastActivity(a))
        assertNull(facts.lastActivity(b))
    }

    @Test
    fun `eviction drops the oldest by time, so re-seeing an old record keeps the recently active conversation`() {
        val max = 256 // the dock's bound (private in DockFacts)
        // The old one first, then the recent one, then others up to exactly the bound.
        clock = 1_000L
        facts.feed(p, GatewayEvent.RunStarted(SessionId("old")))
        clock = 1_000_000L
        val recent = SessionId("recent")
        facts.feed(p, GatewayEvent.RunStarted(recent))
        for (i in 1 until max - 1) { clock += 10; facts.feed(p, GatewayEvent.RunStarted(SessionId("s$i"))) }
        // Full now (old + recent + max-2 others). Re-seeing the old record (server time 2_000) must not move it
        // to the newest place: adding one more conversation then evicts the oldest by time, never the recent one.
        facts.feed(p, GatewayEvent.SessionUpserted(Session(SessionId("old"), "old", time = SessionTime(updatedMs = 2_000L))))
        clock += 10
        facts.feed(p, GatewayEvent.RunStarted(SessionId("extra")))
        assertEquals(1_000_000L, facts.lastActivity(ConversationRef(p, recent)))
        assertNull(facts.lastActivity(ConversationRef(p, SessionId("old"))))
    }

    @Test
    fun `a hidden conversation is listed with its state and comes back with restore`() {
        var m = DockModel.empty(cap = 20).reduce(DockEvent.RunStarted(a)).reduce(DockEvent.RunStarted(b)).reduce(DockEvent.RunEnded(b, DockRunEnd.Failed))
        m = m.reduce(DockEvent.Remove(b))
        val hiding = build(m)
        assertEquals(listOf(a), hiding.rows.map { it.ref })
        assertEquals(1, hiding.hiddenCount)
        assertEquals(DockListHidden(b, null, DockListState.Failed, null), hiding.hidden.single())
        val back = build(m.reduce(DockEvent.Restore(b)))
        assertEquals(0, back.hiddenCount)
        assertEquals(setOf(a, b), back.rows.map { it.ref }.toSet())
    }

    @Test
    fun `rows beyond the reader's cap are counted, not dropped silently`() {
        var m = DockModel.empty(cap = 2)
        for (r in listOf(a, b, c, d)) m = m.reduce(DockEvent.RunStarted(r))
        val model = build(m)
        assertEquals(2, model.rows.size)
        assertEquals(2, model.more)
    }

    @Test
    fun `B-LAMP-001 idle parent with working sub-agent has ownStateRunning false while working parent has true`() {
        val child = ConversationRef(p, SessionId("child"))
        val idleParent =
            DockModel.empty(cap = 20)
                .reduce(DockEvent.Meta(child, "Explore", "explore", a.sessionId))
                .reduce(DockEvent.RunStarted(child))
        val idleRow = build(idleParent).rows.single()
        assertEquals(DockListState.Working, idleRow.state)
        assertFalse(idleRow.ownStateRunning)

        val runningParent = idleParent.reduce(DockEvent.RunStarted(a))
        val runningRow = build(runningParent).rows.single()
        assertEquals(DockListState.Working, runningRow.state)
        assertTrue(runningRow.ownStateRunning)
    }
}
