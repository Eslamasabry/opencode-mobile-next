package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.install.AgentSetupFailure
import io.github.eslamasabry.oc.engine.agents.install.AgentVersionCheck
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
        // Another version, as the agent's version check names it (since native/w4-agent-version a06ed7ac1e the
        // program's silent or failed read-back is NotStarted, see the next test), and Paseo's own check.
        assertEquals(
            AgentSetupFailure.Version,
            AgentInstallFailureRules.of("[oc] The agent reported version 0.65.0; this app installs 0.66.0", ""),
        )
        assertEquals(
            AgentSetupFailure.Version,
            AgentInstallFailureRules.of("[oc] Paseo did not pass its version check", ""),
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

    @Test
    fun `a proot signal from an earlier retried attempt does not hide a later full disk or failed lookup`() {
        val retried = "proot info: vpid 1: terminated with signal 9\n==> Retrying\n"
        assertEquals(
            AgentSetupFailure.Storage,
            AgentInstallFailureRules.of("exit 1", retried + "npm ERR! code ENOSPC\n"),
        )
        assertEquals(
            AgentSetupFailure.Download,
            AgentInstallFailureRules.of("exit 1", retried + "Temporary failure resolving 'ports.ubuntu.com'\n"),
        )
        // The step's own words still say a stop first.
        assertEquals(
            AgentSetupFailure.Interrupted,
            AgentInstallFailureRules.of("proot info: vpid 1: terminated with signal 9", "npm ERR! code ENOSPC\n"),
        )
    }

    @Test
    fun `the agent's version check is read first, so a slow or silent read-back never reads as a failed install`() {
        // Each line holds a word a later rule would take ("version", "download"), and the log tail holds a stop, a
        // full disk and a failed lookup: the version check's own line still decides.
        val tail = "proot info: vpid 1: terminated with signal 9\nnpm ERR! code ENOSPC\nCould not resolve host\n"
        assertEquals(AgentSetupFailure.NotStarted, AgentInstallFailureRules.of(AgentVersionCheck.SLOW_LINE, tail))
        assertEquals(AgentSetupFailure.NotStarted, AgentInstallFailureRules.of(AgentVersionCheck.SILENT_LINE, tail))
        assertEquals(AgentSetupFailure.NotStarted, AgentInstallFailureRules.of(AgentVersionCheck.SLOW_LINE, null))
        assertEquals(
            AgentSetupFailure.Withdrawn,
            AgentInstallFailureRules.of(
                AgentVersionCheck.WITHDRAWN_PREFIX + "0.66.0",
                "[oc] Download failed (curl exit 22)\n",
            ),
        )
        assertEquals(
            AgentSetupFailure.Version,
            AgentInstallFailureRules.of(
                AgentVersionCheck.OTHER_PREFIX + "0.65.0" + AgentVersionCheck.OTHER_MIDDLE + "0.66.0",
                "npm ERR! code ENOSPC\n",
            ),
        )
    }

    @Test
    fun `an install that really failed is never read as a version problem, even after a version line in the log`() {
        // An earlier attempt's version-check line in the log tail does not speak for the step that failed now.
        val earlier = AgentVersionCheck.SLOW_LINE + "\n==> Retrying\n"
        assertEquals(
            AgentSetupFailure.Storage,
            AgentInstallFailureRules.of("exit 1", earlier + "npm ERR! code ENOSPC\n"),
        )
        assertEquals(
            AgentSetupFailure.Download,
            AgentInstallFailureRules.of("[oc] Download failed (curl exit 6)", earlier),
        )
        assertEquals(
            AgentSetupFailure.Checksum,
            AgentInstallFailureRules.of("[oc] The agent download did not match its checksum", earlier),
        )
        assertEquals(
            AgentSetupFailure.Storage,
            AgentInstallFailureRules.of("There is not enough free space on this phone. Free some storage.", earlier),
        )
        assertEquals(
            AgentSetupFailure.Interrupted,
            AgentInstallFailureRules.of("proot info: vpid 1: terminated with signal 9", earlier),
        )
        assertEquals(AgentSetupFailure.Unknown, AgentInstallFailureRules.of("exit 1", earlier))
    }
}
