package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toIntSize
import io.github.eslamasabry.oc.core.common.AppLog

/*
 * Every picture the kit records into a GraphicsLayer, and every such layer it draws, goes through here (device,
 * 2026-11-14: the app died in Glass on the first upward scroll of a long conversation, a native stack overflow on the
 * RenderThread, RenderNode::prepareTreeImpl calling itself 500 frames deep). A layer whose recording draws itself, at
 * once or through layers it draws, is a cycle of render nodes: hwui walks it for ever and the process dies, with no
 * Kotlin exception and nothing a desktop test can see. The kit keeps, per thread, the layers being recorded right now,
 * what each layer's last recording drew, and which recordings each layer was last recorded inside: a node's own layer
 * (a pill's) is re-recorded alone on later frames, outside the page it lies in, yet it is still drawn there. A layer about
 * to be drawn that reaches, through what it drew, a recording this draw lands in is that cycle. Debug, test and preview
 * builds throw [KitRuleBroken]; a release build leaves that one draw out and logs the rule once, so a cycle costs a
 * picture, never the app.
 */

private val log = AppLog.tagged("kit")

/** One recording in progress: its layer and the kit layers drawn into it so far. */
internal class KitLayerRecording(val layer: GraphicsLayer) {
    val drew: ArrayList<GraphicsLayer> = ArrayList(4)
}

/**
 * One thread's recordings: the stack in progress, what each layer's last recording drew, and the recordings each layer
 * was last recorded inside (kept when it is recorded again alone: its node still lies in them).
 */
internal class KitLayerThread {
    val stack: ArrayList<KitLayerRecording> = ArrayList(4)
    val drew: MutableMap<GraphicsLayer, List<GraphicsLayer>> = kitWeakLayerMap()
    val inside: MutableMap<GraphicsLayer, List<GraphicsLayer>> = kitWeakLayerMap()
}

/** This thread's recordings ([KitLayerThread]); drawing runs on one thread per window. */
internal expect fun kitLayerThread(): KitLayerThread

/** A map whose keys go when nothing else holds them (a released layer is never kept alive by what it once drew). */
internal expect fun kitWeakLayerMap(): MutableMap<GraphicsLayer, List<GraphicsLayer>>

/** The kit's guard against a layer drawn inside its own recording (see the file comment). */
internal object KitLayerCycles {
    /** Throw on a cycle (debug, test and preview builds: `KitTheme` sets it from `KitPlatform.isDebug`). */
    var strict: Boolean = false

    private var logged = false

    /**
     * The layers [layer] would close a cycle through if drawn into the recordings in progress on this thread: the path
     * from it to a layer being recorded, [layer] first; null when drawing it is safe.
     */
    fun cycleThrough(layer: GraphicsLayer): List<GraphicsLayer>? {
        val t = kitLayerThread()
        if (t.stack.isEmpty()) return null
        val lands = landsIn(t)
        val path = ArrayList<GraphicsLayer>()
        val seen = ArrayList<GraphicsLayer>()
        return if (reaches(t, layer, lands, path, seen)) path else null
    }

    /** Every recording a draw now lands in: the ones in progress and, up the tree, the ones each was last recorded inside. */
    private fun landsIn(t: KitLayerThread): List<GraphicsLayer> {
        val out = ArrayList<GraphicsLayer>()
        val todo = ArrayList<GraphicsLayer>()
        t.stack.forEach { todo += it.layer }
        while (todo.isNotEmpty()) {
            val l = todo.removeAt(todo.lastIndex)
            if (out.any { it === l } || l.isReleased) continue
            out += l
            todo += t.inside[l].orEmpty()
        }
        return out
    }

    private fun reaches(
        t: KitLayerThread,
        at: GraphicsLayer,
        lands: List<GraphicsLayer>,
        path: ArrayList<GraphicsLayer>,
        seen: ArrayList<GraphicsLayer>,
    ): Boolean {
        if (seen.any { it === at }) return false
        seen += at
        // A released layer draws nothing any more, whatever its last recording held.
        if (at.isReleased) return false
        path += at
        if (lands.any { it === at }) return true
        for (n in t.drew[at].orEmpty()) if (reaches(t, n, lands, path, seen)) return true
        path.removeAt(path.lastIndex)
        return false
    }

    /** Whether [layer] is being recorded on this thread right now. */
    fun recordingNow(t: KitLayerThread, layer: GraphicsLayer): Boolean = t.stack.any { it.layer === layer }

    /** Reports a cycle: throws in strict builds, else logs the rule once. */
    fun broken(path: List<GraphicsLayer>, recording: Boolean) {
        val what =
            if (recording) "a GraphicsLayer is recorded again inside its own recording"
            else if (path.size <= 1) "a GraphicsLayer is drawn inside its own recording"
            else "a GraphicsLayer is drawn inside the recording of a layer it draws (through ${path.size - 1} more)"
        val message = "KitLayerCycles: $what: its render node would draw itself and the renderer would recurse until its stack overflows"
        if (strict) throw KitRuleBroken(message)
        if (!logged) {
            logged = true
            log.w { message }
        }
    }

    /**
     * Opens [layer]'s recording through [record] and runs [block] in it on this thread's stack; once it ends, what it drew
     * is what the layer draws. Nothing is recorded when [layer] is being recorded already (a recording inside itself).
     */
    fun tracked(layer: GraphicsLayer, record: (DrawScope.() -> Unit) -> Unit, block: DrawScope.() -> Unit) {
        val t = kitLayerThread()
        if (recordingNow(t, layer)) {
            broken(listOf(layer), recording = true)
            return
        }
        // Recorded inside others: it lies in them until it is recorded inside others again.
        if (t.stack.isNotEmpty()) t.inside[layer] = t.stack.map { it.layer }
        record {
            val rec = KitLayerRecording(layer)
            t.stack += rec
            try {
                block()
            } finally {
                t.stack.removeAt(t.stack.lastIndex)
                t.drew[layer] = rec.drew
            }
        }
    }
}

/**
 * Records [block] into [layer] at [size] through this scope, tracked by [KitLayerCycles]. As the framework's
 * `DrawScope.record` shorthand does, [block] runs on this scope with its canvas swapped for the recording's, so
 * `drawContent()` still works inside it; unlike the shorthand, the recording's density is a plain copy of this scope's
 * ([kitDensityOf]), never the scope itself.
 *
 * Device 2026-11-14 (Glass, Back from a conversation just after its "…" menu closed): a StackOverflowError on the main
 * thread, `CanvasDrawScope.getDensity` and `LayoutNodeDrawScope.getDensity` calling each other. The shorthand hands the
 * recording this scope as its density, then runs the block on this scope with the recording's density, so the window's
 * one shared `LayoutNodeDrawScope` took itself as its own density: any `density` or `toPx()` read directly in the block
 * (the joined menu's side of the glass, `kitModalView` measuring the menu's edges) chased itself for ever. The layer also
 * kept the scope as its density, so re-making its display list later did the same.
 */
internal fun DrawScope.kitRecord(layer: GraphicsLayer, size: IntSize = this.size.toIntSize(), block: DrawScope.() -> Unit) {
    val outer = this
    val snapshot = kitDensityOf(this)
    val direction = layoutDirection
    KitLayerCycles.tracked(
        layer,
        { inner ->
            layer.record(snapshot, direction, size) {
                val c = drawContext
                outer.draw(c.density, c.layoutDirection, c.canvas, c.size, c.graphicsLayer, inner)
            }
        },
        block,
    )
}

/**
 * Records [block] into this layer in its own draw scope ([density], [layoutDirection], [size]), tracked by
 * [KitLayerCycles]. [density] is copied first ([kitDensityOf]): a draw scope passed as the density would be read, by
 * this recording and by every later re-making of it, through whatever that shared scope points at then.
 */
internal fun GraphicsLayer.kitRecord(density: Density, layoutDirection: LayoutDirection, size: IntSize, block: DrawScope.() -> Unit) {
    val snapshot = kitDensityOf(density)
    KitLayerCycles.tracked(this, { inner -> record(snapshot, layoutDirection, size, inner) }, block)
}

/** A plain copy of [density]'s values: what every kit recording takes as its density, never a draw scope. */
internal fun kitDensityOf(density: Density): Density = Density(density.density, density.fontScale)
/**
 * Draws [layer] here, unless that would close a cycle of recordings ([KitLayerCycles]): then debug, test and preview
 * builds throw and a release build leaves it out. Inside a kit recording it is noted as drawn by that recording.
 */
internal fun DrawScope.kitDrawLayer(layer: GraphicsLayer) {
    val t = kitLayerThread()
    if (t.stack.isNotEmpty()) {
        val cycle = KitLayerCycles.cycleThrough(layer)
        if (cycle != null) {
            KitLayerCycles.broken(cycle, recording = false)
            return
        }
        val drew = t.stack.last().drew
        if (drew.none { it === layer }) drew += layer
    }
    drawLayer(layer)
}
