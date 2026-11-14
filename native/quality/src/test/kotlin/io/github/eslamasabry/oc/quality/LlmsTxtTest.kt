package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps `llms.txt` (repository root) honest: it lists the current documents of the native app for language models and
 * people. A new release note, or a document that moves or is retired, fails here and names the line to update.
 */
class LlmsTxtTest {
    private val repoRoot = File(System.getProperty("oc.root")).parentFile
    private val llms = File(repoRoot, "llms.txt")
    private val prefix = "https://raw.githubusercontent.com/Eslamasabry/opencode-mobile-next/master/"
    private val entry = Regex("""^- \[([^\]]*)]\(([^)]*)\):(.*)$""")

    private class Link(val line: Int, val title: String, val url: String, val description: String)

    private fun lines(): List<String> {
        assertTrue(llms.isFile, "llms.txt is missing at the repository root")
        return llms.readLines()
    }

    private fun links(): List<Link> =
        lines().mapIndexedNotNull { i, text ->
            if (!text.startsWith("- [")) return@mapIndexedNotNull null
            val m = entry.matchEntire(text)
            if (m == null) {
                Link(i + 1, "", "", "")
            } else {
                Link(i + 1, m.groupValues[1], m.groupValues[2], m.groupValues[3].trim())
            }
        }

    private fun linkedPaths(): Set<String> = links().filter { it.url.startsWith(prefix) }.map { it.url.removePrefix(prefix) }.toSet()

    @Test
    fun `llms txt starts with the project name and a summary quote`() {
        val all = lines()
        assertTrue(all.firstOrNull()?.startsWith("# ") == true, "llms.txt line 1 must be the H1 with the project name")
        val quote = all.drop(1).firstOrNull { it.isNotBlank() }
        assertTrue(quote?.startsWith("> ") == true, "llms.txt needs the blockquote summary right after the H1")
    }

    @Test
    fun `every link is a raw master URL of this repository`() {
        val bad =
            links().filter { it.url.isEmpty() || !it.url.startsWith(prefix) || it.url.length == prefix.length || ' ' in it.url }
        assertTrue(bad.isEmpty(), "llms.txt links that are not '- [title](${prefix}path): description':\n" + bad.joinToString("\n") { "line ${it.line}" })
        assertTrue(links().isNotEmpty(), "llms.txt lists no documents")
    }

    @Test
    fun `every linked path exists in the repository`() {
        val missing = links().filter { it.url.startsWith(prefix) && !File(repoRoot, it.url.removePrefix(prefix)).isFile }
        assertTrue(
            missing.isEmpty(),
            "llms.txt links to files that do not exist (moved or deleted: update or remove the line):\n" +
                missing.joinToString("\n") { "line ${it.line}: ${it.url.removePrefix(prefix)}" },
        )
    }

    @Test
    fun `every description is filled in and at most 200 characters`() {
        val bad = links().filter { it.url.isNotEmpty() && (it.description.isEmpty() || it.description.length > 200) }
        assertTrue(
            bad.isEmpty(),
            "llms.txt descriptions must be 1 to 200 characters:\n" +
                bad.joinToString("\n") { "line ${it.line}: ${it.description.length} characters" },
        )
    }

    @Test
    fun `the newest release note and the front documents are linked`() {
        val newest = newestReleaseNote()
        val linked = linkedPaths()
        assertTrue("docs/releases/$newest" in linked, "llms.txt must link the newest release note docs/releases/$newest")
        for (required in listOf("README.md", "PRIVACY.md", "CONTRIBUTING.md")) {
            assertTrue(required in linked, "llms.txt must link $required")
        }
    }

    @Test
    fun `no linked document calls itself retired or superseded near its top`() {
        val stale =
            links().filter { it.url.startsWith(prefix) }.mapNotNull { link ->
                val file = File(repoRoot, link.url.removePrefix(prefix))
                if (!file.isFile) return@mapNotNull null
                val head = file.readLines().take(40).joinToString("\n")
                if (Regex("retired|superseded", RegexOption.IGNORE_CASE).containsMatchIn(head)) {
                    "line ${link.line}: ${link.url.removePrefix(prefix)}"
                } else {
                    null
                }
            }
        assertTrue(stale.isEmpty(), "llms.txt links documents that say retired or superseded in their first 40 lines (remove them):\n" + stale.joinToString("\n"))
    }

    /** The release note file with the highest version: `v<major>.<minor>.<patch>+<code>.md` in `docs/releases/`. */
    private fun newestReleaseNote(): String {
        val name = Regex("""v(\d+)\.(\d+)\.(\d+)\+(\d+)\.md""")
        val notes =
            File(repoRoot, "docs/releases").listFiles().orEmpty().filter { it.isFile }.mapNotNull { f ->
                name.matchEntire(f.name)?.let { m -> f.name to m.groupValues.drop(1).map { it.toLong() } }
            }
        assertTrue(notes.isNotEmpty(), "docs/releases has no release notes")
        val newest =
            notes.maxWithOrNull { a, b ->
                a.second.zip(b.second).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0
            }!!
        assertEquals(1, notes.count { it.second == newest.second }, "two release notes share the highest version")
        return newest.first
    }
}
