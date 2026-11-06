package io.github.eslamasabry.oc.ui.words

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * E00-S14: the update gate, its fallback notices and the upgrade journey are the first native words a person
 * updating from the Flutter app reads, so every one of them is in Arabic too (values-ar), never an English fallback.
 */
class GateWordsInArabicTest {
    private val names = Regex("""<(?:string|plurals)\s+name="([^"]+)"""")

    private fun namesIn(folder: String): Set<String> {
        val dir = File("src/commonMain/composeResources/$folder")
        if (!dir.isDirectory) fail("$folder not found from ${File(".").absolutePath}")
        return dir.listFiles { f -> f.extension == "xml" }.orEmpty()
            .flatMap { file -> names.findAll(file.readText()).map { it.groupValues[1] }.toList() }
            .filter { it.startsWith("migration_") || it.startsWith("upgrade") }
            .toSet()
    }

    @Test
    fun `B-L10N-010 every gate, fallback and upgrade word has an Arabic twin`() {
        val english = namesIn("values")
        assertTrue(english.any { it.startsWith("upgradeConnect") }, "upgrade words not found")
        assertTrue(english.any { it.startsWith("migration_deferred") }, "gate words not found")
        assertEquals(emptyList(), (english - namesIn("values-ar")).sorted(), "missing from values-ar")
    }
}
