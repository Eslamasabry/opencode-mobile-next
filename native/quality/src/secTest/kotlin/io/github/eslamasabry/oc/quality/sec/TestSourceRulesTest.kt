package io.github.eslamasabry.oc.quality.sec

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E02-S01: a green `:feature:*:desktopTest` means the module's tests ran. No feature or UI build file may hide test
 * files from compilation (`exclude` on a source set) or borrow another module's source folder (`srcDir("../…")`, the
 * old `ui/gate` workaround: the golden-diff gate is the test-only `:ui:gate` module now).
 */
object TestSourceRules {
    data class Hit(val path: String, val line: Int, val rule: String)

    /**
     * Modules whose owners still have to bring their excluded tests back; the list only shrinks. Each entry names
     * the owner and why it is not fixed in E02-S01.
     */
    val PENDING: Map<String, String> =
        mapOf(
            "feature/files/build.gradle.kts" to
                "P12-FILES owns :feature:files; outside E02-S01's modules, its stale tests come back in its own story",
            "feature/chat/build.gradle.kts" to
                "chat-lead owns the single-owner :feature:chat; outside E02-S01's modules, its owner restores the tests",
        )

    private val exclude = Regex("""\bexclude\s*[({]""")
    private val borrowed = Regex("""\bsrcDirs?\s*\(\s*"(\.\./|[^"]*ui/gate)""")

    /** The lines of one build file that hide tests or borrow sources. Comments are not code. */
    fun scan(path: String, text: String): List<Hit> =
        text.lines().mapIndexedNotNull { i, raw ->
            val line = raw.substringBefore("//")
            when {
                exclude.containsMatchIn(line) && "kotlin." in line -> Hit(path, i + 1, "source-set exclude hides files from the build")
                exclude.containsMatchIn(line) && "test" in line.lowercase() -> Hit(path, i + 1, "exclude names tests")
                borrowed.containsMatchIn(line) -> Hit(path, i + 1, "srcDir borrows another module's sources")
                else -> null
            }
        }

    /** Every build.gradle.kts under feature/ and ui/ (relative path → text). */
    fun buildFiles(root: File): Map<String, String> =
        listOf("feature", "ui")
            .flatMap { layer -> File(root, layer).listFiles().orEmpty().map { File(it, "build.gradle.kts") } }
            .filter { it.isFile }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
}

class TestSourceRulesTest {
    private val root = File(System.getProperty("oc.root"))
    private val files by lazy { TestSourceRules.buildFiles(root) }

    @Test
    fun `E02-S01 no feature or ui build file excludes tests or borrows another module's sources`() {
        assertTrue(files.size > 20, "scanned ${files.size} build files")
        val hits = files.filterKeys { it !in TestSourceRules.PENDING }.flatMap { (p, t) -> TestSourceRules.scan(p, t) }
        assertTrue(
            hits.isEmpty(),
            "tests must compile and run, not be excluded (depend on :ui:gate for the golden gate):\n" +
                hits.joinToString("\n") { "${it.path}:${it.line} ${it.rule}" },
        )
    }

    @Test
    fun `E02-S01 the pending list only shrinks and every entry still needs to be there`() {
        val offending = files.filter { (p, t) -> TestSourceRules.scan(p, t).isNotEmpty() }.keys
        assertEquals(emptySet(), TestSourceRules.PENDING.keys - offending, "an entry no longer excludes tests: remove it")
        assertTrue(TestSourceRules.PENDING.values.all { it.length > 20 }, "every entry gives a real reason")
    }

    @Test
    fun `E02-S01 planted excludes and borrowed source folders are caught, comments and generated dirs are not`() {
        val planted =
            """
            kotlin.sourceSets.matching { it.name == "desktopTest" }.configureEach {
                kotlin.exclude { it.file.name.endsWith("Test.kt") && it.file.name != "LooksCaptureTest.kt" }
            }
            // kotlin.exclude { it.file.name.endsWith("Test.kt") } — a comment is not code
            kotlin.sourceSets.getByName("desktopTest") {
                kotlin.srcDir("../../ui/gate/src/desktopMain/kotlin")
                resources.srcDir("../../ui/gate/src/desktopMain/resources")
            }
            kotlin.sourceSets.named("commonMain") { kotlin.srcDir(coverageDirectory) }
            exclude("**/build/**")
            filter { exclude("**/*Test.kt") }
            """.trimIndent()
        val hits = TestSourceRules.scan("feature/x/build.gradle.kts", planted)
        assertEquals(listOf(2, 6, 7, 11), hits.map { it.line })
    }
}
