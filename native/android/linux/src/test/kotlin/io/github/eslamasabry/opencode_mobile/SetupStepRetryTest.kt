package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStepRetryTest {
    @Test fun aSignalledStepIsAnInterruption() {
        assertTrue(SetupStepRetry.interrupted("proot info: vpid 1: terminated with signal 15"))
        assertTrue(SetupStepRetry.interrupted("proot info: vpid 1: terminated with signal 9"))
    }

    @Test fun aScriptsOwnFailureIsNot() {
        assertFalse(SetupStepRetry.interrupted(null))
        assertFalse(SetupStepRetry.interrupted("E: Unable to fetch some archives"))
        assertFalse(SetupStepRetry.interrupted("exit 100"))
        assertFalse(SetupStepRetry.interrupted("terminated with signal 11"))
        assertFalse(SetupStepRetry.interrupted("terminated with signal 150"))
    }

    @Test fun anInterruptedStepRunsAgainAtMostTwice() {
        assertTrue(SetupStepRetry.again(attempt = 1, cancelled = false, interrupted = true))
        assertTrue(SetupStepRetry.again(attempt = 2, cancelled = false, interrupted = true))
        assertFalse(SetupStepRetry.again(attempt = SetupStepRetry.ATTEMPTS, cancelled = false, interrupted = true))
    }

    @Test fun aCancelOrARealFailureIsNeverRetried() {
        assertFalse(SetupStepRetry.again(attempt = 1, cancelled = true, interrupted = true))
        assertFalse(SetupStepRetry.again(attempt = 1, cancelled = false, interrupted = false))
    }

    @Test fun aSignalledGuestWithoutAResultIsReportedAsTheInterruption() {
        // PRoot exits zero when its guest is signalled; its last line says so.
        val signalled = "proot info: vpid 1: terminated with signal 15"
        assertEquals(signalled, SetupStepRetry.missingResult(signalled))
        assertTrue(SetupStepRetry.interrupted(SetupStepRetry.missingResult(signalled)))
    }

    @Test fun aScriptThatEndedEarlyStaysAPlainFailure() {
        val plain = "The setup step could not finish. Try again."
        assertEquals(plain, SetupStepRetry.missingResult(null))
        assertEquals(plain, SetupStepRetry.missingResult("Unpacking git (1:2.43.0) ..."))
        assertFalse(SetupStepRetry.interrupted(SetupStepRetry.missingResult(null)))
    }
}
