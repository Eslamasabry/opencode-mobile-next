package io.github.eslamasabry.oc.feature.chats.dock

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.attention.dock.DockPeek
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestRef
import io.github.eslamasabry.oc.engine.attention.dock.DockSide
import io.github.eslamasabry.oc.engine.attention.dock.DockUi
import kotlinx.coroutines.flow.StateFlow

/**
 * What the edge dock's screens need from the app: the one dock model (the app's `AppEdgeDock`, collected once at app
 * scope) and the paths that already do each act. The dock adds no protocol call: opening is the app's navigation,
 * Deny is the home list's permission answer, Stop is the chat's own stop path.
 */
public interface EdgeDockPort {
    public val ui: StateFlow<DockUi>

    /** The newest lines of every conversation the dock shows (rings and their sub-agents), memory only. */
    public val peeks: StateFlow<Map<ConversationRef, DockPeek>>

    /** The person's Settings choice: false draws no dock anywhere. */
    public val shown: StateFlow<Boolean>

    /** The conversation on screen right now (null on the home), so the dock can tell a visible request apart. */
    public val onScreen: StateFlow<ConversationRef?>

    /** Switches to [ref] (a ring or a sub-agent) and marks it seen. */
    public fun open(ref: ConversationRef)

    /** Opens [ref] at its waiting request, where the request shows what it asks before it is answered. */
    public fun openRequest(ref: ConversationRef, requestId: String)

    /** Allows or rejects a waiting permission at once (the home list's path); [onFailed] runs on main when it fails. */
    public fun answer(ref: ConversationRef, request: DockRequestRef, allow: Boolean, onFailed: () -> Unit)

    /** Stops the running turn by the chat's own stop path; [onFailed] runs on main when it fails. */
    public fun stop(ref: ConversationRef, onFailed: () -> Unit)

    public fun rename(ref: ConversationRef, name: String?)

    public fun remove(ref: ConversationRef)

    public fun undoRemove(ref: ConversationRef)

    public fun restore(ref: ConversationRef)

    public fun pin(ref: ConversationRef)

    public fun unpin(ref: ConversationRef)

    public fun reorder(from: ConversationRef, to: ConversationRef)

    public fun setPlacement(side: DockSide, bias: Float)

    public fun tuck()

    public fun untuck()

    /** The app's own new-conversation route. */
    public fun newConversation()
}

/**
 * The dock on the Conversations home, bound by the app (the back-gesture exclusion is Android's). [clearBottom] is
 * the room the home keeps at its foot (the floating +) that the dock must not cover.
 */
public interface HomeEdgeDock {
    @Composable public fun Content(clearBottom: Dp)
}

@ContributesTo(AppScope::class)
public interface HomeEdgeDockBindings {
    @Multibinds(allowEmpty = true) public val homeEdgeDocks: Set<HomeEdgeDock>
}
