package io.github.eslamasabry.oc.engine.agents.phonecontext

import io.github.eslamasabry.oc.engine.agents.phonecontext.PhoneAgentContext.Host
import io.github.eslamasabry.oc.engine.agents.scripts.sh
import io.github.eslamasabry.oc.engine.agents.scripts.tempDir
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Port of test/phone_agent_context_test.dart (cases 1-3; case 4 checks the Android server scripts). */
class PhoneAgentContextTest {
    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = tempDir("oc-ctx-")
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun file(path: String) = File(root.path + path)

    private fun runScript(script: String) {
        val result = sh(listOf("-c", script), environment = mapOf("OC_CTX_ROOT" to root.path))
        assertEquals(0, result.exitCode, result.stdout + result.stderr)
    }

    private fun count(text: String) = Regex(Regex.escape(PhoneAgentContext.BEGIN)).findAll(text).count()

    @Test
    fun `B-LINUX-116 writes the phone context into both OpenCode generations' global AGENTS md`() {
        runScript(PhoneAgentContext.termuxScript)
        for (path in PhoneAgentContext.files) {
            val text = file(path).readText()
            assertEquals(PhoneAgentContext.block(Host.Termux), text)
            assertTrue("person's Android phone" in text)
            assertTrue("no desktop app" in text)
            assertTrue("no adb connection" in text)
        }
    }

    @Test
    fun `B-LINUX-116 replaces its own block on every start and keeps the person's text`() {
        val global = file(PhoneAgentContext.files.first())
        global.parentFile.mkdirs()
        global.writeText("# My rules\n\nUse tabs.\n")
        runScript(PhoneAgentContext.termuxScript)
        runScript(PhoneAgentContext.termuxScript)
        // Moving to the built-in Ubuntu swaps the block, never adds a second.
        runScript(PhoneAgentContext.builtinScript)
        val text = global.readText()
        assertEquals("# My rules\n\nUse tabs.\n\n${PhoneAgentContext.block(Host.Builtin)}", text)
        assertEquals(1, count(text))
    }

    @Test
    fun `B-LINUX-116 text the person adds after the block survives the next start`() {
        runScript(PhoneAgentContext.builtinScript)
        val global = file(PhoneAgentContext.files.last())
        global.writeText("${global.readText()}\nMore rules.\n")
        runScript(PhoneAgentContext.builtinScript)
        val text = global.readText()
        assertTrue(text.startsWith("More rules.\n\n${PhoneAgentContext.BEGIN}"))
        assertEquals(1, count(text))
    }
}
