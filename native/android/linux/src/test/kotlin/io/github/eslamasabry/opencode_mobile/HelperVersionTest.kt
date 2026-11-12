package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperVersionTest {
    private val tested = setOf("0.9.2", "0.11.2", "0.15.3")

    @Test fun plainVersionLineIsRead() {
        assertEquals("0.11.2", HelperVersion.parse("0.11.2\n"))
    }

    @Test fun warningLinesAroundTheVersionDoNotHideIt() {
        val output = "(node:42) ExperimentalWarning: something 20.1.0 is new\n0.15.3\nwarning: low memory\n"
        assertEquals("0.15.3", HelperVersion.parse(output))
    }

    @Test fun versionInsideASentenceIsStillFound() {
        assertEquals("0.11.2", HelperVersion.parse("paseo version 0.11.2"))
    }

    @Test fun noVersionIsNull() {
        assertNull(HelperVersion.parse("command not found"))
        assertNull(HelperVersion.parse(null))
    }

    @Test fun olderTestedHelperRuns() {
        assertTrue(HelperVersion.runs("0.11.2", "0.15.3", tested))
        assertTrue(HelperVersion.runs("0.15.3", "0.15.3", tested))
    }

    @Test fun untestedOrMissingHelperDoesNot() {
        assertFalse(HelperVersion.runs("0.10.0", "0.15.3", tested))
        assertFalse(HelperVersion.runs(null, "0.15.3", tested))
    }
}
