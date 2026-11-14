package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.GlassFinish
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.live.kitLivePictureSpots
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitVisibility
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** The reply bars of each placeholder turn, as fractions of the column (Dart `KitSkeletonTranscript._replyWidths`). */
internal val SKELETON_REPLY_WIDTHS: List<List<Float>> =
    listOf(
        listOf(0.94f, 0.88f, 0.62f), // F: lib/ui/kit/kit_skeleton_transcript.dart:23
        listOf(0.9f, 0.97f, 0.8f, 0.46f), // F: lib/ui/kit/kit_skeleton_transcript.dart:24
    )

/** The line of words inside a placeholder prompt, as a share of the prompt's width. */
internal const val SKELETON_PROMPT_LINE: Float = 0.55f

/**
 * The placeholder's ink (owner 2026-11-14: a conversation loading on Glass over Contour lines showed nothing at all; its
 * bars were `surface3`, dark grey on a near-black ground). The placeholder lines stand for words, so they are drawn in
 * the words' ink held back towards the ground: the least share of the way from the ground to `text1` at which the ink
 * reads as a non-text mark ([GlassFinish.NON_TEXT_FLOOR], 3:1) over the ground and over everything the live background
 * can put behind it. The same rule in every style and both modes; the style's own language comes from the prompt's
 * surface and the bars' corners.
 */
internal object SkeletonInk {
    private const val STEPS = 64

    /** How much further towards `text1` the breath goes at its strongest, as a share of what is left. */
    const val BREATH_SHARE: Float = .35f

    /** The least share of the way from the ground to `text1` that reads over every one of [grounds]; 1 when none does. */
    fun least(roles: Roles, grounds: List<Argb>): Float {
        for (i in 0..STEPS) {
            val t = i / STEPS.toFloat()
            if (reads(at(roles, t), grounds)) return t
        }
        return 1f
    }

    /** The ink at share [t] of the way from the ground to `text1` (both opaque). */
    fun at(roles: Roles, t: Float): Argb = lerp(roles.ground.withAlpha(1f), roles.text1.withAlpha(1f), t.coerceIn(0f, 1f))

    /** True when [ink] reads as a non-text mark over each of [grounds]. */
    fun reads(ink: Argb, grounds: List<Argb>): Boolean =
        grounds.all { wcagContrast(ink, it.withAlpha(1f)) >= GlassFinish.NON_TEXT_FLOOR - 1e-6 }

    /**
     * The ink at rest and at the breath's strongest: the strongest goes [BREATH_SHARE] of the rest of the way to
     * `text1`, and only where it still reads over every ground, so the breath never takes the placeholder below the
     * floor.
     */
    fun pair(roles: Roles, grounds: List<Argb>): Pair<Argb, Argb> {
        val t = least(roles, grounds)
        val rest = at(roles, t)
        val strong = at(roles, t + (1f - t) * BREATH_SHARE)
        return rest to (if (reads(strong, grounds)) strong else rest)
    }
}

/**
 * Placeholder turns while a conversation loads (Dart `KitSkeletonTranscript`): per turn a prompt block (a content
 * surface in the style, as the prompt bubble is, holding one line of placeholder words) and a few reply bars. The lines
 * are the words' ink held back ([SkeletonInk]): plainly visible over the ground and any live background in every style
 * and mode. While motion runs and the app is on screen they breathe slowly between that ink and a stronger one, so the
 * page reads as working; under Calm, Off, reduced motion and still frames they rest. No words, hidden from screen
 * readers. It sits at the bottom, where the newest turn will appear; in a short space the turns nearest the composer
 * show and the rest is cut off at the top, never an overflow.
 *
 * States: loading (decorative)
 * Cap: Conversation (the detail pane's 700 dp)
 * Motion: a slow breath of the lines' ink (MotionTokens.STEP_BREATHE each way); still where loops are not allowed
 * Replaces: KitSkeletonTranscript (lib/ui/kit/kit_skeleton_transcript.dart)
 */
@Composable
public fun KitSkeletonTranscript(modifier: Modifier = Modifier, turns: Int = 2) { // F: lib/ui/kit/kit_skeleton_transcript.dart:18
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val roles = LocalKitRoles.current
    val spots = kitLivePictureSpots()
    val (rest, strong) = remember(roles, spots) { SkeletonInk.pair(roles, listOf(roles.ground) + spots) }
    val breath = skeletonBreath()
    val promptHeight = Space.S6 + Space.S3
    val prompt =
        kitSurfaceLook(
            KitSurfaceInput(role = SurfaceRole.Content, shape = KitShapeId.Button, level = SurfaceLevel.Surface3, ownFill = colors.surface3),
        )
    val barShape = KitShapeId.Pill.shape()
    val line = Modifier.height(Space.S2.dp).skeletonInk(barShape, rest, strong, breath)
    val column: @Composable () -> Unit = {
        Column(
            Modifier
                .widthIn(max = Layout.PANE_DETAIL_MAX_WIDTH.dp)
                .fillMaxWidth()
                .padding(start = tokens.gutter.dp, top = tokens.gutter.dp, end = tokens.gutter.dp, bottom = (tokens.gutter + Space.S2).dp)
                .testTag("kit-skeleton-transcript"),
        ) {
            for (turn in 0 until turns) {
                if (turn > 0) Spacer(Modifier.height((Space.S4 + Space.S3).dp))
                Box(
                    Modifier.fillMaxWidth().height(promptHeight.dp).then(prompt.modifier).testTag("kit-skeleton-prompt"),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.padding(horizontal = Space.S3.dp).fillMaxWidth(SKELETON_PROMPT_LINE).then(line))
                }
                Spacer(Modifier.height(Space.S4.dp))
                for ((i, width) in SKELETON_REPLY_WIDTHS[turn % SKELETON_REPLY_WIDTHS.size].withIndex()) {
                    Box(
                        Modifier
                            .padding(bottom = Space.S2.dp)
                            .fillMaxWidth(width)
                            .then(line)
                            .testTag("kit-skeleton-line-$turn-$i"),
                    )
                }
            }
        }
    }
    BoxWithConstraints(modifier.clearAndSetSemantics {}) {
        if (constraints.hasBoundedHeight) {
            // OverflowBox at the bottom: the nearest turns show, the rest is clipped at the top.
            Box(Modifier.fillMaxWidth().height(maxHeight).clipToBounds(), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true), contentAlignment = Alignment.BottomCenter) { column() }
            }
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) { column() }
        }
    }
}

/**
 * The placeholder's breath (0 rest … 1 strongest), or null where it rests: loops not allowed (Calm, Off, reduced motion,
 * still frames) or the app hidden ([LocalKitVisibility]; the frame clock stops too, this only makes it explicit).
 */
@Composable
private fun skeletonBreath(): State<Float>? {
    val visible by LocalKitVisibility.current.visible.collectAsState()
    if (!KitMotion.state.ambientLoops || !visible) return null
    return rememberInfiniteTransition(label = "kit-skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(MotionTokens.STEP_BREATHE, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
}

/** Fills [shape] with the placeholder ink, read at draw time so the breath redraws without composing again. */
private fun Modifier.skeletonInk(shape: Shape, rest: Argb, strong: Argb, breath: State<Float>?): Modifier =
    drawBehind {
        val k = breath?.value ?: 0f
        val ink = if (k <= 0f) rest else lerp(rest, strong, k)
        drawOutline(shape.createOutline(size, layoutDirection, this), Color(ink.v))
    }
