package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidJoinBody
import io.github.eslamasabry.oc.ui.kit.effects.joinedBand
import io.github.eslamasabry.oc.ui.kit.effects.joinedFill
import io.github.eslamasabry.oc.ui.kit.effects.joined
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * The two hues of the glow (plan 08 §5.3, Flutter `_glowHues`): the theme's accent and a lighter shade of it — deeper
 * on a light surface so the highlight still shows. One colour only since the 2026-10-23 Appearance cuts; never amber
 * (amber means "needs you").
 */
@Immutable
internal data class GlowColours(val primary: Color, val partner: Color) {
    /** The dim base ring under the Classic highlight. */
    val base: Color get() = primary.copy(alpha = primary.alpha * CLASSIC_BASE_ALPHA)

    /** Classic sweep: a dim ring with one soft highlight, primary brightening into partner over a third of the lap. */
    val classicStops: Array<Pair<Float, Color>>
        get() = arrayOf(0f to base, 0.10f to primary, 0.18f to partner, 0.26f to primary, 0.38f to base, 1f to base)

    /** Soft-ring sweep at [share] of the line's peak (the wash is 0.22 / 0.75 of it). */
    fun softStops(share: Float): Array<Pair<Float, Color>> {
        fun a(c: Color, alpha: Float) = c.copy(alpha = c.alpha * alpha * share)
        val peak = GlowGeometry.SOFT_LINE_PEAK
        return arrayOf(
            0f to a(primary, peak), 0.30f to a(partner, peak), 0.55f to a(partner, SOFT_LOW),
            0.80f to a(primary, SOFT_LOW), 1f to a(primary, peak),
        )
    }

    companion object {
        const val CLASSIC_BASE_ALPHA: Float = 0.28f
        const val SOFT_LOW: Float = 0.12f
        const val PARTNER_DARK: Float = 0.55f
        const val PARTNER_LIGHT: Float = 0.30f
    }
}

/**
 * Flutter's `Color.lerp`: straight sRGB channels. Compose's `lerp(Color, Color, Float)` mixes in Oklab, which gives a
 * different shade — the owner likes today's hue, so the Flutter maths is kept (GlowColoursTest).
 */
internal fun srgbLerp(a: Color, b: Color, t: Float): Color =
    Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )

internal fun glowColours(accent: Color, text1: Color, dark: Boolean): GlowColours =
    GlowColours(accent, srgbLerp(accent, text1, if (dark) GlowColours.PARTNER_DARK else GlowColours.PARTNER_LIGHT))

/** The composer pill's corner: 26 dp on phones, 18 dp on wide windows (plan 08 §5.3). */
public object KitComposerGlowDefaults {
    public val Corner: Dp = 26.dp
    public val WideCorner: Dp = 18.dp
}

/**
 * The running glow around the composer pill (plan 08 §5.3; owner likes it, 2026-10-14 — keep the hue). While a reply
 * runs ([active]) a bright highlight travels the pill's border over a dim base ring and a soft halo under it breathes
 * twice per lap; it fades in and out over `GLOW_FADE` (450 ms). [choice] is the one Glow setting.
 *
 * Levels: Full travels and breathes; Calm (also battery saver and a hot phone) draws a still, lit ring with the halo
 * held at its midpoint; Off draws nothing; Saver draws one still 1 dp hairline. [content] is the pill itself; the glow
 * never recomposes it (draw phase only, plan 08 R3) and is invisible to touch and screen readers.
 *
 * Motion: ambient loop (glow lap, halo breath) + fade (strength). Replaces: `_ClassicRing`, `_ClassicHalo`,
 * `_ActivityGlow` (lib/ui/kit/chat/kit_composer_classic.dart, kit_composer_glow.dart).
 */
@Composable
public fun KitComposerGlow(
    active: Boolean,
    modifier: Modifier = Modifier,
    choice: KitGlowChoice = KitGlowChoice.Classic,
    corner: Dp = KitComposerGlowDefaults.Corner,
    /**
     * Glass: the body of glass the box is (the jump pill joins it). While a drop is joined to it the ring, the Soft wash
     * and the halo run round the one joined body, never across the drop's foot along the box's own edge (owner
     * 2026-11-14, c26: the green line cut "Jump to latest" from the box).
     */
    join: KitLiquidJoinBody? = null,
    content: @Composable () -> Unit,
) {
    val outline = remember(join) { join?.let { body -> JoinedGlowOutline(body) } }
    ComposerGlow(active, modifier, choice, corner, outline, content)
}

/**
 * The outline the glow follows while its box is joined to a drop: a band along it ([band], in the ring node's px, its
 * box's top-left at the origin given) and the whole outline filled ([fill]); null while the box stands alone.
 */
internal interface GlowOutline {
    /** Whether a drop is joined now; read in composition (the halo is drawn another way while joined). */
    val joined: Boolean

    fun band(widthPx: Float, centred: Boolean, origin: Offset): Path?

    fun fill(origin: Offset): Path?
}

private class JoinedGlowOutline(private val body: KitLiquidJoinBody) : GlowOutline {
    override val joined: Boolean get() = body.joined

    override fun band(widthPx: Float, centred: Boolean, origin: Offset): Path? = body.joinedBand(widthPx, centred, origin)

    override fun fill(origin: Offset): Path? = body.joinedFill(origin)
}

@Composable
internal fun ComposerGlow(
    active: Boolean,
    modifier: Modifier,
    choice: KitGlowChoice,
    corner: Dp,
    outline: GlowOutline?,
    content: @Composable () -> Unit,
) {
    val motion = KitMotion.state
    val colors = LocalKitColors.current
    val saver = LocalKitAppearance.current.saver
    val plan = glowPlan(choice, motion.level, saver, motion.stillFrame)
    // Live colour, not the accent: on light themes the glow stays bright mint while words and buttons keep the deep
    // green (owner 2026-11-05).
    val colours = remember(colors.live, colors.text1, colors.dark) { glowColours(colors.live, colors.text1, colors.dark) }
    val target = if (active && plan.draw != GlowDraw.None) plan.strength else 0f
    val strength = rememberGlowStrength(target, motion)
    val lit by remember { derivedStateOf { strength.floatValue > 0f } }
    val travelling = plan.draw == GlowDraw.Travel && plan.loops && (active || lit)
    val phase = rememberLoopPhase(travelling, plan.lapMillis, speed = 1f, still = plan.framePhase)
    val shape = remember(corner) { RoundedCornerShape(corner) }
    val drawn = plan.draw != GlowDraw.None && (active || lit)
    Box(modifier) {
        if (drawn && plan.halo && plan.draw != GlowDraw.Hairline) {
            val breath = if (plan.draw == GlowDraw.Travel && plan.loops) ({ haloAlpha(phase.floatValue) }) else null
            // Only a joined box (liquid glass, Android 13+) draws its halo as a blurred fill of the one body.
            if (outline?.joined == true)
                JoinedHalo({ strength.floatValue }, breath, colours.primary, corner, outline, Modifier.matchParentSize())
            else
                ComposerHalo(
                    strength = { strength.floatValue },
                    breath = breath,
                    color = colours.primary,
                    shape = shape,
                    modifier = Modifier.matchParentSize(),
                )
        }
        if (drawn && plan.soft && plan.draw != GlowDraw.Hairline) {
            SoftWash({ strength.floatValue }, { phase.floatValue }, colours, corner, Modifier.matchParentSize(), outline)
        }
        Box(
            if (drawn) {
                Modifier.testTag(GLOW_TAG).composerRing({ strength.floatValue }, { phase.floatValue }, plan, colours, corner, outline)
            } else {
                Modifier
            },
        ) { content() }
    }
}

/** Test handle of the ring node (present only while the glow draws). */
public const val GLOW_TAG: String = "kit-composer-glow"

/**
 * The halo: its own node behind the pill. Its shadow never changes, so the shadow painter is built once; the breath
 * and the fade change only the layer's alpha (plan 08 §5.3 "Why the halo is its own node").
 */
@Composable
internal fun ComposerHalo(
    strength: () -> Float,
    breath: (() -> Float)?,
    color: Color,
    shape: RoundedCornerShape,
    modifier: Modifier,
) {
    val shadow = remember(color) {
        Shadow(radius = GlowGeometry.HALO_BLUR_DP.dp, color = color, spread = GlowGeometry.HALO_SPREAD_DP.dp, alpha = 1f)
    }
    Box(
        modifier
            // The halo spills past the pill: its layer gets that room so the blur is not cut at the pill's rectangle.
            .outset(HALO_ROOM_DP.dp)
            .graphicsLayer { alpha = strength() * (breath?.invoke() ?: GlowGeometry.HALO_STILL) }
            .padding(HALO_ROOM_DP.dp)
            .dropShadow(shape, shadow),
    )
}

/** Room around the pill for the halo's blur and spread (18 dp blur + 1 dp spread, rounded up). */
private const val HALO_ROOM_DP = 24

/**
 * The halo of a box that is a body of joined glass: the same soft light, of the joined outline (the box alone: its own
 * rounded box). A blurred fill of the outline on its own layer; the breath and the fade change only the layer's alpha.
 * Its layer reaches [JOINED_HALO_ROOM_DP] past the box, room for a drop standing on it and the blur.
 */
@Composable
private fun JoinedHalo(strength: () -> Float, breath: (() -> Float)?, color: Color, corner: Dp, outline: GlowOutline, modifier: Modifier) {
    Box(
        modifier
            .outset(JOINED_HALO_ROOM_DP.dp)
            .graphicsLayer {
                val blur = GlowGeometry.HALO_BLUR_DP.dp.toPx()
                renderEffect = BlurEffect(blur, blur, TileMode.Decal)
                alpha = strength() * (breath?.invoke() ?: GlowGeometry.HALO_STILL)
            }
            .drawBehind {
                val room = JOINED_HALO_ROOM_DP.dp.toPx()
                val spread = GlowGeometry.HALO_SPREAD_DP.dp.toPx()
                val joined = outline.fill(Offset(room, room))
                if (joined != null) drawPath(joined, color)
                else
                    drawRoundRect(
                        color,
                        topLeft = Offset(room - spread, room - spread),
                        size = Size(size.width - 2 * (room - spread), size.height - 2 * (room - spread)),
                        cornerRadius = CornerRadius(corner.toPx() + spread),
                    )
            },
    )
}

/** Room around a joined box for its halo: the blur, and a drop standing on the box (the pill's 32 dp and its rebound). */
private const val JOINED_HALO_ROOM_DP = 64

/** Lays the node out [by] larger on every side than the space it is given, centred on it (for glows that spill). */
private fun Modifier.outset(by: Dp): Modifier =
    layout { measurable, constraints ->
        val pad = by.roundToPx()
        val p = measurable.measure(
            constraints.copy(
                minWidth = constraints.minWidth + 2 * pad, maxWidth = constraints.maxWidth + 2 * pad,
                minHeight = constraints.minHeight + 2 * pad, maxHeight = constraints.maxHeight + 2 * pad,
            ),
        )
        layout(p.width - 2 * pad, p.height - 2 * pad) { p.place(-pad, -pad) }
    }

/** The Soft ring's blurred wash on its own layer (a constant blur; the sweep turns by a canvas rotation). */
@Composable
private fun SoftWash(strength: () -> Float, phase: () -> Float, colours: GlowColours, corner: Dp, modifier: Modifier, outline: GlowOutline?) {
    Box(
        modifier
            // The wash spills outside the pill: its layer gets room so the blur is not cut at the pill's edge.
            .outset((GlowGeometry.SOFT_WASH_DP / 2f + GlowGeometry.SOFT_WASH_BLUR_DP * 2f).dp)
            .graphicsLayer {
                val blur = GlowGeometry.SOFT_WASH_BLUR_DP.dp.toPx()
                renderEffect = BlurEffect(blur, blur, TileMode.Decal)
                alpha = strength()
            }
            .drawWithCache {
                val pad = (GlowGeometry.SOFT_WASH_DP / 2f + GlowGeometry.SOFT_WASH_BLUR_DP * 2f).dp.roundToPx().toFloat()
                val pill = Size(size.width - 2 * pad, size.height - 2 * pad)
                val band = ringBand(pill, corner.toPx(), GlowGeometry.SOFT_WASH_DP.dp.toPx(), Offset(pad, pad))
                val sweep = Brush.sweepGradient(
                    *colours.softStops(GlowGeometry.SOFT_WASH_PEAK / GlowGeometry.SOFT_LINE_PEAK),
                    center = Offset(size.width / 2f, size.height / 2f),
                )
                onDrawBehind {
                    clipPath(outline?.band(GlowGeometry.SOFT_WASH_DP.dp.toPx(), true, Offset(pad, pad)) ?: band) {
                        rotate((phase() - QUARTER) * FULL_TURN) { drawCircle(sweep, radius = size.maxDimension) }
                    }
                }
            },
    )
}

/**
 * The ring over the pill. Classic: a 1.6 dp sweep (dim base, one highlight) inside the pill's edge, or a still accent
 * ring under Calm. Soft: a 2.2 dp sweep line on the outline. Saver: a 1 dp accent hairline. The sweep's brush and the
 * ring's path are cached per size; each frame only rotates the canvas (no allocation per frame).
 */
internal fun Modifier.composerRing(
    strength: () -> Float,
    phase: () -> Float,
    plan: GlowPlan,
    colours: GlowColours,
    corner: Dp,
    outline: GlowOutline? = null,
): Modifier =
    drawWithCache {
        val r = corner.toPx()
        val hairline = GlowGeometry.HAIRLINE_DP.dp.toPx()
        val classic = GlowGeometry.CLASSIC_STROKE_DP.dp.toPx()
        val soft = GlowGeometry.SOFT_LINE_DP.dp.toPx()
        val classicBand = ringBand(size, r, classic, Offset.Zero, inside = true)
        val softBand = ringBand(size, r, soft, Offset.Zero)
        val classicSweep = Brush.sweepGradient(*colours.classicStops, center = Offset(size.width / 2f, size.height / 2f))
        val softSweep = Brush.sweepGradient(*colours.softStops(1f), center = Offset(size.width / 2f, size.height / 2f))
        // The sweep covers the joined body too (a drop stands above the box): its circle reaches past the box.
        val radius = size.maxDimension * 1.5f
        onDrawWithContent {
            drawContent()
            val s = strength()
            if (s <= 0f) return@onDrawWithContent
            // Joined to a drop (Glass): the ring runs round the one body; read here, so it follows the drop's motion.
            when {
                plan.draw == GlowDraw.Hairline -> {
                    val joined = outline?.band(hairline, false, Offset.Zero)
                    if (joined != null) drawPath(joined, colours.primary, alpha = s)
                    else
                        drawRoundRect(
                            colours.primary,
                            topLeft = Offset(hairline / 2, hairline / 2),
                            size = Size(size.width - hairline, size.height - hairline),
                            cornerRadius = CornerRadius(r - hairline / 2),
                            style = Stroke(hairline),
                            alpha = s,
                        )
                }
                plan.soft ->
                    clipPath(outline?.band(soft, true, Offset.Zero) ?: softBand) {
                        rotate((phase() - QUARTER) * FULL_TURN) { drawCircle(softSweep, radius, alpha = s) }
                    }
                plan.draw == GlowDraw.Still ->
                    (outline?.band(classic, false, Offset.Zero) ?: classicBand).let { band ->
                        clipPath(band) { drawCircle(colours.primary, radius, alpha = s) }
                    }
                else ->
                    clipPath(outline?.band(classic, false, Offset.Zero) ?: classicBand) {
                        rotate(phase() * FULL_TURN) { drawCircle(classicSweep, radius, alpha = s) }
                    }
            }
        }
    }

/**
 * A rounded band of [width] along a pill of [size] with corner [r]: inside the edge ([inside], Flutter's
 * `deflate(stroke / 2)` stroke) or centred on it. Even-odd fill of two round rects, built once per size.
 */
internal fun ringBand(size: Size, r: Float, width: Float, origin: Offset, inside: Boolean = false): Path {
    val outerInset = if (inside) 0f else -width / 2f
    val innerInset = outerInset + width
    fun rrect(inset: Float) =
        RoundRect(
            left = origin.x + inset, top = origin.y + inset,
            right = origin.x + size.width - inset, bottom = origin.y + size.height - inset,
            cornerRadius = CornerRadius((r - inset).coerceAtLeast(0f)),
        )
    return Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(rrect(outerInset))
        addRoundRect(rrect(innerInset))
    }
}

private const val FULL_TURN = 360f

/** The Soft ring's gradient starts a quarter lap back (Flutter `GradientRotation(2π(phase − .25))`). */
private const val QUARTER = 0.25f

