package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperVersionTest {
    private val tested = setOf("0.9.2", "0.11.2", "0.15.3", "0.15.4", "0.16.1", "0.17.1")

    @Test fun plainVersionLineIsRead() {
        assertEquals("0.11.2", HelperVersion.parse("0.11.2\n"))
    }

    @Test fun warningLinesAroundTheVersionDoNotHideIt() {
        val output = "(node:42) ExperimentalWarning: something 20.1.0 is new\n0.17.1\nwarning: low memory\n"
        assertEquals("0.17.1", HelperVersion.parse(output))
    }

    @Test fun versionInsideASentenceIsStillFound() {
        assertEquals("0.11.2", HelperVersion.parse("paseo version 0.11.2"))
    }

    @Test fun noVersionIsNull() {
        assertNull(HelperVersion.parse("command not found"))
        assertNull(HelperVersion.parse(null))
    }

    @Test fun olderTestedHelperRuns() {
        assertTrue(HelperVersion.runs("0.11.2", "0.17.1", tested))
        assertTrue(HelperVersion.runs("0.17.1", "0.17.1", tested))
    }

    @Test fun untestedOrMissingHelperDoesNot() {
        assertFalse(HelperVersion.runs("0.10.0", "0.17.1", tested))
        assertFalse(HelperVersion.runs(null, "0.17.1", tested))
    }

    // Issue 147: a version check that gave no answer (cold, busy phone) was "helper update required", never retried.
    @Test fun `I147-AGY-11 a silent check of an installed helper is a start to try again, not an update`() {
        assertEquals(HelperVersion.Verdict.NoAnswer, HelperVersion.verdict(null, installed = true, "0.17.1", tested))
        assertEquals(HelperVersion.Verdict.UpdateRequired, HelperVersion.verdict(null, installed = false, "0.17.1", tested))
        assertEquals(HelperVersion.Verdict.UpdateRequired, HelperVersion.verdict("0.10.0\n", installed = true, "0.17.1", tested))
        assertEquals(HelperVersion.Verdict.UpdateRequired, HelperVersion.verdict("garbage", installed = true, "0.17.1", tested))
        assertEquals(HelperVersion.Verdict.Runs, HelperVersion.verdict("0.17.1\n", installed = true, "0.17.1", tested))
    }
}
