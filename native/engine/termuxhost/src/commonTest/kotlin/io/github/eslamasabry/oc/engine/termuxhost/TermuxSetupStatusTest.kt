package io.github.eslamasabry.oc.engine.termuxhost

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** test/termux_scripts_test.dart: status parsing, snapshot separation, success rule. */
class TermuxSetupStatusTest {
    @Test
    fun `B-LINUX-242 setup clock tolerates legacy and malformed timestamps`() {
        assertNull(TermuxSetupStatus.parse("phase=preparing")!!.startedAtEpochSeconds)
        for (value in listOf("", "unknown", "-1", "1.5", "0xff", "+1", "999999999999999999999999")) {
            assertNull(TermuxSetupStatus.parse("phase=preparing\nstarted_at=$value")!!.startedAtEpochSeconds, value)
        }
        assertEquals(0L, TermuxSetupStatus.parse("started_at=0")!!.startedAtEpochSeconds)
        assertEquals(1788800000L, TermuxSetupStatus.parse("started_at=1788800000")!!.startedAtEpochSeconds)
    }

    @Test
    fun `B-LINUX-242 setup status parses persisted manager state`() {
        val status =
            TermuxSetupStatus.parse(
                "phase=ready\nmessage=OpenCode is ready\nport=4096\nrunner=proot\nversion=1.2.3\npid=1234\n" +
                    "operation=restart-1\noperation_result=completed\n",
            )!!
        assertTrue(status.isReady)
        assertFalse(status.isRunning)
        assertEquals(4096, status.port)
        assertEquals("1.2.3", status.version)
        assertEquals(1234, status.pid)
        assertEquals("restart-1", status.operationId)
        assertEquals("completed", status.operationResult)
    }

    @Test
    fun `B-LINUX-242 restart is treated as a running managed operation`() {
        val status =
            TermuxSetupStatus.parse(
                "phase=restarting\nmessage=Restarting the local server\nport=4096\nrunner=proot\nversion=1.2.3\npid=1234\n",
            )!!
        assertTrue(status.isRunning)
        assertFalse(status.isReady)
    }

    @Test
    fun `B-LINUX-242 setup snapshot keeps terminal output separate from status`() {
        val snapshot =
            TermuxSetupSnapshot.parse(
                "phase=installing_opencode\nmessage=Installing OpenCode\nport=4096\nrunner=proot\nversion=\npid=1234\n" +
                    "__OC_SETUP_OUTPUT__\nUnpacking nodejs...\nnpm timing idealTree=7432\nphase=failed\n" +
                    "message=This belongs to the terminal\n",
            )!!
        assertEquals("installing_opencode", snapshot.status.phase)
        assertEquals(1234, snapshot.status.pid)
        assertTrue(snapshot.output.contains("Unpacking nodejs..."))
        assertTrue(snapshot.output.contains("idealTree=7432"))
        assertTrue(snapshot.output.contains("phase=failed"))
        assertEquals("Installing OpenCode", snapshot.status.message)
    }

    @Test
    fun `B-LINUX-242 Android RESULT_OK and shell exit zero indicate success`() {
        val result =
            TermuxCommandResult.fromMap(
                mapOf("stdout" to "ok", "stderr" to "", "exitCode" to 0, "err" to -1, "errorMessage" to ""),
            )
        assertTrue(result.successful)
        assertFalse(TermuxCommandResult.fromMap(mapOf("exitCode" to 0, "err" to 2)).successful)
        assertFalse(TermuxCommandResult.fromMap(mapOf("exitCode" to 1, "err" to -1)).successful)
    }
}
