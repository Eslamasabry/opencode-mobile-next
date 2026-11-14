package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.material3.ColorScheme
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchTimings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.eslamasabry.oc.ui.kit.foundation.kitRecord
import io.github.eslamasabry.oc.ui.kit.foundation.kitDrawLayer

/**
 * The theme change (P-029, M-019): dark to light, a new pack or accent fades over [MS] instead of jumping. The old
 * screen's picture fades out over the new theme ([rememberKitThemeFade]); [lerp] is the colour mix kept for parts
 * that blend two themes.
 */
public object ThemeFade {
    /** Flutter's theme change (`kThemeAnimationDuration`). */
    public const val MS: Int = 200

    /**
     * The reveal (owner 2026-11-10: the cross-fade "great idea, bad and janky implementation"; it passed through a
     * grey wash and stuttered on the frame that re-themes the app). The new theme now opens as a circle from where
     * the finger came down, over this long, and only once that heavy frame has been drawn under the still picture
     * of the old screen.
     */
    public const val REVEAL_MS: Int = 560

    /** The colours [t] of the way from [from] to [to] (0 … 1); dark-or-light follows the nearer end. */
    public fun lerp(from: KitColors, to: KitColors, t: Float): KitColors =
        when {
            t <= 0f -> from
            t >= 1f -> to
            else -> mix(from, to, t)
        }

    private fun mix(from: KitColors, to: KitColors, t: Float): KitColors {
        fun c(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color) = lerp(a, b, t)
        return KitColors(
            dark = if (t < 0.5f) from.dark else to.dark,
            ground = c(from.ground, to.ground),
            surface1 = c(from.surface1, to.surface1),
            surface2 = c(from.surface2, to.surface2),
            surface3 = c(from.surface3, to.surface3),
            hairline = c(from.hairline, to.hairline),
            text1 = c(from.text1, to.text1),
            text2 = c(from.text2, to.text2),
            text3 = c(from.text3, to.text3),
            accent = c(from.accent, to.accent),
            onAccent = c(from.onAccent, to.onAccent),
            partner = c(from.partner, to.partner),
            onPartner = c(from.onPartner, to.onPartner),
            attention = c(from.attention, to.attention),
            attentionFill = c(from.attentionFill, to.attentionFill),
            onAttentionFill = c(from.onAttentionFill, to.onAttentionFill),
            attentionSurface = c(from.attentionSurface, to.attentionSurface),
            attentionLine = c(from.attentionLine, to.attentionLine),
            success = c(from.success, to.success),
            scrim = c(from.scrim, to.scrim),
            codeKeyword = c(from.codeKeyword, to.codeKeyword),
            codeString = c(from.codeString, to.codeString),
            codeType = c(from.codeType, to.codeType),
            elevationShadow = c(from.elevationShadow, to.elevationShadow),
        )
    }
}

/**
 * M-019: the picture of the screen as it was, laid over the new theme and fading out. The live screen switches to the
 * new colours in one frame underneath it, so every word stays fully readable through the change (a colour lerp left
 * text near the ground's colour at the midpoint) and the parts' own moves (the segmented indicator) still run.
 */
@Stable
internal class KitThemeCover(internal val layer: GraphicsLayer) {
    /** The old screen while it fades out; null at rest. */
    internal var shot: ImageBitmap? by mutableStateOf(null)

    /** How much of the old screen is left: 1 (all of it) → 0 as the new theme opens over [ThemeFade.REVEAL_MS]. */
    internal val alpha: Animatable<Float, AnimationVector1D> = Animatable(0f)

    /** True when [shot] is a picture of the whole window: it is then drawn at the cover's place in the window. */
    internal var shotInWindow: Boolean = false

    /** Where the last finger came down, in the cover's pixels: the new theme opens from there. */
    internal var origin: Offset? = null

    /** A theme change is on its way (the old screen is being captured or still covers everything). */
    internal var pending: Boolean by mutableStateOf(false)

    /** The new theme has started to open: the heavy first frame in the new colours is behind us. */
    internal var revealing: Boolean by mutableStateOf(false)

    /** True while the old screen still covers part of the new one. */
    internal val fading: Boolean get() = shot != null

    /** The parts kept out of the fade while [KitThemeCoverHole.open], and the cover's own place to find them from. */
    internal val holes: MutableList<KitThemeCoverHole> = mutableStateListOf()
    internal var coords: LayoutCoordinates? = null

    /** The open holes' outlines in the cover's pixels, or null when there is none. */
    internal fun holePath(direction: LayoutDirection, density: Density): Path? {
        val own = coords?.takeIf { it.isAttached } ?: return null
        var path: Path? = null
        for (hole in holes) {
            val at = hole.coords?.takeIf { hole.open && it.isAttached } ?: continue
            val box = own.localBoundingBoxOf(at, clipBounds = false)
            val one = Path().apply { addOutline(hole.shape.createOutline(box.size, direction, density)) }
            (path ?: Path().also { path = it }).addPath(one, box.topLeft)
        }
        return path
    }
}

/**
 * A part that runs its own theme-switch motion (the mode control, s33): while [open], the theme cover's old picture is
 * not drawn over it, so the part shows the new theme at once under its own motion instead of the old and new themes
 * blended (the grey wash the owner saw mid-fade). [coords] is the part's place, [shape] its outline.
 */
@Stable
internal class KitThemeCoverHole {
    var open: Boolean by mutableStateOf(false)
    var coords: LayoutCoordinates? = null
    var shape: Shape = RectangleShape
}

/** The theme's cover, for parts that keep themselves out of its fade ([KitThemeCoverHole]); null outside a theme. */
internal val LocalKitThemeCover = staticCompositionLocalOf<KitThemeCover?> { null }

/** The colours shown now and the cover that fades the old screen out. */
internal class KitThemeFadeFrame(val colors: KitColors, val roles: Roles, val cover: KitThemeCover)

/**
 * Follows [target]: the screen as it is now is captured, the new colours apply at once underneath, and the capture
 * fades out over [ThemeFade.MS] (linear, Flutter `AnimatedTheme`). At once when [fades] is false (motion Off, still
 * frames). A change during a fade captures what is on screen then (the half-faded cover included), never jumping back.
 */
@Composable
internal fun rememberKitThemeFade(
    target: KitColors,
    /**
     * The roles [target] was made from. They are shown from the same frame as the colours: glass parts read the roles,
     * and with the roles a frame ahead they turned to the new theme while the old screen was still being captured.
     */
    targetRoles: Roles,
    fades: Boolean,
    /** The window as it is on screen (glass included), or null to use the theme layer's own snapshot. */
    captureWindow: (suspend () -> ImageBitmap?)? = null,
): KitThemeFadeFrame {
    val layer = rememberGraphicsLayer()
    val cover = remember(layer) { KitThemeCover(layer) }
    val shown = remember { mutableStateOf(target) }
    val shownRoles = remember { mutableStateOf(targetRoles) }
    LaunchedEffect(target, fades) {
        if (!fades) {
            cover.shot = null
            shown.value = target
            shownRoles.value = targetRoles
            return@LaunchedEffect
        }
        if (target == shown.value) {
            shownRoles.value = targetRoles
            return@LaunchedEffect
        }
        // Nothing drawn yet (the first frame): nothing to fade from.
        if (layer.size.width == 0 || layer.size.height == 0) {
            shown.value = target
            shownRoles.value = targetRoles
            return@LaunchedEffect
        }
        try {
            cover.pending = true
            cover.revealing = false
            // The window as it is on screen, glass and all; where the platform has none, the layer's last frame.
            val window = captureWindow?.invoke()
            cover.shotInWindow = window != null
            val shot = window ?: layer.toImageBitmap()
            cover.alpha.snapTo(1f)
            cover.shot = shot
            shown.value = target
            shownRoles.value = targetRoles
            // P-116: the first frame in the new colours recomposes every part that reads them (~80-95 ms measured).
            // It is drawn under the still picture of the old screen, which hides it whole; the reveal's clock starts
            // on the frame after it, so none of the motion is spent on that frame. Its cost is kept for
            // KitThemeMetrics.
            val first = withFrameNanos { it }
            val second = withFrameNanos { it }
            KitThemeMetrics.record((second - first) / NANOS_PER_MS)
            cover.revealing = true
            cover.alpha.animateTo(0f, tween(ThemeFade.REVEAL_MS, easing = RevealEasing))
        } finally {
            // A newer change (or leaving) cancels this one: what is shown still has to be the target's own pair.
            shown.value = target
            shownRoles.value = targetRoles
            cover.shot = null
            cover.pending = false
            cover.revealing = false
        }
    }
    // Motion Off and still frames show the new colours on the same frame.
    return KitThemeFadeFrame(if (fades) shown.value else target, if (fades) shownRoles.value else targetRoles, cover)
}

/**
 * Draws the theme's content through [cover]'s layer (so the last frame can be captured) and the fading old screen
 * over it. The cover is drawn inside the layer, so a capture taken mid-fade holds what was really on screen.
 */
internal fun Modifier.kitThemeCover(cover: KitThemeCover): Modifier =
    onPlaced { cover.coords = it }
        // Where a finger comes down is where the next theme change opens from; nothing is taken from the touch.
        .pointerInput(cover) {
            awaitEachGesture { cover.origin = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position }
        }
        .drawWithContent {
            val content = this
            val shot = cover.shot
            // Worked out here, not inside record: the recording scope's density is the content's (a cycle).
            val holes = if (shot != null) cover.holePath(layoutDirection, Density(density, fontScale)) else null
            kitRecord(cover.layer) {
                content.drawContent()
                if (shot != null) {
                    // The old screen stays whole outside a circle that grows from the touch to the farthest corner;
                    // inside it the new theme shows, as do the parts that run their own switch (the holes).
                    val left = cover.alpha.value.coerceIn(0f, 1f)
                    val centre = cover.origin ?: Offset(size.width / 2f, 0f)
                    val reach = maxOf(
                        (centre - Offset.Zero).getDistance(),
                        (centre - Offset(size.width, 0f)).getDistance(),
                        (centre - Offset(0f, size.height)).getDistance(),
                        (centre - Offset(size.width, size.height)).getDistance(),
                    )
                    val open = Path().apply { addOval(Rect(centre, reach * (1f - left))) }
                    if (holes != null) open.addPath(holes)
                    val at = if (cover.shotInWindow) cover.coords?.takeIf { it.isAttached }?.positionInWindow() ?: Offset.Zero else Offset.Zero
                    clipPath(open, ClipOp.Difference) { drawImage(shot, topLeft = Offset(-at.x, -at.y)) }
                }
            }
            kitDrawLayer(cover.layer)
        }

/** The reveal's curve: it leaves the control at once (no wait inside it), sweeps the screen and settles. */
private val RevealEasing = CubicBezierEasing(0f, 0f, .2f, 1f)

/**
 * The theme-change frame meter (theme-switch-premium Step 0): after each faded theme change it holds how long the frame
 * that first drew the new colours took (the gap between that frame and the next, so a 16 ms frame on a 60 Hz screen is
 * on time). Debug builds and benchmarks read [lastChangeFrameMs] or set [listener]; the theme never reads it.
 */
public object KitThemeMetrics {
    /** The last theme change's first-frame time in ms; null before the first faded change. */
    public var lastChangeFrameMs: Float? = null
        private set

    /** Called with each change's first-frame time (ms). Set by a debug screen or a benchmark; null by default. */
    public var listener: ((Float) -> Unit)? = null

    internal fun record(ms: Float) {
        lastChangeFrameMs = ms
        listener?.invoke(ms)
    }
}

/**
 * Material's colour scheme, held back after a change until a theme switch's motion has finished
 * ([ThemeSwitchTimings.IDLE_MS] after the tap, so [ThemeSwitchTimings.IDLE_MS] − [ThemeSwitchTimings.COMMIT_MS] after
 * the commit). Material keeps it in a static local, so its swap recomposes every part under the theme; held back, that
 * frame lands when nothing moves. The kit's own colours are not held back. A newer change restarts the wait (the latest
 * wins). At once when [defers] is false (motion Off, still frames) and on the first frame.
 */
@Composable
internal fun rememberSettledScheme(roles: Roles, defers: Boolean): ColorScheme {
    val scheme = remember(roles) { MaterialBridge.scheme(roles) }
    val held = remember { mutableStateOf(scheme) }
    LaunchedEffect(scheme, defers) {
        if (defers && held.value !== scheme) delay(SCHEME_SETTLE_MS)
        held.value = scheme
    }
    return if (defers) held.value else scheme
}

private const val NANOS_PER_MS = 1_000_000f

/** How long Material's scheme is held back after a theme change. */
internal const val SCHEME_SETTLE_MS: Long = ThemeSwitchTimings.IDLE_MS - ThemeSwitchTimings.COMMIT_MS
