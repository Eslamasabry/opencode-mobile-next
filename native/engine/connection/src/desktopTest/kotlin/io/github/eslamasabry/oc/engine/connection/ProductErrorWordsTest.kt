package io.github.eslamasabry.oc.engine.connection

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every product error line is a real sentence in English and Arabic (B-SEC-132: failures are said in words). */
class ProductErrorWordsTest {
    private fun keys(locale: String): Set<String> {
        val dir = File("../../ui/words/src/commonMain/composeResources/$locale")
        assertTrue(dir.isDirectory, dir.absolutePath)
        return dir.listFiles().orEmpty().filter { it.name.endsWith(".xml") }
            .flatMap { Regex("<string name=\"([^\"]+)\"").findAll(it.readText()).map { m -> m.groupValues[1] }.toList() }
            .toSet()
    }

    @Test
    fun `B-SEC-132 every product error line names an English and an Arabic sentence`() {
        for (locale in listOf("values", "values-ar")) {
            val present = keys(locale)
            val missing = ProductErrorLine.entries.map { it.wordsKey }.filter { it !in present }
            assertTrue(missing.isEmpty(), "$locale is missing $missing")
        }
    }
}
