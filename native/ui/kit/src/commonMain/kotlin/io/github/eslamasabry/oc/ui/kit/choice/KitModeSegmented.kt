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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
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
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairlineBorder
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.ThemeRimKind
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchPlan
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchRules
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchTimings
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.KitThemeCoverHole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitThemeCover
import io.github.eslamasabry.oc.ui.kit.theme.ThemeFade
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

/** One segment of [KitModeSegmented]: a value, its word, its [KitIcons] glyph, and its own disabled reason. */
@Immutable
public data class KitModeSegment<T>(
    val value: T,
    val label: String,
    val icon: ImageVector,
    val enabled: KitEnabled = KitEnabled.Enabled,
)

/**
 * The theme mode control (Light / Dark / Auto / Saver; theme-switch-premium, owner-approved 2026-11-08): the tall
 * [KitSegmented] with premium motion. A tap slides the chosen block to the new segment, a rim of light runs round its
 * edge and settles, and the old icon breaks into fine particles that drift and re-form into the new one (the icons
 * at rest are the regular [KitIcons] glyphs, as everywhere else; particles show only during a person's tap, sampled
 * from those glyphs' own shapes). The theme itself commits
 * [ThemeSwitchTimings.COMMIT_MS] later, once the motion is under way, and at most three times a second however fast
 * the taps come. A second tap mid-move retargets the slide and the dots from where they are and restarts the rim.
 * Changes that are not a tap (Auto following the system, rotation, the first frame, a restored process) show the end
 * state at once, with no rim, particles or haptic. While its own switch runs, the control is kept out of the theme
 * cover's fade ([KitThemeCoverHole]), so it never shows the old and new themes blended grey.
 *
 * States: default, selected, disabled, stacked, switching
 * Text: frame
 * Cap: Reading
 * Motion: Full = slide (release spring) + rim + particle morph; Calm = slide + icon cross-fade; Off = instant
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
                    segments.map { KitSegment(it.value, it.label, it.icon, it.enabled) },
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
 * The motion state of one mode control. Only a tap ([begin], [play]) starts motion; [settle] puts every part at its end
 * for a change that came from elsewhere. The clocks are read in the layout and draw phases only, so a running switch
 * never recomposes the control.
 */
@Stable
internal class ModeSwitchMotion(initial: Int) {
    /** The segment the control shows as chosen: the tapped one at once, before the theme commits. */
    var shown by mutableIntStateOf(initial)

    /** The segment the block and particles are leaving (its resting icon fades back in). */
    var from by mutableIntStateOf(initial)

    /** The chosen block's place, in segments (0 = the first). */
    val pos = Animatable(initial.toFloat())

    /** ms into the rim's run; at [ThemeSwitchTimings.RIM_MS] it is gone. */
    val rim = Animatable(ThemeSwitchTimings.RIM_MS.toFloat())

    /** ms into the particle morph or cross-fade; at [ParticleMorph.MORPH_MS] the icons are at rest. */
    val morphMs = Animatable(ParticleMorph.MORPH_MS)

    /** The particles in flight (Full), null at rest or in Calm. */
    var morph: ParticleMorph? by mutableStateOf(null)

    /** Where the block was when the particles set off, in segments (the cloud rides along with it). */
    private var carriedFrom = initial.toFloat()

    /** How far the block has slid from [carriedFrom] towards the chosen segment (0 … 1). */
    fun carried(): Float {
        val span = shown - carriedFrom
        return if (abs(span) < MIN_SPAN) 0f else ((pos.value - carriedFrom) / span).coerceIn(0f, 1f)
    }

    /** Which way the block travels on screen (+1 rightwards), for the rim's start and Brutal's shift. */
    var travel by mutableStateOf(1f)

    var plan: ThemeSwitchPlan by mutableStateOf(ThemeSwitchRules.plan(MotionLevel.Off))

    /** Each icon slot's top-left in root pixels, and the control's own; the particles are drawn relative to it. */
    val slots = mutableStateListOf<Offset>()
    var origin by mutableStateOf(Offset.Zero)

    /** Keeps the control out of the theme cover's fade while its own switch runs (no grey wash over it). */
    val coverHole = KitThemeCoverHole()

    /** Each segment's resting-ink and chosen-ink icon opacity when the current change began. */
    private var restingStart = FloatArray(0)
    private var chosenStart = FloatArray(0)

    internal var lastCommitMs: Long? = null
    internal var commitJob: Job? = null
    private var holeJob: Job? = null

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
        morphMs.snapTo(ParticleMorph.MORPH_MS)
    }

    /** How far the icons' hand-over is (0 … 1), or null at rest. */
    private fun handOver(): Float? {
        val p = (morphMs.value / ParticleMorph.MORPH_MS).coerceIn(0f, 1f)
        return p.takeIf { it < 1f && (plan.dust || plan.crossfade) && shown != from }
    }

    /**
     * Segment [i]'s regular icon in its resting ink: full on every segment but the chosen one. On a change the chosen
     * one's gives way fast and the left one's returns once the particles have gone.
     */
    fun restingAlpha(i: Int): Float {
        val end = if (i == shown) 0f else 1f
        val p = handOver() ?: return end
        val start = restingStart.getOrElse(i) { end }
        val k =
            when {
                i == shown -> (p * ModeLook.TARGET_GIVE_WAY).coerceAtMost(1f)
                morph != null -> ParticleMorph.smooth(p, ModeLook.RETURN_START, 1f)
                else -> p
            }
        return start + (end - start) * k
    }

    /**
     * Segment [i]'s regular icon in the chosen ink: only on the chosen segment at rest. With particles the old one
     * dissolves at once into them and the new one fades in over their last part; in Calm the two cross-fade.
     */
    fun chosenAlpha(i: Int): Float {
        val end = if (i == shown) 1f else 0f
        val p = handOver() ?: return end
        val start = chosenStart.getOrElse(i) { end }
        val k =
            when {
                morph == null -> p
                i == shown -> ParticleMorph.smooth(p, ModeLook.FORM_START, 1f)
                else -> ParticleMorph.smooth(p, 0f, ModeLook.BREAK_END)
            }
        return start + (end - start) * k
    }

    fun begin(target: Int, plan: ThemeSwitchPlan, icons: List<ImageVector>, iconPx: Float) {
        started++
        val old = shown
        // Every icon layer continues from where it is now (an interruption included).
        restingStart = FloatArray(icons.size) { restingAlpha(it) }
        chosenStart = FloatArray(icons.size) { chosenAlpha(it) }
        val fromX = slot(old).x
        val toX = slot(target).x
        if (toX != fromX) travel = if (toX > fromX) 1f else -1f
        // An interruption starts from where the particles are now; otherwise from the old icon at rest.
        val inFlight = morph?.takeIf { morphMs.isRunning }?.sample(morphMs.value)
        val source = inFlight ?: place(IconParticles.of(icons[old]), slot(old), iconPx, withAlpha = true)
        val dest = place(IconParticles.of(icons[target]), slot(target), iconPx, withAlpha = false)
        this.plan = plan
        carriedFrom = pos.value
        morph = if (plan.dust) ParticleMorph(source, dest, iconPx) else null
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
            run(morphMs, plan.dust || plan.crossfade, ParticleMorph.MORPH_MS, scale)
            morph = null
        }
        // The theme cover fades for ThemeFade.MS after the commit, which comes at most IDLE_MS after the tap.
        holeJob?.cancel()
        holeJob =
            scope.launch {
                coverHole.open = true
                val due = withFrameMillis { it } + ((ThemeSwitchTimings.IDLE_MS + ThemeFade.MS) * scale).toLong()
                while (withFrameMillis { it } < due) Unit
                coverHole.open = false
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

/** Unit-square particle points placed on an icon slot at [at], [iconPx] wide (x, y and, [withAlpha], 1). */
private fun place(unit: FloatArray, at: Offset, iconPx: Float, withAlpha: Boolean): FloatArray {
    val n = unit.size / 2
    val stride = if (withAlpha) ParticleMorph.FROM_FLOATS else 2
    return FloatArray(n * stride) { k ->
        val p = k / stride
        when (k % stride) {
            0 -> at.x + unit[2 * p] * iconPx
            1 -> at.y + unit[2 * p + 1] * iconPx
            else -> 1f
        }
    }
}

/**
 * Keeps [motion]'s control out of the theme cover's fade while its own switch runs ([KitThemeCoverHole]): the returned
 * modifier goes on the track, whose outline is [shape].
 */
@Composable
private fun keepOutOfCover(motion: ModeSwitchMotion, shape: androidx.compose.ui.graphics.Shape): Modifier {
    motion.coverHole.shape = shape
    val cover = LocalKitThemeCover.current
    DisposableEffect(cover, motion) {
        cover?.holes?.add(motion.coverHole)
        onDispose { cover?.holes?.remove(motion.coverHole) }
    }
    return Modifier.onPlaced { motion.coverHole.coords = it }
}

/** The look of the switch's moving parts: tuned by eye against the owner's reference (s33). */
internal object ModeLook {
    const val CAPTION_LINE = 1.3f // F: lib/ui/kit/kit_segmented.dart:781
    const val ICON_SCALE = 1.2f // F: lib/ui/kit/kit_segmented.dart:851
    const val MIN_ALPHA = 0.004f

    /** The target's resting icon gives way this many times faster than the morph. */
    const val TARGET_GIVE_WAY = 3f

    /** The old icon has broken into particles by this point of the morph (0 … 1). */
    const val BREAK_END = 0.1f

    /** The new crisp icon starts to show through the particles here, and the left segment's resting icon here. */
    const val FORM_START = 0.72f
    const val RETURN_START = 0.35f

    /** A particle's radius as a share of the icon, and its halo's (the slight blur) as a multiple of that. */
    const val PARTICLE_RADIUS = 0.026f
    const val PARTICLE_HALO_SCALE = 2.4f
    const val PARTICLE_HALO_ALPHA = 0.2f
    const val PARTICLE_HALO_REST = 0.15f

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

/** A segment's place in the row, its icon and the icon's size and inks. */
private class ModeSlot(
    val index: Int,
    val count: Int,
    val icon: ImageVector,
    val iconDp: androidx.compose.ui.unit.Dp,
    val faceWords: Color?,
    val ink: ModeInk,
)

private const val HALF = 0.5f

/** Less than this between the block's start and its segment counts as no slide (the cloud then keeps its own clock). */
private const val MIN_SPAN = 0.01f

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
    val iconPx = with(density) { iconDp.toPx() }

    fun tap(i: Int) {
        if (!currentSegments[i].enabled.isEnabled || i == motion.shown) return
        haptics(KitHaptic.Select)
        val plan = ThemeSwitchRules.plan(if (motionState.stillFrame) MotionLevel.Off else motionState.level)
        motion.begin(i, plan, currentSegments.map { it.icon }, iconPx)
        motion.play(scope, i, specs.release(), motionState.debugTimeScale)
        motion.scheduleCommit(scope, motionState.debugTimeScale) { shown ->
            val target = currentSegments[shown].value
            (target != currentSelected).also { if (it) currentOnChange(target) }
        }
    }

    Box(
        Modifier.fillMaxWidth()
            .then(keepOutOfCover(motion, track.shape))
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
                val slot = ModeSlot(i, segments.size, s.icon, iconDp, thumb.words, ink)
                ModeSegment(motion, slot, s.label, s.enabled) { tap(i) }
            }
        }
        // One Canvas pass for the particles in flight (a tap in Full only); the icons themselves are the glyphs.
        Spacer(Modifier.matchParentSize().modeParticles(motion, ink.chosen, iconPx))
    }
}

/**
 * One segment: its tap target, its seam, its regular icon (in the resting and the chosen ink, the two layers' opacity
 * read in the draw phase from the motion) and its word.
 */
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
            Box(
                Modifier.size(iconDp).testTag("kit-mode-icon").onGloballyPositioned { c ->
                    val p = c.positionInRoot()
                    while (motion.slots.size <= i) motion.slots += Offset.Zero
                    if (motion.slots[i] != p) motion.slots[i] = p
                },
            ) {
                val ink = slot.ink
                KitGlyph(slot.icon, ink.resting[i], iconDp, Modifier.graphicsLayer { alpha = motion.restingAlpha(i) })
                KitGlyph(slot.icon, ink.chosen, iconDp, Modifier.graphicsLayer { alpha = motion.chosenAlpha(i) })
            }
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

/**
 * Draws the particles in flight ([ModeSwitchMotion.morph]); reads the clock in the draw phase only. Each particle is a
 * small round core with a faint halo that grows mid-flight (the slight blur), so the cloud reads as soft dust.
 */
private fun Modifier.modeParticles(motion: ModeSwitchMotion, color: Color, iconPx: Float): Modifier =
    drawWithCache {
        val radius = iconPx * ModeLook.PARTICLE_RADIUS
        var buffer = FloatArray(0)
        onDrawBehind {
            val morph = motion.morph ?: return@onDrawBehind
            val ms = motion.morphMs.value
            if (ms >= ParticleMorph.MORPH_MS) return@onDrawBehind
            if (buffer.size < morph.floats) buffer = FloatArray(morph.floats)
            drawParticles(buffer, morph.frame(ms, buffer, motion.carried()), radius, color)
        }
    }

/** The particles of a [ParticleMorph.frame] of [n] particles. */
private fun DrawScope.drawParticles(frame: FloatArray, n: Int, radius: Float, color: Color) {
    val f = ParticleMorph.FLOATS
    for (k in 0 until n) {
        val a = frame[k * f + ParticleMorph.ALPHA_AT].coerceIn(0f, 1f)
        if (a <= ModeLook.MIN_ALPHA) continue
        val at = Offset(frame[k * f], frame[k * f + 1])
        val r = radius * frame[k * f + ParticleMorph.SIZE_AT]
        val blur = frame[k * f + ParticleMorph.HALO_AT]
        val halo = a * ModeLook.PARTICLE_HALO_ALPHA * (ModeLook.PARTICLE_HALO_REST + blur)
        if (halo > ModeLook.MIN_ALPHA) drawCircle(color, r * ModeLook.PARTICLE_HALO_SCALE, at, alpha = halo)
        drawCircle(color, r, at, alpha = a)
    }
}

/** Tests only: hands each mode control's motion state to the test. */
internal val LocalModeSwitchProbe = staticCompositionLocalOf<((ModeSwitchMotion) -> Unit)?> { null }
