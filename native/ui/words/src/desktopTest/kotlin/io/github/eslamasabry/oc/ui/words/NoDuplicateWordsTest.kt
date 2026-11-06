package io.github.eslamasabry.oc.ui.words

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * One name, one string. A name defined in two files of the same locale folder makes Android throw
 * IllegalStateException the first time a screen reads it (walk 0bf0df4ff, N-39: tapping a project crashed on
 * `linuxSvcTitle`), so the catalogue is checked here rather than on a device.
 */
class NoDuplicateWordsTest {
    private val names = Regex("""<(?:string|plurals|string-array)\s+name="([^"]+)"""")

    @Test
    fun `B-L10N-011 no resource name is defined twice in one locale`() {
        val root = File("src/commonMain/composeResources")
        if (!root.isDirectory) fail("composeResources not found from ${File(".").absolutePath}")
        val folders = root.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        assertTrue(folders.isNotEmpty(), "no values folders under $root")
        val clashes = folders.sortedBy { it.name }.flatMap { folder ->
            folder.listFiles { f -> f.extension == "xml" }.orEmpty()
                .flatMap { file -> names.findAll(file.readText()).map { it.groupValues[1] to file.name }.toList() }
                .groupBy({ it.first }, { it.second })
                .filterValues { it.size > 1 }
                .map { (name, files) -> "${folder.name}/$name in ${files.sorted().joinToString()}" }
        }
        assertTrue(clashes.isEmpty(), "defined more than once:\n" + clashes.joinToString("\n"))
    }
}
