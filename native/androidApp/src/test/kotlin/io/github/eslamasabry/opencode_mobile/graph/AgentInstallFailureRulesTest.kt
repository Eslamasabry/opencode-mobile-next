package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.install.AgentSetupFailure
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each way a phone agent install fails reaches the Agents page as the reason it really had. */
class AgentInstallFailureRulesTest {
    private val aptTail =
        "Reading package lists...\nAfter this operation, 52.3 MB of additional disk space will be used.\n"

    @Test
    fun `a full disk reads as short of space, from the step's words, the log's ENOSPC or the job's own record`() {
        val runner = "There is not enough free space on this phone. Free some storage and try again."
        assertEquals(AgentSetupFailure.Storage, AgentInstallFailureRules.of(runner, ""))
        assertEquals(AgentSetupFailure.Storage, AgentInstallFailureRules.of("exit 1", "npm ERR! code ENOSPC\n"))
        assertEquals(
            AgentSetupFailure.Storage,
            AgentInstallFailureRules.of("E: Write error", "dpkg: error: No space left on device\n"),
        )
        assertEquals(AgentSetupFailure.Storage, AgentInstallFailureRules.of(null, null, "setup_persistence"))
    }

    @Test
    fun `apt's own disk space line no longer turns a failed download into short of space`() {
        val failed =
            AgentInstallFailureRules.of(
                "E: Failed to fetch http://ports.ubuntu.com/pool/main/p/python3",
                aptTail + "Temporary failure resolving 'ports.ubuntu.com'\n",
            )
        assertEquals(AgentSetupFailure.Download, failed)
        assertEquals(AgentSetupFailure.Unknown, AgentInstallFailureRules.of("exit 100", aptTail))
    }

    @Test
    fun `no connection, a failed download and the helper's own connection sentence read as the download failing`() {
        assertEquals(AgentSetupFailure.Download, AgentInstallFailureRules.of("[oc] Download failed (curl exit 6)", ""))
        assertEquals(
            AgentSetupFailure.Download,
            AgentInstallFailureRules.of("[oc] Paseo could not be installed. Check the connection and retry.", ""),
        )
        assertEquals(AgentSetupFailure.Download, AgentInstallFailureRules.of("exit 1", "npm ERR! code EAI_AGAIN\n"))
    }

    @Test
    fun `checksum, version, a stop and anything else keep their own kinds`() {
        assertEquals(
            AgentSetupFailure.Checksum,
            AgentInstallFailureRules.of("[oc] The agent download did not match its checksum", ""),
        )
        assertEquals(
            AgentSetupFailure.Version,
            AgentInstallFailureRules.of("[oc] The agent did not pass its version check", ""),
        )
        assertEquals(
            AgentSetupFailure.Interrupted,
            AgentInstallFailureRules.of("exit 0", "proot info: vpid 1: terminated with signal 15\n"),
        )
        assertEquals(
            AgentSetupFailure.Unknown,
            AgentInstallFailureRules.of("The setup step could not finish. Try again.", ""),
        )
    }
}
