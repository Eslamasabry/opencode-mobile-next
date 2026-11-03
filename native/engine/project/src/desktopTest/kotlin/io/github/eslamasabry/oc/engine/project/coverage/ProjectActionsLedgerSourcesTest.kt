package io.github.eslamasabry.oc.engine.project.coverage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Reads this module's test sources from disk: every proof the ledger names is a test function that exists. */
class ProjectActionsLedgerSourcesTest {
    private val testNames: Set<String> by lazy {
        val dirs = listOf("src/commonTest/kotlin", "src/desktopTest/kotlin").map { moduleFile(it) }
        val name = Regex("@Test\\s+fun\\s+`([^`]+)`")
        dirs.filter { it.isDirectory }.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f -> name.findAll(f.readText()).map { it.groupValues[1] } }
        }.toSet()
    }

    /** The module folder: Gradle runs tests there; fall back to the repository layout when run from the root. */
    private fun moduleFile(path: String): File {
        val here = File(path)
        return if (here.exists()) here else File("native/engine/project/$path")
    }

    @Test
    fun `B-BUILD-004 every proof names a test that exists`() {
        assertTrue(testNames.size > 50, "test sources found: ${testNames.size}")
        for (action in ProjectActionLedger.actions) {
            val proof = action.proof ?: continue
            assertTrue(proof in testNames, "${action.id}: no test named `$proof`")
        }
    }

    @Test
    fun `B-BUILD-004 every test of this module is named after a catalog row`() {
        val row = Regex("^B-[A-Z]+-\\d{3} ")
        val unnamed = testNames.filterNot { row.containsMatchIn(it) }
        assertTrue(unnamed.isEmpty(), "tests without a row id: $unnamed")
    }
}
