package io.github.eslamasabry.oc.feature.chats.dock

import io.github.eslamasabry.oc.engine.attention.dock.DockRequestKind
import io.github.eslamasabry.oc.engine.attention.dock.DockSide
import io.github.eslamasabry.oc.ui.kit.dock.KitDockIntents
import io.github.eslamasabry.oc.ui.kit.dock.KitDockSide

/** What the person is told after an act from the dock. Implemented by the host with the kit's Undo bar and toast. */
public interface DockFeedback {
    /** [key] left the dock: offer Undo. */
    public fun removed(key: String, name: String)

    /** The conversation on screen cannot leave the dock. */
    public fun staysWhileOpen(name: String)

    public fun stopFailed(name: String)

    public fun answerFailed(name: String)
}

/**
 * Routes every [KitDockIntents] callback to the act that really does it ([EdgeDockPort]). [frame] is the frame the
 * person sees now (keys back to conversations, which request a ring asks about, what may be removed).
 *
 * Requests: Deny answers a permission at once by the home list's path. Allow answers at once only while that
 * conversation is on screen, where its request card shows the masked command; elsewhere it opens the conversation at
 * the request, because the swell cannot show what is being allowed. A question or a form (which the dock cannot answer)
 * opens at the request for both.
 */
public class DockIntentRouter(
    private val port: EdgeDockPort,
    private val frame: () -> DockKitFrame,
    private val feedback: DockFeedback,
) : KitDockIntents {
    override fun onOpen(key: String) {
        frame().refs[key]?.let(port::open)
    }

    override fun onOpenChild(key: String, childKey: String) {
        val refs = frame().refs
        (refs[childKey] ?: refs[key])?.let(port::open)
    }

    override fun onAllow(key: String) = answer(key, allow = true)

    override fun onDeny(key: String) = answer(key, allow = false)

    private fun answer(key: String, allow: Boolean) {
        val f = frame()
        val ask = f.asks[key]
        if (ask == null) {
            f.refs[key]?.let(port::open)
            return
        }
        val inPlace =
            ask.request.kind == DockRequestKind.Permission && (!allow || port.onScreen.value == ask.ref)
        if (inPlace) {
            val name = f.nameOf(key).orEmpty()
            port.answer(ask.ref, ask.request, allow) { feedback.answerFailed(name) }
        } else {
            port.openRequest(ask.ref, ask.request.id)
        }
    }

    override fun onStop(key: String) {
        val f = frame()
        val ref = f.refs[key] ?: return
        val name = f.nameOf(key).orEmpty()
        port.stop(ref) { feedback.stopFailed(name) }
    }

    override fun onRemove(key: String) {
        val f = frame()
        val ref = f.refs[key] ?: return
        val name = f.nameOf(key).orEmpty()
        if (key !in f.removable) {
            feedback.staysWhileOpen(name)
            return
        }
        port.remove(ref)
        feedback.removed(key, name)
    }

    /** The Undo after [onRemove]. */
    public fun undoRemove(key: String) {
        frame().refs[key]?.let(port::undoRemove)
    }

    override fun onRestore(key: String) {
        frame().refs[key]?.let(port::restore)
    }

    override fun onRename(key: String, name: String) {
        val ref = frame().refs[key] ?: return
        port.rename(ref, name.trim().takeIf { it.isNotEmpty() })
    }

    override fun onNewConversation() {
        port.newConversation()
    }

    override fun onTuckChange(tucked: Boolean) {
        if (tucked) port.tuck() else port.untuck()
    }

    override fun onReorder(from: Int, to: Int) {
        val f = frame()
        val rings = f.model.rings
        if (from !in rings.indices || to !in rings.indices || from == to) return
        val a = f.refs[rings[from].key] ?: return
        val b = f.refs[rings[to].key] ?: return
        port.reorder(a, b)
    }

    override fun onPinChange(key: String, pinned: Boolean) {
        val ref = frame().refs[key] ?: return
        if (pinned) port.pin(ref) else port.unpin(ref)
    }

    override fun onPlacementChange(side: KitDockSide, verticalBias: Float) {
        port.setPlacement(if (side == KitDockSide.Start) DockSide.Start else DockSide.End, verticalBias)
    }
}
