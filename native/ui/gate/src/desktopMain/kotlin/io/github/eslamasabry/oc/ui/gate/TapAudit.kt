package io.github.eslamasabry.oc.ui.gate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntRect
import io.github.eslamasabry.oc.ui.kit.foundation.KitTapObserver
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitTapObserver

/**
 * The tap audit (issue 147, "buttons do not respond"). A screen is composed in one state under [provide]; [run] then
 * puts a real finger on the centre of every control that has a click action, through the window's pointer pipeline
 * (so anything invisible on top of a control, a scrim, an overlay, a layer that is still animating, takes the tap
 * exactly as it would on a phone), and reports every control that is an offender:
 *
 * - `small`: its box is under 48 dp on a side;
 * - `lost`: it is drawn enabled and the finger on its centre did not reach its action (something else took the tap,
 *   or nothing did);
 * - `stolen`: the finger on its centre reached a different control's action;
 * - `silent`: it is drawn disabled and the finger on it was not answered with the reason;
 * - `no-reason`: it is disabled and says no reason (not even to a screen reader).
 *
 * Controls whose centre is outside the window are skipped (they are scrolled away; a scroll is not a lost tap).
 */
@OptIn(ExperimentalTestApi::class)
class TapAudit(private val state: String) {
    data class Offender(val state: String, val control: String, val rule: String, val detail: String) {
        override fun toString(): String = "[$state] $rule: $control ($detail)"
    }

    private class Observer : KitTapObserver {
        val acted = ArrayList<IntRect>()
        val explained = ArrayList<Pair<IntRect, String>>()

        override fun acted(bounds: IntRect) {
            acted += bounds
        }

        override fun explained(bounds: IntRect, reason: String) {
            explained += bounds to reason
        }
    }

    private val observer = Observer()

    /** Wraps the screen so the kit's tappables report to this audit. */
    @Composable
    fun provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitTapObserver provides observer, content = content)
    }

    fun run(test: ComposeUiTest, windowW: Int, windowH: Int): List<Offender> {
        test.waitForIdle()
        val found = ArrayList<Offender>()
        val clickable =
            SemanticsMatcher("has a click action and is not a text field") {
                SemanticsActions.OnClick in it.config &&
                    SemanticsActions.SetText !in it.config &&
                    SemanticsProperties.EditableText !in it.config
            }
        val items = test.onAllNodes(clickable).fetchSemanticsNodes().map { Item.of(it) }
        for (item in items) {
            val b = item.bounds
            if (b.width <= 0f || b.height <= 0f) continue
            if (b.center.x !in 0f..windowW.toFloat() || b.center.y !in 0f..windowH.toFloat()) continue
            val dpW = b.width / test.density.density
            val dpH = b.height / test.density.density
            if (dpW < 47.5f || dpH < 47.5f) found += Offender(state, item.name, "small", "${dpW.toInt()}x${dpH.toInt()} dp")
            if (item.disabled && item.reason.isNullOrBlank())
                found += Offender(state, item.name, "no-reason", "disabled and says nothing")
            observer.acted.clear()
            observer.explained.clear()
            test.onRoot().performTouchInput { click(Offset(b.center.x, b.center.y)) }
            test.waitForIdle()
            val c = b.center
            fun hits(r: IntRect) = c.x >= r.left && c.x <= r.right && c.y >= r.top && c.y <= r.bottom
            if (item.disabled) {
                if (observer.explained.none { hits(it.first) } && observer.acted.none { hits(it) })
                    found += Offender(state, item.name, "silent", "tap on a disabled control said nothing: ${item.reason ?: "-"}")
            } else if (observer.acted.none { hits(it) }) {
                found +=
                    if (observer.acted.isEmpty() && observer.explained.isEmpty())
                        Offender(state, item.name, "lost", "the tap reached no action")
                    else Offender(state, item.name, "stolen", "the tap reached another control")
            }
        }
        return found
    }

    private class Item(val name: String, val bounds: androidx.compose.ui.geometry.Rect, val disabled: Boolean, val reason: String?) {
        companion object {
            fun of(n: SemanticsNode): Item {
                val c = n.config
                val words =
                    c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
                        ?: c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
                        ?: c.getOrNull(SemanticsProperties.TestTag)
                        ?: "(no words)"
                return Item(
                    words.take(60),
                    n.boundsInWindow,
                    SemanticsProperties.Disabled in c,
                    c.getOrNull(SemanticsProperties.StateDescription),
                )
            }
        }
    }
}
