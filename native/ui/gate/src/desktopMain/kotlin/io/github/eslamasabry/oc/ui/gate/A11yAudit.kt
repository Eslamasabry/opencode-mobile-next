package io.github.eslamasabry.oc.ui.gate

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.abs

/**
 * The accessibility audit: the checks the Android Accessibility Test Framework makes on a screen, run on the semantics tree
 * the screen reader reads, so they run on the desktop test host (B-A11Y-AUDIT). ATF itself cannot do this job here: the
 * Compose test library turns `enableAccessibilityChecks()` into a no-op under Robolectric ("Accessibility checks are
 * currently not supported by Robolectric"), so an ATF test in `androidHostTest` is green whatever it is given; a bad scene
 * proved it. Those checks need a device.
 *
 * Every control the person can act on (a click, a long click, a toggle, a slider, a text field) is an offender when it:
 * - `small`: its box is under 48 dp on a side (the larger of its touch box and its laid-out box, so a control partly scrolled out of view is judged whole);
 * - `unnamed`: a screen reader would read nothing for it (no text, no content description, not even a field label);
 * - `duplicate`: another control has the same box, so one touch could mean either (ATF's duplicate clickable bounds);
 * - `outside`: its box leaves the scene's width, so part of it cannot be reached (what a mirrored or enlarged layout does).
 * And any text is an offender when it is `clipped`: the words are cut off by the box they sit in (the box is shorter
 * than its lines; a deliberate ellipsis at a line limit is not).
 *
 * [sceneWidthPx] is the scene's width in pixels, [densityScale] the pixels per dp of the test window.
 */
@OptIn(ExperimentalTestApi::class)
object A11yAudit {
    data class Offender(val scene: String, val rule: String, val control: String, val detail: String) {
        override fun toString(): String = "[$scene] $rule: $control ($detail)"
    }

    private fun act(n: SemanticsNode): Boolean {
        val c = n.config
        return SemanticsActions.OnClick in c ||
            SemanticsActions.OnLongClick in c ||
            SemanticsActions.SetProgress in c ||
            SemanticsActions.SetText in c ||
            SemanticsProperties.ToggleableState in c
    }

    private fun words(n: SemanticsNode): String? {
        val c = n.config
        val text = c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val description = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        return listOfNotNull(text, description).joinToString(" ").trim().ifEmpty { null }
    }

    fun run(scene: String, test: ComposeUiTest, sceneWidthPx: Float, densityScale: Float = test.density.density): List<Offender> {
        test.waitForIdle()
        val found = ArrayList<Offender>()
        val always = SemanticsMatcher("any") { true }
        val acting = test.onAllNodes(SemanticsMatcher("acts") { act(it) }).fetchSemanticsNodes().filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        for (n in acting) {
            val name = words(n)
            val label = name ?: "(unnamed, ${n.config.getOrNull(SemanticsProperties.TestTag) ?: "no tag"})"
            // The box a finger can hit, or the laid-out box, whichever is larger: a scroll area clips the first of a control
            // that is only partly in view, and a control may lay out a little under 48 dp while its touch box is whole.
            val touch = n.touchBoundsInRoot
            val w = maxOf(touch.width, n.size.width.toFloat()) / densityScale
            val h = maxOf(touch.height, n.size.height.toFloat()) / densityScale
            if (w < 47.5f || h < 47.5f) found += Offender(scene, "small", label, "${w.toInt()}x${h.toInt()} dp")
            if (name == null) found += Offender(scene, "unnamed", label, "a screen reader reads nothing")
            val b = n.boundsInRoot
            if (b.left < -1f || b.right > sceneWidthPx + 1f) found += Offender(scene, "outside", label, "x ${b.left.toInt()}..${b.right.toInt()} of $sceneWidthPx px")
        }
        for (i in acting.indices) for (j in i + 1 until acting.size) {
            val a = acting[i].boundsInRoot
            val b = acting[j].boundsInRoot
            val clickA = SemanticsActions.OnClick in acting[i].config
            val clickB = SemanticsActions.OnClick in acting[j].config
            if (clickA && clickB && abs(a.left - b.left) < 1f && abs(a.top - b.top) < 1f && abs(a.right - b.right) < 1f && abs(a.bottom - b.bottom) < 1f)
                found += Offender(scene, "duplicate", words(acting[i]) ?: "(unnamed)", "same box as ${words(acting[j]) ?: "(unnamed)"}")
        }
        // Text is read from the unmerged tree: each run of words has its own layout.
        for (n in test.onAllNodes(always, useUnmergedTree = true).fetchSemanticsNodes()) {
            val get = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: continue
            val results = ArrayList<TextLayoutResult>()
            if (get(results) != true) continue
            for (r in results) {
                if (r.size.height + 1 < r.multiParagraph.height)
                    found += Offender(scene, "clipped", r.layoutInput.text.text.take(50), "${r.lineCount} lines, box ${r.size.height} px of ${r.multiParagraph.height.toInt()} px")
            }
        }
        return found.distinct()
    }
}
