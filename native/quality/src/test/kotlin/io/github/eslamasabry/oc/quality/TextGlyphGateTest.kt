package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-KIT-GLYPH-001: a mark is a kit glyph ([io.github.eslamasabry.oc.ui.kit.icon.KitIcons] through `KitIcon`), never a
 * text character standing in for an icon (owner 2026-11-06: "−", "✓", "i", "•" and provider initials looked unmade).
 * The scan catches a one-character letter or symbol passed straight to `KitText`/`Text`/`BasicText`, a `when` branch
 * that picks such a character, and an initials stand-in (`.take(1|2)` of a name rendered as text).
 */
class TextGlyphGateTest {
    private val root = File(System.getProperty("oc.root"))

    /** Files whose one-character marks are content typography, not icons. Each entry carries its reason. */
    private val allowed =
        mapOf(
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt" to
                "list bullets of rendered Markdown are part of the text, they copy and select with it",
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/diff/KitDiffView.kt" to
                "the + and − signs of a diff line are the diff's own notation, read in monospace beside the code",
            "ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalKeyBar.kt" to
                "terminal keys are labelled with the character they type",
        )

    @Test
    fun `B-KIT-GLYPH-001 no kit or feature part draws a text character as an icon`() {
        val files = BuildRestGates.production(root, "feature", "ui", "androidApp", "desktopApp")
        assertTrue(files.size > 100, "scanned ${files.size} files")
        val hits = files.filterKeys { it !in allowed }.flatMap { (p, t) -> TextGlyphs.hits(t).map { "$p:$it" } }
        assertTrue(hits.isEmpty(), "text characters drawn as icons; use KitIcon(KitIcons.…) or nothing:\n" + hits.joinToString("\n"))
    }

    @Test
    fun `B-KIT-GLYPH-001 every allowed file still exists and gives a real reason`() {
        allowed.forEach { (path, reason) ->
            assertTrue(File(root, path).isFile, "$path is gone: remove it from the list")
            assertTrue(reason.length > 20, "$path needs a real reason")
        }
    }

    @Test
    fun `B-KIT-GLYPH-001 planted marks are caught and words are not`() {
        val planted =
            """
            KitText("✓", role = TypeRole.Body)
            Text("i")
            KitConsequenceMark.Lost -> "−"
            ComponentState.Failed -> "!"
            KitText(leading.text.take(2), role = TypeRole.Label)
            KitText(name.first().toString())
            KitText("OK")
            KitText(title, maxLines = 1)
            KitText("·", role = TypeRole.Secondary) // a separator between two facts is punctuation
            val x = if (a) "…" else ""
            """.trimIndent()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), TextGlyphs.hits(planted))
    }
}

/** The one-character-mark rules as a pure function over a file's text, returning 1-based line numbers. */
internal object TextGlyphs {
    // A single letter, or a symbol that reads as an icon. Typographic punctuation (·, …, -) is not on the list.
    private const val MARK = """(?:\p{L}|[✓✔✗✕✖×☑☐☒•●○◦▪▸▶►★☆⚠ⓘ!?+−])"""
    private val TEXT_CALL = Regex("""\b(?:KitText|Text|BasicText)\(\s*"$MARK"""")
    private val BRANCH = Regex("""->\s*"$MARK"\s*$""")
    private val INITIALS = Regex("""\b(?:KitText|Text|BasicText)\(\s*[\w.]+\.(?:take\([12]\)|first\(\)|firstOrNull\(\))""")

    fun hits(text: String): List<Int> =
        text.lines().mapIndexedNotNull { i, raw ->
            val line = raw.substringBefore("// ").trim()
            if (line.startsWith("*") || line.startsWith("/*")) null
            else if (TEXT_CALL.containsMatchIn(line) || BRANCH.containsMatchIn(line) || INITIALS.containsMatchIn(line)) i + 1
            else null
        }
}
