package io.github.eslamasabry.oc.ui.kit.wait

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.kitToneOnGlass
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.lap
import io.github.eslamasabry.oc.ui.kit.theme.KitHct
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitSignals
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import kotlin.math.abs

/** The real kinds of wait the app has, one mark each (two drawings where the owner kept both: see [KitWaitVariant]). */
public enum class KitWaitKind {
    /** Something the app started is coming up: the phone's Linux, its server. */
    Starting,

    /** An agent or its helper is getting ready to answer. */
    Warming,

    /** A download whose share done is known: pass it as `fraction`. */
    Downloading,

    /** The agent is writing its reply. */
    Writing,

    /** The person's message is on its way. */
    Sending,

    /** The app is waiting for the person. */
    NeedsYou,
}

/**
 * Which of a kind's two drawings: Starting (a sunrise, or three columns coming up), Writing (a nib writing a stroke, or
 * a line written behind a caret), Sending (a paper dart, or a paper plane that leaves and the next one glides in). The
 * other kinds have one drawing and ignore it.
 */
public enum class KitWaitVariant { Default, Alternate }

/**
 * How a mark is coloured.
 * - [Ink]: the ink of the words beside it, as the family was first drawn; only "needs you"'s dot is amber.
 * - [Colour]: the same drawings with colours that mean something, all from the theme's roles: heat and light (the sun,
 *   the steam) in the theme's warm role, what a wait is making or moving (the download that has arrived, the line being
 *   written, the message flying, the server coming up) in the accent, the person's turn in the amber of "needs you".
 *   The thing itself stays in ink. Flat fills only; light themes take each role's own light value, made to read on a
 *   light page, not a dimmed dark one.
 */
public enum class KitWaitMode { Ink, Colour }

/**
 * The mode every [KitWaitMark] under it is drawn in, unless a mark is given its own. Ink by default; the app's
 * Appearance choice (not built yet) provides it at the root.
 */
public val LocalKitWaitMode: ProvidableCompositionLocal<KitWaitMode> = compositionLocalOf { KitWaitMode.Ink }

/** [KitWaitMark]'s sizes: it is drawn for [Size] beside 14 to 16 sp words and never grows past [MaxSize]. */
public object KitWaitMarkDefaults {
    public val Size: Dp = SIZE_DP.dp
    public val MinSize: Dp = MIN_SIZE_DP.dp
    public val MaxSize: Dp = MAX_SIZE_DP.dp
}

private const val SIZE_DP = 20
private const val MIN_SIZE_DP = 12
private const val MAX_SIZE_DP = 40

/** The mark comes in from this share of its size. */
private const val ARRIVES_FROM = 0.8f

/**
 * The nine drawings: one loop each, of its own length ([lapMillis]; every beat inside a loop is at least a calm
 * heartbeat from the next), on its own grid ([grid] units across the box). [mirrors]: whether it turns round in a
 * right-to-left language. What grows, writes or flies along the reading direction does; a sun, a cup and its steam do
 * not (they have no direction).
 */
internal enum class WaitDrawing(val lapMillis: Int, val mirrors: Boolean, val grid: Float) {
    Sunrise(lapMillis = 4400, mirrors = false, grid = 24f),
    Columns(lapMillis = 4200, mirrors = true, grid = WAIT_BOX),
    Cup(lapMillis = 4000, mirrors = false, grid = 24f),
    Download(lapMillis = 2800, mirrors = true, grid = WAIT_BOX),
    Nib(lapMillis = 3600, mirrors = true, grid = 24f),
    Lines(lapMillis = 3200, mirrors = true, grid = WAIT_BOX),
    Dart(lapMillis = 3400, mirrors = true, grid = 24f),
    Plane(lapMillis = 3000, mirrors = true, grid = WAIT_BOX),
    Person(lapMillis = 4000, mirrors = true, grid = WAIT_BOX),
}

internal fun waitDrawing(kind: KitWaitKind, variant: KitWaitVariant): WaitDrawing {
    val alternate = variant == KitWaitVariant.Alternate
    return when (kind) {
        KitWaitKind.Starting -> if (alternate) WaitDrawing.Columns else WaitDrawing.Sunrise
        KitWaitKind.Warming -> WaitDrawing.Cup
        KitWaitKind.Downloading -> WaitDrawing.Download
        KitWaitKind.Writing -> if (alternate) WaitDrawing.Lines else WaitDrawing.Nib
        KitWaitKind.Sending -> if (alternate) WaitDrawing.Plane else WaitDrawing.Dart
        KitWaitKind.NeedsYou -> WaitDrawing.Person
    }
}

/**
 * The waiting mark of one [kind] of wait, for the start of any row beside the words that name the wait
 * ("Starting OpenCode…"). It is drawn in code: in [KitWaitMode.Ink] in one ink, [tone] (pass the tone of the words
 * beside it); in [KitWaitMode.Colour] the same drawing with the theme's meaning colours (see [KitWaitMode]).
 *
 * The box is always [size] square (at most [KitWaitMarkDefaults.MaxSize]), shown or not, and everything is drawn
 * inside it, so the words beside it never move when the mark appears, leaves or animates. While [shown] is false
 * nothing is drawn and no clock runs.
 *
 * [fraction] is the share done, 0..1, read by [KitWaitKind.Downloading] only; a new value eases in.
 *
 * States: shown, not shown (keeps its box); Ink or Colour; default or alternate drawing
 * Text: none; decorative, no semantics: the words beside it say the wait, and a screen reader hears those
 * Cap: 20 dp square, 40 dp at most
 * Motion: the drawing's own loop at Full only (class AmbientLoop); Calm (Battery Saver, heat), Off, Saver, tests and
 * screenshots draw the drawing's still and run no clock; comes in on Enter and leaves on Quick, instant under Off
 * (class Fade). The loop's place is read while drawing only: a frame redraws this mark's own layer and recomposes
 * nothing.
 * Hidden: none
 * Replaces: none (the first use stands where there was no mark)
 */
@Suppress("LongParameterList") // composable part; rule retired for @Composable in the 3.0.1 rule-set review
@Composable
public fun KitWaitMark(
    kind: KitWaitKind,
    modifier: Modifier = Modifier,
    variant: KitWaitVariant = KitWaitVariant.Default,
    mode: KitWaitMode = LocalKitWaitMode.current,
    tone: TextTone = TextTone.Secondary,
    size: Dp = KitWaitMarkDefaults.Size,
    fraction: Float = 0f,
    shown: Boolean = true,
) {
    val box = size.coerceIn(KitWaitMarkDefaults.MinSize, KitWaitMarkDefaults.MaxSize)
    val specs = KitMotion.specs
    val presence =
        animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = if (shown) specs.enter<Float>() else specs.exit<Float>(),
            label = "kit-wait-presence",
        )
    val seen by remember { derivedStateOf { presence.value > 0f } }
    if (!shown && !seen) {
        // Not shown: the box only. Nothing is drawn and nothing is scheduled.
        Spacer(modifier.size(box))
        return
    }
    val drawing = waitDrawing(kind, variant)
    val level = animateFloatAsState(fraction.coerceIn(0f, 1f), specs.effects(), label = "kit-wait-fraction")
    // Battery Saver and the true-black Saver look draw the still: a loop costs nothing there.
    val saver = LocalKitAppearance.current.saver || LocalKitSignals.current.powerSave
    // A download that has all arrived has nothing left to animate.
    val loops = KitMotion.state.ambientLoops && !saver && !(kind == KitWaitKind.Downloading && fraction >= 1f)
    val phase =
        if (loops) {
            rememberInfiniteTransition(label = "kit-wait")
                .animateFloat(0f, 1f, specs.lap(drawing.lapMillis), label = "kit-wait-phase")
        } else {
            null
        }
    val clock =
        remember(phase, level) {
            WaitClock(still = phase == null, phase = { phase?.value ?: 0f }, fraction = { level.value })
        }
    val face =
        modifier.size(box).graphicsLayer {
            val here = presence.value
            alpha = here
            scaleX = ARRIVES_FROM + (1f - ARRIVES_FROM) * here
            scaleY = scaleX
        }
    WaitMarkFace(drawing, mode, tone, clock, face)
}

/** A number read while drawing. A plain `() -> Float` would box its answer on every frame; this does not. */
internal fun interface WaitReading {
    fun read(): Float
}

/**
 * What a face draws from. [phase] and [fraction] are read inside the draw block only, so a change redraws and never
 * recomposes; with [still] the drawing's still is drawn and [phase] is not read at all.
 */
internal class WaitClock(val still: Boolean, val phase: WaitReading, val fraction: WaitReading)

/** The colours of [mode] from the theme: roles only, never a colour of the mark's own. */
@Composable
internal fun waitColours(mode: KitWaitMode, tone: TextTone): WaitColours {
    val colors = LocalKitColors.current
    // In Colour the mark brings its own meaning colours: a body in a colour tone (the accent of a progress line)
    // would swallow them, so the body takes the secondary ink there.
    val colourTone = tone == TextTone.Accent || tone == TextTone.Attention || tone == TextTone.Success
    val ink = kitToneOnGlass(if (mode == KitWaitMode.Colour && colourTone) TextTone.Secondary else tone)
    return when (mode) {
        KitWaitMode.Ink -> WaitColours(ink = ink, warm = ink, live = ink, call = colors.attention)
        KitWaitMode.Colour -> {
            // On a light page the warm and the accent are their own light values (made to read on that page, not a
            // dark colour dimmed). The dot is the bright amber of the "needs you" badge in both; on a light page it
            // carries a fine edge of the deeper amber so it holds against the page.
            val warm =
                remember(colors.codeString, colors.accent, colors.dark) {
                    waitWarm(colors.codeString, colors.accent, colors.dark)
                }
            WaitColours(
                ink = ink,
                warm = warm ?: ink,
                live = colors.accent,
                call = colors.attentionFill,
                callEdge = if (colors.dark) null else colors.attention,
            )
        }
    }
}

/** The least chroma the warm light has on a dark page, and the brightest it gets there (HCT). */
private const val WARM_CHROMA = 64.0
private const val WARM_TONE_DARK = 74.0

/** An accent this close in hue to the warm (degrees), with at least this much colour, collides with it. */
private const val WARM_COLLIDES_WITHIN = 35.0
private const val COLOURED = 24.0
private const val HALF_TURN = 180.0
private const val FULL_TURN_DEGREES = 360.0

/**
 * The warm of heat and light, from the theme's warm role ([warmRole], `codeString`). On a dark page that role is a pale
 * peach made for code text; the sun and the steam take its own hue with more colour and a little less light, so they
 * read as warm light. On a light page the role is already a deep warm and is used as it is. Null when the [accent] is
 * itself a warm colour of that hue: the warm marks then draw in ink, so the warm and the accent never meet.
 */
internal fun waitWarm(warmRole: Color, accent: Color, dark: Boolean): Color? {
    val warm = KitHct.of(warmRole)
    val lead = KitHct.of(accent)
    val apart = abs(warm.hue - lead.hue).let { if (it > HALF_TURN) FULL_TURN_DEGREES - it else it }
    return when {
        lead.chroma >= COLOURED && apart < WARM_COLLIDES_WITHIN -> null
        !dark -> warmRole
        else -> KitHct(warm.hue, maxOf(warm.chroma, WARM_CHROMA), minOf(warm.tone, WARM_TONE_DARK)).toColor()
    }
}

/**
 * The drawing itself, shared by [KitWaitMark] (its own clock) and `KitWaitMarksDemo` (a fixed phase). One frame is
 * one pass over at most six filled or stroked shapes, clipped to the box; the pen, its stroke and its path are made
 * once per size, colours and style, not per frame.
 */
@Composable
internal fun WaitMarkFace(
    drawing: WaitDrawing,
    mode: KitWaitMode,
    tone: TextTone,
    clock: WaitClock,
    modifier: Modifier = Modifier,
) {
    val colours = waitColours(mode, tone)
    val hand = waitHand(LocalKitStyle.current.style)
    val mirrored = drawing.mirrors && LocalLayoutDirection.current == LayoutDirection.Rtl
    Spacer(
        modifier.drawWithCache {
            val pen = WaitPen(size.minDimension, colours, hand)
            onDrawBehind {
                clipRect {
                    if (mirrored) scale(-1f, 1f) { pen.mark(this, drawing, clock) } else pen.mark(this, drawing, clock)
                }
            }
        },
    )
}

private fun WaitPen.mark(scope: DrawScope, drawing: WaitDrawing, clock: WaitClock) {
    val still = clock.still
    // Held on its step for a stepping hand, before any mark reads it: Brutal's drawings are the loop's own.
    val phase = if (still) 0f else place(clock.phase.read())
    onGrid(drawing.grid)
    when (drawing) {
        WaitDrawing.Sunrise -> sunrise(scope, phase, still)
        WaitDrawing.Columns -> columns(scope, phase, still)
        WaitDrawing.Cup -> cup(scope, phase, still)
        WaitDrawing.Download -> downloading(scope, phase, clock.fraction.read(), still)
        WaitDrawing.Nib -> nib(scope, phase, still)
        WaitDrawing.Lines -> lines(scope, phase, still)
        WaitDrawing.Dart -> dart(scope, phase, still)
        WaitDrawing.Plane -> plane(scope, phase, still)
        WaitDrawing.Person -> needsYou(scope, phase, still)
    }
}
