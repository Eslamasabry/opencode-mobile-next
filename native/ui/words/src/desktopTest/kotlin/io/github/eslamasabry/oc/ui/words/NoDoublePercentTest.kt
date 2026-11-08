package io.github.eslamasabry.oc.ui.words

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * compose-resources substitutes only `%1$s`-style arguments and prints every other character literally, so the
 * Android-style `%%` escape reaches the screen as two percent signs (B-122: "40%% used"). A percent sign is written
 * once in the composeResources catalogue. The Android-resource strings under `android-res` still use `%%`.
 */
class NoDoublePercentTest {
    @Test
    fun `B-122 no composeResources string escapes a percent sign as double percent`() {
        val root = File("src/commonMain/composeResources")
        if (!root.isDirectory) fail("composeResources not found from ${File(".").absolutePath}")
        val hits = root.walkTopDown().filter { it.extension == "xml" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                if ("%%" in line) "${file.parentFile.name}/${file.name}:${i + 1}" else null
            }
        }.toList()
        assertTrue(hits.isEmpty(), "write one % in compose-resources strings:\n" + hits.joinToString("\n"))
    }
}
