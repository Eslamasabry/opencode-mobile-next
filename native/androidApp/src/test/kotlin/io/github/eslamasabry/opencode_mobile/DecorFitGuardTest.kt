package io.github.eslamasabry.opencode_mobile

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-270: after a language switch the page sat between black bands until the app was reopened. `recreate()` reuses the
 * old window's decor, and the first Android 15 builds reset it to "fit the bars", which edge-to-edge cannot undo once
 * the decor is installed. The order in `onCreate` is the fix, so it is guarded here; the bands themselves are proven
 * only on an Android 15 device or emulator (later Android 15 builds and Robolectric carry the setting over).
 */
class DecorFitGuardTest {
    private val activity = File("src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt").readText()

    /** The code lines of one function, comments left out. */
    private fun statements(signature: String): List<String> {
        val start = activity.indexOf(signature)
        assertTrue(start >= 0, "MainActivity has $signature")
        return activity
            .substring(start)
            .substringAfter('{')
            .substringBefore("\n    override fun ")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
    }

    @Test
    fun `B-270 the decor fit is released before anything in onCreate can install the decor`() {
        assertEquals("DecorFit.release(window)", statements("override fun onCreate(").first())
    }

    @Test
    fun `B-270 resuming does not guess the bar colours again over the app's own`() {
        assertFalse(statements("override fun onResume(").any { "enableEdgeToEdge(" in it })
    }
}
