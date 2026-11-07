package io.github.eslamasabry.oc.ui.gate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The device script's numbers (native/config/align/spec.json) are the kit tokens the JVM layer uses. */
class AlignSpecFileTest {
    private val file = File(System.getProperty("user.dir"), "../../config/align/spec.json").canonicalFile

    private fun numbers(key: String): List<Float> {
        val text = file.readText()
        val m = Regex("\"$key\"\\s*:\\s*(\\[[^]]*]|[-0-9.]+)").find(text) ?: error("$key missing from $file")
        return Regex("[-0-9.]+").findAll(m.groupValues[1]).map { it.value.toFloat() }.toList()
    }

    @Test
    fun `spec json matches the kit tokens`() {
        val s = AlignRules.Spec()
        assertEquals(listOf(s.gutter), numbers("gutter"))
        assertEquals(listOf(s.rail), numbers("rail"))
        assertEquals(s.leadColumns, numbers("leadColumns"))
        assertEquals(listOf(s.iconTile), numbers("iconTile"))
        assertEquals(s.iconSizes, numbers("iconSizes"))
        assertEquals(listOf(s.grid), numbers("grid"))
        assertEquals(listOf(s.lineGap), numbers("lineGap"))
        assertEquals(listOf(s.stepMax), numbers("stepMax"))
        assertEquals(listOf(s.target), numbers("target"))
        assertEquals(listOf(s.slack), numbers("slack"))
        assertEquals(listOf(s.labelReach), numbers("labelReach"))
    }
}
