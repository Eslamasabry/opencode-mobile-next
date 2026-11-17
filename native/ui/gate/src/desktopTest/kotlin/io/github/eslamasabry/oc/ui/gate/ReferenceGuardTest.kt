package io.github.eslamasabry.oc.ui.gate

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.AssumptionViolatedException

/**
 * B-CI-312: a guard whose reference picture is missing never reads as a pass. Where the lane fetches the pictures
 * (`full`, `candidate`, `OC_REFERENCES=required`) it FAILS with a plain message; anywhere else it is SKIPPED (a JUnit
 * assumption, never a pass) and prints the line the CI report counts.
 */
class ReferenceGuardTest {
    private val absent = "gate_selftest_reference_that_does_not_exist"

    private fun printed(block: () -> Unit): String {
        val saved = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        try {
            block()
        } finally {
            System.setOut(saved)
        }
        return buffer.toString()
    }

    @Test
    fun `B-CI-312 the lanes that fetch their references are the ones that must have them`() {
        assertTrue(ReferenceGuard.required(mapOf("LANE" to "candidate")))
        assertTrue(ReferenceGuard.required(mapOf("LANE" to "full")))
        assertTrue(ReferenceGuard.required(mapOf("OC_REFERENCES" to "required")))
        assertEquals(false, ReferenceGuard.required(mapOf("LANE" to "fast")))
        assertEquals(false, ReferenceGuard.required(emptyMap()))
    }

    @Test
    fun `B-CI-312 a missing reference fails with a plain message in a lane that fetches it`() {
        val e = assertFailsWith<AssertionError> { ReferenceGuard.missing("picture x_light", mapOf("LANE" to "candidate")) }
        assertEquals("reference picture x_light is missing: this lane must fetch it (native/tool/design/fetch_all_goldens.sh)", e.message)
    }

    @Test
    fun `B-CI-312 a missing reference is a counted skip, never a pass, where nothing fetches it`() {
        val out = printed {
            assertFailsWith<AssumptionViolatedException> { ReferenceGuard.missing("picture x_light", emptyMap()) }
        }
        assertTrue(out.lines().any { it == "OC-GUARD-SKIPPED picture x_light" }, out)
    }

    @Test
    fun `B-CI-312 the gate itself skips, with the count line, when its picture is missing and the lane does not fetch it`() {
        // A test run by hand or in the fast/custom lane has no LANE; in a references-required lane the gate fails instead.
        if (ReferenceGuard.required()) {
            assertFailsWith<AssertionError> {
                FlutterGoldenGate.check(absent, Frame.Part, modes = listOf("light")) { Box(Modifier) }
            }
        } else {
            val out = printed {
                assertFailsWith<AssumptionViolatedException> {
                    FlutterGoldenGate.check(absent, Frame.Part, modes = listOf("light")) { Box(Modifier) }
                }
            }
            assertTrue("OC-GUARD-SKIPPED picture ${absent}_light" in out, out)
        }
    }
}
