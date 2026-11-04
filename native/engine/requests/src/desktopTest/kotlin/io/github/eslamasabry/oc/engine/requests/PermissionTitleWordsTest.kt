package io.github.eslamasabry.oc.engine.requests

import io.github.eslamasabry.oc.engine.requests.ask.PermissionTitle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-L10N-016 in every locale: each title key has its own non-empty words in English and Arabic. */
class PermissionTitleWordsTest {
    private fun words(locale: String): Map<String, String> {
        var dir = File("").absoluteFile
        while (!File(dir, "ui/words").isDirectory) dir = dir.parentFile ?: error("ui/words not found")
        val folder = File(dir, "ui/words/src/commonMain/composeResources/$locale")
        val entry = Regex("""<string name="([^"]+)">([^<]*)</string>""")
        return folder.listFiles { f -> f.name.endsWith(".xml") }!!.flatMap { f ->
            entry.findAll(f.readText()).map { it.groupValues[1] to it.groupValues[2] }.toList()
        }.toMap()
    }

    @Test
    fun `B-L10N-016 permission titles are plain words in every locale and unknown or empty ids read Permission needed`() {
        assertEquals("Permission needed", words("values")["e7PermissionAction6"])
        for (locale in listOf("values", "values-ar")) {
            val table = words(locale)
            val titles = PermissionTitle.entries.map { key -> table[key.wordsKey].orEmpty() }
            for ((key, text) in PermissionTitle.entries.zip(titles)) {
                assertTrue(text.isNotBlank(), "$locale ${key.wordsKey}")
                assertTrue('_' !in text, "$locale ${key.wordsKey} names an id")
            }
            assertEquals(titles.size, titles.toSet().size, "$locale: titles are distinct")
        }
    }
}
