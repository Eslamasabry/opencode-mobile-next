package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.engine.agents.scripts.run
import io.github.eslamasabry.oc.engine.agents.scripts.tempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnharnessedProviderScriptTest {
    @Test
    fun `B-AGENT-UH-013 runtime setup is private stdin only and clear keeps conversations`() {
        val root = tempDir("uh-provider")
        try {
            val script = UnharnessedProvider.scriptAt(root.path)
            val input =
                UnharnessedProvider.input(
                    OwnProvider("https://example.invalid/v1", "fixture-model"),
                    "fixture-value",
                )
            assertFalse(script.contains("fixture-value"))
            val response = run(listOf("sh", "-c", script), stdin = input)
            assertEquals(0, response.exitCode, response.stderr)
            assertEquals("", response.stdout)
            val models = File(root, "models.json")
            assertTrue(models.readText().contains("openai-completions"))
            assertTrue(models.readText().contains("fixture-value"))
            assertEquals("600", run(listOf("stat", "-c", "%a", models.path)).stdout.trim())
            val session =
                File(root, "sessions/kept.jsonl").apply {
                    parentFile.mkdirs()
                    writeText("fixture")
                }
            assertEquals(
                0,
                run(listOf("sh", "-c", script), stdin = UnharnessedProvider.input(null)).exitCode,
            )
            assertFalse(models.exists())
            assertFalse(File(root, "phone-provider.json").exists())
            assertTrue(session.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
