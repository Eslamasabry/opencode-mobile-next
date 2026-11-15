@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.ui.graphics.toAwtImage
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Live glass proof pictures and numbers ([LiveGlassFrames]): every frame of each scene, numbered, for today's join and
 * the live one. Not a test: it runs only when `LIVE_GLASS_FRAMES_OUT` names a folder (`LIVE_GLASS_VARIANTS`: `today`,
 * `live` or both, comma-separated; `LIVE_GLASS_DENSITY`: px per dp, 1.5 by default) and writes there
 * `<variant>/<scene>/<nnn>-<ms>ms.png` (the bar and the menu, the frame's number, time and state on it),
 * `<variant>-<scene>.csv` (every measure of every frame, and what the frame cost to draw) and `summary.txt` (the steps
 * found, the frame cost).
 */
class LiveGlassFramesRenders {
    @Test
    fun `live glass frames`() {
        val out = System.getenv("LIVE_GLASS_FRAMES_OUT")?.let(::File) ?: return
        val variants = (System.getenv("LIVE_GLASS_VARIANTS") ?: "today,live").split(',').map { v -> LiveGlassFrames.Variant.entries.first { it.key == v.trim() } }
        val density = System.getenv("LIVE_GLASS_DENSITY")?.toFloatOrNull() ?: 1.5f
        out.mkdirs()
        val summary = StringBuilder()
        for (variant in variants) for (scene in LiveGlassFrames.scenes) {
            val frames =
                try {
                    LiveGlassFrames.run(variant, scene, density, keepImages = true)
                } catch (e: Throwable) {
                    summary.append("${variant.key} ${scene.name}: could not draw: ${e::class.simpleName}: ${e.message?.take(300)}\n")
                    continue
                }
            val dir = File(out, "${variant.key}/${scene.name}").also { it.mkdirs() }
            val menuBottom = frames.maxOf { it.glassBottomDp }
            for (f in frames) {
                val full = f.image!!.toAwtImage()
                val h = ((menuBottom + 28) * density).toInt().coerceAtMost(full.height)
                val crop = BufferedImage(full.width, h, BufferedImage.TYPE_INT_RGB)
                val g = crop.createGraphics()
                g.drawImage(full, 0, 0, null)
                g.font = Font(Font.MONOSPACED, Font.BOLD, (11 * density).toInt())
                val label = "${variant.key} ${scene.name} #%03d %4d ms %s%s".format(f.index, f.ms, if (f.open) "open" else "closed", if (f.joined) " joined" else "")
                g.color = java.awt.Color(0, 0, 0, 170)
                g.fillRect(0, h - (16 * density).toInt(), full.width, (16 * density).toInt())
                g.color = java.awt.Color.WHITE
                g.drawString(label, (6 * density).toInt(), h - (4 * density).toInt())
                g.dispose()
                ImageIO.write(crop, "png", File(dir, "%03d-%04dms.png".format(f.index, f.ms)))
            }
            File(out, "${variant.key}-${scene.name}.csv").writeText(
                buildString {
                    append("frame,ms,open,joined,bar_light,bar_sharp,bar_end,menu_middle,menu_rim,column_sharp,glass_bottom_dp,glass_left_dp,cost_ms\n")
                    for (f in frames)
                        append(
                            "${f.index},${f.ms},${f.open},${f.joined},%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.2f,%.2f,%.1f\n".format(
                                f.barLight,
                                f.barSharp,
                                f.barEnd,
                                f.menuMiddle,
                                f.menuRim,
                                f.columnSharp,
                                f.glassBottomDp,
                                f.glassLeftDp,
                                f.costMs,
                            )
                        )
                }
            )
            val costs = frames.drop(1).map { it.costMs }.sorted()
            fun pct(p: Double) = costs[((costs.size - 1) * p).toInt()]
            summary.append("${variant.key} ${scene.name}: ${frames.size} frames, draw cost ms p50 %.1f p90 %.1f max %.1f\n".format(pct(.5), pct(.9), costs.last()))
            val stalls = LiveGlassFrames.stalls(frames)
            summary.append("  stalls: ${if (stalls.isEmpty()) "none" else stalls.joinToString { (i, n, kind) -> "#%03d %d frames %s".format(i, n, kind) }}\n")
            for ((name, tol, value) in measures) {
                // Scrolling moves the page under the glass: where the glass is is read against the still page, so not there.
                if (scene.scrollDpPerFrame != 0f && name.startsWith("glass")) continue
                val steps = LiveGlassFrames.steps(frames, tol, value)
                summary.append("  $name steps: ${if (steps.isEmpty()) "none" else steps.joinToString { (i, d) -> "#%03d %+.2f".format(i, d) }}\n")
            }
        }
        File(out, "summary.txt").writeText(summary.toString())
    }

    private val measures: List<Triple<String, Double, (LiveGlassFrames.Frame) -> Double>> =
        listOf(
            Triple("bar light", .3, { f: LiveGlassFrames.Frame -> f.barLight }),
            Triple("bar sharpness", .15, { f: LiveGlassFrames.Frame -> f.barSharp }),
            Triple("bar end light", .3, { f: LiveGlassFrames.Frame -> f.barEnd }),
            Triple("menu middle light", .5, { f: LiveGlassFrames.Frame -> f.menuMiddle }),
            Triple("menu rim light", .5, { f: LiveGlassFrames.Frame -> f.menuRim }),
            Triple("column sharpness", .15, { f: LiveGlassFrames.Frame -> f.columnSharp }),
            Triple("glass bottom dp", 1.5, { f: LiveGlassFrames.Frame -> f.glassBottomDp }),
            Triple("glass left dp", 1.5, { f: LiveGlassFrames.Frame -> f.glassLeftDp }),
        )
}
