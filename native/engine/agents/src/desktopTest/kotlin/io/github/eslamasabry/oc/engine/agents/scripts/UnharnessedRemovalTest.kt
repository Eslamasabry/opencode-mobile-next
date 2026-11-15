package io.github.eslamasabry.oc.engine.agents.scripts

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnharnessedRemovalTest {
    @Test
    fun `B-AGENT-UH-023 removal deletes every owned kernel mapping and session while preserving projects and other agents`() {
        val root = tempDir("uh-removal")
        try {
            File(root, ".local/bin").mkdirs()
            File(root, ".local/share/oc-agents/unharnessed/0.9.8-phone.1").mkdirs()
            for (owner in listOf("owner-a", "owner-b")) {
                for (name in
                    listOf(
                        "kernel-venv/runtime",
                        "acp-sessions.json",
                        "sessions/kept.jsonl",
                        "models.json",
                    )) File(root, ".oc-profiles/$owner/unharnessed/$name").apply {
                    parentFile.mkdirs()
                    writeText("fixture")
                }
                File(root, ".oc-profiles/$owner/claude/settings.json").apply {
                    parentFile.mkdirs()
                    writeText("keep")
                }
            }
            val project =
                File(root, "projects/hello.py").apply {
                    parentFile.mkdirs()
                    writeText("keep")
                }
            val uid = run(listOf("id", "-u")).stdout.trim()
            val script =
                "import os\nos.getuid = lambda: 1000\n" +
                    AgentRemovalScripts.REMOVE_PY.replace("/home/oc", root.path)
                        .replace("entry.st_uid != 1000", "entry.st_uid != $uid")
            val result =
                run(
                    listOf(
                        "python3",
                        "-c",
                        script,
                        "unharnessed",
                        "unharnessed",
                        "0.9.8-phone.1",
                        "a".repeat(32),
                    ),
                    environment = mapOf("HOME" to root.path),
                )
            assertEquals(0, result.exitCode, result.stderr)
            assertFalse(File(root, ".local/share/oc-agents/unharnessed").exists())
            for (owner in listOf("owner-a", "owner-b")) {
                assertFalse(File(root, ".oc-profiles/$owner/unharnessed").exists())
                assertTrue(File(root, ".oc-profiles/$owner/claude/settings.json").exists())
            }
            assertTrue(project.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
