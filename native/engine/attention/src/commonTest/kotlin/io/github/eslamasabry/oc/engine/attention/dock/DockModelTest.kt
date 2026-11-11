package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DockModelTest {
    private val p = ProfileId("p1")
    private fun ref(n: String, profile: ProfileId = p) = ConversationRef(profile, SessionId(n))
    private val a = ref("a")
    private val b = ref("b")
    private val c = ref("c")

    private fun run(vararg events: DockEvent, cap: Int = DockModel.DEFAULT_CAP): DockUi =
        events.fold(DockModel.empty(cap)) { m, e -> m.reduce(e) }.ui

    private fun DockUi.state(r: ConversationRef): DockState? = entries.firstOrNull { it.ref == r }?.state

    // ---- state derivation (one row per state of the owner's table)

    @Test
    fun `each fact derives its state`() {
        data class Case(val label: String, val events: List<DockEvent>, val expect: DockState?)
        val cases =
            listOf(
                Case("busy with a task list", listOf(DockEvent.RunStarted(a), DockEvent.Tasks(a, 3, 7)), DockState.WorkingWithTasks(3, 7)),
                Case("busy, no task list is never a percentage", listOf(DockEvent.RunStarted(a)), DockState.Working),
                Case("an empty list is no list", listOf(DockEvent.RunStarted(a), DockEvent.Tasks(a, 0, 0)), DockState.Working),
                Case("busy and reasoning", listOf(DockEvent.RunStarted(a), DockEvent.Reasoning(a, true)), DockState.Thinking),
                Case("tasks beat thinking", listOf(DockEvent.RunStarted(a), DockEvent.Reasoning(a, true), DockEvent.Tasks(a, 1, 2)), DockState.WorkingWithTasks(1, 2)),
                Case("reasoning ended", listOf(DockEvent.RunStarted(a), DockEvent.Reasoning(a, true), DockEvent.Reasoning(a, false)), DockState.Working),
                Case("done is clamped to the total", listOf(DockEvent.RunStarted(a), DockEvent.Tasks(a, 9, 4)), DockState.WorkingWithTasks(4, 4)),
                Case("a request beats working", listOf(DockEvent.RunStarted(a), DockEvent.RequestOpened(a, "r1")), DockState.NeedsYou),
                Case("answered request is working again", listOf(DockEvent.RunStarted(a), DockEvent.RequestOpened(a, "r1"), DockEvent.RequestSettled(a, "r1")), DockState.Working),
                Case("two requests need both answered", listOf(DockEvent.RunStarted(a), DockEvent.RequestOpened(a, "r1"), DockEvent.RequestOpened(a, "r2"), DockEvent.RequestSettled(a, "r1")), DockState.NeedsYou),
                Case("busy then idle is finished", listOf(DockEvent.RunStarted(a), DockEvent.RunEnded(a, DockRunEnd.Succeeded), DockEvent.Opened(b)), DockState.Finished),
                Case("busy then failed", listOf(DockEvent.RunStarted(a), DockEvent.RunEnded(a, DockRunEnd.Failed), DockEvent.Opened(b)), DockState.Failed),
                Case("a stop is not a celebration", listOf(DockEvent.RunStarted(a), DockEvent.RunEnded(a, DockRunEnd.Stopped)), null),
                Case("an idle end that never saw a start", listOf(DockEvent.RunEnded(a, DockRunEnd.Succeeded)), null),
                Case("an idle end cannot overwrite a failure", listOf(DockEvent.RunStarted(a), DockEvent.RunEnded(a, DockRunEnd.Failed), DockEvent.RunEnded(a, DockRunEnd.Succeeded), DockEvent.Opened(b)), DockState.Failed),
                Case("a new run clears an unseen finish", listOf(DockEvent.RunStarted(a), DockEvent.RunEnded(a, DockRunEnd.Succeeded), DockEvent.RunStarted(a)), DockState.Working),
            )
        for (case in cases) {
            val ui = run(DockEvent.Opened(b), *case.events.toTypedArray())
            assertEquals(case.expect, ui.state(a), case.label)
        }
    }

    @Test
    fun `the session record upgrades an unseen finish to a failure and never resurrects a seen or unknown one`() {
        // Not in the dock: ignored, so an old failed session does not appear at app start.
        assertTrue(run(DockEvent.LastRunFailed(a, true)).entries.isEmpty())
        val finished = DockModel.empty().reduce(DockEvent.Opened(b)).reduce(DockEvent.RunStarted(a)).reduce(DockEvent.RunEnded(a, DockRunEnd.Succeeded))
        assertEquals(DockState.Finished, finished.ui.state(a))
        val raised = finished.reduce(DockEvent.LastRunFailed(a, true))
        assertEquals(DockState.Failed, raised.ui.state(a))
        assertEquals(1, raised.ui.entries.first { it.ref == a }.pulse)
        // Seen, then the record repeats the same failure: it stays quiet.
        val seen = raised.reduce(DockEvent.Seen(a)).reduce(DockEvent.LastRunFailed(a, true))
        assertNull(seen.ui.state(a))
    }

    // ---- in / out

    @Test
    fun `presence follows live count, alerts and the tuck`() {
        data class Case(val label: String, val events: List<DockEvent>, val expect: DockPresence)
        val cases =
            listOf(
                Case("nothing", emptyList(), DockPresence.Hidden),
                Case("one live retreats to the nub", listOf(DockEvent.RunStarted(a)), DockPresence.Nub),
                Case("two live come out", listOf(DockEvent.RunStarted(a), DockEvent.RunStarted(b)), DockPresence.Out),
                Case("one of two stops: back to the nub", listOf(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.RunEnded(b, DockRunEnd.Stopped)), DockPresence.Nub),
                Case("an unanswered request alone brings it out", listOf(DockEvent.RunStarted(a), DockEvent.RequestOpened(a, "r")), DockPresence.Out),
                Case("a failure alone brings it out", listOf(DockEvent.RunStarted(a), DockEvent.RunEnded(a, DockRunEnd.Failed)), DockPresence.Out),
                Case("a plain finish alone stays a nub", listOf(DockEvent.RunStarted(a), DockEvent.RunEnded(a, DockRunEnd.Succeeded)), DockPresence.Nub),
                Case("the person tucked it", listOf(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Tuck), DockPresence.Nub),
                Case("tuck then untuck", listOf(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Tuck, DockEvent.Untuck), DockPresence.Out),
                Case("a new failure untucks", listOf(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Tuck, DockEvent.RunEnded(a, DockRunEnd.Failed)), DockPresence.Out),
                Case("a new request untucks", listOf(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Tuck, DockEvent.RequestOpened(b, "r")), DockPresence.Out),
                Case("a finish does not untuck", listOf(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Tuck, DockEvent.RunEnded(a, DockRunEnd.Succeeded)), DockPresence.Nub),
                Case("tuck is forgotten when nothing is live", listOf(DockEvent.RunStarted(a), DockEvent.Tuck, DockEvent.RunEnded(a, DockRunEnd.Stopped), DockEvent.RunStarted(a), DockEvent.RunStarted(b)), DockPresence.Out),
                Case("a request in the open conversation does not untuck", listOf(DockEvent.Opened(a), DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Tuck, DockEvent.RequestOpened(a, "r")), DockPresence.Nub),
            )
        for (case in cases) assertEquals(case.expect, run(*case.events.toTypedArray()).presence, case.label)
    }

    // ---- membership, order, cap

    @Test
    fun `idle conversations leave and the open one stays only beside a live one`() {
        val alone = run(DockEvent.Opened(a))
        assertTrue(alone.entries.isEmpty(), "an idle open conversation alone is no dock")
        val beside = run(DockEvent.Opened(a), DockEvent.RunStarted(b))
        assertEquals(listOf(b, a).toSet(), beside.entries.map { it.ref }.toSet())
        assertEquals(DockState.Idle, beside.state(a))
        assertEquals(1, beside.liveCount)
        val left = DockModel.empty().reduce(DockEvent.RunStarted(b)).reduce(DockEvent.RunEnded(b, DockRunEnd.Stopped))
        assertTrue(left.ui.entries.isEmpty())
    }

    @Test
    fun `every server contributes and a deleted server's conversations go`() {
        val other = ProfileId("p2")
        val x = ref("x", other)
        val ui = run(DockEvent.RunStarted(a), DockEvent.RunStarted(x))
        assertEquals(listOf(a, x), ui.entries.map { it.ref })
        assertEquals(DockPresence.Out, ui.presence)
        val gone = DockModel.empty().reduce(DockEvent.RunStarted(a)).reduce(DockEvent.RunStarted(x)).reduce(DockEvent.ProfileGone(other))
        assertEquals(listOf(a), gone.ui.entries.map { it.ref })
        val deleted = gone.reduce(DockEvent.Deleted(a))
        assertTrue(deleted.ui.entries.isEmpty())
        // A closed connection drops that server's rings but keeps its nicknames for when it comes back.
        val named = DockModel.empty().reduce(DockEvent.NicknameSet(x, "Far")).reduce(DockEvent.RunStarted(x)).reduce(DockEvent.RunStarted(a))
        val offline = named.reduce(DockEvent.ProfileOffline(other))
        assertEquals(listOf(a), offline.ui.entries.map { it.ref })
        assertEquals("Far", offline.reduce(DockEvent.RunStarted(x)).ui.entries.first { it.ref == x }.name)
    }

    @Test
    fun `order is stable across state changes and a newcomer goes last`() {
        val events =
            listOf(
                DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.RunStarted(c),
                DockEvent.Tasks(c, 1, 2), DockEvent.RequestOpened(b, "r"), DockEvent.RunEnded(a, DockRunEnd.Failed),
                DockEvent.Reasoning(c, true), DockEvent.RequestSettled(b, "r"), DockEvent.Tasks(a, 0, 3),
            )
        var m = DockModel.empty()
        for (e in events) {
            m = m.reduce(e)
            val seen = m.ui.entries.map { it.ref }
            assertEquals(listOf(a, b, c).filter { it in seen }, seen, "after $e")
        }
        // A leaves and comes back: it goes last now.
        m = m.reduce(DockEvent.Seen(a)).reduce(DockEvent.RunStarted(a))
        assertEquals(listOf(b, c, a), m.ui.entries.map { it.ref })
    }

    @Test
    fun `above the cap what matters stays, in stable order, and the rest is counted`() {
        val refs = (1..6).map { ref("s$it") }
        val events = refs.map { DockEvent.RunStarted(it) } + DockEvent.RequestOpened(refs[5], "r") + DockEvent.RunEnded(refs[4], DockRunEnd.Failed)
        val ui = run(*events.toTypedArray(), cap = 4)
        assertEquals(4, ui.entries.size)
        assertEquals(2, ui.overflow)
        // NeedsYou (s6) and Failed (s5) survive; the first two plain workers fill the rest; order stays s1,s2,s5,s6.
        assertEquals(listOf("s1", "s2", "s5", "s6"), ui.entries.map { it.ref.sessionId.raw })
        val roomy = run(*events.toTypedArray())
        assertEquals(0, roomy.overflow)
        assertEquals(6, roomy.entries.size)
        assertEquals(7, DockModel.DEFAULT_CAP)
    }

    // ---- seen

    @Test
    fun `opening clears finished and failed, and a run that ends on screen is seen at once`() {
        val m =
            DockModel.empty().reduce(DockEvent.Opened(c)).reduce(DockEvent.RunStarted(a)).reduce(DockEvent.RunStarted(b))
                .reduce(DockEvent.RunEnded(a, DockRunEnd.Succeeded)).reduce(DockEvent.RunEnded(b, DockRunEnd.Failed))
        assertEquals(DockState.Finished, m.ui.state(a))
        assertEquals(DockState.Failed, m.ui.state(b))
        val openedA = m.reduce(DockEvent.Opened(a))
        assertEquals(DockState.Idle, openedA.ui.state(a), "finished cleared by opening")
        assertEquals(DockState.Failed, openedA.ui.state(b), "the other is untouched")
        val dismissed = m.reduce(DockEvent.Seen(b))
        assertNull(dismissed.ui.state(b))
        assertEquals(DockState.Finished, dismissed.ui.state(a))
        // On screen: no blip, no mark.
        val onScreen = DockModel.empty().reduce(DockEvent.Opened(a)).reduce(DockEvent.RunStarted(a)).reduce(DockEvent.RunEnded(a, DockRunEnd.Failed))
        assertTrue(onScreen.ui.entries.isEmpty())
    }

    @Test
    fun `pulse counts new failures and requests only`() {
        val m = DockModel.empty().reduce(DockEvent.RunStarted(a)).reduce(DockEvent.RunStarted(b))
        assertEquals(0, m.ui.entries.first { it.ref == a }.pulse)
        val req = m.reduce(DockEvent.RequestOpened(a, "r1")).reduce(DockEvent.RequestOpened(a, "r2"))
        assertEquals(1, req.ui.entries.first { it.ref == a }.pulse, "a second request while one waits is not a new blip")
        val failed = m.reduce(DockEvent.RunEnded(b, DockRunEnd.Failed))
        assertEquals(1, failed.ui.entries.first { it.ref == b }.pulse)
    }

    // ---- reconcile reads (the stream is volatile)

    @Test
    fun `a status read adds busy sessions and finishes the ones that stopped without an event`() {
        val m = DockModel.empty().reduce(DockEvent.StatusesRead(p, setOf(a.sessionId, b.sessionId)))
        assertEquals(DockPresence.Out, m.ui.presence)
        val after = m.reduce(DockEvent.StatusesRead(p, setOf(a.sessionId)))
        assertEquals(DockState.Working, after.ui.state(a))
        assertEquals(DockState.Finished, after.ui.state(b))
        val otherServer = m.reduce(DockEvent.StatusesRead(ProfileId("p2"), emptySet()))
        assertEquals(DockState.Working, otherServer.ui.state(a), "another server's read changes nothing here")
    }

    @Test
    fun `a request read replaces the beliefs of that server`() {
        val m = DockModel.empty().reduce(DockEvent.RunStarted(a)).reduce(DockEvent.RequestOpened(a, "old"))
        val read = m.reduce(DockEvent.RequestsRead(p, mapOf(b.sessionId to setOf("new"))))
        assertEquals(DockState.Working, read.ui.state(a), "the answered request is gone")
        assertEquals(DockState.NeedsYou, read.ui.state(b), "a request on a session the dock did not know joins it")
    }

    // ---- names

    @Test
    fun `a nickname shows before the title and clearing brings the title back`() {
        val m = DockModel.empty().reduce(DockEvent.RunStarted(a)).reduce(DockEvent.Meta(a, "Fix the bank API", "build"))
        assertEquals("Fix the bank API", m.ui.entries.single().name)
        assertEquals("build", m.ui.entries.single().agent)
        val named = m.reduce(DockEvent.NicknameSet(a, "Bank"))
        assertEquals("Bank", named.ui.entries.single().name)
        assertEquals("Fix the bank API", named.ui.entries.single().title)
        assertEquals("Fix the bank API", named.reduce(DockEvent.NicknameSet(a, null)).ui.entries.single().name)
        // A title heard before the conversation joined arrives with it.
        val early = DockModel.empty().reduce(DockEvent.Meta(b, "Early", null)).reduce(DockEvent.RunStarted(b))
        assertEquals("Early", early.ui.entries.single().title)
        // A loaded set replaces that server's names only.
        val loaded = named.reduce(DockEvent.Nicknames(p, mapOf(a.sessionId to "Loaded")))
        assertEquals("Loaded", loaded.ui.entries.single().name)
    }
}
