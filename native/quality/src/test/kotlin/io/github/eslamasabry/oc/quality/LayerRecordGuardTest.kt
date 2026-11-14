package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Device 2026-11-14 (Glass): a GraphicsLayer drawn inside its own recording, through the joined "Jump to latest" pill,
 * overflowed the renderer's stack on the first scroll up a long conversation. The kit's guard (`KitLayerCycles`) sees a
 * cycle only where the recording and the draw go through its helpers (`kitRecord`, `kitDrawLayer` in
 * `KitLayerRecord.kt`), so every UI file records and draws its layers through them; a raw `layer.record` or `drawLayer`
 * would be a blind spot.
 */
class LayerRecordGuardTest {
    private val root = File(System.getProperty("oc.root"))

    /** The helpers' own file: the only place that records and draws a layer directly. */
    private val allowed = setOf("KitLayerRecord.kt")

    /** Receivers of a `record` that is not a layer's. */
    private val notLayers = setOf("KitThemeMetrics", "store", "recentModels")

    private val draw = Regex("""(?<![\w.])drawLayer\(""")
    private val record = Regex("""(?<![\w.])([A-Za-z_][\w.]*)\.record\s*[({]""")

    private fun hits(path: String, text: String): List<Int> =
        if (path.substringAfterLast('/') in allowed) emptyList()
        else text.lines().mapIndexedNotNull { i, raw0 ->
            val line = raw0.substringBefore("//").trim()
            when {
                line.isEmpty() || line.startsWith("*") || line.startsWith("/*") || line.startsWith("import ") -> null
                line.startsWith("fun ") || " fun " in line -> null
                draw.containsMatchIn(line) -> i + 1
                record.findAll(line).any { it.groupValues[1].substringBefore('.') !in notLayers } -> i + 1
                else -> null
            }
        }

    @Test
    fun `LAYER-GUARD every UI file records and draws its GraphicsLayers through the kit's tracked helpers`() {
        val files = BuildRestGates.production(root, "ui").filter { (_, t) -> "GraphicsLayer" in t || "drawLayer" in t }
        assertTrue(files.size > 10, "scanned ${files.size} files")
        val bad = files.flatMap { (p, t) -> hits(p, t).map { "$p:$it" } }
        assertTrue(bad.isEmpty(), "record with kitRecord and draw with kitDrawLayer (KitLayerRecord.kt):\n" + bad.joinToString("\n"))
    }

    /**
     * The density each `record(` in the helpers' own file starts with. Device 2026-11-14 (Glass, Back from a conversation):
     * the framework's `DrawScope.record(size) { }` shorthand hands the recording the node's shared draw scope as its
     * density and runs the block on that scope with it, so the scope's density was itself and a `toPx()` in the block
     * overflowed the main thread's stack. The helpers record with a plain copy (`kitDensityOf`) named `snapshot`.
     */
    private fun recordDensities(text: String): List<String> =
        text.lines().mapNotNull { raw ->
            val line = raw.substringBefore("//").trim()
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("/*")) null
            else Regex("""(?<![\w])record\(\s*([^,)]*)""").find(line)?.groupValues?.get(1)?.trim()
        }

    @Test
    fun `LAYER-GUARD the kit's recordings take a plain copy of the density, never a draw scope`() {
        val helpers = BuildRestGates.production(root, "ui").entries.single { (p, _) -> p.substringAfterLast('/') == "KitLayerRecord.kt" }
        val densities = recordDensities(helpers.value)
        assertTrue(densities.size >= 2, "both helpers record: $densities")
        assertTrue(densities.all { it == "snapshot" }, "record with the snapshot density (kitDensityOf), not this scope or its size: $densities")
        assertTrue("fun kitDensityOf(density: Density): Density = Density(density.density, density.fontScale)" in helpers.value)
    }

    @Test
    fun `LAYER-GUARD a recording that takes the scope's size or a passed density is caught`() {
        val planted =
            """
            KitLayerCycles.tracked(layer, { inner -> layer.record(size, inner) }, block)
            KitLayerCycles.tracked(this, { inner -> record(density, layoutDirection, size, inner) }, block)
            layer.record(snapshot, direction, size) {
            // layer.record(size) in a comment
            """.trimIndent()
        assertEquals(listOf("size", "density", "snapshot"), recordDensities(planted))
    }

    @Test
    fun `LAYER-GUARD planted raw records and draws are caught, the helpers and comments are not`() {
        val planted =
            """
            layer.record { drawContent() }
            // drawLayer(layer) in a comment
            translate(x, y) { drawLayer(state.layer) }
            blur.record(Density(1f), dir, size) {
            kitRecord(layer) { kitDrawLayer(other) }
            KitThemeMetrics.record(ms)
            """.trimIndent()
        assertEquals(listOf(1, 3, 4), hits("ui/kit/x/Pane.kt", planted))
        assertEquals(emptyList(), hits("ui/kit/foundation/KitLayerRecord.kt", planted))
    }
}
