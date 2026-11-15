package io.github.eslamasabry.oc.engine.agents.scripts

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue 147 launch blocker, second half (Galaxy S22, build 10046): Codex answered "the command tool failed to start: its
 * host executable is missing". Codex 0.169.0 runs a model's commands through its code-mode host, which it starts from
 * its own folder (`<folder of the codex program>/codex-code-mode-host`; reproduced off-phone under proot: "failed to
 * spawn code-mode host …/payload/codex-code-mode-host: host executable was not found"). The pin was the bare binary,
 * so that file never existed. The pin is now the official package, and the installer keeps its whole tree.
 */
class CodexPackageInstallTest {
    @Test
    fun `B-AGENT-CODEX-010 the Codex install keeps the code-mode host beside the program it starts`() {
        val codex = AgentCatalog.builtIn.byId(AgentId("codex"))!!.recipe!!
        for (architecture in listOf(AgentArchitecture.Arm64, AgentArchitecture.X64)) {
            val artifact = codex.artifacts.getValue(architecture)
            assertTrue(artifact.url.contains("/codex-package-"), artifact.url)
            assertEquals("bin/codex", artifact.archiveMember)
        }
        val dir = tempDir("oc-codex-package")
        try {
            // A package shaped like the release one (no symlinks; the installer refuses them).
            val tree = File(dir, "tree").apply { mkdirs() }
            File(tree, "bin").mkdirs()
            File(tree, "bin/codex").writeText("#!/bin/sh\n")
            File(tree, "bin/codex-code-mode-host").writeText("#!/bin/sh\n")
            File(tree, "bin/codex-exec-server").writeText("#!/bin/sh\n")
            File(tree, "codex-path").mkdirs()
            File(tree, "codex-path/rg").writeText("#!/bin/sh\n")
            File(tree, "codex-package.json").writeText("""{"entrypoint":"bin/codex"}""")
            listOf("bin/codex", "bin/codex-code-mode-host", "bin/codex-exec-server", "codex-path/rg").forEach {
                File(tree, it).setExecutable(true)
            }
            val archive = File(dir, "package.tar.gz")
            assertEquals(0, run(listOf("tar", "-czf", archive.path, "-C", tree.path, ".")).exitCode)
            val payload = File(dir, "payload").apply { mkdirs() }
            val member = codex.artifacts.getValue(AgentArchitecture.Arm64).archiveMember!!
            val extracted = run(listOf("python3", "-c", AgentPhoneScripts.EXTRACT_PY, archive.path, payload.path, "tarGz", member))
            assertEquals(0, extracted.exitCode, extracted.stderr)
            val program = File(payload, member)
            assertTrue(program.isFile && program.canExecute())
            // Codex looks for its host in the folder of the program the launcher runs.
            assertTrue(File(program.parentFile, "codex-code-mode-host").let { it.isFile && it.canExecute() })
            assertTrue(File(payload, "codex-path/rg").isFile)
        } finally {
            dir.deleteRecursively()
        }
    }
}
