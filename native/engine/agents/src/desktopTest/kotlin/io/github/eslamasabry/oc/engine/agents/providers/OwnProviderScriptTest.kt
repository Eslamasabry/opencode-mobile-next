package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.engine.agents.scripts.sh
import io.github.eslamasabry.oc.engine.agents.scripts.tempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue 147: the own-provider script really writes Claude Code's settings and takes them out again (fake values). */
class OwnProviderScriptTest {
    private val fakeKey = "fake-key-not-real-0000"

    private fun read(file: File): JsonObject = Json.parseToJsonElement(file.readText()).jsonObject

    @Test
    fun `B-AGENT-OWN-010 apply writes the env block 0600 and keeps the person's other settings, clear removes only ours`() {
        val dir = tempDir("oc-own-provider")
        try {
            val settings = File(dir, "claude/settings.json")
            settings.parentFile.mkdirs()
            settings.writeText("""{"theme":"dark","env":{"KEEP_ME":"1","ANTHROPIC_MODEL":"old"}}""")
            val script = OwnProviderScript.scriptAt(settings.absolutePath)

            val applied = sh(listOf("-c", script), stdin = OwnProviderScript.apply(OwnProvider("https://llm.example.test", "glm-test"), fakeKey))
            assertEquals(OwnProviderScript.OK, applied.exitCode, applied.stderr)
            assertEquals("", applied.stdout)
            assertFalse(applied.stderr.contains(fakeKey))
            val after = read(settings)
            val env = after["env"]!!.jsonObject
            assertEquals("dark", after["theme"]!!.jsonPrimitive.content)
            assertEquals("1", env["KEEP_ME"]!!.jsonPrimitive.content)
            assertEquals("https://llm.example.test", env["ANTHROPIC_BASE_URL"]!!.jsonPrimitive.content)
            assertEquals(fakeKey, env["ANTHROPIC_AUTH_TOKEN"]!!.jsonPrimitive.content)
            assertEquals("glm-test", env["ANTHROPIC_MODEL"]!!.jsonPrimitive.content)
            assertEquals("glm-test", env["ANTHROPIC_DEFAULT_SONNET_MODEL"]!!.jsonPrimitive.content)
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(settings.toPath()),
            )

            val cleared = sh(listOf("-c", script), stdin = OwnProviderScript.clear())
            assertEquals(OwnProviderScript.OK, cleared.exitCode, cleared.stderr)
            val clean = read(settings)
            assertEquals(mapOf("KEEP_ME" to "1"), clean["env"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content })
            assertFalse(settings.readText().contains(fakeKey))
            assertEquals("dark", clean["theme"]!!.jsonPrimitive.content)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `B-AGENT-OWN-011 a missing settings file is created, and refused input or an unreadable file change nothing`() {
        val dir = tempDir("oc-own-provider")
        try {
            val settings = File(dir, "claude/settings.json")
            val script = OwnProviderScript.scriptAt(settings.absolutePath)
            val made = sh(listOf("-c", script), stdin = OwnProviderScript.apply(OwnProvider("https://llm.example.test", null), fakeKey))
            assertEquals(OwnProviderScript.OK, made.exitCode, made.stderr)
            assertNull(read(settings)["env"]!!.jsonObject["ANTHROPIC_MODEL"])

            // A name this app does not manage is refused: the script never writes arbitrary variables.
            val foreign = sh(listOf("-c", script), stdin = """{"set":{"PATH":"/tmp"}}""" + "\n")
            assertEquals(OwnProviderScript.BAD_INPUT, foreign.exitCode)
            assertTrue(settings.readText().contains("ANTHROPIC_BASE_URL"))

            settings.writeText("not json")
            val broken = sh(listOf("-c", script), stdin = OwnProviderScript.clear())
            assertEquals(OwnProviderScript.UNREADABLE_SETTINGS, broken.exitCode)
            assertEquals("not json", settings.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
