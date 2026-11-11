package io.github.eslamasabry.oc.feature.chats.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.agents.persona.AgentPersona
import io.github.eslamasabry.oc.engine.attention.dock.DockChild
import io.github.eslamasabry.oc.engine.attention.dock.DockEntry
import io.github.eslamasabry.oc.engine.attention.dock.DockPeek
import io.github.eslamasabry.oc.engine.attention.dock.DockPresence
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestRef
import io.github.eslamasabry.oc.engine.attention.dock.DockSide
import io.github.eslamasabry.oc.engine.attention.dock.DockState
import io.github.eslamasabry.oc.engine.attention.dock.DockUi
import io.github.eslamasabry.oc.ui.kit.dock.KitDockChild
import io.github.eslamasabry.oc.ui.kit.dock.KitDockLine
import io.github.eslamasabry.oc.ui.kit.dock.KitDockLineRole
import io.github.eslamasabry.oc.ui.kit.dock.KitDockModel
import io.github.eslamasabry.oc.ui.kit.dock.KitDockPlacement
import io.github.eslamasabry.oc.ui.kit.dock.KitDockRing
import io.github.eslamasabry.oc.ui.kit.dock.KitDockRingState
import io.github.eslamasabry.oc.ui.kit.dock.KitDockSide

/** The request a ring's Allow and Deny are about: its own oldest, or that of the sub-agent that raised the ring. */
public data class DockAsk(val ref: ConversationRef, val request: DockRequestRef)

/**
 * One frame of the dock as the kit draws it, plus what the intents need to act: every key the kit reports back maps to
 * its conversation in [refs]; [asks] says which request a ring's Allow and Deny answer; [removable] holds the keys the
 * model lets the person remove (not the conversation on screen).
 */
public data class DockKitFrame(
    val model: KitDockModel,
    val refs: Map<String, ConversationRef> = emptyMap(),
    val asks: Map<String, DockAsk> = emptyMap(),
    val removable: Set<String> = emptySet(),
) {
    /** The name the kit shows for [key] (a ring, a sub-agent or a hidden conversation), or null. */
    public fun nameOf(key: String): String? {
        for (ring in model.rings) {
            if (ring.key == key) return ring.name
            ring.children.firstOrNull { it.key == key }?.let { return it.name }
        }
        return model.hidden.firstOrNull { it.key == key }?.name
    }

    public companion object {
        public val Empty: DockKitFrame = DockKitFrame(KitDockModel(emptyList()))
    }
}

/**
 * Maps the engine's [DockUi] onto the kit's [KitDockModel]. Pure: no Compose, no copy beyond [untitled] (the name of a
 * conversation the server has not titled yet). States map one to one. The dock is always available and tucked by
 * default: the nub (its dots carry each ring's state colour, so an alert elsewhere shows on it) is the resting form,
 * and the dock is drawn out only after the person pulled it out ([DockPresence.Out]); a dock with no ring is still a real model (the strip or nub with
 * its "+"). Only a switched-off dock, or a hidden one (no server connected), is [DockKitFrame.Empty], which the kit
 * draws as nothing.
 */
public object DockKitMapper {
    /** A key no two conversations share (the profile id's length keeps "a/b"+"c" apart from "a"+"b/c"). */
    public fun key(ref: ConversationRef): String = "${ref.profileId.raw.length}:${ref.profileId.raw}/${ref.sessionId.raw}"

    /**
     * The mark a ring draws, the one the home row shows for the conversation: the agent's own mark when the session
     * names a known agent (an agents server's "claude", "codex"), else the server's own agent mark (OpenCode, Codex).
     * An OpenCode session's agent is a mode such as "build", which is no mark: it falls through to the server's.
     * Null when neither is known, so the kit draws the monogram of the name.
     */
    public fun markOf(agent: String?, serverMark: String?): String? =
        agent?.takeIf { it.isNotBlank() }?.let { AgentPersona.of(it).markSlug }?.takeIf { it.isNotEmpty() }
            ?: serverMark?.takeIf { it.isNotBlank() }

    public fun state(state: DockState): KitDockRingState =
        when (state) {
            is DockState.WorkingWithTasks -> KitDockRingState.WorkingWithTasks(state.done, state.total)
            DockState.Working -> KitDockRingState.Working
            DockState.Thinking -> KitDockRingState.Thinking
            DockState.NeedsYou -> KitDockRingState.NeedsYou
            DockState.Finished -> KitDockRingState.Finished
            DockState.Failed -> KitDockRingState.Failed
            DockState.Idle -> KitDockRingState.Idle
        }

    public fun map(
        ui: DockUi,
        peeks: Map<ConversationRef, DockPeek>,
        untitled: String,
        shown: Boolean = true,
        /** False where the screen has its own "new conversation" control (the home's green "+"): the dock offers none. */
        offersNew: Boolean = true,
    ): DockKitFrame {
        // Never hidden for lack of live conversations, nor for having no ring: only switched off, or hidden.
        if (!shown || ui.presence == DockPresence.Hidden) return DockKitFrame.Empty
        val refs = HashMap<String, ConversationRef>()
        val asks = HashMap<String, DockAsk>()
        val removable = HashSet<String>()
        // The tail never repeats the conversation's own name: a session titled from its first prompt would otherwise
        // show that prompt twice (title, then the same sentence under the state).
        fun lines(ref: ConversationRef, vararg names: String?): List<KitDockLine> =
            when (val peek = peeks[ref]) {
                is DockPeek.Lines ->
                    peek.rows.filterNot { repeats(it.text, *names) }
                        .map { KitDockLine(if (it.fromPerson) KitDockLineRole.Person else KitDockLineRole.Agent, it.text) }
                DockPeek.Unavailable, null -> emptyList()
            }
        fun named(name: String?): String = name?.trim()?.takeIf { it.isNotEmpty() } ?: untitled
        fun child(c: DockChild): KitDockChild {
            val k = key(c.ref)
            refs[k] = c.ref
            if (c.removable) removable += k
            return KitDockChild(k, named(c.name), state(c.state), c.pulse, activity(lines(c.ref, c.name, c.title, c.agent)))
        }
        val rings =
            ui.entries.map { e ->
                val k = key(e.ref)
                refs[k] = e.ref
                if (e.removable) removable += k
                askOf(e)?.let { asks[k] = it }
                val tail = lines(e.ref, e.name, e.title, e.nickname)
                KitDockRing(
                    key = k,
                    name = named(e.name),
                    markId = markOf(e.agent, e.serverMark),
                    state = state(e.state),
                    pulse = e.pulse,
                    children = e.children.map(::child),
                    childOverflow = e.childOverflow,
                    causedBy = e.causedBy?.let { key(it) },
                    server = e.serverName.takeIf { ui.severalServers },
                    lastLine = activity(tail),
                    peek = tail,
                    pinned = e.pinned,
                )
            }
        val hidden =
            ui.hidden.map { h ->
                val k = key(h.ref)
                refs[k] = h.ref
                KitDockRing(key = k, name = named(h.name), markId = markOf(h.agent, h.serverMark), state = state(h.state))
            }
        val model =
            KitDockModel(
                rings = rings,
                hidden = hidden,
                current = ui.open?.let { key(it) },
                currentChild = ui.openChild?.let { key(it) },
                tucked = ui.tucked || ui.presence == DockPresence.Nub,
                placement =
                    KitDockPlacement(
                        side = if (ui.placement.side == DockSide.Start) KitDockSide.Start else KitDockSide.End,
                        verticalBias = ui.placement.bias,
                    ),
                offersNew = offersNew,
            )
        return DockKitFrame(model, refs, asks, removable)
    }

    /**
     * The line under a ring's state: its latest activity, the newest line of the agent's own (a reply or a step), never
     * the person's prompt and never the name (the tail already left those out). Null when there is none.
     */
    private fun activity(tail: List<KitDockLine>): String? = tail.lastOrNull { it.role != KitDockLineRole.Person }?.text

    private fun norm(text: String): String = text.trim().replace(spaces, " ").lowercase()

    private val spaces = Regex("\\s+")

    /** True when [line] says the same as one of [names] (case and spacing aside; a cut name counts as its start). */
    internal fun repeats(line: String, vararg names: String?): Boolean {
        val l = norm(line)
        if (l.isEmpty()) return true
        return names.any { raw ->
            val n = raw?.let(::norm).orEmpty()
            when {
                n.isEmpty() -> false
                n == l -> true
                n.endsWith("…") || n.endsWith("...") -> n.trimEnd('…', '.').trimEnd().let { it.isNotEmpty() && l.startsWith(it) }
                else -> false
            }
        }
    }

    /**
     * The request a ring's Allow / Deny answer: its own oldest while it waits itself, else the oldest of the sub-agent
     * that raised it, else of any waiting sub-agent. Null when the ring waits on nothing it can name.
     */
    public fun askOf(e: DockEntry): DockAsk? {
        if (e.state != DockState.NeedsYou) return null
        if (e.ownState == DockState.NeedsYou) e.requests.firstOrNull()?.let { return DockAsk(e.ref, it) }
        val raised = e.causedBy?.let { ref -> e.children.firstOrNull { it.ref == ref } }
        raised?.requests?.firstOrNull()?.let { return DockAsk(raised.ref, it) }
        e.requests.firstOrNull()?.let { return DockAsk(e.ref, it) }
        for (c in e.children) c.requests.firstOrNull()?.let { return DockAsk(c.ref, it) }
        return null
    }
}
