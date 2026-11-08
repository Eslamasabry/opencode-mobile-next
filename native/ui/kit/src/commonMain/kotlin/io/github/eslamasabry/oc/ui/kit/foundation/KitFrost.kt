package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.FrostStrength
import kotlin.random.Random
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import io.github.eslamasabry.oc.ui.design.effects.FrostConditions
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.design.style.Style

/**
 * What scrolls behind a frost surface, recorded once per frame (plan 06 K12). Compose cannot blur
 * "what is behind" without a source, so `KitScreen` hoists this and the transcript list wears
 * [kitFrostSource].
 */
@Stable
public class KitFrostState internal constructor(internal val layer: GraphicsLayer) {
    internal var sourceOrigin: Offset = Offset.Zero

    /** Counts the recordings (not state: read by liquid panes' samplers, never by a draw). */
    internal var version: Int = 0
}

@Composable
public fun rememberKitFrostState(): KitFrostState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { KitFrostState(layer) }
}

/**
 * Records the content into [state] and draws it as usual. A kit modifier on the feature allowlist.
 */
public fun Modifier.kitFrostSource(state: KitFrostState): Modifier =
    this.onGloballyPositioned { state.sourceOrigin = it.positionInRoot() }
        .drawWithContent {
            state.layer.record { this@drawWithContent.drawContent() }
            state.version++
            drawLayer(state.layer)
        }

/**
 * Frost behind a part (the composer or the chat header only, owner rule R3). The content recorded
 * by [kitFrostSource] is drawn through the platform's blur + saturation effect (Android API 31+),
 * then the role's tint at the strength's alpha, then the role's edge line. Solid — the tint colour,
 * opaque — when the platform has no effect (below API 31, desktop) or a hold applies (Saver,
 * reduced motion, high contrast, a Style other than Glass).
 */
@Composable
public fun Modifier.kitFrost(
    role: FrostRole,
    state: KitFrostState,
    shown: Boolean = true,
): Modifier {
    val platform = LocalKitPlatform.current
    val appearance = LocalKitAppearance.current
    val colors = LocalKitColors.current
    val reduced = LocalKitMotion.current.reduced
    val signals by platform.signals.collectAsState()
    val density = LocalDensity.current.density
    val conditions =
        FrostConditions(
            appearance.saver,
            reduced,
            signals.highContrast,
            // N-57: only Glass frosts (owner: Solid stays opaque, "no glass"); every other style lies on its ground.
            styleAllows = LocalKitStyle.current.style == Style.Glass,
        )
    val spec =
        if (shown) FrostPolicy.spec(role, appearance.frost, colors.dark, conditions) else null
    val effect =
        remember(spec, density, platform) { spec?.let { platform.frostEffect(it, density) } }
    // N-52: the frameless composer lies on the page's ground like the header, never on a grey band of its own.
    val tint = colors.ground
    val edge: Color =
        if (role == FrostRole.ChatHeader) colors.text1.copy(alpha = FrostPolicy.HEADER_EDGE_ALPHA)
        else Color.White.copy(alpha = FrostPolicy.HIGHLIGHT_ALPHA)
    val blur = rememberGraphicsLayer()
    val origin = remember { arrayOf(Offset.Zero) }
    return this.onGloballyPositioned { origin[0] = it.positionInRoot() }
        .clipToBounds()
        .drawBehind {
            if (effect == null || spec == null) {
                drawRect(tint)
            } else {
                // N-52/N-57: the recorded content has no ground of its own (the screen paints that), so the blurred
                // copy alone is mostly clear and the sharp content under this part showed through it. The opaque
                // ground goes first: only the blur and the tint are seen.
                drawRect(colors.ground.copy(alpha = 1f))
                blur.renderEffect = effect
                val shift = state.sourceOrigin - origin[0]
                blur.record { translate(shift.x, shift.y) { drawLayer(state.layer) } }
                drawLayer(blur)
                drawRect(tint.copy(alpha = spec.tintAlpha))
            }
            if (effect != null && spec != null) {
                val y = if (role == FrostRole.ChatHeader) size.height - 1f else 0f
                drawLine(edge, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
        }
}

/**
 * Where the glass lies relative to its child's box (Dart `KitFrost.paneInsets`); negative values run it out past the
 * box (the header's glass up through the status bar).
 */
public data class KitFrostInsets(val start: Dp = 0.dp, val top: Dp = 0.dp, val end: Dp = 0.dp, val bottom: Dp = 0.dp)

/** The constants of kit_frost.dart that are not the strength's (Dart `KitFrost.noiseOpacity` … `fade`). */
public object KitFrostRules {
    /** Glass fading in or out as content moves under or away. */
    public const val FADE_MS: Int = 180 // F: lib/ui/kit/kit_frost.dart:166

    /** The noise tile's side in pixels, seeded so goldens are stable. */
    public const val NOISE_TILE: Int = 64 // F: lib/ui/kit/kit_frost.dart:251

    /** The noise seed. */
    public const val NOISE_SEED: Int = 7 // F: lib/ui/kit/kit_frost.dart:252

    /**
     * The tint's alpha at fade [t] (0 solid → 1 glass), Dart `_backdrop`: the header leaves the page's opaque ground
     * for the glass ([FrostStrength.headerTint]); the composer's pane, clear at rest, takes its tint on. Dark lays
     * [FrostPolicy.DARK_TINT_RELIEF] less tint over the blur.
     */
    public fun tintAlpha(role: FrostRole, strength: FrostStrength, dark: Boolean, t: Float): Float {
        val relief = if (dark) FrostPolicy.DARK_TINT_RELIEF else 0f
        return if (role == FrostRole.ChatHeader) {
            val header = strength.headerTint - relief
            1f + (header - 1f) * t // F: lib/ui/kit/kit_frost.dart:391
        } else {
            (strength.tint - relief) * t // F: lib/ui/kit/kit_frost.dart:391
        }
    }
}

/**
 * A surface that is glass while [shown] and [strength] allow it, and solid otherwise, fading between the two over
 * [KitFrostRules.FADE_MS] (instant under reduced motion; Dart `KitFrost`). The glass is painted behind [content] and
 * never clips it; [content] keeps its place and state through every change. What lies behind is the content recorded
 * by [kitFrostSource] into [state]; the platform blurs it (Android API 31+), and where it cannot (desktop, older
 * Android) the pane is its tint alone. A null [strength] is solid whatever [shown] says: no blur layer at all.
 *
 * States: none — a surface around its child. Cap: wrapper (its child's).
 */
@Composable
public fun KitFrost(
    role: FrostRole,
    strength: FrostStrength?,
    shown: Boolean,
    tint: Color,
    state: KitFrostState,
    modifier: Modifier = Modifier,
    radius: Dp = 0.dp,
    paneInsets: KitFrostInsets = KitFrostInsets(),
    edges: Boolean = true,
    content: @Composable () -> Unit,
) {
    val platform = LocalKitPlatform.current
    val colors = LocalKitColors.current
    val reduced = LocalKitMotion.current.reduced
    val density = LocalDensity.current.density
    val usable = strength?.takeIf { it != FrostStrength.Off }
    val on = usable != null && shown
    // The last strength that showed, kept while the glass fades out.
    val last = remember { arrayOf(usable) }
    if (usable != null) last[0] = usable
    val amount = remember { Animatable(if (on) 1f else 0f) }
    LaunchedEffect(on, usable, reduced) {
        when {
            usable == null || reduced -> amount.snapTo(if (on) 1f else 0f) // turned off: solid at once
            on -> amount.animateTo(1f, tween(KitFrostRules.FADE_MS))
            else -> amount.animateTo(0f, tween(KitFrostRules.FADE_MS))
        }
    }
    val glass = last[0]
    val effect =
        remember(glass, density, platform) {
            glass?.let { platform.frostEffect(FrostSpec(it.sigmaDp, it.saturation, 0f), density) }
        }
    val saturate = remember(glass) { glass?.let { ColorFilter.colorMatrix(ColorMatrix(KitFrostSaturation.matrix(it.saturation))) } }
    val noise = remember { kitFrostNoise() }
    val blur = rememberGraphicsLayer()
    val origin = remember { arrayOf(Offset.Zero) }
    val hairline = kitHairline()
    val light = if (colors.dark) colors.text1 else colors.surface1
    Box(
        modifier
            .onGloballyPositioned { origin[0] = it.positionInRoot() }
            .drawBehind {
                val t = amount.value
                if (t <= 0f || glass == null) return@drawBehind
                val l = paneInsets.start.toPx(); val tp = paneInsets.top.toPx()
                val w = size.width - l - paneInsets.end.toPx(); val h = size.height - tp - paneInsets.bottom.toPx()
                if (w <= 0f || h <= 0f) return@drawBehind
                val corner = CornerRadius(radius.toPx())
                val pane = Path().apply { addRoundRect(RoundRect(Rect(Offset(l, tp), Size(w, h)), corner)) }
                clipPath(pane) {
                    if (effect != null) {
                        blur.renderEffect = effect
                        blur.colorFilter = saturate
                        val shift = state.sourceOrigin - origin[0]
                        blur.record { translate(shift.x, shift.y) { drawLayer(state.layer) } }
                        drawLayer(blur)
                    }
                    drawRect(tint.copy(alpha = KitFrostRules.tintAlpha(role, glass, colors.dark, t)))
                    val specks = colors.elevationShadow.copy(alpha = FrostPolicy.NOISE_OPACITY * t)
                    drawRect(
                        ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)),
                        colorFilter = ColorFilter.tint(specks, BlendMode.SrcIn),
                    )
                }
            }
            .drawWithContent {
                drawContent()
                val t = amount.value
                if (t <= 0f || glass == null || !edges) return@drawWithContent
                val l = paneInsets.start.toPx(); val tp = paneInsets.top.toPx()
                val w = size.width - l - paneInsets.end.toPx(); val h = size.height - tp - paneInsets.bottom.toPx()
                val stroke = hairline.toPx()
                translate(l, tp) {
                    if (role == FrostRole.ChatHeader) {
                        val y = h - stroke / 2 // F: lib/ui/kit/kit_frost.dart:487
                        drawLine(colors.text1.copy(alpha = FrostPolicy.HEADER_EDGE_ALPHA * t), Offset(0f, y), Offset(w, y), stroke)
                        return@translate
                    }
                    val r = radius.toPx()
                    // The edge all round, ink, inside the pane by half a line (kit_frost.dart:491-493).
                    drawRoundRect(
                        colors.text1.copy(alpha = FrostPolicy.EDGE_ALPHA * t),
                        Offset(stroke / 2, stroke / 2), // F: lib/ui/kit/kit_frost.dart:491
                        Size(w - stroke, h - stroke),
                        CornerRadius((r - stroke / 2).coerceAtLeast(0f)), // F: lib/ui/kit/kit_frost.dart:491
                        style = Stroke(stroke),
                    )
                    // The light along the top: the outline's upper part only (kit_frost.dart:494-503).
                    clipRect(0f, 0f, w, maxOf(r, stroke) + 1f) {
                        drawRoundRect(
                            light.copy(alpha = FrostPolicy.HIGHLIGHT_ALPHA * t),
                            Offset(stroke * 1.5f, stroke * 1.5f), // NV: the outline deflated by one more line width
                            Size(w - stroke * 3, h - stroke * 3), // NV: both sides of that deflation
                            CornerRadius((r - stroke * 1.5f).coerceAtLeast(0f)), // NV: the same deflation
                            style = Stroke(stroke),
                        )
                    }
                }
            },
    ) {
        content()
    }
}

/** The saturation matrix of kit_frost.dart:222-232 (Rec. 709 luma), row-major 4×5 as Compose's ColorMatrix takes it. */
public object KitFrostSaturation {
    public fun matrix(s: Float): FloatArray {
        val r = 0.2126f // F: lib/ui/kit/kit_frost.dart:224
        val g = 0.7152f // F: lib/ui/kit/kit_frost.dart:224
        val b = 0.0722f // F: lib/ui/kit/kit_frost.dart:224
        val i = 1 - s
        return floatArrayOf(
            r * i + s, g * i, b * i, 0f, 0f,
            r * i, g * i + s, b * i, 0f, 0f,
            r * i, g * i, b * i + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}

/** A 64 px tile of white specks of random strength, seeded, used as an alpha mask under the tint. */
private fun kitFrostNoise(): ImageBitmap {
    val side = KitFrostRules.NOISE_TILE
    val image = ImageBitmap(side, side)
    val canvas = Canvas(image)
    val random = Random(KitFrostRules.NOISE_SEED)
    val paint = Paint()
    for (y in 0 until side) {
        for (x in 0 until side) {
            paint.color = Color.White.copy(alpha = random.nextFloat())
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
        }
    }
    return image
}
