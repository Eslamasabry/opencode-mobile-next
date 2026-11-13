package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.ui.design.style.Style
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Help > Tips > "Make it yours", step 2 ("Pick how cards and bars feel: ...") must name every style the Style page
 * offers, in each language, as the page spells them. The list is [Style.entries]: adding a style makes the `when`
 * below stop compiling, and a sentence that does not name it fails here.
 */
class TourStyleSentenceTest {
    private val words = File("../../ui/words/src/commonMain/composeResources")

    private fun labelKey(style: Style): String =
        when (style) {
            Style.Solid -> "appearanceSurfaceSolid"
            Style.Glass -> "rb34AppearanceSurfaceGlass"
            Style.Soft -> "appearanceSurfaceSoft"
            Style.Brutal -> "appearanceSurfaceBrutal"
            Style.Paper -> "appearanceSurfacePaper"
            Style.Signal -> "signalStyleName"
        }

    private fun strings(folder: File): Map<String, String> {
        val out = mutableMapOf<String, String>()
        folder.listFiles { f -> f.extension == "xml" }.orEmpty().forEach { file ->
            Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(file.readText())
                .forEach { out[it.groupValues[1]] = it.groupValues[2] }
        }
        return out
    }

    @Test
    fun `the style sentence names every style, in every language`() {
        assertTrue(words.isDirectory, "composeResources not found from ${File(".").absoluteFile}")
        val folders = words.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        assertTrue(folders.size >= 9, "expected English and the eight translations, found ${folders.map { it.name }}")
        val english = strings(File(words, "values"))
        assertEquals(6, Style.entries.size, "a style was added or removed: update the sentence in every language, then this count")
        for (folder in folders) {
            val own = english + strings(folder)
            val sentence = own.getValue("tourMakeItYoursStyle")
            for (style in Style.entries) {
                val label = own.getValue(labelKey(style))
                assertTrue(label in sentence, "${folder.name}: step 2 does not name ${style.name} (\"$label\"): $sentence")
            }
        }
    }
}
