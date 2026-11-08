package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairlineBorder
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitDotMorph
import io.github.eslamasabry.oc.ui.kit.icon.KitPixelIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitPixelIcons
import io.github.eslamasabry.oc.ui.kit.icon.MorphDot
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.ThemeRimKind
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchPlan
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchRules
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchTimings
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kit.theme.ThemeSwitchColorRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** One segment of [KitModeSegmented]: a value, its word, its pixel icon, and its own disabled reason. */
@Immutable
public data class KitModeSegment<T>(
    val value: T,
    val label: String,
    val icon: KitPixelIcon,
    val enabled: KitEnabled = KitEnabled.Enabled,
)

/**
 * The theme mode control (Light / Dark / Auto / Saver; theme-switch-premium, owner-approved 2026-11-08): the tall
 * [KitSegmented] with premium motion. A tap slides the chosen block to the new segment, a rim of light runs round its
 * edge and settles, and the old icon's dots travel and re-form into the new pixel icon; the theme itself commits
 * [ThemeSwitchTimings.COMMIT_MS] later, once the motion is under way, and at most three times a second however fast
 * the taps come. A second tap mid-move retargets the slide and the dots from where they are and restarts the rim.
 * Changes that are not a tap (Auto following the system, rotation, the first frame, a restored process) show the end
 * state at once, with no rim, dots or haptic.
 *
 * States: default, selected, disabled, stacked, switching
 * Text: frame
 * Cap: Reading
 * Motion: Full = slide (release spring) + rim + dot morph; Calm = slide + icon cross-fade; Off = instant
 * Replaces: KitSegmented(tall) on Appearance › Mode
 */
@Composable
public fun <T> KitModeSegmented(
    segments: List<KitModeSegment<T>>,
    selected: T,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    onChange: (T) -> Unit,
) {
    kitAssert(SegmentedRules.validCount(segments.size)) { "KitModeSegmented needs 2 to 4 segments (B-KIT-031)" }
    kitAssert(segments.any { it.value == selected }) { "KitModeSegmented.selected must be one of its segments" }
    kitAssert(segments.all { it.enabled !is KitEnabled.Disabled || it.enabled.reason.isNotBlank() }) {
        "KitModeSegmented: a disabled segment must say why (B-KIT-031)"
    }
    val tokens = LocalKitTokens.current
    val typography = LocalKitTypography.current
    val measurer = rememberTextMeasurer()
    val appDensity = LocalKitAppDensity.current ?: LocalDensity.current
    val fontScale = appDensity.fontScale
    val reasons = segments.mapNotNull { it.enabled.reason }.distinct()
    Column(
        modifier.fillMaxWidth().semantics {
            contentDescription = semanticsLabel
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val unscaled = Density(appDensity.density, 1f)
            val widths =
                segments.map { s ->
                    val words =
                        measurer.measure(s.label, typography.style(TypeRole.Caption), density = unscaled).size.width /
                            appDensity.density * minOf(fontScale, TextScalePolicy.FRAME_CAP)
                    2f * Space.S1 + maxOf(words, tokens.smallIcon.toFloat()) // F: lib/ui/kit/kit_segmented.dart:488
                }
            val room = maxWidth.value - 2f * kitHairline().value
            val stacked = fontScale >= SegmentedRules.STACK_AT_SCALE || widths.any { it > room / segments.size }
            if (stacked) {
                StackedSegments(
                    segments.map { KitSegment(it.value, it.label, it.icon.vector, it.enabled) },
                    selected,
                    KitEnabled.Enabled,
                    onChange,
                )
            } else {
                ModeRow(segments, selected, onChange)
            }
        }
        if (reasons.isNotEmpty()) {
            Column(Modifier.padding(top = Space.S1.dp).testTag("kit-segmented-reason")) {
                reasons.forEach { reason ->
                    KitText(reason, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
                }
            }
        }
    }
}

/**
 * The motion state of one mode control. Only a tap ([tap]) starts motion; [settle] puts every part at its end for a
 * change that came from elsewhere. The clocks are read in the layout and draw phases only, so a running switch never
 * recomposes the control.
 */
@Stable
internal class ModeSwitchMotion(initial: Int) {
    /** The segment the control shows as chosen: the tapped one at once, before the theme commits. */
    var shown by mutableIntStateOf(initial)

    /** The segment the block and dots are leaving (its ink icon fades back in). */
    var from by mutableIntStateOf(initial)

    /** The chosen block's place, in segments (0 = the first). */
    val pos = Animatable(initial.toFloat())

    /** ms into the rim's run; at [ThemeSwitchTimings.RIM_MS] it is gone. */
    val rim = Animatable(ThemeSwitchTimings.RIM_MS.toFloat())

    /** ms into the dot morph or cross-fade; at [KitDotMorph.MORPH_MS] the icons are at rest. */
    val morphMs = Animatable(KitDotMorph.MORPH_MS)

    /** The dots in flight (Full), null at rest or in Calm. */
    var morph: KitDotMorph? by mutableStateOf(null)

    /** Which way the block travels on screen (+1 rightwards), for the rim's start and Brutal's shift. */
    var travel by mutableStateOf(1f)

    var plan: ThemeSwitchPlan by mutableStateOf(ThemeSwitchRules.plan(MotionLevel.Off))

    /** Each icon slot's top-left in root pixels, and the control's own; the dots are drawn relative to it. */
    val slots = mutableStateListOf<Offset>()
    var origin by mutableStateOf(Offset.Zero)

    internal var lastCommitMs: Long? = null
    internal var commitJob: Job? = null

    /** Test hook: how many times motion was started (taps that moved the choice). */
    var started: Int = 0
        private set

    val switching: Boolean get() = pos.isRunning || rim.isRunning || morphMs.isRunning

    fun slot(i: Int): Offset = (slots.getOrNull(i) ?: Offset.Zero) - origin

    suspend fun settle(index: Int) {
        shown = index
        from = index
        morph = null
        pos.snapTo(index.toFloat())
        rim.snapTo(ThemeSwitchTimings.RIM_MS.toFloat())
        morphMs.snapTo(KitDotMorph.MORPH_MS)
    }

    fun begin(target: Int, plan: ThemeSwitchPlan, icons: List<KitPixelIcon>, iconPx: Float) {
        started++
        this.plan = plan
        val old = shown
        val fromX = slot(old).x
        val toX = slot(target).x
        if (toX != fromX) travel = if (toX > fromX) 1f else -1f
        // An interruption starts from where the dots are now; otherwise from the old icon at rest.
        val cell = iconPx / KitPixelIcons.GRID
        val inFlight =
            morph?.takeIf { morphMs.isRunning }?.sample(morphMs.value)?.filter { it.alpha > ModeLook.IN_FLIGHT_ALPHA }
        val source =
            inFlight ?: icons[old].dots.map { d -> MorphDot(slot(old).x + d.x * cell, slot(old).y + d.y * cell) }
        val dest = icons[target].dots.map { d -> Offset(slot(target).x + d.x * cell, slot(target).y + d.y * cell) }
        morph = if (plan.dust) KitDotMorph(source, dest, cell) else null
        // The segment being left gets its resting icon back; on an interruption that is the target given up.
        from = old
        shown = target
    }

    /** Starts the slide, the rim and the icons for a tap on [target] (the clocks restart from where they are). */
    fun play(scope: CoroutineScope, target: Int, release: FiniteAnimationSpec<Float>, scale: Float) {
        val plan = plan
        scope.launch { if (plan.slide) pos.animateTo(target.toFloat(), release) else pos.snapTo(target.toFloat()) }
        scope.launch { run(rim, plan.rim, ThemeSwitchTimings.RIM_MS.toFloat(), scale) }
        scope.launch {
            run(morphMs, plan.dust || plan.crossfade, KitDotMorph.MORPH_MS, scale)
            morph = null
        }
    }

    private suspend fun run(clock: Animatable<Float, AnimationVector1D>, plays: Boolean, end: Float, scale: Float) {
        if (!plays) {
            clock.snapTo(end)
            return
        }
        clock.snapTo(0f)
        clock.animateTo(end, tween((end * scale).toInt(), easing = LinearEasing))
    }

    /**
     * The commit: after the motion is under way, and at most three times a second (the debounce). It waits on the
     * frame clock the motion runs on (and its debug time scale), not on a timer; a newer tap replaces it. [commit]
     * gets the segment shown when it is due.
     */
    fun scheduleCommit(scope: CoroutineScope, scale: Float, commit: (Int) -> Boolean) {
        val plan = plan
        commitJob?.cancel()
        commitJob =
            scope.launch {
                val now = withFrameMillis { it }
                val at = ThemeSwitchRules.commitAt(now, lastCommitMs, plan)
                val due = now + ((at - now) * scale).toLong()
                while (withFrameMillis { it } < due) Unit
                if (commit(shown)) lastCommitMs = at
            }
    }
}

/** The look of the switch's moving parts: tuned by eye against the owner's reference (s33). */
internal object ModeLook {
    const val CAPTION_LINE = 1.3f // F: lib/ui/kit/kit_segmented.dart:781
    const val ICON_SCALE = 1.2f // F: lib/ui/kit/kit_segmented.dart:851
    const val IN_FLIGHT_ALPHA = 0.02f
    const val MIN_ALPHA = 0.001f

    /** A dot fills this much of its cell, with corners this round (of the dot). */
    const val DOT_FILL = 0.82f
    const val DOT_CORNER = 0.28f

    /** The target's resting icon gives way this many times faster than the morph. */
    const val TARGET_GIVE_WAY = 3f

    /** Floats per dot in a morph frame (x, y, alpha, scale). */
    const val DOT_FLOATS = 4

    const val RIM_STROKE_DP = 1.5f
    const val RIM_START_SAMPLES = 64
    const val RIM_FADE_IN_MS = 60f
    const val RIM_TAIL = 0.35f
    const val RIM_TAIL_PIECES = 10
    const val HALO_WIDTH = 5f
    const val HALO_ALPHA = 0.16f
    const val GLOW_WIDTH = 2.6f
    const val GLOW_ALPHA = 0.32f
    const val INNER_WIDTH = 7f
    const val INNER_ALPHA = 0.34f
    const val INNER_MID_WIDTH = 3.5f
    const val INNER_MID_ALPHA = 0.3f
    const val SETTLE_ALPHA = 0.25f
    const val SETTLE_GLOW_WIDTH = 2f
    const val SETTLE_INNER_WIDTH = 6f
    const val HARD_SWEEP_WIDTH = 2f
    const val INK_SWEEP_WIDTH = 1.2f
}

/** The chosen block: the raised step (surface 2 in light, 3 in dark) in the style's selected look, as KitSegmented. */
@Composable
private fun chosenBlockLook(disabled: Boolean): io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceLook {
    val colors = LocalKitColors.current
    val step = if (colors.dark) colors.surface3 else colors.surface2
    return kitSurfaceLook(
        KitSurfaceInput(
            SurfaceRole.Control, KitShapeId.Tile, SurfaceLevel.Surface3, ownFill = step, color = step,
            selected = true, disabled = disabled,
        ),
    )
}

/**
 * The icons' and rim's colours: every style's rim takes the derived colour (3:1 on the track; Brutal's and Paper's
 * own outlines are already ink, so an ink sweep would not show), the chosen icon its readable accent. A style that
 * paints its chosen face itself ([faceWords], Paper's inked block) gets the accent moved until it reads on that face.
 */
@Composable
private fun modeInk(on: List<Boolean>, shownOn: Boolean, faceWords: Color?): ModeInk {
    val colors = LocalKitColors.current
    val step = if (colors.dark) colors.surface3 else colors.surface2
    val switch = remember(colors, step) { ThemeSwitchColorRules.of(colors, colors.surface1, step) }
    val onFace =
        remember(switch, faceWords) {
            faceWords?.let { w ->
                // The face is the far side of its words: dark under light words, light under dark ones.
                val lightWords = w.luminance() > HALF
                val face = if (lightWords) Color.Black else Color.White
                ThemeSwitchColorRules.readable(switch.selectedInk, lightWords, listOf(face))
            }
        }
    return ModeInk(
        rim = switch.rim,
        chosen = if (shownOn) onFace ?: switch.selectedInk else colors.text3,
        resting = on.map { if (it) colors.text2 else colors.text3 },
    )
}

/** A segment's place in the row and the size of its icon. */
private class ModeSlot(val index: Int, val count: Int, val iconDp: androidx.compose.ui.unit.Dp, val faceWords: Color?)

private const val HALF = 0.5f

/** One grid cell and one dot of a pixel icon, in pixels. */
private class DotSize(val cell: Float, val dot: Float)

private class IconLook(val ink: ModeInk, val size: DotSize)

/** The colours of one control's icons and rim, for the draw passes. */
@Immutable
private class ModeInk(val rim: Color, val chosen: Color, val resting: List<Color>)

@Composable
private fun <T> ModeRow(segments: List<KitModeSegment<T>>, selected: T, onChange: (T) -> Unit) {
    val haptics = rememberKitHaptics()
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val style = LocalKitStyle.current.style
    val motionState = KitMotion.state
    val specs = KitMotion.specs
    val hairline = kitHairline()
    val inset = (Space.S1 / 2f).dp // F: lib/ui/kit/kit_segmented.dart:629
    val density = LocalDensity.current
    val iconDp = (tokens.smallIcon * ModeLook.ICON_SCALE).dp
    val selectedIndex = segments.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val motion = remember { ModeSwitchMotion(selectedIndex) }
    LocalModeSwitchProbe.current?.let { probe -> SideEffect { probe(motion) } }
    val scope = rememberCoroutineScope()
    val currentSegments by rememberUpdatedState(segments)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentSelected by rememberUpdatedState(selected)
    // No replay: a change that is not this control's own tap shows its end state at once.
    LaunchedEffect(selectedIndex) {
        if (motion.commitJob?.isActive != true && motion.shown != selectedIndex) motion.settle(selectedIndex)
    }
    val shownOn = segments[motion.shown].enabled.isEnabled
    val trackInput =
        KitSurfaceInput(SurfaceRole.Track, KitShapeId.Button, SurfaceLevel.Surface1, ownFill = colors.surface1)
    val track = kitSurfaceLook(trackInput)
    val thumb = chosenBlockLook(!shownOn)
    val ink = modeInk(segments.map { it.enabled.isEnabled }, shownOn, thumb.words)
    val rimKind = ThemeSwitchRules.rimFor(style)
    val icons = segments.map { it.icon }
    val iconPx = with(density) { iconDp.toPx() }

    fun tap(i: Int) {
        if (!currentSegments[i].enabled.isEnabled || i == motion.shown) return
        haptics(KitHaptic.Select)
        val plan = ThemeSwitchRules.plan(if (motionState.stillFrame) MotionLevel.Off else motionState.level)
        motion.begin(i, plan, icons, iconPx)
        motion.play(scope, i, specs.release(), motionState.debugTimeScale)
        motion.scheduleCommit(scope, motionState.debugTimeScale) { shown ->
            val target = currentSegments[shown].value
            (target != currentSelected).also { if (it) currentOnChange(target) }
        }
    }

    Box(
        Modifier.fillMaxWidth()
            .then(track.modifier)
            .then(
                if (style == Style.Solid) {
                    Modifier.kitHairlineBorder(hairline, colors.hairline, track.shape)
                } else {
                    Modifier
                },
            )
            .padding(hairline)
            .onGloballyPositioned { motion.origin = it.positionInRoot() },
    ) {
        // The chosen block: its place is read in placement, its rim in drawing.
        Box(
            Modifier.matchParentSize()
                .modeBlock(motion, segments.size, inset, rimKind)
                .testTag("kit-mode-block")
                .then(thumb.modifier)
                .modeRim(motion, rimKind, ink.rim, thumb.shape, density),
        )
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            segments.forEachIndexed { i, s ->
                ModeSegment(motion, ModeSlot(i, segments.size, iconDp, thumb.words), s.label, s.enabled) { tap(i) }
            }
        }
        // One Canvas pass for every icon: the resting ones and the dots in flight.
        Spacer(Modifier.matchParentSize().modeIcons(motion, icons, ink, iconPx))
    }
}

/** One segment: its tap target, its seam, an empty slot where the icon pass draws its icon, and its word. */
@Composable
private fun RowScope.ModeSegment(
    motion: ModeSwitchMotion,
    slot: ModeSlot,
    label: String,
    segEnabled: KitEnabled,
    onTap: () -> Unit,
) {
    val i = slot.index
    val iconDp = slot.iconDp
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val density = LocalDensity.current
    val hairline = kitHairline()
    val inset = (Space.S1 / 2f).dp
    val captionSize = with(density) { LocalKitTypography.current.style(TypeRole.Caption).fontSize.toDp() }
    val captionLine = captionSize * ModeLook.CAPTION_LINE * density.fontScale
    val tallHeight = maxOf(tokens.minTarget.dp, iconDp + Space.S1.dp + captionLine + (Space.S2 * 2).dp) // F: :783-786
    val chosen = i == motion.shown
    val words =
        when {
            !segEnabled.isEnabled -> TextTone.Tertiary
            chosen -> TextTone.Primary
            else -> TextTone.Secondary
        }
    val seam = SegmentedRules.seamAfter(i, slot.count, motion.shown, tall = true)
    val seamAlpha by animateFloatAsState(if (seam) 1f else 0f, KitMotion.specs.quick(), label = "mode-seam")
    val seamColor = colors.hairline
    Box(
        Modifier.weight(1f)
            .height(tallHeight)
            .drawBehind {
                if (seamAlpha > 0f) {
                    val x = if (layoutDirection == LayoutDirection.Rtl) 0f else size.width
                    val line = seamColor.copy(alpha = seamColor.alpha * seamAlpha)
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), hairline.toPx())
                }
            }
            .kitSelectTap(chosen, Role.RadioButton, segEnabled, haptic = null, KitShapeId.Tile, onTap)
            .padding(inset)
            .padding(horizontal = (Space.S2.toFloat() - Space.S1 / 2f).dp), // F: lib/ui/kit/kit_segmented.dart:629
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(
                Modifier.size(iconDp).onGloballyPositioned { c ->
                    val p = c.positionInRoot()
                    while (motion.slots.size <= i) motion.slots += Offset.Zero
                    if (motion.slots[i] != p) motion.slots[i] = p
                },
            )
            Spacer(Modifier.height(Space.S1.dp))
            // Hook H3, as KitChoiceCard: under a face the style paints itself the word takes the face's ink, as long as
            // the block is over this segment (it changes once as the block crosses the middle, not every frame).
            val face = slot.faceWords
            val under by remember(motion, i) { derivedStateOf { abs(motion.pos.value - i) < HALF } }
            val wordColors =
                if (face != null && under) colors.copy(text1 = face, text2 = face, text3 = face) else colors
            CompositionLocalProvider(LocalKitColors provides wordColors) {
                KitText(label, role = TypeRole.Caption, tone = words, kind = KitTextKind.Frame, maxLines = 1)
            }
        }
    }
}

/**
 * Places the chosen block on its segment ([ModeSwitchMotion.pos]) in the layout phase. Brutal's block leans 2 px into
 * its offset shadow during the sweep (below and to the end side; placeRelative mirrors it in RTL).
 */
private fun Modifier.modeBlock(
    motion: ModeSwitchMotion,
    count: Int,
    inset: androidx.compose.ui.unit.Dp,
    rim: ThemeRimKind,
): Modifier =
    layout { measurable, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val seg = w.toFloat() / count
        val ins = inset.roundToPx()
        val bw = (seg - 2 * ins).toInt().coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(bw, (h - 2 * ins).coerceAtLeast(0)))
        layout(w, h) {
            val shift =
                if (rim == ThemeRimKind.HardSweep && motion.plan.rim) {
                    val t = (motion.rim.value / ThemeSwitchTimings.RIM_TRAVEL_MS).coerceIn(0f, 1f)
                    (ThemeSwitchRules.BRUTAL_SHIFT_PX * sin(PI.toFloat() * t)).toInt()
                } else {
                    0
                }
            placeable.placeRelative((motion.pos.value * seg + ins).toInt() + shift, ins + shift)
        }
    }

/** Draws every segment's pixel icon at its slot, and the dots in flight; reads the clocks in the draw phase only. */
private fun Modifier.modeIcons(
    motion: ModeSwitchMotion,
    icons: List<KitPixelIcon>,
    ink: ModeInk,
    iconPx: Float,
): Modifier =
    drawWithCache {
        val cell = iconPx / KitPixelIcons.GRID
        val dot = cell * ModeLook.DOT_FILL
        var buffer = FloatArray(0)
        onDrawBehind {
            val ms = motion.morphMs.value
            val p = (ms / KitDotMorph.MORPH_MS).coerceIn(0f, 1f)
            val moving = p < 1f && (motion.plan.dust || motion.plan.crossfade)
            val look = IconLook(ink, DotSize(cell, dot))
            for (i in icons.indices) drawResting(motion, i, icons[i], look, if (moving) p else 1f)
            val morph = motion.morph
            if (moving && morph != null) {
                if (buffer.size < morph.floats) buffer = FloatArray(morph.floats)
                drawDots(buffer, morph.frame(ms, buffer), dot, ink.chosen)
            }
        }
    }

/** One segment's icon at rest, or, while the icons change ([p] < 1), its share of the hand-over. */
private fun DrawScope.drawResting(motion: ModeSwitchMotion, i: Int, icon: KitPixelIcon, look: IconLook, p: Float) {
    val ink = look.ink
    val cell = look.size.cell
    val dot = look.size.dot
    fun draw(color: Color, alpha: Float) {
        if (alpha <= ModeLook.MIN_ALPHA) return
        val o = motion.slot(i)
        val c = color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f))
        val corner = CornerRadius(dot * ModeLook.DOT_CORNER)
        for (d in icon.dots) {
            drawRoundRect(c, Offset(o.x + d.x * cell - dot / 2f, o.y + d.y * cell - dot / 2f), Size(dot, dot), corner)
        }
    }
    val shown = motion.shown
    val from = motion.from
    // In Calm (no dots) the chosen look cross-fades between the two segments; with dots it travels instead.
    val fades = motion.plan.crossfade || motion.morph == null
    when {
        p >= 1f || (i != shown && i != from) || shown == from ->
            draw(if (i == shown) ink.chosen else ink.resting[i], 1f)
        i == shown -> {
            draw(ink.resting[i], 1f - (p * ModeLook.TARGET_GIVE_WAY).coerceAtMost(1f))
            if (fades) draw(ink.chosen, p)
        }
        else -> {
            draw(ink.resting[i], p)
            if (fades) draw(ink.chosen, 1f - p)
        }
    }
}

/** The dots in flight, from a [KitDotMorph.frame] of [n] dots. */
private fun DrawScope.drawDots(frame: FloatArray, n: Int, dot: Float, color: Color) {
    val f = ModeLook.DOT_FLOATS
    for (k in 0 until n) {
        val a = frame[k * f + 2].coerceIn(0f, 1f)
        if (a <= ModeLook.MIN_ALPHA) continue
        val s = dot * frame[k * f + f - 1]
        val at = Offset(frame[k * f] - s / 2f, frame[k * f + 1] - s / 2f)
        drawRoundRect(color.copy(alpha = color.alpha * a), at, Size(s, s), CornerRadius(s * ModeLook.DOT_CORNER))
    }
}

/** Tests only: hands each mode control's motion state to the test. */
internal val LocalModeSwitchProbe = staticCompositionLocalOf<((ModeSwitchMotion) -> Unit)?> { null }
