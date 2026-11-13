package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitPixelRatio
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitMarkDone
import io.github.eslamasabry.oc.ui.words.res.kitMarkFailed
import io.github.eslamasabry.oc.ui.words.res.kitMarkPaused
import io.github.eslamasabry.oc.ui.words.res.kitMarkWaiting
import io.github.eslamasabry.oc.ui.words.res.kitMarkWorking
import io.github.eslamasabry.oc.ui.words.res.kitTaskNeedsYou
import io.github.eslamasabry.oc.ui.words.res.kitTaskStopped
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Where a task stands (Dart `KitTaskState`): a step's four states, one waiting on the person, one stopped early. */
public enum class KitTaskState { Waiting, Working, Done, Failed, NeedsYou, Stopped }

/**
 * How big a [KitTaskMark] is drawn: [Slot] is a row's 32 dp mark slot (the step glyphs); [Mini] is the edge light's
 * 11 dp mark (LAMP-CRAFT §1): needs you an amber dot, failed a neutral rounded square with ×, working a ring whose arc
 * is the share done (a still dashed ring when that share is unknown), done an accent ring with a drawn check.
 */
public enum class KitMarkSize(public val dp: Int) {
    Slot(FlutterKitTokens.MARK_SLOT_SIZE),
    Mini(TaskMarkRules.MINI_DP),
}

/** Pure rules of [KitTaskMark] (Dart `KitTaskMark.wordFor` and its paused assert). */
public object TaskMarkRules {
    /** The edge light's mark, one per 15 dp row of its face (LAMP-CRAFT §1, `MarkSize.Mini`). */
    public const val MINI_DP: Int = 11

    /** The least arc a working mini mark shows, so a run that has just begun still reads as a run. */
    public const val MINI_LEAST_ARC: Float = 0.08f

    /** The share of the circle a working mini mark's arc covers; null progress is the still dashed ring. */
    public fun miniArc(progress: Float?): Float? = progress?.let { if (it.isNaN()) MINI_LEAST_ARC else it.coerceIn(MINI_LEAST_ARC, 1f) }

    /** Paused only applies to a waiting or working task (STATE-11). */
    public fun canPause(state: KitTaskState): Boolean = state == KitTaskState.Waiting || state == KitTaskState.Working

    /** Waiting · Working · Done · Failed · Needs you · Stopped · Paused. */
    public fun word(state: KitTaskState, paused: Boolean = false): StringResource {
        require(!paused || canPause(state)) { "KitTaskMark: paused only applies to waiting or working" }
        if (paused) return Res.string.kitMarkPaused
        return when (state) {
            KitTaskState.Waiting -> Res.string.kitMarkWaiting
            KitTaskState.Working -> Res.string.kitMarkWorking
            KitTaskState.Done -> Res.string.kitMarkDone
            KitTaskState.Failed -> Res.string.kitMarkFailed
            KitTaskState.NeedsYou -> Res.string.kitTaskNeedsYou
            KitTaskState.Stopped -> Res.string.kitTaskStopped
        }
    }

    /** The waiting ring: text3, or text2 where text3 misses 3:1 on the ground or a surface (LOOK-8). */
    public fun ringColor(roles: KitColors): Color {
        val text3 = Argb(roles.text3.toArgb())
        val grounds = listOf(roles.ground, roles.surface1, roles.surface2, roles.surface3).map { Argb(it.toArgb()) }
        val kept = RoleDerivation.readableOn(text3, grounds, 3.0, toward = Argb(roles.text1.toArgb())) // F: lib/ui/kit/kit_status_mark.dart:78
        return if (kept == text3) roles.text3 else roles.text2
    }
}

/**
 * A task row's one leading mark (Dart `KitTaskMark`): the step marks for waiting, working, done and failed, a
 * question in the attention tone when the task needs the person, and a muted stop mark when it was cancelled, in the
 * 32 dp mark slot. Every mark carries its word: in semantics always, and as visible text after the mark with
 * [showLabel]. A change of state cross-fades on the quick pace (instant under reduced motion).
 *
 * The four step glyphs follow `kit_status_mark.dart` (order 05's `KitStatusMark`, not in native yet); once it lands,
 * the step states draw through it.
 *
 * States: waiting, working, done, failed, paused (waiting or working), needsYou, stopped
 * Text: chrome (secondary)
 * Cap: Glyph
 * Motion: cross-fade on a change of state (QUICK); Off = instant, working shows a still dot
 * Replaces: KitTaskMark (lib/ui/kit/kit_task_mark.dart)
 */
@Composable
public fun KitTaskMark(
    state: KitTaskState,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    label: String? = null,
    showLabel: Boolean = false,
    /** [KitMarkSize.Mini] draws the edge light's 11 dp mark; [showLabel] and [paused] do not apply to it. */
    size: KitMarkSize = KitMarkSize.Slot,
    /** A working mini mark's share done, 0..1; null = unknown (the still dashed ring). Read only at [KitMarkSize.Mini]. */
    progress: Float? = null,
) {
    val word = label ?: stringResource(TaskMarkRules.word(state, paused))
    if (size == KitMarkSize.Mini) {
        KitMiniTaskMark(state, progress, modifier.clearAndSetSemantics { contentDescription = word })
        return
    }
    val specs = KitMotion.specs
    val mark: @Composable (Modifier) -> Unit = { m ->
        AnimatedContent(
            targetState = state to paused,
            modifier = m,
            transitionSpec = { fadeIn(specs.quick()) togetherWith fadeOut(specs.quick()) },
            contentAlignment = Alignment.Center,
            label = "kit-task-mark",
        ) { (shown, held) -> TaskGlyph(shown, held) }
    }
    if (!showLabel) {
        mark(modifier.clearAndSetSemantics { contentDescription = word })
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        mark(Modifier.clearAndSetSemantics {})
        Spacer(Modifier.width(LocalKitTokens.current.labelGap.dp))
        KitText(word, Modifier.weight(1f, fill = false).clearAndSetSemantics { contentDescription = word }, role = TypeRole.Secondary)
    }
}

/** One state's glyph in the 32 dp slot. */
@Composable
private fun TaskGlyph(state: KitTaskState, paused: Boolean) {
    val roles = LocalKitColors.current
    val glyph = taskGlyphSize()
    Box(Modifier.size(FlutterKitTokens.MARK_SLOT_SIZE.dp), contentAlignment = Alignment.Center) {
        when {
            paused -> KitGlyph(KitIcons.pause, roles.text2, glyph)
            state == KitTaskState.Done -> KitGlyph(KitIcons.check, roles.success, glyph)
            state == KitTaskState.Failed -> KitGlyph(KitIcons.error, roles.text1, glyph)
            state == KitTaskState.Working ->
                if (KitMotion.state.reduced) KitGlyph(KitIcons.statusDot, roles.accent, FlutterKitTokens.MARK_DOT_SIZE.dp)
                else KitWorkingMark(roles.accent, size = glyph, strokeWidth = FlutterKitTokens.SPINNER_STROKE.dp)
            state == KitTaskState.Waiting ->
                Box(
                    Modifier
                        .size(FlutterKitTokens.MARK_RING_SIZE.dp)
                        .border(kitHairline(), TaskMarkRules.ringColor(roles), CircleShape),
                )
            state == KitTaskState.NeedsYou -> KitGlyph(KitIcons.question, roles.attention, glyph)
            else -> KitGlyph(KitIcons.stopCircle, roles.text2, glyph)
        }
    }
}

/** The small icon grown with text up to the icon cap and snapped to whole physical pixels (LOOK-33). */
@Composable
private fun taskGlyphSize(): Dp {
    val scaled = LocalKitTokens.current.smallIcon * minOf(kitTextScale(), TextScalePolicy.ICON_CAP)
    val ratio = kitPixelRatio()
    return if (ratio > 0f) ((scaled * ratio).roundToInt() / ratio).dp else scaled.dp
}

/**
 * The 11 dp mark (LAMP-CRAFT §1-2). It draws nothing between changes: a new state cross-fades (QUICK), a finished
 * mark's check draws itself (CHECK_DRAW), a working arc moves to a new share on the spatial spring; the dashed ring of
 * unknown progress never moves. Off = every change is final at once.
 */
@Composable
private fun KitMiniTaskMark(state: KitTaskState, progress: Float?, modifier: Modifier) {
    val specs = KitMotion.specs
    val share = TaskMarkRules.miniArc(progress) ?: 0f
    val arc = remember { Animatable(share) }
    LaunchedEffect(share) { if (arc.targetValue != share) arc.animateTo(share, specs.spatial()) }
    val check = remember { Animatable(if (state == KitTaskState.Done) 1f else 0f) }
    LaunchedEffect(state) {
        if (state == KitTaskState.Done) { if (check.targetValue != 1f) check.animateTo(1f, specs.checkDraw()) }
        else if (check.targetValue != 0f) check.snapTo(0f)
    }
    val colors = LocalKitColors.current
    val inks = MiniInks(
        track = TaskMarkRules.ringColor(colors).copy(alpha = MINI_TRACK_ALPHA),
        accent = colors.accent,
        attention = colors.attention,
        fail = colors.text2,
        onFail = colors.ground,
        quiet = colors.text2,
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val known = progress != null
    Crossfade(state, modifier.size(KitMarkSize.Mini.dp.dp), animationSpec = specs.quick(), label = "kit-mini-mark") { shown ->
        Canvas(Modifier.size(KitMarkSize.Mini.dp.dp)) {
            drawMiniMark(shown, known, { arc.value }, { check.value }, inks, rtl)
        }
    }
}

private class MiniInks(val track: Color, val accent: Color, val attention: Color, val fail: Color, val onFail: Color, val quiet: Color)

/** The mini mark's art on a 12-unit grid (the prototype's `mini()` glyphs), scaled to the canvas. */
private fun DrawScope.drawMiniMark(
    state: KitTaskState,
    known: Boolean,
    arc: () -> Float,
    check: () -> Float,
    inks: MiniInks,
    rtl: Boolean,
) {
    val u = size.minDimension / MINI_GRID
    val r = MINI_RING_R * u
    val ring = Stroke(MINI_RING_W * u)
    when (state) {
        KitTaskState.NeedsYou -> drawCircle(inks.attention, MINI_DOT_R * u)
        KitTaskState.Failed -> {
            drawRoundRect(inks.fail, Offset(u, u), Size(size.width - 2 * u, size.height - 2 * u), CornerRadius(MINI_SQUARE_R * u))
            val w = MINI_RING_W * u
            drawLine(inks.onFail, Offset(4 * u, 4 * u), Offset(8 * u, 8 * u), w, StrokeCap.Round)
            drawLine(inks.onFail, Offset(8 * u, 4 * u), Offset(4 * u, 8 * u), w, StrokeCap.Round)
        }
        KitTaskState.Working -> {
            drawCircle(inks.track, r, style = ring)
            val stroke = MINI_ARC_W * u
            if (!known) {
                drawCircle(
                    inks.accent, r,
                    style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(MINI_DASH * u, MINI_GAP * u))),
                )
            } else {
                val sweep = FULL_TURN * arc()
                val box = Size(2 * r, 2 * r)
                val at = Offset(center.x - r, center.y - r)
                if (rtl) scale(-1f, 1f) { drawArc(inks.accent, TWELVE_O_CLOCK, sweep, false, at, box, style = Stroke(stroke, cap = StrokeCap.Round)) }
                else drawArc(inks.accent, TWELVE_O_CLOCK, sweep, false, at, box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        KitTaskState.Done -> {
            drawCircle(inks.accent, r, style = ring)
            val t = check()
            if (t > 0f) {
                val tick = Path().apply {
                    moveTo(3.9f * u, 6.2f * u)
                    lineTo(5.4f * u, 7.7f * u)
                    lineTo(8.2f * u, 4.5f * u)
                }
                val measure = PathMeasure().apply { setPath(tick, false) }
                val drawn = Path()
                measure.getSegment(0f, measure.length * t, drawn, true)
                drawPath(drawn, inks.accent, style = Stroke(MINI_RING_W * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        KitTaskState.Waiting -> drawCircle(inks.track, r, style = ring)
        KitTaskState.Stopped -> drawCircle(inks.quiet, r, style = ring)
    }
}

private const val MINI_GRID: Float = 12f // NV: the prototype's 12-unit glyph box
private const val MINI_RING_R: Float = 4.3f // NV: ring radius on the 12-unit grid
private const val MINI_RING_W: Float = 1.6f // NV: ring and check stroke on the 12-unit grid
private const val MINI_ARC_W: Float = 2f // NV: working arc stroke on the 12-unit grid
private const val MINI_DOT_R: Float = 5f // NV: needs-you dot radius on the 12-unit grid
private const val MINI_SQUARE_R: Float = 2.2f // NV: failed square corner on the 12-unit grid
private const val MINI_DASH: Float = 2f // NV: unknown-progress dash on the 12-unit grid
private const val MINI_GAP: Float = 2.5f // NV: unknown-progress gap on the 12-unit grid
private const val MINI_TRACK_ALPHA: Float = 0.5f // NV: the track ring is the waiting ring at half strength
private const val FULL_TURN: Float = 360f // NV: degrees in a turn
private const val TWELVE_O_CLOCK: Float = -90f // NV: twelve o'clock in Compose degrees
