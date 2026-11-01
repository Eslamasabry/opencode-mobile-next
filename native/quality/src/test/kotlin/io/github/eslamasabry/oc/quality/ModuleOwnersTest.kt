package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every Gradle project has a MODULE_OWNERS row and every row names a project (plan 03 §2.4). */
class ModuleOwnersTest {
    private val root = File(System.getProperty("oc.root"))

    @Test
    fun `MODULE_OWNERS rows equal the discovered modules`() {
        // The same discovery rule as settings.gradle.kts.
        val discovered =
            listOf("core", "protocol", "engine", "ui", "feature", "android", "tools").flatMap { layer ->
                File(root, layer).listFiles().orEmpty()
                    .filter { File(it, "build.gradle.kts").isFile }
                    .map { ":$layer:${it.name}" }
            } + listOf("androidApp", "desktopApp", "quality").filter { File(root, "$it/build.gradle.kts").isFile }.map { ":$it" }
        val rows =
            File(root, "MODULE_OWNERS").readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.split('\t').first().trim() }
        assertEquals(discovered.sorted(), rows.sorted())
    }
}
