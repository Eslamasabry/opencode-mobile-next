package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidSource
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState

/*
 * Words on the chat's glass (device, Android 15, Glass dark, 2026-11-11: "Steer while it works…" and "Ask OpenCode…"
 * had blurred transcript lines running through them). The body's own fade (kitScrollEdges) never reached the glass:
 * the message box's lens reads the screen's recording of its body, made inside the body before any fade, so with the
 * blur cut to 2 dp the transcript ran through its words at full brightness. The remedy the skill names second ("a
 * scroll-edge fade, so content thins out before it runs under a pane"), without more blur, tint or a plate: what the
 * bottom block's glass sees of the body fades out as it passes under the pane that carries words (kitScreenWordsPane),
 * whole at the pane's top edge, gone by the inner end of its lens rim. The rim still bends real content, so the box
 * stays glass at its edge; under the words nothing bright is left. Draw phase only: the body itself, its layout and
 * its scroll are untouched, and the bar and the jump pill keep seeing the whole recording.
 */

/**
 * Words on the top panes (the chat's bar, the Conversations home's and Settings' floating header): the same fade as the
 * message box's, turned over. What the pane's glass sees of the body is whole at the pane's bottom edge and gone by the
 * inner end of its lens rim, measured upward, so the rim still bends real rows and the title, the chips and the status
 * edge sit on nothing bright.
 */
internal enum class KitWordsClear {
    /** Cleared below the pane's top edge (the message box). */
    Below,

    /** Cleared above the pane's bottom edge (the bars). */
    Above,
}

/**
 * A recording as the glass over it sees it: a copy of the screen's recording, re-recorded each time the body records, and
 * cleared along one edge of a pane ([KitWordsClear]). The recording is a feature's ([KitFrostState]) or the kit's own
 * ([KitLiquidSource]); [faded] or [fadedSource] is the copy, handed to the pane's face, its words host and its edge.
 */
@Stable
internal class KitWordsFade private constructor(
    private val side: KitWordsClear,
    private val target: GraphicsLayer,
    private val layerOf: () -> GraphicsLayer,
    private val originOf: () -> Offset,
    private val sync: () -> Unit,
    private val bump: () -> Unit,
    /** The copy as a feature's recording; null when the source is the kit's. */
    val faded: KitFrostState?,
    /** The copy as the kit's own recording; null when the source is a feature's. */
    val fadedSource: KitLiquidSource?,
) {
    private val brush = EdgeBrush()

    /**
     * Records the copy again (the source's version is read by the caller's draw, so this runs after each recording of
     * the body), cleared along the pane edge [edge] (root px: the pane's top edge for [KitWordsClear.Below], its bottom
     * edge for [KitWordsClear.Above]) over its lens rim [rim]. Records nothing onto this scope's canvas.
     */
    fun DrawScope.record(edge: Float, rim: Float, clearAbove: Boolean = false) {
        val from = layerOf()
        val origin = originOf()
        sync()
        // Nothing recorded yet (the body's first frame): nothing to see.
        if (from.size.width <= 0 || from.size.height <= 0) return
        target.record(this, layoutDirection, from.size) {
            drawLayer(from)
            if (!edge.isNaN() && rim > 0f) {
                val h = size.height
                // On whole pixels (wholePx): a clearing gradient across a fractional pixel edge crashed the emulator's
                // renderer (KitScrollEdges.kt).
                when (side) {
                    KitWordsClear.Below -> {
                        val y0 = wholePx(edge - origin.y).coerceIn(0f, h)
                        val y1 = wholePx(edge - origin.y + rim).coerceIn(0f, h)
                        if (y1 > y0) {
                            drawRect(brush.of(0f, 1f, 1f, y0, y1), Offset(0f, y0), Size(size.width, y1 - y0), blendMode = BlendMode.DstOut)
                        }
                        if (y1 < h) drawRect(Color.Black, Offset(0f, y1), Size(size.width, h - y1), blendMode = BlendMode.DstOut)
                        // Parts of glass stand above the box inside the block (phrase chips, a request card's controls):
                        // their words get nothing bright either. Only the box's own rim keeps the recording.
                        if (clearAbove && y0 > 0f) drawRect(Color.Black, Offset(0f, 0f), Size(size.width, y0), blendMode = BlendMode.DstOut)
                    }
                    KitWordsClear.Above -> {
                        val y1 = wholePx(edge - origin.y).coerceIn(0f, h)
                        val y0 = wholePx(edge - origin.y - rim).coerceIn(0f, h)
                        if (y1 > y0) {
                            drawRect(brush.of(1f, 0f, 1f, y0, y1), Offset(0f, y0), Size(size.width, y1 - y0), blendMode = BlendMode.DstOut)
                        }
                        if (y0 > 0f) drawRect(Color.Black, Offset(0f, 0f), Size(size.width, y0), blendMode = BlendMode.DstOut)
                    }
                }
            }
        }
        // The panes that read the copy draw again (as kitFrostSource does for its own panes).
        Snapshot.withoutReadObservation { bump() }
    }

    internal companion object {
        fun of(source: KitFrostState, layer: GraphicsLayer, side: KitWordsClear): KitWordsFade {
            val copy = KitFrostState(layer)
            return KitWordsFade(
                side, layer, { source.layer }, { source.version; source.sourceOrigin },
                { copy.sourceOrigin = source.sourceOrigin }, { copy.version++ }, copy, null,
            )
        }

        fun of(source: KitLiquidSource, layer: GraphicsLayer, side: KitWordsClear): KitWordsFade {
            val copy = KitLiquidSource(layer)
            return KitWordsFade(
                side, layer, { source.layer },
                { source.version; source.coordinates?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero },
                // The copy lies where the recording lies: the panes place it by the source's own coordinates.
                { copy.coordinates = source.coordinates }, { copy.version++ }, null, copy,
            )
        }
    }
}

/** A [KitWordsFade] of [source] for the bottom block of one screen (the default) or a top pane. */
@Composable
internal fun rememberKitWordsFade(source: KitFrostState, side: KitWordsClear = KitWordsClear.Below): KitWordsFade {
    val layer = rememberGraphicsLayer()
    return remember(source, layer, side) {
        // Its own offscreen pass, so the clearing clears the recording alone, never what a pane draws under it.
        layer.compositingStrategy = CompositingStrategy.Offscreen
        KitWordsFade.of(source, layer, side)
    }
}

/** A [KitWordsFade] of the kit's own recording [source], which keeps recording while the copy is in use. */
@Composable
internal fun rememberKitWordsFade(source: KitLiquidSource, side: KitWordsClear): KitWordsFade {
    val layer = rememberGraphicsLayer()
    val fade = remember(source, layer, side) {
        layer.compositingStrategy = CompositingStrategy.Offscreen
        KitWordsFade.of(source, layer, side)
    }
    // The kit's recording runs only while a pane uses it; the panes now use the copy, so this holds the original open.
    DisposableEffect(source) {
        source.users++
        onDispose { source.users-- }
    }
    return fade
}

/**
 * Records [fade] where it is drawn: before this node's content ([after] false: the bottom block, drawn after the body
 * has recorded) or after it ([after] true: the body itself, so panes drawn before the bar read this frame's copy). The
 * edge and the rim are read while drawing.
 */
internal fun Modifier.kitWordsFadeRecorder(
    fade: KitWordsFade,
    edge: () -> Float,
    rim: () -> Float,
    after: Boolean = false,
    clearAbove: () -> Boolean = { false },
): Modifier =
    drawWithContent {
        if (!after) with(fade) { record(edge(), rim(), clearAbove()) }
        drawContent()
        if (after) with(fade) { record(edge(), rim(), clearAbove()) }
    }

/** Where a top pane's bottom edge lies and how deep its lens rim reaches, in root px (NaN / 0 while unknown). */
@Stable
internal class KitTopPane {
    var bottom: Float by mutableFloatStateOf(Float.NaN)
    var rim: Float by mutableFloatStateOf(0f)
}

/**
 * Reports this box's pane to [pane]: the pane lies [insetX] dp in from the box's sides and [insetY] dp in from its top
 * and bottom, with [skipTop] px of the box above it (a status bar's padding). The rim is [LiquidEdge.rimPx] for the
 * Bar role's recipe at the pane's size.
 */
@Composable
internal fun Modifier.kitTopPaneReport(pane: KitTopPane, insetX: Int, insetY: Int, skipTop: () -> Int = { 0 }): Modifier {
    val density = LocalDensity.current.density
    val recipe = LiquidEdge.recipe(LiquidRole.Bar, dark = true)
    DisposableEffect(pane) {
        onDispose {
            pane.bottom = Float.NaN
            pane.rim = 0f
        }
    }
    return onGloballyPositioned { c ->
        val iy = insetY * density
        val w = c.size.width - 2 * insetX * density
        val h = c.size.height - skipTop() - 2 * iy
        pane.bottom = c.positionInRoot().y + c.size.height - iy
        pane.rim = LiquidEdge.rimPx(minOf(w, h).coerceAtLeast(0f), density, recipe)
    }
}
