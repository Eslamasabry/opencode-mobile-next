package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.scripts.sh
import io.github.eslamasabry.oc.engine.agents.scripts.tempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Issue 147: the read of the install records runs and reads what the install script writes. */
class AgentInstallRecordScriptTest {
    @Test
    fun `B-AGENT-UPD-003 the record read finds each install's record and launcher program`() {
        val root = tempDir("oc-records")
        try {
            val final = "/home/oc/.local/share/oc-agents/codex/0.169.0"
            val dir = File(root, final.removePrefix("/")).apply { mkdirs() }
            File(dir, ".oc-pin").writeText("0.169.0|arm64|97b97d87eea075c5")
            // The launcher exactly as the install script writes it.
            File(dir, "launch").writeText("#!/bin/sh\nexec \"$final/payload/codex-aarch64-unknown-linux-musl\" \"\$@\"\n")
            val gemini = "/home/oc/.local/share/oc-agents/gemini/" + AgentCatalog.builtIn.byId(AgentId("gemini"))!!.recipe!!.version
            val gdir = File(root, gemini.removePrefix("/")).apply { mkdirs() }
            File(gdir, "launch").writeText(
                "#!/bin/sh\nexec /home/oc/.local/node/bin/node \"$gemini/payload/package/bundle/gemini.js\" \"\$@\"\n",
            )
            val run = sh(listOf("-c", AgentInstallRecord.readScript(AgentCatalog.builtIn, root.path)))
            assertEquals(0, run.exitCode, run.stderr)
            val read = AgentInstallRecord.parse(run.stdout)
            assertEquals(
                AgentInstallRecorded("0.169.0|arm64|97b97d87eea075c5", "$final/payload/codex-aarch64-unknown-linux-musl"),
                read[AgentId("codex")],
            )
            assertNull(read.getValue(AgentId("gemini")).record)
            assertEquals("$gemini/payload/package/bundle/gemini.js", read.getValue(AgentId("gemini")).program)
            assertEquals(AgentInstallRecorded(null, null), read[AgentId("claude")])
        } finally {
            root.deleteRecursively()
        }
    }
}
