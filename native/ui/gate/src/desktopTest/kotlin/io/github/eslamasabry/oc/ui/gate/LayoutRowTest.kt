package io.github.eslamasabry.oc.ui.gate

import androidx.compose.foundation.text.BasicText
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Scoreboard 2026-11-07 item 0.1: a layout guard finding is the shot's own failing row, and the gate still renders the
 * picture. Before the fix `LayoutGuard.check` threw ahead of the render, so no native picture and no row were written.
 */
class LayoutRowTest {
    private val root = File(System.getProperty("user.dir"), "../../..").canonicalFile
    private val gate = File(root, "looks-out/gate")

    @Test
    fun `G0-01 a layout finding is recorded as its own row and the picture is still rendered`() {
        val name = "gate_selftest_layout_row"
        val row = File(gate, "layout/$name.tsv")
        val picture = File(gate, "${name}_light-native.png")
        row.delete()
        picture.delete()

        // Words flush on the window edge break the 16 dp gutter (edge-text); the shot has no Flutter golden either.
        val thrown = assertFailsWith<AssertionError> {
            FlutterGoldenGate.check(name, Frame.Screen, modes = listOf("light")) { BasicText("Flush") }
        }

        assertTrue("edge-text" in thrown.message.orEmpty(), "the layout finding is the reported failure: ${thrown.message}")
        assertTrue(thrown.suppressed.any { "missing Flutter golden" in it.message.orEmpty() }, "the picture step ran too")
        assertTrue(picture.isFile, "the native picture is rendered despite the layout finding")
        val cells = row.readText().trimEnd('\n').split('\t')
        assertEquals(name, cells[0])
        assertEquals("FAIL", cells[1])
        assertTrue("edge-text" in cells[2] && '\n' !in cells[2], "one-line detail: ${cells[2]}")
    }

    @Test
    fun `G0-02 a clean shot writes a PASS layout row`() {
        assertEquals("kit_x\tPASS\t-\n", FlutterGoldenGate.layoutRow("kit_x", null))
        assertEquals(
            "kit_x\tFAIL\tlayout guard: a b Phone edge-text \"Flush\": x 0\n",
            FlutterGoldenGate.layoutRow("kit_x", "layout guard: a\tb\n  Phone edge-text \"Flush\": x 0"),
        )
    }
}
