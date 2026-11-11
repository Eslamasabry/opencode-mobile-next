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
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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

/** The screen's recording [source] as the bottom block's glass sees it: [faded], cleared under the words pane. */
@Stable
internal class KitWordsFade(val source: KitFrostState, val faded: KitFrostState) {
    private val brush = EdgeBrush()

    /**
     * Records [faded] again from [source] (read here, so this runs after each recording of the body), cleared below the
     * words pane's top edge as [bottom] reports it. Records nothing onto this scope's canvas.
     */
    fun DrawScope.record(bottom: KitScreenBottom) {
        source.version
        val top = bottom.wordsTop
        val rim = bottom.wordsRim
        val from = source.layer
        val origin = source.sourceOrigin
        faded.sourceOrigin = origin
        // Nothing recorded yet (the body's first frame): nothing to see.
        if (from.size.width <= 0 || from.size.height <= 0) return
        faded.layer.record(this, layoutDirection, from.size) {
            drawLayer(from)
            if (!top.isNaN() && rim > 0f) {
                val h = size.height
                // On whole pixels (wholePx): a clearing gradient across a fractional pixel edge crashed the emulator's
                // renderer (KitScrollEdges.kt).
                val y0 = wholePx(top - origin.y).coerceIn(0f, h)
                val y1 = wholePx(top - origin.y + rim).coerceIn(0f, h)
                if (y1 > y0) {
                    drawRect(brush.of(0f, 1f, 1f, y0, y1), Offset(0f, y0), Size(size.width, y1 - y0), blendMode = BlendMode.DstOut)
                }
                if (y1 < h) drawRect(Color.Black, Offset(0f, y1), Size(size.width, h - y1), blendMode = BlendMode.DstOut)
            }
        }
        // The panes that read [faded] draw again (as kitFrostSource does for its own panes).
        Snapshot.withoutReadObservation { faded.version++ }
    }
}

/** A [KitWordsFade] of [source] for the bottom block of one screen. */
@Composable
internal fun rememberKitWordsFade(source: KitFrostState): KitWordsFade {
    val layer = rememberGraphicsLayer()
    return remember(source, layer) {
        // Its own offscreen pass, so the clearing clears the recording alone, never what a pane draws under it.
        layer.compositingStrategy = CompositingStrategy.Offscreen
        KitWordsFade(source, KitFrostState(layer))
    }
}

/**
 * Records [fade] before the bottom block draws (the body has recorded by then: it is drawn first), so the block's
 * glass reads this frame's content.
 */
internal fun Modifier.kitWordsFadeRecorder(fade: KitWordsFade, bottom: KitScreenBottom): Modifier =
    drawWithContent {
        with(fade) { record(bottom) }
        drawContent()
    }
