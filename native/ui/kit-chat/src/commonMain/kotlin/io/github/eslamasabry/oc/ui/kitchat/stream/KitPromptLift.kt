package io.github.eslamasabry.oc.ui.kitchat.stream

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion

/** A single admitted prompt's origin, held only while this chat is on screen. Never persisted. */
public class KitPromptLiftState {
    private var editor: Rect? = null
    private var offered: Pair<String, Rect>? = null
    private var serial = 0L

    public fun editorAt(bounds: Rect) {
        editor = bounds
    }

    public fun offer(text: String): Long {
        serial += 1
        offered = editor?.let { text to it }
        return serial
    }

    public fun cancel(id: Long) {
        if (id == serial) offered = null
    }

    internal fun take(text: String): Rect? {
        val offer = offered?.takeIf { it.first == text } ?: return null
        offered = null
        return offer.second
    }
}

public val LocalKitPromptLift: ProvidableCompositionLocal<KitPromptLiftState?> =
    staticCompositionLocalOf {
        null
    }

/** Shares only the editor's measured origin with newly admitted optimistic bubbles. */
@Composable
public fun KitPromptLiftHost(content: @Composable () -> Unit) {
    val state = remember { KitPromptLiftState() }
    CompositionLocalProvider(LocalKitPromptLift provides state, content = content)
}

/** Measures the editor without changing layout. */
@Composable
public fun Modifier.kitPromptOrigin(): Modifier {
    val state = LocalKitPromptLift.current
    return if (state == null) this else onGloballyPositioned { state.editorAt(it.boundsInRoot()) }
}

/**
 * Paint-only lift: Full rises/scales, Calm fades in place, Off and system reduced motion draw
 * immediately.
 */
@Composable
public fun Modifier.kitPromptLift(id: Long, text: String): Modifier {
    val state = LocalKitPromptLift.current
    val origin = remember(id) { state?.take(text) }
    val motion = KitMotion.state
    if (origin == null || motion.level == MotionLevel.Off) return this
    val position = remember(id) { floatArrayOf(origin.center.y) }
    val progress = remember(id) { Animatable(0f) }
    LaunchedEffect(id, motion.level) {
        progress.animateTo(1f, tween(250, easing = CubicBezierEasing(.16f, 1f, .3f, 1f)))
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return this.onGloballyPositioned { position[0] = it.boundsInRoot().center.y }
        .graphicsLayer {
            val t = progress.value
            alpha = (t * 2).coerceAtMost(1f)
            if (motion.level == MotionLevel.Full) {
                translationY = (origin.center.y - position[0]).coerceAtLeast(0f) * (1f - t)
                scaleX = .92f + .08f * t
                scaleY = scaleX
                transformOrigin = TransformOrigin(if (rtl) 0f else 1f, 1f)
            }
        }
}
