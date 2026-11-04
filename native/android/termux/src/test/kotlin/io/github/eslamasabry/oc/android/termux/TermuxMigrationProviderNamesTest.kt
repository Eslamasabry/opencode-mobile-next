package io.github.eslamasabry.oc.android.termux

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Flutter test/termux_migration_provider_names_test.dart, case for case.
class TermuxMigrationProviderNamesTest {
    private val secret = "synthetic-secret-never-render"
    private lateinit var root: Path

    @Before
    fun setUp() {
        root = tempDir("migration-provider-names-")
    }

    @After
    fun tearDown() {
        root.deleteRecursivelyQuietly()
    }

    private fun empty() = JsonObject(emptyMap())

    @Test
    fun `B-SEC-177 JSONC returns only fixed labels and ignores secrets and custom names`() {
        val out = ByteArrayOutputStream()
        val previousOut = System.out
        val previousErr = System.err
        System.setOut(PrintStream(out))
        System.setErr(PrintStream(out))
        val labels = try {
            parseMigrationProviderNames(
                """
                {
                  // Provider settings can contain credentials.
                  "provider": {
                    "anthropic": {"name": "$secret", "options": {"apiKey": "$secret"}},
                    "openai": {"options": {"baseURL": "https://example.test/a//b/*c*/"}},
                    "$secret": {"name": "OpenAI"},
                    "github-copilot": {},
                    "github-copilot-enterprise": {}, /* duplicates */
                  },
                }
                """.trimIndent(),
            )
        } finally {
            System.setOut(previousOut)
            System.setErr(previousErr)
        }
        assertEquals(listOf("Anthropic", "GitHub Copilot", "OpenAI"), labels)
        assertEquals("", out.toString())
        assertFalse(labels.joinToString().contains(secret))
    }

    @Test
    fun `B-SEC-177 invalid, over-limit and non-provider documents disclose nothing`() {
        val many = JsonObject(
            buildMap {
                repeat(65) { put("provider$it", empty()) }
                put("openai", empty())
            },
        )
        for (text in listOf(
            """{"provider":{"anthropic":{}}""",
            """{"provider":{"anthropic":{}}} /* unfinished""",
            """{"provider":{"anthropic":"$secret"}}""",
            """{"auth":{"openai":{"key":"$secret"}}}""",
            """{"provider":[]}""",
            JsonObject(mapOf("provider" to many)).toString(),
            " ".repeat(1024 * 1024 + 1),
            "[".repeat(65) + "]".repeat(65),
        )) {
            assertEquals(emptyList(), parseMigrationProviderNames(text), text.take(60))
        }
    }

    @Test
    fun `B-SEC-177 escaped strings and comment markers cannot introduce provider IDs`() {
        val document = buildJsonObject {
            putJsonObject("provider") {
                putJsonObject("anthropic") { put("name", "\\\" // /* $secret") }
                putJsonObject("custom") {
                    put("name", "openai")
                    putJsonObject("provider") { putJsonObject("google") {} }
                }
            }
        }
        assertEquals(listOf("Anthropic"), parseMigrationProviderNames(document.toString()))
    }

    @Test
    fun `B-SEC-177 a model-only config gives fixed provider labels and never model text`() {
        assertEquals(
            listOf("Anthropic", "OpenAI"),
            parseMigrationProviderNames(buildJsonObject { put("model", "anthropic/$secret"); put("small_model", "openai/$secret") }.toString()),
        )
        assertEquals(
            emptyList(),
            parseMigrationProviderNames(buildJsonObject { put("model", "$secret/anthropic"); put("small_model", "custom/openai") }.toString()),
        )
        for (providers in listOf(
            JsonNull,
            JsonArray(emptyList()),
            JsonObject(mapOf("anthropic" to JsonPrimitive(secret))),
            JsonObject(buildMap { repeat(65) { put("provider$it", empty()) } }),
        )) {
            val document = JsonObject(mapOf("provider" to providers, "model" to JsonPrimitive("openai/$secret")))
            assertEquals(emptyList(), parseMigrationProviderNames(document.toString()))
        }
    }

    private fun config(relative: String, content: String): Path {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.write(file, content.toByteArray())
        return file
    }

    @Test
    fun `B-SEC-177 the private export reader reads only fixed OC1 and OC2 config paths and deduplicates labels`() {
        config(".config/opencode/opencode.json", """{"provider":{"anthropic":{},"openai":{}}}""")
        config(".oc-opencode2/config/opencode/opencode.jsonc", """{/* isolated */"provider":{"openai":{},"google":{},},}""")
        config(".local/share/opencode/auth.json", """{"provider":{"groq":{"key":"$secret"}}}""")
        config("opencode.json", """{"provider":{"mistral":{}}}""")
        assertEquals(listOf("Anthropic", "Google", "OpenAI"), readMigrationProviderNames(root))
    }

    @Test
    fun `B-SEC-177 the reader rejects oversized files and symlinked files or parents`() {
        val oversized = config(".config/opencode/opencode.json", " ".repeat(1024 * 1024) + """{"provider":{"anthropic":{}}}""")
        assertEquals(emptyList(), readMigrationProviderNames(root))
        Files.delete(oversized)
        val outside = config("other.json", """{"provider":{"openai":{}}}""")
        Files.createSymbolicLink(oversized, outside)
        assertEquals(emptyList(), readMigrationProviderNames(root))
        Files.delete(oversized)
        val originalParent = oversized.parent
        val movedParent = Files.move(originalParent, root.resolve("actual"))
        Files.write(movedParent.resolve("opencode.json"), """{"provider":{"openai":{}}}""".toByteArray())
        Files.createSymbolicLink(originalParent, movedParent)
        assertEquals(emptyList(), readMigrationProviderNames(root))
    }

    @Test
    fun `B-SEC-177 the reader rejects export-root symlinks and contains read failures`() {
        val target = Files.createDirectory(root.resolve("actual"))
        val file = target.resolve(".config/opencode/opencode.json")
        Files.createDirectories(file.parent)
        Files.write(file, """{"provider":{"openai":{}}}""".toByteArray())
        val link = Files.createSymbolicLink(root.resolve("export"), target)
        assertEquals(emptyList(), readMigrationProviderNames(link))
        assertEquals(emptyList(), readMigrationProviderNames(root.resolve("missing")))
        // The same tree read directly is fine: only the link was refused.
        assertTrue(readMigrationProviderNames(target).isNotEmpty())
    }

    @Test
    fun `B-SEC-177 the reader permits a platform alias above the verified export root`() {
        val actual = root.resolve("actual")
        val file = actual.resolve("export/.config/opencode/opencode.json")
        Files.createDirectories(file.parent)
        Files.write(file, """{"provider":{"openai":{}}}""".toByteArray())
        val alias = Files.createSymbolicLink(root.resolve("platform-alias"), actual)
        assertEquals(listOf("OpenAI"), readMigrationProviderNames(alias.resolve("export")))
    }
}
