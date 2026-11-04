package io.github.eslamasabry.oc.engine.termuxhost

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** test/termux_healing_arm_test.dart and test/termux_recovery_scripts_test.dart, run for real. */
class TermuxRecoveryScriptsTest {
    @AfterTest
    fun cleanUp() = Shell.cleanUp()

    private fun fixture(template: String, scenario: String): Shell.Result {
        val home = Shell.temp("oc-recovery-$scenario-")
        val script = File(home, "fixture.sh")
        script.writeText(Shell.fill(template, "functions" to Shell.managerFunctions(), "scenario" to scenario))
        return Shell.bashFile(script, env = mapOf("HOME" to home.path))
    }

    @Test
    fun `B-LINUX-232 first recovery arm admits only a confirmed crash with no live owner`() {
        val template = Shell.resource("healing-arm.sh")
        for (scenario in listOf(
            "crash", "recovery", "stopped", "setup-failed", "port-busy", "live-pid", "different-port", "switch-pending",
        )) {
            val result = fixture(template, scenario)
            assertEquals(if (scenario == "crash" || scenario == "recovery") 0 else 75, result.exitCode, "$scenario: ${result.stderr}")
        }
    }

    @Test
    fun `B-LINUX-232 storage accepts checked counts and rejects malformed or impossible responses`() {
        val snapshot = TermuxStorageSnapshot.parse("total_kib=1048576\navailable_kib=524288\n")!!
        assertEquals(1073741824L, snapshot.totalBytes)
        assertEquals(536870912L, snapshot.availableBytes)
        for (value in listOf(
            "",
            "total_kib=12",
            "total_kib=0\navailable_kib=0",
            "total_kib=1\navailable_kib=2",
            "total_kib=1\navailable_kib=-1",
            "total_kib=1\navailable_kib=0\navailable_kib=1",
            "total_kib=1\navailable_kib=0\nother=secret",
        )) {
            assertNull(TermuxStorageSnapshot.parse(value), value)
        }
    }

    @Test
    fun `B-LINUX-232 recovery scripts validate syntax and shell identities`() {
        for (text in listOf(
            TermuxScripts.storageScript(),
            TermuxScripts.managerScript,
            TermuxScripts.recoveryControlScript("permit-1", enable = true),
            TermuxScripts.recoveryControlScript("permit-1", enable = false),
            TermuxScripts.restartScript(operationId = "attempt-1", recoveryToken = "permit-1", expectedOperationId = ""),
        )) {
            val result = Shell.syntax("bash", text)
            assertEquals(0, result.exitCode, result.stderr)
        }
        assertFailsWith<IllegalArgumentException> { TermuxScripts.recoveryControlScript("x'; touch /tmp/no", enable = true) }
        assertFailsWith<IllegalArgumentException> {
            TermuxScripts.restartScript(operationId = "one", recoveryToken = "token", expectedOperationId = "';bad")
        }
    }

    @Test
    fun `B-LINUX-232 executed preflight refuses a changed operation, revoked permit, live PID and occupied port`() {
        val template = Shell.resource("recovery-preflight.sh")
        for (scenario in listOf("allowed", "operation", "permit", "live", "port", "setup-failed", "stopped")) {
            val result = fixture(template, scenario)
            assertEquals(if (scenario == "allowed") 0 else 75, result.exitCode, "$scenario: ${result.stderr}")
        }
    }

    @Test
    fun `B-LINUX-232 executed disarm revokes before cancelling its owned startup and retries incomplete cancellation`() {
        val result = fixture(Shell.resource("recovery-disarm.sh"), "disarm")
        assertEquals(0, result.exitCode, result.stderr)
        assertEquals("setup\nserver\n", result.stdout)
    }
}
