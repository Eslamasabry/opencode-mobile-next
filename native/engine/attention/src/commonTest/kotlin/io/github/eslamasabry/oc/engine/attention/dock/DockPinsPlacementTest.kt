package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.attention.activity.FakeAttentionKeyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class DockPinsPlacementTest {
    private val p = ProfileId("p1")
    private val q = ProfileId("p2")
    private fun ref(n: String, profile: ProfileId = p) = ConversationRef(profile, SessionId(n))
    private val a = ref("a")
    private val b = ref("b")
    private val c = ref("c")

    private fun model(vararg events: DockEvent): DockModel = events.fold(DockModel.empty()) { m, e -> m.reduce(e) }

    private val online = DockEvent.PinsLoaded(p, emptyList())

    // ---- pins

    @Test
    fun `a pinned ring leads, keeps the person's order and stays when idle`() {
        val m = model(online, DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.RunStarted(c), DockEvent.Pin(c), DockEvent.Pin(b))
        assertEquals(listOf(c, b, a), m.ui.entries.map { it.ref }, "pinned in pin order, then the stable rest")
        assertEquals(listOf(true, true, false), m.ui.entries.map { it.pinned })
        val idle = m.reduce(DockEvent.RunEnded(c, DockRunEnd.Stopped)).reduce(DockEvent.RunEnded(a, DockRunEnd.Stopped))
        assertEquals(listOf(c, b, a), idle.ui.entries.map { it.ref }, "c is idle but pinned; a is idle and stays as a recent ring after them")
        assertEquals(DockState.Idle, idle.ui.entries.first().state)
        // Only idle pinned rings: a nub, not nothing.
        val only = model(online, DockEvent.RunStarted(a), DockEvent.Pin(a), DockEvent.RunEnded(a, DockRunEnd.Stopped))
        assertEquals(DockPresence.Nub, only.ui.presence)
        assertEquals(0, only.ui.liveCount)
        assertEquals(DockPresence.Nub, model(online, DockEvent.RunStarted(a), DockEvent.RunEnded(a, DockRunEnd.Stopped)).ui.presence, "one idle recent ring: the nub, never hidden")
    }

    @Test
    fun `reorder moves a pinned ring to another pinned ring's place`() {
        val base = arrayOf(online, DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.RunStarted(c), DockEvent.Pin(a), DockEvent.Pin(b), DockEvent.Pin(c))
        data class Case(val label: String, val move: DockEvent, val expect: List<ConversationRef>)
        val cases =
            listOf(
                Case("last to first", DockEvent.Reorder(c, a), listOf(c, a, b)),
                Case("first to last", DockEvent.Reorder(a, c), listOf(b, c, a)),
                Case("middle to first", DockEvent.Reorder(b, a), listOf(b, a, c)),
                Case("onto itself", DockEvent.Reorder(a, a), listOf(a, b, c)),
                Case("an unpinned ring cannot be moved", DockEvent.Reorder(ref("zz"), a), listOf(a, b, c)),
            )
        for (k in cases) assertEquals(k.expect, model(*base, k.move).pinnedRefs, k.label)
        // Unpinning hands the ring back to the stable order.
        val unpinned = model(*base).reduce(DockEvent.Unpin(a))
        assertEquals(listOf(b, c, a), unpinned.ui.entries.map { it.ref })
        assertFalse(unpinned.ui.entries.last().pinned)
    }

    @Test
    fun `pins honour remove, the cap, deletion, and a server that is not connected`() {
        val m = model(online, DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Pin(a), DockEvent.Remove(a))
        assertEquals(listOf(b), m.ui.entries.map { it.ref })
        assertEquals(listOf(a), m.pinnedRefs, "still pinned")
        assertTrue(a in m.reduce(DockEvent.Restore(a)).ui.entries.map { it.ref })

        val many = (1..DockModel.MAX_PINNED + 2).map { ref("s$it") }
        val full = many.fold(model(online)) { acc, r -> acc.reduce(DockEvent.RunStarted(r)) }.let { start -> many.fold(start) { acc, r -> acc.reduce(DockEvent.Pin(r)) } }
        assertEquals(many.take(DockModel.MAX_PINNED), full.pinnedRefs)

        assertEquals(emptyList(), model(online, DockEvent.RunStarted(a), DockEvent.Pin(a)).reduce(DockEvent.Deleted(a)).pinnedRefs)
        assertEquals(emptyList(), model(online, DockEvent.RunStarted(a), DockEvent.Pin(a)).reduce(DockEvent.ProfileGone(p)).pinnedRefs)
        // A sub-agent cannot be pinned; nor a ring that is not in the dock.
        assertTrue(model(online, DockEvent.Link(b, a.sessionId), DockEvent.RunStarted(b)).reduce(DockEvent.Pin(b)).pinnedRefs.isEmpty())
        assertTrue(model(online).reduce(DockEvent.Pin(a)).pinnedRefs.isEmpty())
        // Offline: pinned rings of that server are not drawn, and come back with the connection.
        val offline = model(online, DockEvent.RunStarted(a), DockEvent.Pin(a), DockEvent.RunEnded(a, DockRunEnd.Stopped)).reduce(DockEvent.ProfileOffline(p))
        assertTrue(offline.ui.entries.isEmpty())
        assertEquals(listOf(a), offline.reduce(DockEvent.PinsLoaded(p, listOf(a.sessionId))).ui.entries.map { it.ref })
    }

    @Test
    fun `saved pins draw idle rings with the names heard so far`() {
        val m = model(DockEvent.PinsLoaded(p, listOf(b.sessionId, a.sessionId)), DockEvent.Meta(a, "Alpha", "build"))
        assertEquals(listOf(b, a), m.ui.entries.map { it.ref })
        assertEquals("Alpha", m.ui.entries.last().title)
        assertEquals("build", m.ui.entries.last().agent)
        // A pin made before the saved list arrived is kept after it, saved ones first.
        val merged = model(online, DockEvent.RunStarted(c), DockEvent.Pin(c)).reduce(DockEvent.PinsLoaded(p, listOf(a.sessionId)))
        assertEquals(listOf(a, c), merged.pinnedRefs)
    }

    @Test
    fun `pins survive a restart in order and an empty list removes the key`() = runTest(StandardTestDispatcher()) {
        val disk = FakeAttentionKeyStore()
        val first = DockController(this, DockNicknames(disk), {}, pins = DockPins(disk))
        first.loadPins(p)
        first.feed(DockEvent.RunStarted(a))
        first.feed(DockEvent.RunStarted(b))
        first.pin(a)
        first.pin(b)
        first.reorder(b, a)
        testScheduler.advanceUntilIdle()
        assertEquals("""["b","a"]""", disk.data[DockKeys.pinsKey(p)])
        val second = DockController(this, DockNicknames(disk), {}, pins = DockPins(disk))
        assertEquals(listOf(SessionId("b"), SessionId("a")), second.loadPins(p))
        assertEquals(listOf(b, a), second.ui.value.entries.map { it.ref })
        second.unpin(a)
        second.unpin(b)
        testScheduler.advanceUntilIdle()
        assertFalse(DockKeys.pinsKey(p) in disk.data)
    }

    // ---- placement

    @Test
    fun `placement defaults to the end edge a little below the middle and clamps`() {
        assertEquals(DockSide.End, DockModel.empty().ui.placement.side)
        assertEquals(DockPlacement.DEFAULT_BIAS, DockModel.empty().ui.placement.bias)
        assertTrue(DockPlacement.DEFAULT_BIAS > 0.5f && DockPlacement.DEFAULT_BIAS < 0.75f)
        data class Case(val label: String, val side: DockSide, val bias: Float, val expect: DockPlacement)
        val cases =
            listOf(
                Case("plain", DockSide.Start, 0.3f, DockPlacement(DockSide.Start, 0.3f)),
                Case("too high", DockSide.End, -2f, DockPlacement(DockSide.End, 0f)),
                Case("too low", DockSide.End, 9f, DockPlacement(DockSide.End, 1f)),
                Case("not a number", DockSide.Start, Float.NaN, DockPlacement(DockSide.Start, DockPlacement.DEFAULT_BIAS)),
            )
        for (k in cases) assertEquals(k.expect, model(DockEvent.SetPlacement(DockPlacement.of(k.side, k.bias))).ui.placement, k.label)
    }

    @Test
    fun `placement is saved for the device and read back`() = runTest(StandardTestDispatcher()) {
        val disk = FakeAttentionKeyStore()
        val first = DockController(this, DockNicknames(disk), {}, placementStore = DockPlacementStore(disk))
        first.setPlacement(DockSide.Start, 0.25f)
        testScheduler.advanceUntilIdle()
        assertEquals("start", disk.data[DockKeys.SIDE])
        assertEquals("0.25", disk.data[DockKeys.BIAS])
        val second = DockController(this, DockNicknames(disk), {}, placementStore = DockPlacementStore(disk))
        second.loadPlacement()
        assertEquals(DockPlacement(DockSide.Start, 0.25f), second.ui.value.placement)
        // Nothing saved, or junk: the defaults.
        val blank = DockController(this, DockNicknames(FakeAttentionKeyStore()), {}, placementStore = DockPlacementStore(FakeAttentionKeyStore()))
        blank.loadPlacement()
        assertEquals(DockPlacement(), blank.ui.value.placement)
        disk.data[DockKeys.BIAS] = "high"
        disk.data[DockKeys.SIDE] = "sideways"
        assertEquals(DockPlacement(), DockPlacementStore(disk).load())
    }

    @Test
    fun `only the keys with a profile id are swept`() {
        val id = "1735000000000001"
        val keys = listOf("oc.dockNames.$id", "oc.dockRemoved.$id", "oc.dockPins.$id", DockKeys.SIDE, DockKeys.BIAS, "oc.dockPins.1735000000000002")
        assertEquals(
            setOf("oc.dockNames.$id", "oc.dockRemoved.$id", "oc.dockPins.$id"),
            ProfileKeyRule.scopedKeys(id, keys, emptySet()),
        )
        assertTrue(DockKeys.SHAPES.any { it.prefix == DockKeys.PINS_PREFIX })
    }

    // ---- server and agent facts

    @Test
    fun `entries carry their server and the dock says when several are live`() {
        val x = ref("x", q)
        val names = arrayOf(DockEvent.ServerName(p, "Home box"), DockEvent.ServerName(q, "Work"))
        val one = model(*names, DockEvent.RunStarted(a), DockEvent.RunStarted(b))
        assertEquals(listOf("Home box", "Home box"), one.ui.entries.map { it.serverName })
        assertFalse(one.ui.severalServers)
        val two = one.reduce(DockEvent.RunStarted(x))
        assertTrue(two.ui.severalServers)
        assertEquals("Work", two.ui.entries.first { it.ref == x }.serverName)
        // A server whose only ring is idle (open, kept beside a live one) does not make it several.
        val idleOther = model(*names, DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Opened(x))
        assertFalse(idleOther.ui.severalServers)
        // Children carry the name too, and a blank name clears it.
        val kid = model(*names, DockEvent.Link(b, a.sessionId), DockEvent.RunStarted(a), DockEvent.RunStarted(b))
        assertEquals("Home box", kid.ui.entries.single().children.single().serverName)
        assertEquals(null, kid.reduce(DockEvent.ServerName(p, " ")).ui.entries.single().serverName)
        assertFalse(model(DockEvent.RunStarted(a)).ui.severalServers)
    }

    @Test
    fun `an assistant message names the agent when the session record has not`() {
        val message = MessageInfo(id = "m1", sessionId = a.sessionId.raw, role = Role.Assistant, agent = "explore")
        val ui = (DockFeed.events(p, GatewayEvent.RunStarted(a.sessionId)) + DockFeed.events(p, GatewayEvent.MessageUpserted(message)))
            .fold(DockModel.empty()) { m, e -> m.reduce(e) }.ui
        assertEquals("explore", ui.entries.single().agent)
        val user = MessageInfo(id = "m2", sessionId = a.sessionId.raw, role = Role.User, agent = "build")
        // Changed on purpose: the person's own message is a send (it tucks the dock), no longer "nothing".
        assertEquals(listOf<DockEvent>(DockEvent.Sent(a)), DockFeed.events(p, GatewayEvent.MessageUpserted(user)))
    }

    // ---- peek

    private fun text(part: String, message: String, session: String, body: String, synthetic: Boolean = false) =
        GatewayEvent.PartUpserted(Part(id = part, messageId = message, sessionId = session, type = PartType.Text, rawType = "text", text = body, synthetic = synthetic))

    @Test
    fun `a peek shows the newest lines from the stream, in words, with no read`() = runTest {
        val peeks = DockPeeks()
        assertEquals(DockPeek.Unavailable, peeks.peek(a).first(), "nothing cached")
        peeks.feed(p, GatewayEvent.MessageUpserted(MessageInfo("m1", "a", Role.User)))
        peeks.feed(p, GatewayEvent.MessageUpserted(MessageInfo("m2", "a", Role.Assistant)))
        peeks.feed(p, text("p1", "m1", "a", "  Fix the\n bank API "))
        peeks.feed(p, text("p2", "m2", "a", "## Done\n**Fixed** it in `api.kt`, see [notes](http://x)"))
        peeks.feed(p, text("p3", "m2", "a", "hidden", synthetic = true))
        val lines = assertIs<DockPeek.Lines>(peeks.peek(a).first())
        assertEquals(
            listOf(DockPeekRow(true, "Fix the bank API"), DockPeekRow(false, "Done Fixed it in api.kt, see notes")),
            lines.rows,
        )
        assertEquals(DockPeek.Unavailable, peeks.peek(b).first())
        // A part that grows replaces its line; only the newest few rows stay.
        peeks.feed(p, text("p2", "m2", "a", "Updated"))
        assertEquals("Updated", assertIs<DockPeek.Lines>(peeks.peek(a).first()).rows.last().text)
        for (i in 1..6) peeks.feed(p, text("q$i", "m2", "a", "line $i"))
        val capped = assertIs<DockPeek.Lines>(peeks.peek(a).first()).rows
        assertEquals(DockPeeks.MAX_ROWS, capped.size)
        assertEquals("line 6", capped.last().text)
        peeks.feed(p, GatewayEvent.SessionDeleted(a.sessionId))
        assertEquals(DockPeek.Unavailable, peeks.peek(a).first())
    }

    @Test
    fun `a quiet conversation falls back to the home list's saved line, and many conversations are bounded`() = runTest {
        val peeks = DockPeeks { r -> if (r == b) "saved line" else null }
        assertEquals(listOf(DockPeekRow(false, "saved line")), assertIs<DockPeek.Lines>(peeks.peek(b).first()).rows)
        assertEquals(DockPeek.Unavailable, peeks.peek(a).first())
        for (i in 1..DockPeeks.MAX_CONVERSATIONS + 3) peeks.feed(p, text("p$i", "m", "s$i", "hi $i"))
        assertEquals(DockPeek.Unavailable, peeks.peek(ref("s1")).first(), "the oldest conversation was forgotten")
        assertIs<DockPeek.Lines>(peeks.peek(ref("s${DockPeeks.MAX_CONVERSATIONS + 3}")).first())
        assertEquals(DockPeeks.MAX_LINE, DockPeeks.oneLine("x".repeat(500))!!.length)
        assertEquals(null, DockPeeks.oneLine("  \n "))
    }

    // ---- actions

    private class FakeActions : DockActions {
        val calls = mutableListOf<String>()

        override suspend fun answerPermission(ref: ConversationRef, requestId: String, allow: Boolean): Outcome<Unit> {
            calls += "answer ${ref.sessionId.raw} $requestId $allow"
            return Outcome.Ok(Unit)
        }

        override suspend fun stop(ref: ConversationRef): Outcome<Unit> {
            calls += "stop ${ref.sessionId.raw}"
            return Outcome.Ok(Unit)
        }
    }

    @Test
    fun `the sheet's answer and stop go to the existing paths, once, and questions are refused`() = runTest {
        val actions = FakeActions()
        val dock = DockController(this, DockNicknames(FakeAttentionKeyStore()), {}, actions = actions)
        assertTrue(dock.answer(a, DockRequestRef(DockRequestKind.Permission, "r1"), true) is Outcome.Ok)
        assertTrue(dock.answer(a, DockRequestRef(DockRequestKind.Permission, "r2"), false) is Outcome.Ok)
        assertTrue(dock.stop(b) is Outcome.Ok)
        val refused = dock.answer(a, DockRequestRef(DockRequestKind.Question, "q1"), true)
        assertIs<Failure.Unavailable>((refused as Outcome.Failed).failure)
        assertEquals(listOf("answer a r1 true", "answer a r2 false", "stop b"), actions.calls)
        val none = DockController(this, DockNicknames(FakeAttentionKeyStore()), {})
        assertTrue(none.stop(a) is Outcome.Failed)
        assertTrue(none.answer(a, DockRequestRef(DockRequestKind.Permission, "r"), true) is Outcome.Failed)
        assertEquals(DockPeek.Unavailable, none.peek(a).first())
    }
}
