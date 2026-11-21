package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The flicker rule (owner 2026-11-20: "No release until we make sure no flickering systematically"): the UI tests of
 * the recorded modules start Compose through `kitUiTest` (ui/gate, flicker/KitUiTest.kt), so every frame they draw is
 * read for blinks, doubles, returns, jumps and a first frame unlike the settled page. A test that starts Compose
 * itself draws frames nobody reads.
 *
 * The recorded modules hold a `flicker-baseline.tsv`: the findings that were there when the module moved over, one
 * line each. It only shrinks (a line goes with its fix, in the fix's commit); at the cut the chat, Home and update
 * journeys' baselines are empty.
 */
class FlickerEntryGuardTest {
    private val root = File(System.getProperty("oc.root"))

    /** Modules whose UI tests are recorded. The list grows module by module (the other modules move after the cut). */
    private val recorded = listOf("feature/chat", "feature/chats")

    /** Baseline lines per recorded module on the day it moved over. Lower a number with each fix; never raise it. */
    private val ceilings = mapOf("feature/chat" to 330, "feature/chats" to 215)

    /** Test files in recorded modules that may start Compose themselves (path from the native root to reason). */
    private val allowed: Map<String, String> = emptyMap()

    private val runners =
        listOf(
            "runComposeUiTest", "runDesktopComposeUiTest", "runSkikoComposeUiTest", "runInternalSkikoComposeUiTest",
            "createComposeRule", "createEmptyComposeRule",
        )
    private val raw = Regex("""\b(${runners.joinToString("|")})\b|\b(Skiko|Desktop)ComposeUiTest\s*\(""")

    private fun hits(path: String, text: String): List<Int> =
        if (path in allowed) {
            emptyList()
        } else {
            text.lines().mapIndexedNotNull { i, line0 ->
                val line = line0.substringBefore("//").trim()
                val code = line.isNotEmpty() && !line.startsWith("*") && !line.startsWith("/*")
                if (code && raw.containsMatchIn(line)) i + 1 else null
            }
        }

    private fun testFiles(module: String): Map<String, String> =
        File(root, "$module/src").listFiles().orEmpty()
            .filter { it.isDirectory && it.name.contains("Test") }
            .flatMap { set -> set.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .associate { it.relativeTo(root).path to it.readText() }

    @Test
    fun `FLICKER-GUARD UI tests in the recorded modules start Compose through kitUiTest`() {
        val files = recorded.flatMap { testFiles(it).entries }
        assertTrue(files.size > 100, "scanned ${files.size} test files")
        val bad = files.flatMap { (p, t) -> hits(p, t).map { "$p:$it" } }
        assertTrue(bad.isEmpty(), "start the test with kitUiTest { } (ui/gate flicker):\n" + bad.joinToString("\n"))
        val gone = allowed.keys.filter { !File(root, it).isFile }
        assertEquals(emptyList(), gone, "an allowed file is gone: remove its line")
    }

    @Test
    fun `FLICKER-GUARD the recorded modules' baselines only shrink and every line names a test, rule and words`() {
        val rules = setOf("BLINK", "DOUBLE", "ABA", "JUMP", "FIRST", "RECOMPOSE")
        for (module in recorded) {
            val lines =
                File(root, "$module/flicker-baseline.tsv").takeIf { it.isFile }?.readLines().orEmpty()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
            val ceiling = ceilings[module] ?: 0
            assertTrue(lines.size <= ceiling, "$module/flicker-baseline.tsv: ${lines.size} lines, at most $ceiling")
            val malformed = lines.filter { l -> l.split('\t').let { it.size < 3 || '.' !in it[0] || it[1] !in rules } }
            assertTrue(malformed.isEmpty(), "$module/flicker-baseline.tsv: not <Class.test> <RULE> <words>: $malformed")
        }
    }

    @Test
    fun `FLICKER-GUARD planted direct starts are caught, kitUiTest and comments are not`() {
        val planted =
            """
            fun a() = runComposeUiTest { }
            fun b() = runDesktopComposeUiTest(360, 780) { }
            // runComposeUiTest in a comment
            fun c() = kitUiTest(360, 780) { }
            val rule = createComposeRule()
            fun d() = androidx.compose.ui.test.v2.runSkikoComposeUiTest { }
            val t = DesktopComposeUiTest(360, 780, useStandardTestDispatcherForComposition = true)
            fun e(meter: FrameMeter) = FrameMeter.attach(this as SkikoComposeUiTest)
            """.trimIndent()
        assertEquals(listOf(1, 2, 5, 6, 7), hits("feature/chat/src/desktopTest/X.kt", planted))
    }
}
