package io.github.eslamasabry.oc.ui.words

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** I147-HINT: the New conversation box's question is never empty in any language folder that defines it. */
class NewConversationHintWordsTest {
    private val entry = Regex("""<string\s+name="chatsNewBuildPrompt"[^>]*>([^<]*)</string>""")

    @Test
    fun `I147-HINT-4 chatsNewBuildPrompt is non-empty in the default words and in every language that has it`() {
        val root = File("src/commonMain/composeResources")
        if (!root.isDirectory) fail("composeResources not found from ${File(".").absolutePath}")
        val folders = root.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        val found =
            folders.associate { folder ->
                folder.name to
                    folder.listFiles { f -> f.extension == "xml" }.orEmpty()
                        .mapNotNull { entry.find(it.readText())?.groupValues?.get(1)?.trim() }
            }
        assertTrue(found["values"].orEmpty().singleOrNull()?.isNotEmpty() == true, "the default words hold the question")
        val empty = found.filterValues { texts -> texts.any { it.isEmpty() } }.keys
        assertTrue(empty.isEmpty(), "empty in: $empty")
        assertTrue(found["values-ar"].orEmpty().singleOrNull()?.isNotEmpty() == true, "Arabic holds the question")
    }
}
