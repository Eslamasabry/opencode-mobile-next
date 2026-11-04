package io.github.eslamasabry.oc.android.termux

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Every word the presenter names exists in :ui:words in English and Arabic, and every failure code has its sentence.
class TermuxMigrationWordsTest {
    private fun names(language: String): Set<String> {
        val folder = File("../../ui/words/src/commonMain/composeResources/$language")
        assertTrue(folder.isDirectory, folder.path)
        return folder.listFiles()!!.filter { it.name.endsWith(".xml") }
            .flatMap { file -> Regex("<(?:string|plurals) name=\"([A-Za-z0-9_]+)\"").findAll(file.readText()).map { it.groupValues[1] }.toList() }
            .toSet()
    }

    @Test
    fun `B-LINUX-238 every migration word exists in English and Arabic`() {
        for (language in listOf("values", "values-ar")) {
            val present = names(language)
            val missing = MigrationWord.entries.map { it.name }.filterNot { it in present }
            assertEquals(emptyList(), missing, language)
        }
    }

    @Test
    fun `B-LINUX-238 every failure code and item has its own plain words`() {
        assertEquals(TermuxMigrationFailure.entries.size - 1, TermuxMigrationFailure.entries.map(::migrationFailureWords).toSet().size)
        assertEquals(migrationFailureWords(TermuxMigrationFailure.invalidArchive), migrationFailureWords(TermuxMigrationFailure.checksumMismatch))
        assertEquals(TermuxMigrationItem.entries.size, TermuxMigrationItem.entries.map(::migrationItemLabel).toSet().size)
        assertEquals(TermuxMigrationItem.entries.size, TermuxMigrationItem.entries.map(::migrationItemWhat).toSet().size)
    }
}
