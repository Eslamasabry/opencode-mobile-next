package io.github.eslamasabry.oc.engine.agents.providers

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnharnessedAdapterTest {
    @Test
    fun `B-AGENT-UH-001 to 005 the ACP bridge keeps identities and frames without running the agent`() {
        val root =
            generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
                .first { File(it, "tool/unharnessed/test_adapter.py").isFile }
        val output = File.createTempFile("unharnessed-adapter-test", ".txt")
        try {
            val process =
                ProcessBuilder("python3", "-B", File(root, "tool/unharnessed/test_adapter.py").path)
                    .redirectErrorStream(true)
                    .redirectOutput(output)
                    .start()
            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            assertTrue(finished, "Bridge tests timed out")
            assertEquals(0, process.exitValue(), output.readText())
        } finally {
            output.delete()
        }
    }
}
