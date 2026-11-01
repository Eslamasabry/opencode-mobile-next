package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupScriptCompletionTest {
    private fun run(script: String): Pair<Int, SetupScriptCompletion> {
        val completion = SetupScriptCompletion()
        val process = ProcessBuilder("/bin/sh", "-c", completion.wrap(script)).redirectErrorStream(true).start()
        process.outputStream.close()
        process.inputStream.bufferedReader().forEachLine { completion.accept(it) }
        return process.waitFor() to completion
    }

    @Test fun successWithoutTrailingNewlineHasExplicitResult() {
        val (code, completion) = run("set -eu\nprintf '%s' \"a'b\"")
        assertEquals(0, code)
        assertEquals(0, completion.exitCode)
    }

    @Test fun errexitAndExplicitExitRetainTheirFailure() {
        for ((script, expected) in listOf("set -e\nfalse\necho unreachable" to 1, "exit 127" to 127)) {
            val (code, completion) = run(script)
            assertEquals(expected, code)
            assertEquals(expected, completion.exitCode)
        }
    }

    @Test fun signalledGuestCannotProduceSuccess() {
        val (code, completion) = run("kill -TERM \$\$")
        assertEquals(143, code)
        assertEquals(143, completion.exitCode)
    }

    @Test fun missingDuplicateMalformedAndOtherInvocationRecordsCannotPass() {
        val completion = SetupScriptCompletion("current")
        assertNull(completion.exitCode)
        assertFalse(completion.accept("::oc-setup-result previous 0"))
        assertNull(completion.exitCode)
        assertTrue(completion.accept("::oc-setup-result current 0"))
        assertEquals(0, completion.exitCode)
        assertTrue(completion.accept("::oc-setup-result current 0"))
        assertNull(completion.exitCode)
        for (code in listOf("garbage", "256", "-1")) {
            val invalid = SetupScriptCompletion("current")
            assertTrue(invalid.accept("::oc-setup-result current $code"))
            assertNull(invalid.exitCode)
        }
    }
}
