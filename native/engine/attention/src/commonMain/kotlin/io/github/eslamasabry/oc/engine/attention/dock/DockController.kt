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
) {
    private var model = DockModel.empty(cap)
    private val state = MutableStateFlow(model.ui)
    public val ui: StateFlow<DockUi> = state.asStateFlow()

    /** Feeds one fact. The wiring calls this; the UI does not. */
    public fun feed(event: DockEvent) {
        val next = model.reduce(event)
        if (next === model) return
        model = next
        state.value = next.ui
        if (event is DockEvent.Deleted) scope.launch { nicknames.clear(event.ref) }
        if (event is DockEvent.ProfileGone) nicknames.forget(event.profile)
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
