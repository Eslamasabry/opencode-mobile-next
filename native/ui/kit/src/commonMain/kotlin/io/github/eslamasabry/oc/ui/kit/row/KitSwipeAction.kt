package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuGroup
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import kotlin.coroutines.cancellation.CancellationException

/**
 * A row's one swipe (Dart `KitSwipeAction`; kit-v2 §2.5): an accelerator for an act whose treatment is "act, then
 * Undo". It never confirms, carries no destructive tint, and always has a twin in the row's menu that runs the identical
 * path ([menuItem]). The gesture itself is drawn by [KitSwipeRow]; this is the act and its one path from every door.
 *
 * [id] is unique per row in its list. [label] is the verb, as on the menu item ("Archive"), also the word revealed behind
 * the row and the custom accessibility action's name. [onAct] does the act (or, for a deferred local act, hides the item
 * and returns; the write happens in [onCommit]) and returns true when done, false when it failed and the caller already
 * showed a notice where the thing is; throwing counts as false. [undoMessage] names the thing ("Archived 'Fix login'").
 * [onUndo] runs the inverse or cancels a deferred commit. [onCommit] runs when the undo window closes unused; null means
 * [onAct] already did the write and [onUndo] is the server's inverse call.
 *
 * States: idle, revealing, acted (undo showing), failed
 * Text: none (the row and the undo bar carry the words)
 * Cap: none
 * Motion: none of its own (KitSwipeRow, KitUndoBar)
 * Hidden: the swipe (its menu twin and the custom accessibility action are the visible ways)
 * Replaces: KitSwipeAction (lib/ui/kit/kit_swipe_action.dart)
 */
@Immutable
public data class KitSwipeAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onAct: suspend () -> Boolean,
    val undoMessage: String,
    val onUndo: suspend () -> Unit,
    val onCommit: (suspend () -> Unit)? = null,
    val group: KitMenuGroup? = null,
) {
    /**
     * The one path from every door (the swipe, the menu twin, the custom accessibility action): runs [onAct]; when it
     * returns true, offers the undo line through [showUndo] and returns true. A false or throwing [onAct] ends here: the
     * caller's notice is the failure and no undo line is offered for an act that did not happen. With no host left to
     * show the undo line ([showUndo] null), a deferred act commits at once rather than being lost.
     */
    public suspend fun run(showUndo: ((KitUndoOffer) -> Unit)?): Boolean {
        val done =
            try {
                onAct()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                false
            }
        if (!done) return false
        if (showUndo != null) {
            showUndo(undoOffer())
            return true
        }
        onCommit?.invoke()
        return true
    }

    /** The undo line this act offers: its [id] tells offers apart, so a new act replaces the old line. */
    public fun undoOffer(): KitUndoOffer = KitUndoOffer(id = "kit-swipe-$id", message = undoMessage)

    /** The row menu's twin item: the same label, icon and group, running [run] through [select]. */
    public fun menuItem(select: () -> Unit): KitMenuItem = KitMenuItem(label = label, onSelect = select, icon = icon, group = group)
}
