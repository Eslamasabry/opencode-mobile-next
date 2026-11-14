package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.choice.rememberKitHaptics
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrameText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitRollingNumber
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.status.KitMarkSize
import io.github.eslamasabry.oc.ui.kit.status.KitTaskMark
import io.github.eslamasabry.oc.ui.kit.status.KitTaskState
import io.github.eslamasabry.oc.ui.kit.status.TaskMarkRules
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The edge light's measures (LAMP-CRAFT §1, "Open kit work"), in dp. The face is a tab cut by the screen edge: its
 * inner side is fully round, its edge side square. Everything on the light is one of these.
 */
public object KitLampDims {
    /** The touch box's width from the physical edge (`Space.S6`). */
    public const val TOUCH_W: Int = Space.S6

    /** The touch box reaches this far above and below the face (touch height = face height + 16). */
    public const val TOUCH_PAD: Int = Space.S2

    /** The face's width (`Space.S4`). */
    public const val FACE_W: Int = Space.S4

    /** The face's width when "9+" at the frame text cap needs it (`Space.S5`); the touch box stays [TOUCH_W]. */
    public const val FACE_W_WIDE: Int = Space.S5

    /** The quiet tab: 12 × 40 with a chevron and no number. */
    public const val QUIET_W: Int = Space.S3
    public const val QUIET_H: Int = Space.S6 + Space.S4

    /** The face while the list is open: the list's close tab, 16 × 48 with a ×. */
    public const val OPEN_H: Int = Space.S6 * 2

    /** One row of the face (the number, or one mark): 11 dp mark + 4 dp between marks. */
    public const val ROW: Int = TaskMarkRules.MINI_DP + Space.S1

    /** Above the first row and below the last (`Space.S1`). */
    public const val PAD: Int = Space.S1

    /** The face shows at most this many marks. */
    public const val MAX_MARKS: Int = 3

    /** The tallest face: the number and three marks (8 + 4 × 15 = 68). */
    public const val MAX_H: Int = PAD * 2 + ROW * (MAX_MARKS + 1)

    /** The face's inner corners: half its width (`KitShapeId.Pill`, cut by the edge). */
    public const val CORNER: Int = FACE_W / 2

    /** The × and the chevron are drawn in the mark's box. */
    public const val GLYPH: Int = TaskMarkRules.MINI_DP

    /** The halo's ring (2 dp, amber), growing [MotionTokens.LIGHT_HALO_DP] beyond the face. */
    public const val HALO_STROKE: Int = Space.S1 / 2

    /** Requests that arrive within this window of the last halo share it (one pulse per burst). */
    public const val BURST_MS: Long = 2_000L

    /** The largest number the face writes out; above it, "9+". */
    public const val MAX_COUNT: Int = 9
}

/** What one mark on the face stands for (LAMP-PLAN §2, F2). */
public enum class KitLampMarkKind { NeedsYou, Failed, Working, Finished }

/**
 * One other conversation's mark. [key] tells conversations apart (an arriving mark fades in; a request is pulsed for
 * once per key). [progress] is a working run's share done, 0..1 (its task list's done / total); null = no task list,
 * the still dashed ring.
 */
@Immutable
public data class KitLampMark(val key: Any, val kind: KitLampMarkKind, val progress: Float? = null)

/**
 * The light's whole state, plain values only: [count] is needs you + failed (the caller counts; the face writes "9+"
 * above nine), [marks] every other conversation that has one (the face picks and orders at most three), [open] while
 * the list is open (the face becomes its close tab).
 */
@Immutable
public data class KitLampFace(val count: Int = 0, val marks: List<KitLampMark> = emptyList(), val open: Boolean = false) {
    /** Nothing to say: the quiet tab. */
    public val quiet: Boolean get() = !open && count <= 0 && marks.isEmpty()
}

/** Which edge the light sits on, in reading order: [End] is the right edge in English and the left in Arabic. */
public enum class KitLampSide { Start, End }

/** The light's pure rules. */
public object KitLampRules {
    /** Every needs you first, then failed, then runs with the most progress first (a just-finished one leads them). */
    public fun shown(marks: List<KitLampMark>): List<KitLampMark> {
        val needs = marks.filter { it.kind == KitLampMarkKind.NeedsYou }
        val failed = marks.filter { it.kind == KitLampMarkKind.Failed }
        val runs = marks.filter { it.kind == KitLampMarkKind.Working || it.kind == KitLampMarkKind.Finished }.sortedByDescending { runRank(it) }
        return (needs + failed + runs).take(KitLampDims.MAX_MARKS)
    }

    private fun runRank(m: KitLampMark): Float =
        when {
            m.kind == KitLampMarkKind.Finished -> FINISHED_RANK
            m.progress == null || m.progress.isNaN() -> UNKNOWN_RANK
            else -> m.progress.coerceIn(0f, 1f)
        }

    /** The number shows on a closed face with someone to count. */
    public fun numberShown(face: KitLampFace): Boolean = !face.open && face.count > 0

    /** "3", or "9+" above nine, in the phone's digits. */
    public fun numberText(count: Int): String =
        if (count > KitLampDims.MAX_COUNT) Numbers.format(KitLampDims.MAX_COUNT) + "+" else Numbers.format(count)

    /** Amber when anyone needs the person; ink when only failures are counted. */
    public fun numberTone(face: KitLampFace): TextTone =
        if (face.marks.any { it.kind == KitLampMarkKind.NeedsYou }) TextTone.Attention else TextTone.Primary

    /** Quiet 40, open 48, else 8 + 15 per row (the number's and each mark's), between 40 and 68. */
    public fun faceHeightDp(face: KitLampFace): Int =
        when {
            face.open -> KitLampDims.OPEN_H
            face.quiet -> KitLampDims.QUIET_H
            else -> {
                val rows = (if (numberShown(face)) 1 else 0) + shown(face.marks).size
                (KitLampDims.PAD * 2 + KitLampDims.ROW * rows).coerceIn(KitLampDims.QUIET_H, KitLampDims.MAX_H)
            }
        }

    /** Quiet 12; 20 only while "9+" is written at the frame text cap; else 16. */
    public fun faceWidthDp(face: KitLampFace, textScale: Float): Int =
        when {
            face.quiet -> KitLampDims.QUIET_W
            numberShown(face) && face.count > KitLampDims.MAX_COUNT && textScale >= TextScalePolicy.FRAME_CAP -> KitLampDims.FACE_W_WIDE
            else -> KitLampDims.FACE_W
        }

    /** The touch box's height: the face's and [KitLampDims.TOUCH_PAD] above and below. */
    public fun touchHeightDp(faceHeightDp: Int): Int = faceHeightDp + KitLampDims.TOUCH_PAD * 2

    /** True when the light sits on the physical left edge (Start in a left-to-right language, End in Arabic). */
    public fun onLeft(side: KitLampSide, rtl: Boolean): Boolean = (side == KitLampSide.Start) != rtl

    private const val FINISHED_RANK: Float = 2f // NV: above any share, so a just-finished run leads the runs
    private const val UNKNOWN_RANK: Float = -1f // NV: below any share, so a run without a task list comes last
}

/**
 * The edge light (LAMP-PLAN §2 F2, LAMP-CRAFT §1-2): a small tab on the screen edge that tells, without a tap, how the
 * other conversations stand. The [face] number counts needs you + failed (amber when anyone needs the person); under it
 * up to three marks (needs you, then failed, then runs by progress). With nothing to say it is the quiet "‹" tab; while
 * [KitLampFace.open] it is the list's close tab (×). The touch box is 24 dp wide from the physical edge and 16 dp taller
 * than the face; it takes taps only (a sideways swipe stays the system's Back; the layer passes drags through).
 *
 * [side] is in reading order and mirrors the whole part (the square side on the edge). [label] is the spoken name the
 * caller words ("Other conversations, 1 needs you, 1 failed, 3 working"; "Close conversations" while open); the face's
 * marks are not read on their own. [visible] fades the light (QUICK) for the keyboard, a cover or a page without it;
 * hidden, it takes no touch and says nothing.
 *
 * When a new request arrives while the light is closed and shown, one halo leaves the face (2 dp amber ring growing to
 * 10 dp beyond it while fading, LIGHT_PULSE on `Pulse`) with one [KitHaptic.Attention]; never twice for one request,
 * and a burst within 2 s gets one. Between changes it draws nothing at all.
 *
 * States: quiet, counted (number + marks), open (close tab), pressed, hidden; Solid, Glass, Soft, Brutal, Paper by role
 * Text: frame (the number, capped 1.3×; marks never scale)
 * Cap: Glyph (the face is at most 20 × 68, the touch box 24 × 84)
 * Motion: height and width on spatialFast; a mark fades in from 0.9 (QUICK, LIGHT_MARK_DELAY after its slot starts);
 *   the number rolls; open/close cross-fades the face (QUICK); halo once; Calm = the halo only fades; Off = instant, no
 *   halo (the haptic stays)
 * Replaces: none (the rings' tucked nub, KitEdgeDock, when the owner picks it)
 */
@Composable
public fun KitLamp(
    face: KitLampFace,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    side: KitLampSide = KitLampSide.End,
    visible: Boolean = true,
) {
    val specs = KitMotion.specs
    val motion = KitMotion.state
    val left = KitLampRules.onLeft(side, LocalLayoutDirection.current == LayoutDirection.Rtl)
    val targetH = KitLampRules.faceHeightDp(face).toFloat()
    val targetW = KitLampRules.faceWidthDp(face, minOf(kitTextScale(), TextScalePolicy.FRAME_CAP)).toFloat()
    val height = remember { Animatable(targetH) }
    val width = remember { Animatable(targetW) }
    LaunchedEffect(targetH) { if (height.targetValue != targetH) height.animateTo(targetH, specs.spatialFast()) }
    LaunchedEffect(targetW) { if (width.targetValue != targetW) width.animateTo(targetW, specs.spatialFast()) }
    val shown = remember { Animatable(if (visible) 1f else 0f) }
    val shownTarget = if (visible) 1f else 0f
    LaunchedEffect(shownTarget) { if (shown.targetValue != shownTarget) shown.animateTo(shownTarget, specs.quick()) }
    val halo = rememberLampHalo(face, visible)
    val interactions = remember { MutableInteractionSource() }
    // A mark that arrives after the first frame fades in; the marks of the first frame stand at once.
    val settled = remember { BooleanArray(1) }
    SideEffect { settled[0] = true }
    val touch =
        if (visible) {
            Modifier
                .semantics { contentDescription = label }
                .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
        } else Modifier.clearAndSetSemantics {}
    val haloStroke = KitLampDims.HALO_STROKE.dp
    val haloReach = MotionTokens.LIGHT_HALO_DP.dp
    val travels = motion.travels
    val attention = LocalKitColors.current.attention
    Layout(
        content = { LampFace(face, left, interactions, settled[0]) },
        modifier =
            modifier
                .graphicsLayer { alpha = shown.value }
                .testTag(LAMP_TAG)
                .then(touch)
                .drawWithContent {
                    // The face is a tab cut by the edge: nothing is drawn past the physical edge, everything else
                    // (the halo on the inner side, a hard shadow below) is.
                    val far = size.width + size.height
                    clipRect(left = if (left) 0f else -far, top = -far, right = if (left) far else size.width, bottom = size.height + far) {
                        val t = halo.value
                        if (t > 0f && t < 1f) {
                            val fw = width.value.dp.toPx()
                            val fh = height.value.dp.toPx()
                            val x = if (left) 0f else size.width - fw
                            val y = KitLampDims.TOUCH_PAD.dp.toPx()
                            val reach = (if (travels) 1f + (haloReach.value - 1f) * t else 1f).dp.toPx()
                            drawHalo(x, y, fw, fh, reach, haloStroke.toPx(), KitLampDims.CORNER.dp.toPx(), left, attention.copy(alpha = attention.alpha * (1f - t)))
                        }
                        this@drawWithContent.drawContent()
                    }
                },
    ) { measurables, _ ->
        val fw = width.value.dp.roundToPx()
        val fh = height.value.dp.roundToPx()
        val pad = KitLampDims.TOUCH_PAD.dp.roundToPx()
        val tw = KitLampDims.TOUCH_W.dp.roundToPx()
        val p = measurables.first().measure(Constraints.fixed(fw, fh))
        layout(tw, fh + pad * 2) { p.place(if (left) 0 else tw - fw, pad) }
    }
}

/** The halo: the face's outline grown by [reach], square past the edge (the clip cuts it there). */
private fun DrawScope.drawHalo(x: Float, y: Float, w: Float, h: Float, reach: Float, stroke: Float, corner: Float, left: Boolean, color: Color) {
    val past = corner + reach
    val l = if (left) x - past else x - reach
    val r = if (left) x + w + reach else x + w + past
    drawRoundRect(
        color,
        Offset(l, y - reach),
        Size(r - l, h + reach * 2),
        CornerRadius(corner + reach),
        style = Stroke(stroke),
    )
}

/** The one halo for a request that arrives while the light is closed and shown (0 = none, running 0 → 1). */
@Composable
private fun rememberLampHalo(face: KitLampFace, visible: Boolean): Animatable<Float, AnimationVector1D> {
    val specs = KitMotion.specs
    val reduced = KitMotion.state.reduced
    val haptics = rememberKitHaptics()
    val halo = remember { Animatable(0f) }
    val pulse = remember { LampPulse() }
    val scope = rememberCoroutineScope()
    val needs = face.marks.filter { it.kind == KitLampMarkKind.NeedsYou }.mapTo(HashSet()) { it.key }
    val quietNow = face.open || !visible
    LaunchedEffect(needs) {
        if (!pulse.arrive(needs) || quietNow) return@LaunchedEffect
        if (!pulse.take(withFrameMillis { it })) return@LaunchedEffect
        haptics(KitHaptic.Attention)
        if (reduced) return@LaunchedEffect
        // Its own scope: a second request inside the halo must not cut it off half-drawn.
        scope.launch {
            halo.snapTo(0f)
            halo.animateTo(1f, specs.lightPulse())
            halo.snapTo(0f)
        }
    }
    return halo
}

/** Which requests were already pulsed for, and when the last halo left (frame time, ms). */
private class LampPulse {
    private val seen = HashSet<Any>()
    private var started = false
    private var lastMs = Long.MIN_VALUE

    /** True when [keys] hold a request never seen before (not on the first frame: those were there already). */
    fun arrive(keys: Set<Any>): Boolean {
        val fresh = keys.any { it !in seen }
        seen.addAll(keys)
        val first = !started
        started = true
        return fresh && !first
    }

    /** True when a halo may leave at [nowMs]: one per [KitLampDims.BURST_MS]. */
    fun take(nowMs: Long): Boolean {
        if (lastMs != Long.MIN_VALUE && nowMs - lastMs < KitLampDims.BURST_MS) return false
        lastMs = nowMs
        return true
    }
}

private enum class FaceMode { Quiet, Counted, Open }

/** The face: the style's floating face by role (Glass's by its roles), the press inside it, then quiet, marks or ×. */
@Composable
private fun LampFace(face: KitLampFace, left: Boolean, interactions: MutableInteractionSource, settled: Boolean) {
    val colors = LocalKitColors.current
    val style = LocalKitStyle.current.style
    val specs = KitMotion.specs
    val reduced = KitMotion.state.reduced
    val pressed by interactions.collectIsPressedAsState()
    // Paper's open tab inverts: ink with a paper ×.
    val inverted = face.open && style == Style.Paper
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                role = SurfaceRole.Floating,
                shape = KitShapeId.Button,
                level = SurfaceLevel.Surface2,
                ownFill = colors.surface2,
                color = if (inverted) colors.text1 else null,
                pressed = pressed || face.open,
                exactRadius = KitLampDims.CORNER.dp,
            ),
        )
    val ink = if (inverted) colors.ground else look.words ?: colors.text1
    val platform = LocalKitPlatform.current
    val indication = remember(platform, ink, reduced) { if (reduced) KitFillIndication(ink) else platform.pressIndication(ink) }
    val corner = KitLampDims.CORNER.dp
    val mode = when {
        face.open -> FaceMode.Open
        face.quiet -> FaceMode.Quiet
        else -> FaceMode.Counted
    }
    Box(
        Modifier
            .testTag(LAMP_FACE_TAG)
            .clearAndSetSemantics {}
            // The face runs a corner's width past the edge, so its edge side is square once the edge cuts it.
            .layout { m, c ->
                val extra = corner.roundToPx()
                val p = m.measure(Constraints.fixed(c.maxWidth + extra, c.maxHeight))
                layout(c.maxWidth, c.maxHeight) { p.place(if (left) -extra else 0, 0) }
            }
            .kitSurface(look)
            .indication(interactions, indication)
            .absolutePadding(left = if (left) corner else 0.dp, right = if (left) 0.dp else corner),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(mode, Modifier.fillMaxSize(), animationSpec = specs.quick(), label = "kit-lamp-face") { shownMode ->
            when (shownMode) {
                FaceMode.Open ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KitGlyph(KitIcons.close, ink, KitLampDims.GLYPH.dp) }
                FaceMode.Quiet -> {
                    val chevron = colors.text2
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(KitLampDims.GLYPH.dp)) { drawChevron(left, chevron) }
                    }
                }
                FaceMode.Counted -> LampMarks(face, settled)
            }
        }
    }
}

/** The number on top, then up to three marks, one per 15 dp row. */
@Composable
private fun LampMarks(face: KitLampFace, settled: Boolean) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (KitLampRules.numberShown(face)) {
            Box(Modifier.fillMaxWidth().height(KitLampDims.ROW.dp), contentAlignment = Alignment.Center) {
                KitFrameText {
                    KitRollingNumber(
                        KitLampRules.numberText(face.count),
                        Modifier.wrapContentSize(unbounded = true),
                        role = TypeRole.Label,
                        tone = KitLampRules.numberTone(face),
                    )
                }
            }
        }
        KitLampRules.shown(face.marks).forEach { mark -> key(mark.key) { LampMarkSlot(mark, settled) } }
    }
}

/** One mark's row: an arriving mark fades in growing from MARK_FROM, LIGHT_MARK_DELAY after its slot opens. */
@Composable
private fun LampMarkSlot(mark: KitLampMark, settled: Boolean) {
    val specs = KitMotion.specs
    val reduced = KitMotion.state.reduced
    val appear = remember { Animatable(if (settled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            if (!reduced) delay(specs.delayMs(MotionTokens.LIGHT_MARK_DELAY.toFloat()))
            appear.animateTo(1f, specs.quick())
        }
    }
    Box(Modifier.fillMaxWidth().height(KitLampDims.ROW.dp), contentAlignment = Alignment.Center) {
        KitTaskMark(
            mark.kind.taskState(),
            Modifier.graphicsLayer {
                val a = appear.value
                alpha = a
                val s = MotionTokens.MARK_FROM + (1f - MotionTokens.MARK_FROM) * a
                scaleX = s
                scaleY = s
            },
            size = KitMarkSize.Mini,
            progress = mark.progress,
        )
    }
}

private fun KitLampMarkKind.taskState(): KitTaskState =
    when (this) {
        KitLampMarkKind.NeedsYou -> KitTaskState.NeedsYou
        KitLampMarkKind.Failed -> KitTaskState.Failed
        KitLampMarkKind.Working -> KitTaskState.Working
        KitLampMarkKind.Finished -> KitTaskState.Done
    }

/** The quiet tab's chevron, pointing into the page (away from the edge it sits on), on the mark's 12-unit grid. */
private fun DrawScope.drawChevron(left: Boolean, color: Color) {
    val u = size.minDimension / CHEVRON_GRID
    val tip = if (left) CHEVRON_GRID - CHEVRON_TIP else CHEVRON_TIP
    val back = if (left) CHEVRON_GRID - CHEVRON_BACK else CHEVRON_BACK
    val path = Path().apply {
        moveTo(back * u, CHEVRON_TOP * u)
        lineTo(tip * u, CHEVRON_GRID / 2 * u)
        lineTo(back * u, (CHEVRON_GRID - CHEVRON_TOP) * u)
    }
    drawPath(path, color, style = Stroke(CHEVRON_STROKE * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** The light's touch box, for tests and the layer that places it. */
public const val LAMP_TAG: String = "kit-lamp"

/** The light's face inside its touch box. */
public const val LAMP_FACE_TAG: String = "kit-lamp-face"

private const val CHEVRON_GRID: Float = 12f // NV: the prototype's 12-unit glyph box
private const val CHEVRON_TIP: Float = 4f // NV: the chevron's point on the 12-unit grid
private const val CHEVRON_BACK: Float = 7.6f // NV: the chevron's open ends on the 12-unit grid
private const val CHEVRON_TOP: Float = 2.4f // NV: the chevron's upper end on the 12-unit grid
private const val CHEVRON_STROKE: Float = 1.9f // NV: the chevron's stroke on the 12-unit grid
