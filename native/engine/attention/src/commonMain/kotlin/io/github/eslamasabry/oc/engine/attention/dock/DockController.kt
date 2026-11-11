package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the dock's UI consumes: one [ui] flow and the person's intents. No Compose here.
 *
 * Confined to one dispatcher (the app's main): every call mutates [DockModel] in place of a lock, like the app's other
 * main-thread state holders. [navigate] opens a conversation the way a tap in the conversation list does; the UI
 * layer supplies it. The tuck is not persisted: it lasts until a new failure or request, or until nothing is live.
 */
public class DockController(
    private val scope: CoroutineScope,
    private val nicknames: DockNicknames,
    private val navigate: (ConversationRef) -> Unit,
    cap: Int = DockModel.DEFAULT_CAP,
    private val removals: DockRemovals? = null,
    private val pins: DockPins? = null,
    private val placementStore: DockPlacementStore? = null,
    private val actions: DockActions? = null,
    private val peeks: DockPeeks? = null,
) {
    private var model = DockModel.empty(cap)
    private val state = MutableStateFlow(model.ui)
    public val ui: StateFlow<DockUi> = state.asStateFlow()

    /** Feeds one fact. The wiring calls this; the UI does not. */
    public fun feed(event: DockEvent) {
        val next = model.reduce(event)
        if (next === model) return
        val removedBefore = model.removedRefs
        val pinsBefore = model.pinnedRefs
        val placementBefore = model.ui.placement
        model = next
        state.value = next.ui
        persist(removedBefore, next.removedRefs) { profile ->
            removals?.save(profile) { model.removedRefs.filter { it.profileId == profile }.map { it.sessionId } }
        }
        // Order matters for pins, so any change of the list (including a reorder) saves the profile's whole list.
        persist(pinsBefore, next.pinnedRefs, orderMatters = true) { profile ->
            pins?.save(profile) { model.pinnedRefs.filter { it.profileId == profile }.map { it.sessionId } }
        }
        if (placementStore != null && next.ui.placement != placementBefore && event is DockEvent.SetPlacement) {
            scope.launch { placementStore.save(model.ui.placement) }
        }
        if (event is DockEvent.Deleted) scope.launch { nicknames.clear(event.ref) }
        if (event is DockEvent.ProfileGone) {
            nicknames.forget(event.profile)
            peeks?.forgetProfile(event.profile)
        }
        if (event is DockEvent.Deleted) peeks?.forget(event.ref)
    }

    private fun persist(
        before: List<ConversationRef>,
        after: List<ConversationRef>,
        orderMatters: Boolean = false,
        save: suspend (ProfileId) -> Unit,
    ) {
        if (before == after) return
        val changed =
            if (orderMatters) (before + after).map { it.profileId }.toSet().filter { p -> before.filter { it.profileId == p } != after.filter { it.profileId == p } }
            else (before.toSet() - after.toSet() + (after.toSet() - before.toSet())).map { it.profileId }.toSet()
        for (profile in changed) scope.launch { save(profile) }
    }

    /** The person tapped a ring: switch to that conversation and mark it seen. */
    public fun open(ref: ConversationRef) {
        feed(DockEvent.Opened(ref))
        navigate(ref)
    }

    /** Navigation reports which conversation is on screen (null when none), however it was reached. */
    public fun conversationShown(ref: ConversationRef?) {
        feed(DockEvent.Opened(ref))
    }

    /** Holds a ring and types a name. Blank clears it and the session title shows again. */
    public fun rename(ref: ConversationRef, name: String?) {
        scope.launch {
            when (val saved = nicknames.set(ref, name)) {
                is NicknameResult.Saved -> feed(DockEvent.NicknameSet(ref, saved.name))
                NicknameResult.Refused -> Unit
            }
        }
    }

    /**
     * The person removes a ring (or a sub-agent). It stays out until it needs them or fails, or until [restore]; the
     * conversation on screen cannot be removed. See [DockModel] for the full rule.
     */
    public fun remove(ref: ConversationRef) {
        feed(DockEvent.Remove(ref))
    }

    /** The short Undo after [remove]. The model keeps no timer: the UI decides how long to offer it. */
    public fun undoRemove(ref: ConversationRef) {
        feed(DockEvent.Restore(ref))
    }

    /** Brings one conversation back from the hidden list. */
    public fun restore(ref: ConversationRef) {
        feed(DockEvent.Restore(ref))
    }

    /** Reads the removed set saved for a profile. Call when its connection goes live, before the first reconcile. */
    public suspend fun loadRemoved(profile: ProfileId) {
        val saved = removals?.load(profile).orEmpty()
        if (saved.isNotEmpty()) feed(DockEvent.RemovedLoaded(profile, saved))
    }

    /** Pins a ring (not a sub-agent): it stays at the top in the person's order and does not leave when idle. */
    public fun pin(ref: ConversationRef) {
        feed(DockEvent.Pin(ref))
    }

    public fun unpin(ref: ConversationRef) {
        feed(DockEvent.Unpin(ref))
    }

    /** Moves pinned [from] to where pinned [to] is. */
    public fun reorder(from: ConversationRef, to: ConversationRef) {
        feed(DockEvent.Reorder(from, to))
    }

    /** Reads the pins saved for a profile and marks it connected. Call before the first reconcile; always call it. */
    public suspend fun loadPins(profile: ProfileId): List<io.github.eslamasabry.oc.core.common.ids.SessionId> {
        val saved = pins?.load(profile).orEmpty()
        feed(DockEvent.PinsLoaded(profile, saved))
        return saved
    }

    /** Device-wide, not per profile. [bias] is clamped to 0..1. */
    public fun setPlacement(side: DockSide, bias: Float) {
        feed(DockEvent.SetPlacement(DockPlacement.of(side, bias)))
    }

    public suspend fun loadPlacement() {
        placementStore?.let { feed(DockEvent.SetPlacement(it.load())) }
    }

    /** The peek card for a ring, without switching to it. [DockPeek.Unavailable] when nothing is cached. */
    public fun peek(ref: ConversationRef): kotlinx.coroutines.flow.Flow<DockPeek> =
        peeks?.peek(ref) ?: kotlinx.coroutines.flow.flowOf(DockPeek.Unavailable)

    /**
     * Allows or rejects a waiting permission from the sheet, by the same path the home list uses (answered at once; the
     * UI offers its own Undo if it wants one, the dock adds no second hold). Questions and forms need their own
     * answer screens, so they are refused here.
     */
    public suspend fun answer(ref: ConversationRef, request: DockRequestRef, allow: Boolean): io.github.eslamasabry.oc.core.common.Outcome<Unit> {
        val port = actions ?: return io.github.eslamasabry.oc.core.common.Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("dockActions")))
        if (request.kind != DockRequestKind.Permission) {
            return io.github.eslamasabry.oc.core.common.Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("dockAnswerNeedsScreen")))
        }
        return port.answerPermission(ref, request.id, allow)
    }

    /** Stops the running turn of a conversation, by the chat's own stop path. */
    public suspend fun stop(ref: ConversationRef): io.github.eslamasabry.oc.core.common.Outcome<Unit> =
        actions?.stop(ref)
            ?: io.github.eslamasabry.oc.core.common.Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("dockActions")))

    /** Clears Finished or Failed without opening the conversation. */
    public fun dismissSeen(ref: ConversationRef) {
        feed(DockEvent.Seen(ref))
    }

    public fun tuck() {
        feed(DockEvent.Tuck)
    }

    public fun untuck() {
        feed(DockEvent.Untuck)
    }

    /** Reads the saved nicknames of a profile into the dock. Call when its connection goes live. */
    public suspend fun loadNicknames(profile: ProfileId) {
        feed(DockEvent.Nicknames(profile, nicknames.load(profile)))
    }

    public fun profileGone(profile: ProfileId) {
        feed(DockEvent.ProfileGone(profile))
    }
}
