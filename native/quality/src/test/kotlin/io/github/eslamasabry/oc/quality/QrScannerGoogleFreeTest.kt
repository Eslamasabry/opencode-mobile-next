package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-185 (twin of Flutter's ML Kit-free scanner, c1e564d17): the pairing QR scanner decodes with zxing-cpp over CameraX,
 * and no build file pulls Google's ML Kit barcode model or the Play-services scanner (about 3 MB, and proprietary code
 * F-Droid refuses). Reads the build files as text.
 */
class QrScannerGoogleFreeTest {
    private val root = File(System.getProperty("oc.root") ?: ".").absoluteFile
    private val forbidden = Regex("""com\.google\.mlkit|mlkit|barcode-scanning|play-services-code-scanner|mobile_scanner""", RegexOption.IGNORE_CASE)

    private fun buildTexts(): List<Pair<String, String>> =
        root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" && !it.name.startsWith(".") }
            .filter { it.isFile && (it.name.endsWith(".gradle.kts") || it.name == "libs.versions.toml") }
            .map { it.relativeTo(root).path to it.readText() }
            .toList()

    @Test
    fun `no build file names ML Kit or the Play scanner`() {
        val hits = buildTexts().filter { (path, text) -> forbidden.containsMatchIn(stripComments(text)) && !path.startsWith("quality") }.map { it.first }
        assertEquals(emptyList(), hits, "ML Kit or the Play scanner is back in: $hits")
    }

    @Test
    fun `the scanner reads codes with zxing-cpp`() {
        val camera = File(root, "android/camera/build.gradle.kts").readText()
        assertTrue("zxing-cpp" in camera, "the camera module declares the zxing-cpp adapter")
        assertTrue("zxing-cpp" in File(root, "gradle/libs.versions.toml").readText())
    }

    /** `// No ML Kit` in a comment is the opposite of a dependency. */
    private fun stripComments(text: String): String = text.lines().joinToString("\n") { it.substringBefore("//") }
}
