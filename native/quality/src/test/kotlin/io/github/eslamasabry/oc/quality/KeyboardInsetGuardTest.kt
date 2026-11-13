package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The window's keyboard measurement only settles when the keyboard's closing animation ends in this window; if that end
 * never arrives it keeps the keyboard's last height and the page stands one keyboard high over an empty screen. The kit
 * reads it in ONE place (`kitKeyboardInsets()`, which lets a page down once the animation's target says the keyboard
 * is gone); everything else asks the kit (`kitKeyboardVisible()`, `kitKeyboardHeight()`, `kitImePadding()`).
 */
class KeyboardInsetGuardTest {
    private val root = File(System.getProperty("oc.root"))

    /** The kit's keyboard files: the only readers of the raw measurement. A reader elsewhere needs a reason here. */
    private val allowed: Map<String, String> =
        mapOf(
            "KitKeyboard.kt" to "the one value every part reads",
            "KitKeyboard.android.kt" to "Android's animation target",
            "KitKeyboard.desktop.kt" to "the desktop stand-in",
        )

    private val raw = Regex("""WindowInsets\.(Companion\.)?ime\b|\bimeAnimationTarget\b|\bimePadding\(|\bisImeVisible\b|layout\.ime\b|layout\.imePadding\b""")

    private fun hits(path: String, text: String): List<Int> =
        if (path.substringAfterLast('/') in allowed) emptyList()
        else text.lines().mapIndexedNotNull { i, raw0 ->
            val line = raw0.substringBefore("//").trim()
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("/*")) null
            else if (raw.containsMatchIn(line)) i + 1 else null
        }

    @Test
    fun `KBD-GUARD no production file outside the kit's keyboard files reads the raw keyboard inset`() {
        val files =
            root.listFiles { f -> f.isDirectory && f.name != "quality" && f.name != "build" }.orEmpty().flatMap { dir ->
                BuildRestGates.production(root, dir.name).entries
            }
        assertTrue(files.size > 500, "scanned ${files.size} files")
        val bad = files.flatMap { (p, t) -> hits(p, t).map { "$p:$it" } }
        assertTrue(bad.isEmpty(), "read the keyboard through the kit (kitKeyboardVisible / kitKeyboardHeight / kitImePadding):\n" + bad.joinToString("\n"))
    }

    @Test
    fun `KBD-GUARD planted raw readers are caught, the kit's own files and comments are not`() {
        val planted =
            """
            val a = WindowInsets.ime.getBottom(d)
            // WindowInsets.ime in a comment
            Modifier.imePadding()
            val b = WindowInsets.imeAnimationTarget
            val ok = kitKeyboardHeight()
            Modifier.kitImePadding()
            """.trimIndent()
        assertEquals(listOf(1, 3, 4), hits("feature/x/Screen.kt", planted))
        assertEquals(emptyList(), hits("ui/kit/frame/KitKeyboard.kt", planted))
    }
}
