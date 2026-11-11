package io.github.eslamasabry.oc.feature.chats.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
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
 * conversation the server has not titled yet). States map one to one; a hidden dock (nothing live, or the person
 * turned it off) is an empty model, which the kit draws as nothing.
 */
public object DockKitMapper {
    /** A key no two conversations share (the profile id's length keeps "a/b"+"c" apart from "a"+"b/c"). */
    public fun key(ref: ConversationRef): String = "${ref.profileId.raw.length}:${ref.profileId.raw}/${ref.sessionId.raw}"

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
    ): DockKitFrame {
        if (!shown || ui.presence == DockPresence.Hidden || ui.entries.isEmpty()) return DockKitFrame.Empty
        val refs = HashMap<String, ConversationRef>()
        val asks = HashMap<String, DockAsk>()
        val removable = HashSet<String>()
        fun lines(ref: ConversationRef): List<KitDockLine> =
            when (val peek = peeks[ref]) {
                is DockPeek.Lines ->
                    peek.rows.map { KitDockLine(if (it.fromPerson) KitDockLineRole.Person else KitDockLineRole.Agent, it.text) }
                DockPeek.Unavailable, null -> emptyList()
            }
        fun named(name: String?): String = name?.trim()?.takeIf { it.isNotEmpty() } ?: untitled
        fun child(c: DockChild): KitDockChild {
            val key = key(c.ref)
            refs[key] = c.ref
            if (c.removable) removable += key
            return KitDockChild(key, named(c.name), state(c.state), c.pulse, lines(c.ref).lastOrNull()?.text)
        }
        val rings =
            ui.entries.map { e ->
                val key = key(e.ref)
                refs[key] = e.ref
                if (e.removable) removable += key
                askOf(e)?.let { asks[key] = it }
                val tail = lines(e.ref)
                KitDockRing(
                    key = key,
                    name = named(e.name),
                    markId = e.agent?.takeIf { it.isNotBlank() },
                    state = state(e.state),
                    pulse = e.pulse,
                    children = e.children.map(::child),
                    childOverflow = e.childOverflow,
                    causedBy = e.causedBy?.let(::key),
                    server = e.serverName.takeIf { ui.severalServers },
                    lastLine = tail.lastOrNull()?.text,
                    peek = tail,
                    pinned = e.pinned,
                )
            }
        val hidden =
            ui.hidden.map { h ->
                val key = key(h.ref)
                refs[key] = h.ref
                KitDockRing(key = key, name = named(h.name), markId = h.agent?.takeIf { it.isNotBlank() }, state = state(h.state))
            }
        val model =
            KitDockModel(
                rings = rings,
                hidden = hidden,
                current = ui.open?.let(::key),
                currentChild = ui.openChild?.let(::key),
                tucked = ui.tucked || ui.presence == DockPresence.Nub,
                placement =
                    KitDockPlacement(
                        side = if (ui.placement.side == DockSide.Start) KitDockSide.Start else KitDockSide.End,
                        verticalBias = ui.placement.bias,
                    ),
            )
        return DockKitFrame(model, refs, asks, removable)
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
