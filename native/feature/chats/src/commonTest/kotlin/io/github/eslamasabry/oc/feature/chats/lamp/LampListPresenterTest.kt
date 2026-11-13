package io.github.eslamasabry.oc.feature.chats.lamp

import io.github.eslamasabry.oc.engine.attention.dock.DockListState.Done
import io.github.eslamasabry.oc.engine.attention.dock.DockListState.Failed
import io.github.eslamasabry.oc.engine.attention.dock.DockListState.Idle
import io.github.eslamasabry.oc.engine.attention.dock.DockListState.NeedsYou
import io.github.eslamasabry.oc.engine.attention.dock.DockListState.Working
import io.github.eslamasabry.oc.engine.attention.dock.DockListHidden
import io.github.eslamasabry.oc.engine.attention.dock.DockProgress
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldReply
import io.github.eslamasabry.oc.feature.chats.presentation.RelativeAge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Edge light's list as a pure function of the dock's list model (slice M1). */
class LampListPresenterTest {
    @Test
    fun `B-LAMP-M1 groups come in the plan's order and the open conversation has no row`() {
        val ui =
            present(
                lmodel(
                    lrow("open", Working, open = true),
                    lrow("recent", Idle),
                    lrow("done", Done),
                    lrow("work", Working),
                    lrow("fail", Failed),
                    lrow("need", NeedsYou, request = perm()),
                    lrow("pin", Idle, pinned = true),
                ),
            )
        assertEquals(
            listOf(LampGroupKind.Pinned, LampGroupKind.NeedsYou, LampGroupKind.Failed, LampGroupKind.Working, LampGroupKind.Done, LampGroupKind.Recent),
            ui.groups.map { it.kind },
        )
        assertTrue(ui.groups.flatMap { it.rows }.none { it.ref == lref("open") }, "the open conversation is not a row")
        assertEquals("Title open", ui.openTitle)
        assertEquals(false, ui.empty)
    }

    @Test
    fun `B-LAMP-M1 a pinned conversation that needs you stays among those who need you`() {
        val ui = present(lmodel(lrow("a", NeedsYou, pinned = true, request = perm()), lrow("b", Idle, pinned = true), lrow("c", Working, pinned = true)))
        assertEquals(listOf(lref("a")), ui.rows(LampGroupKind.NeedsYou).map { it.ref })
        assertEquals(listOf(lref("b"), lref("c")), ui.rows(LampGroupKind.Pinned).map { it.ref })
    }

    @Test
    fun `B-LAMP-M1 done and recent are newest first, the others keep the dock's order`() {
        val ui = present(lmodel(lrow("d1", Done, last = 10), lrow("d2", Done, last = 30), lrow("d3", Done, last = 20), lrow("w1", Working), lrow("w2", Working)))
        assertEquals(listOf("d2", "d3", "d1"), ui.rows(LampGroupKind.Done).map { it.ref.sessionId.raw })
        assertEquals(listOf("w1", "w2"), ui.rows(LampGroupKind.Working).map { it.ref.sessionId.raw })
    }

    @Test
    fun `B-LAMP-M1 state lines say how it goes and drop the word the heading already says`() {
        val ui =
            present(
                lmodel(
                    lrow("tasks", Working, progress = DockProgress(3, 7)),
                    lrow("think", Working, thinking = true),
                    lrow("plain", Working),
                    lrow("done", Done),
                    lrow("pinWork", Working, pinned = true),
                    lrow("pinDone", Done, pinned = true),
                    lrow("need", NeedsYou, last = 10_000_000 - 4 * 60_000L),
                    lrow("fail", Failed, last = 10_000_000 - 2 * 3_600_000L),
                ),
                LampInputs(nowMs = 10_000_000),
            )
        assertEquals(LampStateLine.TasksDone(3, 7), ui.row("tasks").stateLine)
        assertEquals(LampStateLine.Thinking, ui.row("think").stateLine)
        assertNull(ui.row("plain").stateLine, "Working repeats the heading")
        assertNull(ui.row("done").stateLine, "Done repeats the heading")
        assertEquals(LampStateLine.Working, ui.row("pinWork").stateLine)
        assertEquals(LampStateLine.Done, ui.row("pinDone").stateLine)
        assertEquals(LampStateLine.For(RelativeAge.Minutes(4)), ui.row("need").stateLine)
        assertEquals(LampStateLine.For(RelativeAge.Hours(2)), ui.row("fail").stateLine)
    }

    @Test
    fun `B-LAMP-M1 time words, newest line and server come through as the home says them`() {
        val ui = present(lmodel(lrow("a", Idle, last = 1_000_000 - 90_000L, newest = "Ran the tests", server = "Phone")))
        val row = ui.row("a")
        assertEquals(RelativeAge.Minutes(1), row.age)
        assertEquals(1_000_000 - 90_000L, row.lastActivityMs)
        assertEquals("Ran the tests", row.newestLine)
        assertEquals("Phone", row.server)
    }

    @Test
    fun `B-LAMP-M1 paused words, in parallel with others still working`() {
        val ui = present(lmodel(waitingBlock(kid("w", NeedsYou, request = perm("r9")), kid("x", Working), kid("y", Working))))
        val row = ui.row("p")
        assertEquals(LampTreeKind.Parallel, row.tree!!.kind)
        assertEquals(LampStateLine.Waiting(1, 2), row.stateLine)
    }

    @Test
    fun `B-LAMP-M1 paused words, only the waiting child holds the run`() {
        val ui = present(lmodel(waitingBlock(kid("w", NeedsYou, request = perm("r9")), kid("x", Done), kid("y", Done))))
        assertEquals(LampStateLine.WaitingToFinish, ui.row("p").stateLine)
    }

    @Test
    fun `B-LAMP-M1 paused words, in a sequence it pauses at its step`() {
        // two finished before it, one waiting last: they ran one after another as far as the data shows
        val ui = present(lmodel(waitingBlock(kid("a", Done), kid("b", Done), kid("w", NeedsYou, request = perm("r9")))))
        val row = ui.row("p")
        assertEquals(LampTreeKind.Sequence, row.tree!!.kind)
        assertEquals(LampStateLine.PausedAtStep(3, 3), row.stateLine)
    }

    private fun waitingBlock(vararg kids: io.github.eslamasabry.oc.engine.attention.dock.DockListSubAgent) =
        lrow("p", NeedsYou, request = perm("r9"), causedBy = "w", kids = kids.toList())

    @Test
    fun `B-LAMP-M1 parallel when two run at once, sequence only when one at a time fits, and an overlap stays`() {
        val two = listOf(kid("a", Working), kid("b", Working))
        assertEquals(LampTreeKind.Parallel, LampTrees.kindOf(two, overlapSeen = false))
        assertTrue(LampTrees.overlaps(two))
        assertEquals(LampTreeKind.Parallel, LampTrees.kindOf(listOf(kid("a", Working), kid("b", NeedsYou)), false), "waiting counts as live")
        val oneAtATime = listOf(kid("a", Done), kid("b", Working))
        assertEquals(LampTreeKind.Sequence, LampTrees.kindOf(oneAtATime, false))
        assertEquals(LampTreeKind.Parallel, LampTrees.kindOf(oneAtATime, overlapSeen = true), "once seen at the same time, never a sequence")
        assertEquals(LampTreeKind.Parallel, LampTrees.kindOf(listOf(kid("a", Working), kid("b", Done)), false), "a finished one after the live one: no order claimed")
        assertEquals(LampTreeKind.Parallel, LampTrees.kindOf(listOf(kid("a", Working)), false), "one child claims no order")
        assertEquals(LampTreeKind.Parallel, LampTrees.kindOf(listOf(kid("a", Done), kid("b", Done)), false), "nothing live: no evidence")
    }

    @Test
    fun `B-LAMP-M1 the child that needs you stays in place, carries the request and is scrolled into view`() {
        val ui =
            present(
                lmodel(
                    lrow("calm", Working),
                    waitingBlock(kid("a", Working), kid("w", NeedsYou, request = perm("r9")), kid("c", Working)),
                ),
            )
        val tree = ui.row("p").tree!!
        assertEquals(listOf("a", "w", "c"), tree.children.map { it.ref.sessionId.raw }, "real order, not lifted out")
        val needing = tree.children[1]
        assertTrue(needing.paused)
        assertTrue(needing.scrollIntoView)
        assertEquals(1, tree.children.count { it.scrollIntoView })
        assertEquals("r9", needing.request!!.requestId)
        assertEquals(lref("w"), needing.request!!.owner)
        assertEquals(needing.key, ui.scrollToKey)
        assertNull(ui.row("p").request, "the child, not the block, carries it")
        assertEquals(LampGroupKind.NeedsYou, ui.groups.first { g -> g.rows.any { it.ref == lref("p") } }.kind, "the block lifts into Needs you")
    }

    @Test
    fun `B-LAMP-M1 above five sub-agents the five most urgent show in their real order, then more`() {
        val kids = listOf(kid("k1", Done), kid("k2", Working), kid("k3", NeedsYou, request = perm()), kid("k4", Done), kid("k5", Failed), kid("k6", Working), kid("k7", Idle))
        val ui = present(lmodel(lrow("p", NeedsYou, causedBy = "k3", request = perm(), kids = kids, more = 2)))
        val tree = ui.row("p").tree!!
        assertEquals(listOf("k1", "k2", "k3", "k5", "k6"), tree.children.map { it.ref.sessionId.raw }, "urgent five, real order")
        assertEquals(2 + 2, tree.more, "two cut here, two the engine did not list")
        assertEquals(9, tree.count)
        assertEquals(0, tree.moreInside)
    }

    @Test
    fun `B-LAMP-M1 five or fewer sub-agents all show`() {
        val ui = present(lmodel(lrow("p", Working, kids = List(5) { kid("k$it") })))
        assertEquals(5, ui.row("p").tree!!.children.size)
        assertEquals(0, ui.row("p").tree!!.more)
    }

    @Test
    fun `B-LAMP-M1 a block folds itself when every sub-agent is done, never while one needs you`() {
        val finished = lrow("p", Working, kids = listOf(kid("a", Done), kid("b", Done)))
        assertTrue(present(lmodel(finished)).row("p").tree!!.folded)
        assertEquals(false, present(lmodel(lrow("p", Working, kids = listOf(kid("a", Done), kid("b", Working))))).row("p").tree!!.folded)
        val needs = lrow("p", NeedsYou, causedBy = "a", request = perm(), kids = listOf(kid("a", NeedsYou, request = perm()), kid("b", Done)))
        assertEquals(false, present(lmodel(needs), LampInputs(1_000_000, folds = mapOf(lref("p") to true))).row("p").tree!!.folded)
        // the person's choice wins while it is still running
        val running = lrow("p", Working, kids = listOf(kid("a", Working)))
        assertTrue(present(lmodel(running), LampInputs(1_000_000, folds = mapOf(lref("p") to true))).row("p").tree!!.folded)
        assertEquals(2, present(lmodel(finished)).row("p").tree!!.count)
    }

    @Test
    fun `B-LAMP-M1 fold memory forgets the choice when the last sub-agent finishes, and opens again when work resumes`() {
        val folds = LampFolds()
        val running = lmodel(lrow("p", Working, kids = listOf(kid("a", Working), kid("b", Working))))
        val finished = lmodel(lrow("p", Working, kids = listOf(kid("a", Done), kid("b", Done))))
        folds.observe(running)
        folds.toggle(lref("p"), folded = false) // opened by the person
        assertEquals(mapOf(lref("p") to false), folds.folds)
        folds.observe(finished)
        assertTrue(folds.folds.isEmpty(), "the block folds itself again")
        assertTrue(lref("p") in folds.overlapSeen, "two ran at once")
        folds.toggle(lref("p"), folded = false)
        folds.observe(finished)
        assertEquals(mapOf(lref("p") to false), folds.folds, "opened after it folded: kept")
    }

    @Test
    fun `B-LAMP-M1 the face counts needs you plus failed, marks in priority order, three at most`() {
        val ui =
            present(
                lmodel(
                    lrow("w1", Working, progress = DockProgress(1, 4)),
                    lrow("f", Failed),
                    lrow("w2", Working, progress = DockProgress(3, 4)),
                    lrow("n", NeedsYou, request = perm()),
                    lrow("w3", Working),
                ),
            )
        assertEquals(2, ui.face.number)
        assertEquals(listOf(LampMarkKind.NeedsYou, LampMarkKind.Failed, LampMarkKind.Working), ui.face.marks.map { it.kind })
        assertEquals(0.75f, ui.face.marks[2].progress, "the run that got furthest")
        assertEquals(3, ui.face.working)
        assertEquals(false, ui.face.quiet)
    }

    @Test
    fun `B-LAMP-M1 runs on the face order by progress, a run without a list last as a dashed ring`() {
        val ui = present(lmodel(lrow("a", Working), lrow("b", Working, progress = DockProgress(1, 10)), lrow("c", Working, progress = DockProgress(5, 10))))
        assertEquals(listOf(0.5f, 0.1f, null), ui.face.marks.map { it.progress })
        assertEquals(0, ui.face.number)
    }

    @Test
    fun `B-LAMP-M1 several needing you fill the marks before anything else`() {
        val ui = present(lmodel(lrow("a", NeedsYou), lrow("b", NeedsYou), lrow("c", NeedsYou), lrow("d", NeedsYou), lrow("e", Failed)))
        assertEquals(List(3) { LampMarkKind.NeedsYou }, ui.face.marks.map { it.kind })
        assertEquals(5, ui.face.number)
    }

    @Test
    fun `B-LAMP-M1 nothing running, nothing waiting is quiet, and the open conversation is not counted`() {
        val ui = present(lmodel(lrow("open", NeedsYou, open = true), lrow("a", Idle), lrow("b", Done)))
        assertTrue(ui.face.quiet)
        assertEquals(0, ui.face.number)
        assertTrue(ui.face.marks.isEmpty())
    }

    @Test
    fun `B-LAMP-M1 working and waiting sub-agents of the open conversation stand as rows of their own`() {
        val ui =
            present(
                lmodel(
                    lrow(
                        "open",
                        Working,
                        open = true,
                        kids = listOf(kid("w", NeedsYou, request = perm("r2")), kid("x", Working, progress = DockProgress(2, 5)), kid("d", Done), kid("f", Failed)),
                    ),
                ),
            )
        assertEquals(listOf(lref("w")), ui.rows(LampGroupKind.NeedsYou).map { it.ref })
        assertEquals(listOf(lref("x")), ui.rows(LampGroupKind.Working).map { it.ref })
        assertEquals("Title open", ui.row("w").subAgentOf)
        assertEquals("r2", ui.row("w").request!!.requestId)
        assertEquals(LampStateLine.TasksDone(2, 5), ui.row("x").stateLine)
        assertEquals(2, ui.groups.sumOf { it.rows.size }, "done and failed ones are in the chat, not here")
        assertEquals(1, ui.face.number)
    }

    @Test
    fun `B-LAMP-M1 a permission offers Allow once and Deny, a question Answer, a held reply its receipt`() {
        val model = lmodel(lrow("a", NeedsYou, request = perm("r1")), lrow("b", NeedsYou, request = question("q1")))
        val ui = present(model)
        assertEquals(LampAnswerUi.AllowDeny, ui.row("a").request!!.answer)
        assertEquals(LampAnswerUi.Answer, ui.row("b").request!!.answer)
        val held = present(model, LampInputs(1_000_000, held = mapOf("r1" to HomeHeldReply.Rejected)))
        assertEquals(LampAnswerUi.Held(HomeHeldReply.Rejected), held.row("a").request!!.answer)
        assertEquals("edit", ui.row("a").request!!.action)
        assertEquals("wait7.txt", ui.row("a").request!!.target)
        assertEquals("Which branch?", ui.row("b").request!!.text)
    }

    @Test
    fun `B-LAMP-M1 stop is on a running row and shows its hold, then Stopped`() {
        val model = lmodel(lrow("a", Working), lrow("b", Idle))
        assertEquals(LampStopUi.Button, present(model).row("a").stop)
        assertNull(present(model).row("b").stop)
        assertEquals(LampStopUi.Holding, present(model, LampInputs(1, stops = mapOf(lref("a") to StopPhase.Holding))).row("a").stop)
        assertEquals(LampStopUi.Stopped, present(model, LampInputs(1, stops = mapOf(lref("a") to StopPhase.Stopped))).row("a").stop)
    }

    @Test
    fun `B-LAMP-M1 hidden, more and the footer count`() {
        val ui =
            present(
                lmodel(
                    lrow("a", Idle),
                    lrow("b", Idle),
                    more = 5,
                    hidden = listOf(DockListHidden(lref("h"), "Hidden one", Working, null)),
                ),
            )
        assertEquals(1, ui.hidden!!.count)
        assertEquals("Hidden one", ui.hidden!!.items.single().title)
        assertEquals(5, ui.more)
        assertEquals(7, ui.allCount)
        assertTrue(present(lmodel()).empty)
        assertNull(present(lmodel(lrow("a"))).hidden)
    }

    @Test
    fun `B-LAMP-M1 edit mode puts pin, rename and hide on rows and move up or down on pinned ones in order`() {
        val model = lmodel(lrow("p1", Idle, pinned = true), lrow("p2", Idle, pinned = true), lrow("p3", Working, pinned = true), lrow("r", Idle))
        assertNull(present(model).row("p1").edit)
        val edit = present(model, LampInputs(1, editing = true))
        assertEquals(LampRowEditUi(true, canMoveUp = false, canMoveDown = true, canHide = true, isSubAgent = false), edit.row("p1").edit)
        assertEquals(true, edit.row("p2").edit!!.canMoveUp)
        assertEquals(true, edit.row("p2").edit!!.canMoveDown)
        assertEquals(false, edit.row("p3").edit!!.canMoveDown)
        assertEquals(false, edit.row("r").edit!!.canMoveUp)
        assertEquals(true, edit.editing)
    }

    @Test
    fun `B-LAMP-M1 a sub-agent row of the open conversation has no pin, rename or hide in edit mode`() {
        val ui = present(lmodel(lrow("open", Working, open = true, kids = listOf(kid("w", Working)))), LampInputs(1, editing = true))
        assertEquals(true, ui.row("w").edit!!.isSubAgent)
        assertEquals(false, ui.row("w").edit!!.canHide)
    }

    @Test
    fun `B-LAMP-M1 several servers name each row's server, the folded count and a null title pass through`() {
        val ui = present(lmodel(lrow("a", Idle, name = null, server = "Laptop"), lrow("b", Working, kids = listOf(kid("k", Done)))))
        assertNull(ui.row("a").title)
        assertEquals("Laptop", ui.row("a").server)
        assertEquals(1, ui.row("b").tree!!.count)
    }
}
