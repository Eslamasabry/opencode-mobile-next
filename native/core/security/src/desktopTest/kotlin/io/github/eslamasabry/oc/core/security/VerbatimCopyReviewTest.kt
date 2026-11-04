package io.github.eslamasabry.oc.core.security

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Port of the "redact: false is only for the person's own content (SEC-13)" group in test/redaction_test.dart. Native
 * has no `redact:` flag: every clipboard write hands text to the platform verbatim, so each write site in production
 * Kotlin is reviewed here, the reviewed counts only shrink, and each entry says why it is safe.
 */
class VerbatimCopyReviewTest {
    /** path (from native/) → (count, why the text is the person's own or already masked). */
    private val reviewedOwnContent: Map<String, Pair<Int, String>> =
        mapOf(
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitDetailsFold.kt" to
                (1 to "Copy all of a Details fold: the fold holds Redacted details and closed categories only"),
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitStateView.kt" to
                (1 to "copies failure.details, a Redacted value built by the one Redactor"),
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/header/KitChatHeaderParts.kt" to
                (1 to "KitClipboardEffect: the chat header masks everything but the person's own prompts first"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ChatFeelWiring.kt" to
                (1 to "a reply or code block the person chose to copy: their own conversation content"),
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SettingsWiring.kt" to
                (1 to "the problem report text, already built from scrubbed diagnostics facts"),
        )

    /** Known unmasked copies awaiting a fix; each names its owner. Empty today. */
    private val findings: Map<String, Pair<Int, String>> = emptyMap()

    private val copyCall = Regex("""\.(setPrimaryClip|setClipEntry)\(|\bclipboard\w*\.setText\(""")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*[\s\S]*?\*/"""), "").lines().joinToString("\n") { it.substringBefore("//") }

    private fun nativeRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return requireNotNull(dir) { "native/ root not found" }
    }

    private fun copySites(): Map<String, Int> {
        val root = nativeRoot()
        val counts = sortedMapOf<String, Int>()
        root.walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") && it.name !in setOf("spikes", "third_party") }
            .filter { it.isFile && it.extension == "kt" && "/src/" in it.path && "Test" !in it.path.substringAfter("/src/").substringBefore('/') }
            .filter { f -> f.path.substringAfter("/src/").substringBefore('/').let { it.endsWith("Main") || it == "main" || it == "jvmShared" } }
            .forEach { file ->
                val n = copyCall.findAll(stripComments(file.readText())).count()
                if (n > 0) counts[file.relativeTo(root).path] = n
            }
        return counts
    }

    @Test
    fun `B-SEC-145 every verbatim copy in the app is reviewed and the reviewed lists only shrink`() {
        val sites = copySites()
        val reviewed = reviewedOwnContent + findings
        val problems = mutableListOf<String>()
        for ((path, count) in sites) {
            val entry = reviewed[path]
            if (entry == null) problems += "$path: $count new verbatim clipboard write(s) — only the person's own content copies verbatim"
            else if (count > entry.first) problems += "$path: $count verbatim copies, reviewed ${entry.first} — review the new call site"
        }
        for ((path, entry) in reviewed) {
            val count = sites[path] ?: 0
            if (count < entry.first) problems += "$path: stale — now $count, listed ${entry.first}; shrink the entry"
        }
        assertEquals(emptyList(), problems, problems.joinToString("\n"))
    }

    @Test
    fun `B-SEC-145 every reviewed entry gives a real reason and findings name their owner`() {
        for ((path, entry) in reviewedOwnContent + findings) {
            assertTrue(entry.first > 0, path)
            assertTrue(entry.second.trim().length > 10, path)
        }
        for ((path, entry) in findings) assertTrue("owner" in entry.second, "$path: a finding names its owner")
        assertTrue(reviewedOwnContent.keys.intersect(findings.keys).isEmpty())
    }

    @Test
    fun `B-SEC-145 the scan finds a verbatim copy in code but not in comments`() {
        val sample =
            "// clipboard.setText(x) in a comment\n" +
                "/* manager.setPrimaryClip(clip) */\n" +
                "clipboard.setText(AnnotatedString(text))\n" +
                "manager.setPrimaryClip(ClipData.newPlainText(\"l\", text))"
        assertEquals(2, copyCall.findAll(stripComments(sample)).count())
    }
}
