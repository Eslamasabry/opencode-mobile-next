package io.github.eslamasabry.oc.engine.usagereaders

import io.github.eslamasabry.oc.engine.usagereaders.collector.InAppQuotaCollector
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The sign-in helper inside Ubuntu, run with a real Node (>= 20) when this machine has one; skipped otherwise. */
class KeysHelperNodeTest {
    private val work: File = Files.createTempDirectory("oc-quota-keys-").toFile().also {
        File(it, "keys.mjs").writeText(InAppQuotaCollector.KEYS_HELPER_SOURCE)
    }

    @AfterTest
    fun cleanUp() {
        work.deleteRecursively()
    }

    private val nodeOk: Boolean by lazy {
        try {
            val p = ProcessBuilder("node", "--version").redirectErrorStream(true).start()
            p.waitFor(20, TimeUnit.SECONDS)
            val major = p.inputStream.bufferedReader().readText().trim().removePrefix("v").substringBefore('.').toIntOrNull()
            major != null && major >= 20
        } catch (e: java.io.IOException) {
            false
        }
    }

    private class Run(val exitCode: Int, val output: String)

    private fun runHelper(vararg candidates: String): Run {
        val p = ProcessBuilder(listOf("node", "${work.path}/keys.mjs", work.path, "${work.path}/agents") + candidates)
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(30, TimeUnit.SECONDS)
        return Run(p.exitValue(), out)
    }

    private fun agentCodex(): File = File(work, "agents/p1/codex/auth.json").apply {
        parentFile.mkdirs()
        writeText("""{"tokens":{"access_token":"A","account_id":"B"}}""")
    }

    @Test
    fun `B-LINUX-178 writes 0600 key files, prints counts only, prefers OpenCode`() {
        if (!nodeOk) return
        val auth = File(work, "auth.json").apply {
            writeText(
                """{"zai-coding-plan":{"type":"api","key":"FIXTURE-GLM-KEY-VALUE"},""" +
                    """"openai":{"type":"oauth","access":"FIXTURE-ACCESS","accountId":"acct","expires":1}}""",
            )
        }
        agentCodex()
        val result = runHelper(auth.path)
        assertEquals(0, result.exitCode)
        assertTrue("codex=opencode glm=1 minimax=0" in result.output)
        assertFalse("FIXTURE" in result.output)
        val glm = File(work, "glm.key")
        assertEquals("FIXTURE-GLM-KEY-VALUE", glm.readText().trim())
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(glm.toPath()),
        )
        assertFalse(File(work, "minimax.key").exists())
        val env = File(work, "auth.env").readText()
        assertTrue("OCMN_QUOTA_AUTH_FORMAT=opencode" in env)
        assertTrue(auth.path in env)
        assertFalse("FIXTURE" in env)
    }

    @Test
    fun `B-LINUX-178 uses an agent Codex sign-in only when OpenCode has none`() {
        if (!nodeOk) return
        val auth = File(work, "auth.json").apply { writeText("{}") }
        val agent = agentCodex()
        val result = runHelper(auth.path)
        assertTrue("codex=agent" in result.output)
        val env = File(work, "auth.env").readText()
        assertTrue("OCMN_QUOTA_AUTH_FORMAT=codex" in env)
        assertTrue(agent.path in env)
    }

    @Test
    fun `B-LINUX-178 a key that was removed in OpenCode is removed here`() {
        if (!nodeOk) return
        val auth = File(work, "auth.json").apply {
            writeText("""{"minimax-coding-plan":{"type":"api","key":"FIXTURE-MM"}}""")
        }
        runHelper(auth.path)
        assertTrue(File(work, "minimax.key").exists())
        auth.writeText("{}")
        runHelper(auth.path)
        assertFalse(File(work, "minimax.key").exists())
    }
}
