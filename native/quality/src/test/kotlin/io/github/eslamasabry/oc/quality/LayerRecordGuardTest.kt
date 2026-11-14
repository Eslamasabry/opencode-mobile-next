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
