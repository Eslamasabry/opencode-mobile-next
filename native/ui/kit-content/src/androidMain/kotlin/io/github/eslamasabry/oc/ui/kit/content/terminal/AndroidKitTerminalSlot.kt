package io.github.eslamasabry.oc.ui.kit.content.terminal

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Android's live terminal slot (plan 06 §7.7 `.live`, 11 W11-08a): hosts the terminal host's view — a Termux
 * `TerminalView` over the PTY — in an `AndroidView`. The kit never names the Termux type; it only needs an Android
 * [View], so the same slot shows a local PTY now and the forked `RemoteTerminalSession` view later (S5-TERMFORK).
 *
 * The view's own accessibility children are hidden (`IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`): together with
 * [KitTerminalLive]'s `clearAndSetSemantics`, TalkBack finds one "Terminal" node and never the grid. Sessions outlive
 * screens (17 §2.6), so a view still attached to an old parent is detached before it is hosted again.
 */
public object AndroidKitTerminalSlot : KitTerminalSlot {
    @Composable
    override fun Live(platformView: Any, modifier: Modifier) {
        key(platformView) {
            AndroidView(
                factory = { _ -> hostable(platformView) },
                modifier = modifier,
            )
        }
    }

    /** The terminal's view, detached from any earlier parent and hidden from accessibility. */
    internal fun hostable(platformView: Any): View {
        val view =
            requireNotNull(platformView as? View) {
                "KitTerminalLive: the terminal handle must carry an android.view.View"
            }
        (view.parent as? ViewGroup)?.removeView(view)
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        return view
    }
}
