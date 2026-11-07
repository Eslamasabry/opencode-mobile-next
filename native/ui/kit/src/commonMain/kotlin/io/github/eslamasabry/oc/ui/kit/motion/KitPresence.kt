package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** The direction a [KitPresence] slot opens along (Dart `Axis`). */
public enum class KitPresenceAxis { Horizontal, Vertical }

/**
 * A part that comes and goes without a jump (Dart `KitPresence`, lib/ui/kit/kit_presence.dart): it fades while its slot
 * opens along the row, and leaves the same way — one tween over QUICK on the Enter curve drives both the slot's
 * share and the opacity. Once gone it composes nothing, so a hidden part is not focusable and not read out. A presence
 * first composed shown is simply there; only a change of [visible] plays. Under reduced motion the change is instant.
 * The last content keeps drawing while the part leaves.
 *
 * States: none — it only shows or hides its content
 * Text: none (content decides)
 * Cap: none (wrapper)
 * Motion: slot share + opacity on QUICK / Enter; Off = instant
 * Replaces: KitPresence (lib/ui/kit/kit_presence.dart)
 */
@Composable
public fun KitPresence(
    visible: Boolean,
    modifier: Modifier = Modifier,
    axis: KitPresenceAxis = KitPresenceAxis.Horizontal,
    content: @Composable () -> Unit
) {
    val specs = KitMotion.specs
    // Flutter TweenAnimationBuilder(end: show ? 1 : 0, duration: reduced ? zero : quick, curve: enter), kit_presence.dart.
    val t by animateFloatAsState(if (visible) 1f else 0f, specs.quick(), label = "kit.presence")
    // The content to keep drawing while the part leaves (Dart `_last`).
    val last = remember { LastContent() }
    if (visible) last.content = content
    if (presenceGone(t, visible)) return
    val shown = last.content ?: content
    Layout(
        content = { shown() },
        // Clipped only while the slot is opening; at rest nothing is cut.
        modifier = modifier.then(if (t < 1f) Modifier.clipToBounds() else Modifier).graphicsLayer { alpha = t },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val w = placeables.maxOfOrNull { it.width } ?: 0
        val h = placeables.maxOfOrNull { it.height } ?: 0
        val (sw, sh) = presenceSlot(w, h, t, axis)
        layout(sw, sh) {
            // Dart Align(widthFactor/heightFactor): the child stays centred in the shrinking slot.
            placeables.forEach { it.place(Alignment.Center.align(IntSize(it.width, it.height), IntSize(sw, sh), layoutDirection)) }
        }
    }
}

private class LastContent {
    var content: (@Composable () -> Unit)? = null
}

/** True once a leaving part has finished (Dart `if (t == 0 && !widget.show) return SizedBox.shrink()`). */
internal fun presenceGone(t: Float, visible: Boolean): Boolean = t == 0f && !visible

/** The slot a [KitPresence] takes at progress [t] (Dart `Align(widthFactor: horizontal ? t : 1, heightFactor: …)`). */
internal fun presenceSlot(width: Int, height: Int, t: Float, axis: KitPresenceAxis): Pair<Int, Int> =
    when (axis) {
        KitPresenceAxis.Horizontal -> (width * t).roundToInt() to height
        KitPresenceAxis.Vertical -> width to (height * t).roundToInt()
    }
