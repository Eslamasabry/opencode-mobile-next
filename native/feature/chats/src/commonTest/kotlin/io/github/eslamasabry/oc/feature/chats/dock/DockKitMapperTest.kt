package io.github.eslamasabry.oc.feature.chats.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.dock.DockChild
import io.github.eslamasabry.oc.engine.attention.dock.DockEntry
import io.github.eslamasabry.oc.engine.attention.dock.DockHidden
import io.github.eslamasabry.oc.engine.attention.dock.DockPeek
import io.github.eslamasabry.oc.engine.attention.dock.DockPeekRow
import io.github.eslamasabry.oc.engine.attention.dock.DockPlacement
import io.github.eslamasabry.oc.engine.attention.dock.DockPresence
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestKind
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestRef
import io.github.eslamasabry.oc.engine.attention.dock.DockSide
import io.github.eslamasabry.oc.engine.attention.dock.DockState
import io.github.eslamasabry.oc.engine.attention.dock.DockUi
import io.github.eslamasabry.oc.ui.kit.dock.KitDockLine
import io.github.eslamasabry.oc.ui.kit.dock.KitDockLineRole
import io.github.eslamasabry.oc.ui.kit.dock.KitDockRingState
import io.github.eslamasabry.oc.ui.kit.dock.KitDockSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

class DockKitMapperTest {
    private val p = ProfileId("p1")

    private fun ref(id: String, profile: ProfileId = p) = ConversationRef(profile, SessionId(id))

    private fun entry(id: String, state: DockState = DockState.Working, title: String? = "T-$id") =
        DockEntry(ref = ref(id), title = title, nickname = null, agent = null, state = state, pulse = 0)

    private fun ui(vararg entries: DockEntry, presence: DockPresence = DockPresence.Out) =
        DockUi(entries = persistentListOf(*entries), presence = presence)

    @Test
    fun every_engine_state_maps_to_the_same_kit_state() {
        assertEquals(KitDockRingState.WorkingWithTasks(3, 7), DockKitMapper.state(DockState.WorkingWithTasks(3, 7)))
        assertEquals(KitDockRingState.Working, DockKitMapper.state(DockState.Working))
        assertEquals(KitDockRingState.Thinking, DockKitMapper.state(DockState.Thinking))
        assertEquals(KitDockRingState.NeedsYou, DockKitMapper.state(DockState.NeedsYou))
        assertEquals(KitDockRingState.Finished, DockKitMapper.state(DockState.Finished))
        assertEquals(KitDockRingState.Failed, DockKitMapper.state(DockState.Failed))
        assertEquals(KitDockRingState.Idle, DockKitMapper.state(DockState.Idle))
    }

    @Test
    fun a_hidden_or_switched_off_dock_is_an_empty_model() {
        assertTrue(DockKitMapper.map(ui(entry("a"), presence = DockPresence.Hidden), emptyMap(), "Untitled").model.rings.isEmpty())
        assertTrue(DockKitMapper.map(ui(entry("a")), emptyMap(), "Untitled", shown = false).model.rings.isEmpty())
        assertTrue(DockKitMapper.map(DockUi(), emptyMap(), "Untitled").model.rings.isEmpty())
    }

    @Test
    fun a_dock_with_no_ring_is_a_real_frame_unless_hidden() {
        // Connected, nothing to offer yet: the strip or nub with its "+", tucked as the presence says.
        val hiddenOne = DockHidden(ref("h"), title = "Old work", nickname = null, agent = null, state = DockState.Working, parent = null)
        val empty = DockUi(presence = DockPresence.Nub, hidden = persistentListOf(hiddenOne), placement = DockPlacement(DockSide.Start, 0.2f))
        val frame = DockKitMapper.map(empty, emptyMap(), "U")
        assertNotEquals(DockKitFrame.Empty, frame)
        assertTrue(frame.model.rings.isEmpty())
        assertTrue(frame.model.tucked)
        assertEquals(KitDockSide.Start, frame.model.placement.side)
        assertEquals(0.2f, frame.model.placement.verticalBias)
        assertEquals("Old work", frame.model.hidden.single().name)
        assertNull(frame.model.current)
        assertFalse(DockKitMapper.map(empty.copy(presence = DockPresence.Out), emptyMap(), "U").model.tucked)
        // Hidden (nothing connected) or switched off: nothing.
        assertEquals(DockKitFrame.Empty, DockKitMapper.map(empty.copy(presence = DockPresence.Hidden), emptyMap(), "U"))
        assertEquals(DockKitFrame.Empty, DockKitMapper.map(empty, emptyMap(), "U", shown = false))
    }

    @Test
    fun a_quiet_dock_with_only_idle_recent_rings_is_drawn_tucked_not_hidden() {
        // The owner's "always available": nothing live is the nub with the recent conversations, never no dock.
        val quiet = DockUi(entries = persistentListOf(entry("a", DockState.Idle), entry("b", DockState.Idle)), presence = DockPresence.Nub, liveCount = 0)
        val frame = DockKitMapper.map(quiet, emptyMap(), "Untitled")
        assertEquals(listOf(KitDockRingState.Idle, KitDockRingState.Idle), frame.model.rings.map { it.state })
        assertTrue(frame.model.tucked)
        assertEquals(ref("a"), frame.refs[frame.model.rings[0].key], "a tap opens that conversation")
        // One idle open conversation alone is still a dock.
        val open = DockUi(entries = persistentListOf(entry("a", DockState.Idle)), presence = DockPresence.Nub, open = ref("a"))
        assertEquals(DockKitMapper.key(ref("a")), DockKitMapper.map(open, emptyMap(), "Untitled").model.current)
        // Switched off still hides it.
        assertTrue(DockKitMapper.map(quiet, emptyMap(), "Untitled", shown = false).model.rings.isEmpty())
    }

    @Test
    fun rings_keep_order_names_marks_pins_and_their_keys_lead_back() {
        val named = entry("a").copy(nickname = "Bank API", agent = "claude-code", pinned = true, pulse = 2)
        val frame = DockKitMapper.map(ui(named, entry("b", title = null)), emptyMap(), "Untitled")
        val rings = frame.model.rings
        assertEquals(listOf("Bank API", "Untitled"), rings.map { it.name })
        assertEquals("claude-code", rings[0].markId)
        assertNull(rings[1].markId)
        assertTrue(rings[0].pinned)
        assertEquals(2, rings[0].pulse)
        assertEquals(ref("a"), frame.refs[rings[0].key])
        assertEquals(ref("b"), frame.refs[rings[1].key])
    }

    @Test
    fun keys_of_different_profiles_never_collide() {
        assertNotEquals(
            DockKitMapper.key(ConversationRef(ProfileId("a/b"), SessionId("c"))),
            DockKitMapper.key(ConversationRef(ProfileId("a"), SessionId("b/c"))),
        )
    }

    @Test
    fun sub_agents_become_children_with_their_states_and_the_ring_names_the_one_that_raised_it() {
        val child = DockChild(ref = ref("c1"), title = "Look into the cache", agent = "explore", state = DockState.NeedsYou, pulse = 1)
        val e =
            entry("a", DockState.NeedsYou)
                .copy(ownState = DockState.Working, causedBy = ref("c1"), children = persistentListOf(child), childOverflow = 2)
        val frame = DockKitMapper.map(ui(e), emptyMap(), "Untitled")
        val ring = frame.model.rings.single()
        assertEquals(KitDockRingState.NeedsYou, ring.state)
        assertEquals("explore", ring.children.single().name)
        assertEquals(KitDockRingState.NeedsYou, ring.children.single().state)
        assertEquals(2, ring.childOverflow)
        assertEquals(ring.children.single().key, ring.causedBy)
        assertEquals(ref("c1"), frame.refs[ring.children.single().key])
    }

    @Test
    fun the_server_is_named_only_when_several_servers_are_live() {
        val e = entry("a").copy(serverName = "Mac mini")
        assertNull(DockKitMapper.map(ui(e), emptyMap(), "U").model.rings.single().server)
        val several = DockUi(entries = persistentListOf(e), presence = DockPresence.Out, severalServers = true)
        assertEquals("Mac mini", DockKitMapper.map(several, emptyMap(), "U").model.rings.single().server)
    }

    @Test
    fun peek_lines_give_the_tail_and_the_last_line() {
        val peeks =
            mapOf(
                ref("a") to DockPeek.Lines(persistentListOf(DockPeekRow(true, "fix it"), DockPeekRow(false, "Done, tests pass"))),
                ref("b") to DockPeek.Unavailable,
            )
        val rings = DockKitMapper.map(ui(entry("a"), entry("b")), peeks, "U").model.rings
        assertEquals(
            listOf(KitDockLine(KitDockLineRole.Person, "fix it"), KitDockLine(KitDockLineRole.Agent, "Done, tests pass")),
            rings[0].peek,
        )
        assertEquals("Done, tests pass", rings[0].lastLine)
        assertTrue(rings[1].peek.isEmpty())
        assertNull(rings[1].lastLine)
    }

    @Test
    fun the_line_under_the_state_never_repeats_the_name() {
        // The device run: a session titled from its prompt showed that prompt again under "Needs you".
        val prompt = "create a file one.txt with one line"
        val waiting = entry("a", DockState.NeedsYou, title = prompt)
        val onlyPrompt = mapOf(ref("a") to DockPeek.Lines(persistentListOf(DockPeekRow(true, "  Create a file one.txt  with one line "))))
        val ring = DockKitMapper.map(ui(waiting), onlyPrompt, "U").model.rings.single()
        assertEquals(prompt, ring.name)
        assertNull(ring.lastLine, "nothing new to say: no line")
        assertTrue(ring.peek.isEmpty(), "the swell and peek draw no copy of the title")
        // The latest activity is the agent's newest line; the person's own words are not activity.
        val replied =
            mapOf(ref("a") to DockPeek.Lines(persistentListOf(DockPeekRow(true, prompt), DockPeekRow(false, "Writing one.txt"), DockPeekRow(true, "thanks"))))
        val withReply = DockKitMapper.map(ui(waiting), replied, "U").model.rings.single()
        assertEquals("Writing one.txt", withReply.lastLine)
        assertEquals(listOf("Writing one.txt", "thanks"), withReply.peek.map { it.text })
        // A nickname or a cut title counts as the name too; a sub-agent's line never repeats its own name either.
        val nicknamed = entry("a", title = "Long title that was cu…").copy(nickname = "Bank")
        val tail = mapOf(ref("a") to DockPeek.Lines(persistentListOf(DockPeekRow(false, "Long title that was cut here"), DockPeekRow(false, "bank"))))
        assertNull(DockKitMapper.map(ui(nicknamed), tail, "U").model.rings.single().lastLine)
        val child = DockChild(ref("c1"), "Look around", "explore", DockState.Working, 0)
        val childTail = mapOf(ref("c1") to DockPeek.Lines(persistentListOf(DockPeekRow(false, "Explore"))))
        val kid = DockKitMapper.map(ui(entry("p").copy(children = persistentListOf(child))), childTail, "U").model.rings.single().children.single()
        assertNull(kid.lastLine)
    }

    @Test
    fun current_tuck_placement_and_hidden_follow_the_model() {
        val hidden = DockHidden(ref("h"), title = "Old work", nickname = null, agent = null, state = DockState.Finished, parent = null)
        val dockUi =
            DockUi(
                entries = persistentListOf(entry("a"), entry("c1")),
                presence = DockPresence.Nub,
                open = ref("a"),
                openChild = ref("c1"),
                hidden = persistentListOf(hidden),
                placement = DockPlacement(DockSide.Start, 0.3f),
            )
        val frame = DockKitMapper.map(dockUi, emptyMap(), "U")
        val model = frame.model
        assertEquals(DockKitMapper.key(ref("a")), model.current)
        assertEquals(DockKitMapper.key(ref("c1")), model.currentChild)
        assertTrue(model.tucked)
        assertEquals(KitDockSide.Start, model.placement.side)
        assertEquals(0.3f, model.placement.verticalBias)
        assertEquals("Old work", model.hidden.single().name)
        assertEquals(KitDockRingState.Finished, model.hidden.single().state)
        assertEquals(ref("h"), frame.refs[model.hidden.single().key])
    }

    @Test
    fun only_removable_conversations_are_offered_for_removal() {
        val frame = DockKitMapper.map(ui(entry("a").copy(removable = false), entry("b")), emptyMap(), "U")
        assertFalse(DockKitMapper.key(ref("a")) in frame.removable)
        assertTrue(DockKitMapper.key(ref("b")) in frame.removable)
    }

    @Test
    fun a_waiting_ring_asks_about_its_own_oldest_request_first() {
        val own = DockRequestRef(DockRequestKind.Permission, "r1")
        val e = entry("a", DockState.NeedsYou).copy(requests = persistentListOf(own, DockRequestRef(DockRequestKind.Question, "r2")))
        assertEquals(DockAsk(ref("a"), own), DockKitMapper.askOf(e))
    }

    @Test
    fun a_ring_raised_by_a_sub_agent_asks_about_that_sub_agents_request() {
        val ask = DockRequestRef(DockRequestKind.Permission, "c-r")
        val child = DockChild(ref("c1"), "child", "explore", DockState.NeedsYou, 1, requests = persistentListOf(ask))
        val e = entry("a", DockState.NeedsYou).copy(ownState = DockState.Working, causedBy = ref("c1"), children = persistentListOf(child))
        assertEquals(DockAsk(ref("c1"), ask), DockKitMapper.askOf(e))
        assertEquals(DockAsk(ref("c1"), ask), DockKitMapper.map(ui(e), emptyMap(), "U").asks[DockKitMapper.key(ref("a"))])
    }

    @Test
    fun a_ring_that_waits_on_nothing_asks_nothing() {
        assertNull(DockKitMapper.askOf(entry("a", DockState.Working)))
        assertNull(DockKitMapper.askOf(entry("a", DockState.NeedsYou)))
    }

    @Test
    fun names_are_found_for_rings_children_and_hidden() {
        val child = DockChild(ref("c1"), "child", "explore", DockState.Working, 0)
        val hidden = DockHidden(ref("h"), "Old", null, null, DockState.Failed, null)
        val dockUi = DockUi(entries = persistentListOf(entry("a").copy(children = persistentListOf(child))), presence = DockPresence.Out, hidden = persistentListOf(hidden))
        val frame = DockKitMapper.map(dockUi, emptyMap(), "U")
        assertEquals("T-a", frame.nameOf(DockKitMapper.key(ref("a"))))
        assertEquals("explore", frame.nameOf(DockKitMapper.key(ref("c1"))))
        assertEquals("Old", frame.nameOf(DockKitMapper.key(ref("h"))))
        assertNull(frame.nameOf("nothing"))
    }

    @Test
    fun `a ring draws the mark the home row shows for its conversation`() {
        // An OpenCode conversation: its mode ("build") is no mark, so the server's OpenCode mark is used.
        assertEquals("opencode", DockKitMapper.markOf("build", "opencode"))
        assertEquals("opencode", DockKitMapper.markOf(null, "opencode"))
        // A phone agent names itself.
        assertEquals("claude", DockKitMapper.markOf("claude", null))
        assertEquals("codex", DockKitMapper.markOf("Codex", null))
        // Nothing known: the kit draws the monogram.
        assertNull(DockKitMapper.markOf(null, null))
        assertNull(DockKitMapper.markOf("build", null))
        assertNull(DockKitMapper.markOf(" ", " "))
    }
}
