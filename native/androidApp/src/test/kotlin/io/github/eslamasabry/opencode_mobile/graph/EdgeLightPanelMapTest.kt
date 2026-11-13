package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestKind
import io.github.eslamasabry.oc.feature.chats.lamp.LampAnswerUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampChildState
import io.github.eslamasabry.oc.feature.chats.lamp.LampChildUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampFact
import io.github.eslamasabry.oc.feature.chats.lamp.LampGroupKind
import io.github.eslamasabry.oc.feature.chats.lamp.LampGroupUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampHeight
import io.github.eslamasabry.oc.feature.chats.lamp.LampHiddenItemUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampHiddenUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampPlace
import io.github.eslamasabry.oc.feature.chats.lamp.LampRequestUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampRowUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampSide
import io.github.eslamasabry.oc.feature.chats.lamp.LampState
import io.github.eslamasabry.oc.feature.chats.lamp.LampStateLine
import io.github.eslamasabry.oc.feature.chats.lamp.LampStopUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampTreeKind
import io.github.eslamasabry.oc.feature.chats.lamp.LampTreeUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldReply
import io.github.eslamasabry.oc.feature.chats.presentation.RelativeAge
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampGroup
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampHeight
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampRowMark
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampSide
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampTone
import io.github.eslamasabry.oc.ui.kit.request.KitRequestKind
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeKind
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStepState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The edge light's list as the kit draws it: the rows presenter's state mapped to the panel's plain values. */
class EdgeLightPanelMapTest {
    // Every word is its own name; templates keep their holes, so a test can see what went where.
    private val t = LampTexts(LampWord.entries.associateWith { "${it.name}(%1\$s,%2\$s)" }, mapOf(3 to "3 sub-agents"))
    private fun ref(s: String) = ConversationRef(ProfileId("p"), SessionId(s))

    private fun row(
        key: String,
        state: LampState,
        line: LampStateLine? = null,
        request: LampRequestUi? = null,
        stop: LampStopUi? = null,
        tree: LampTreeUi? = null,
        subAgentOf: String? = null,
        pinned: Boolean = false,
        title: String? = key,
    ) =
        LampRowUi(
            key = key, ref = ref(key), title = title, state = state, stateLine = line, newestLine = "last line", request = request,
            lastActivityMs = null, age = RelativeAge.Minutes(4), server = null, pinned = pinned, subAgentOf = subAgentOf,
            tree = tree, stop = stop, edit = null,
        )

    private fun ui(vararg groups: LampGroupUi, hidden: LampHiddenUi? = null, open: String? = null) =
        LampUi(openTitle = open, groups = groups.toList().toImmutableList(), hidden = hidden, allCount = 7, more = 2, empty = false)

    private fun group(kind: LampGroupKind, vararg rows: LampRowUi) = LampGroupUi(kind, rows.toList().toImmutableList())

    private fun permission(answer: LampAnswerUi = LampAnswerUi.AllowDeny, owner: ConversationRef = ref("a")) =
        LampRequestUi(DockRequestKind.Permission, "req1", "edit", "wait7.txt", "", owner, answer)

    @Test
    fun `the kit lists groups top to bottom with Needs you by the thumb, the opposite of the presenter`() {
        val model =
            LampPanelMap.model(
                ui(
                    group(LampGroupKind.NeedsYou, row("n", LampState.NeedsYou)),
                    group(LampGroupKind.Working, row("w", LampState.Working)),
                    group(LampGroupKind.Pinned, row("p", LampState.Idle, pinned = true)),
                    group(LampGroupKind.Recent, row("r", LampState.Idle)),
                ),
                t,
            )
        assertEquals(
            listOf(KitLampGroup.Pinned, KitLampGroup.Recent, KitLampGroup.Working, KitLampGroup.NeedsYou),
            model.sections.map { it.group },
        )
        assertEquals(7, model.all)
        assertEquals(2, model.more)
    }

    @Test
    fun `a task list gives progress and its words, a stuck run says for how long in amber`() {
        val working = LampPanelMap.row(row("w", LampState.Working, LampStateLine.TasksDone(1, 4)), t)
        assertEquals(0.25f, working.progress)
        assertTrue(working.state!!.startsWith("TasksDone("))
        assertEquals(KitLampRowMark.Working, working.mark)
        val needs = LampPanelMap.row(row("n", LampState.NeedsYou, LampStateLine.For(RelativeAge.Minutes(4))), t)
        assertEquals(KitLampTone.Attention, needs.stateTone)
        assertTrue(needs.state!!.startsWith("For("))
        assertNull(LampPanelMap.row(row("x", LampState.Working), t).progress)
    }

    @Test
    fun `a waiting permission is a permission, a question is a question, and the words name what it asks`() {
        val allow = LampPanelMap.row(row("a", LampState.NeedsYou, request = permission()), t)
        assertEquals(KitRequestKind.Permission, allow.request!!.kind)
        assertTrue(allow.request!!.words.startsWith("AsksEdit("), allow.request!!.words)
        assertNull(allow.held)
        val question =
            LampRequestUi(DockRequestKind.Question, "q1", "", "", "Which one?", ref("a"), LampAnswerUi.Answer)
        val asked = LampPanelMap.row(row("a", LampState.NeedsYou, request = question), t)
        assertEquals(KitRequestKind.Question, asked.request!!.kind)
        assertTrue(asked.request!!.words.startsWith("AsksQuestion("))
        val bare = LampPanelMap.requestWords(question.copy(text = ""), t)
        assertTrue(bare.startsWith("AsksQuestionBare("))
    }

    @Test
    fun `a held answer says Allowed or Denied and the request stays for the receipt`() {
        val allowed = LampPanelMap.row(row("a", LampState.NeedsYou, request = permission(LampAnswerUi.Held(HomeHeldReply.Allowed))), t)
        assertTrue(allowed.held!!.allowed)
        assertTrue(allowed.held!!.label.startsWith("Allowed("))
        val denied = LampPanelMap.row(row("a", LampState.NeedsYou, request = permission(LampAnswerUi.Held(HomeHeldReply.Rejected))), t)
        assertEquals(false, denied.held!!.allowed)
        assertTrue(denied.held!!.label.startsWith("Denied("))
        assertEquals("req1", denied.held!!.key)
    }

    @Test
    fun `stop is a square, then a held Stopping receipt, then the word Stopped`() {
        val button = LampPanelMap.row(row("w", LampState.Working, stop = LampStopUi.Button), t)
        assertTrue(button.stoppable)
        assertNull(button.stopping)
        val holding = LampPanelMap.row(row("w", LampState.Working, stop = LampStopUi.Holding), t)
        assertTrue(holding.stopping!!.label.startsWith("Stopping("))
        assertEquals(false, holding.stoppable)
        val stopped = LampPanelMap.row(row("w", LampState.Working, stop = LampStopUi.Stopped), t)
        assertTrue(stopped.state!!.startsWith("Stopped("))
        assertNull(stopped.stopping)
    }

    @Test
    fun `sub-agents become the step tree in their order and a sub-agent row cannot be edited`() {
        fun child(k: String, s: LampChildState, fact: LampFact? = null, req: LampRequestUi? = null) =
            LampChildUi(k, ref(k), k, s, fact, req, paused = req != null, step = 1, scrollIntoView = false, stop = null)

        val tree =
            LampTreeUi(
                kind = LampTreeKind.Sequence,
                children = persistentListOf(child("c1", LampChildState.Done), child("c2", LampChildState.NeedsYou, req = permission(owner = ref("c2")))),
                more = 0, moreInside = 0, count = 3, folded = false,
            )
        val mapped = LampPanelMap.row(row("a", LampState.Working, tree = tree), t)
        assertEquals(KitStepTreeKind.Sequence, mapped.steps)
        assertEquals(listOf(KitTreeStepState.Done, KitTreeStepState.NeedsYou), mapped.children.map { it.state })
        assertEquals("req1", mapped.children[1].request!!.key)
        val withFact = LampPanelMap.child(child("c3", LampChildState.Working, LampFact(2, 5)), t)
        assertEquals(0.4f, withFact.progress)
        assertTrue(withFact.fact!!.startsWith("FactOf("))
        val sub = LampPanelMap.row(row("s", LampState.Working, subAgentOf = "Parent"), t)
        assertEquals(false, sub.canEdit)
        assertTrue(sub.newest!!.startsWith("SubAgentOf("))
    }

    @Test
    fun `the open conversation names the header, hidden conversations are rows keyed by their reference`() {
        val model =
            LampPanelMap.model(
                ui(
                    group(LampGroupKind.Recent, row("r", LampState.Idle)),
                    hidden = LampHiddenUi(persistentListOf(LampHiddenItemUi(ref("h"), null, LampState.Idle))),
                    open = "Mine",
                ),
                t,
            )
        assertTrue(model.youAreIn!!.startsWith("YouAreIn("))
        assertEquals(ref("h"), model.hidden.single().key)
        assertTrue(model.hidden.single().title.startsWith("Untitled("))
    }

    @Test
    fun `a tap's key finds its row, its sub-agent and its hidden conversation again`() {
        val child = LampChildUi("c1", ref("c1"), "c1", LampChildState.Working, null, null, false, 1, false, null)
        val tree = LampTreeUi(LampTreeKind.Parallel, persistentListOf(child), 0, 0, 1, false)
        val index = LampIndex(ui(group(LampGroupKind.Working, row("w", LampState.Working, tree = tree))))
        assertEquals(ref("w"), index.row("w")!!.ref)
        assertEquals(ref("c1"), index.child("c1")!!.second.ref)
        assertEquals("w", index.child("c1")!!.first.key)
        assertNull(index.row("nope"))
        assertEquals(ref("h"), index.hidden(ref("h")))
    }

    @Test
    fun `short ages read as a moment, minutes, hours and days`() {
        assertTrue(LampPanelMap.age(RelativeAge.JustNow, t).startsWith("AgeNow("))
        assertEquals("AgeMinutes(4,%2\$s)", LampPanelMap.age(RelativeAge.Minutes(4), t))
        assertTrue(LampPanelMap.age(RelativeAge.Hours(2), t).startsWith("AgeHours("))
        assertTrue(LampPanelMap.age(RelativeAge.Days(3), t).startsWith("AgeDays("))
    }

    @Test
    fun `the place goes to the kit and back unchanged for all six spots`() {
        for (side in LampSide.entries) for (height in LampHeight.entries) {
            val place = LampPlace(side, height)
            assertEquals(place, LampPanelMap.place(LampPanelMap.place(place)))
        }
        assertEquals(KitLampSide.Start, LampPanelMap.place(LampPlace(LampSide.Start, LampHeight.High)).side)
        assertEquals(KitLampHeight.High, LampPanelMap.place(LampPlace(LampSide.Start, LampHeight.High)).height)
    }

    @Test
    fun `words with holes fill in order`() {
        assertEquals("YouAreIn(Mine,%2\$s)", t.fmt(LampWord.YouAreIn, "Mine"))
        val template = LampTexts(mapOf(LampWord.ChildLabel to "%1\$s, sub-agent of %2\$s, %3\$s"))
        assertEquals("a, sub-agent of b, c", template.fmt(LampWord.ChildLabel, "a", "b", "c"))
    }
}
