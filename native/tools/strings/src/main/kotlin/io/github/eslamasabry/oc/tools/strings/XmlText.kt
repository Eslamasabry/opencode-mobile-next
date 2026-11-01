package io.github.eslamasabry.oc.tools.strings

/**
 * Escaping of one string value for a `<string>` or `<item>` element.
 *
 * What CMP does with a value (compose-gradle-plugin 1.12.2, `PrepareComposeResources.kt`): the XML is parsed with
 * a DOM parser, `textContent` is taken, then `handleSpecialCharacters` turns `\n`, `\t` and `\uXXXX` into the
 * character and `\\` into `\`. **Nothing else is unescaped**: `\'`, `\"`, `\@` and `\?` would reach the screen
 * with their backslash. Whitespace is not collapsed.
 *
 * Android's aapt2 does more: an unescaped `'` or `"` changes the parse, a leading `@` or `?` makes a reference,
 * and runs of whitespace collapse. So there are two profiles:
 *
 * - [Profile.CMP] for the feature files (`strings_<feature>.xml`), read only by CMP. Apostrophes and quotes stay
 *   as typed, which keeps 600+ English strings readable for the people who edit them from now on.
 * - [Profile.ANDROID] for `strings_os.xml`, which CMP reads **and** `:androidApp` copies into Android `res`. Every
 *   character that means something to aapt2 is written as a `\uXXXX` escape, which both readers decode to the
 *   same character.
 *
 * In both profiles: `&`, `<`, `>` are XML-escaped; newline and tab become `\n` and `\t`; a literal backslash
 * becomes `\\`; bidi controls, zero-width and other invisible characters become `\uXXXX` so a reviewer can see them.
 */
object XmlText {
    enum class Profile { CMP, ANDROID }

    fun escape(value: String, profile: Profile): String {
        val out = StringBuilder(value.length + 16)
        val last = value.length - 1
        for ((i, c) in value.withIndex()) {
            when {
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\t' -> out.append("\\t")
                c == '&' -> out.append("&amp;")
                c == '<' -> out.append("&lt;")
                c == '>' -> out.append("&gt;")
                isInvisible(c) -> out.append(u(c))
                profile == Profile.ANDROID && (c == '\'' || c == '"') -> out.append(u(c))
                profile == Profile.ANDROID && i == 0 && (c == '@' || c == '?') -> out.append(u(c))
                profile == Profile.ANDROID && c == ' ' && (i == 0 || i == last || value[i - 1] == ' ') -> out.append(u(c))
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /** Invisible or direction-changing characters, plus C0/C1 controls other than newline and tab. */
    fun isInvisible(c: Char): Boolean =
        (c < ' ' && c != '\n' && c != '\t') ||
            c in '\u007F'..'\u009F' ||
            c == '\u00A0' || c == '\u00AD' || c == '\u061C' ||
            c in '\u200B'..'\u200F' ||
            c in '\u2028'..'\u202E' ||
            c in '\u2060'..'\u2069' ||
            c == '\uFEFF'

    private fun u(c: Char) = "\\u%04X".format(c.code)

    /**
     * A faithful copy of CMP 1.12.2 `handleSpecialCharacters`, applied after the DOM parser. Tests use it to prove
     * that what we write reads back as the ARB text.
     */
    fun cmpUnescape(string: String): String {
        val unicodeNewLineTabRegex = Regex("""\\u[a-fA-F\d]{4}|\\n|\\t""")
        val doubleSlashRegex = Regex("""\\\\""")
        val doubleSlashIndexes = doubleSlashRegex.findAll(string).map { it.range.first }
        val handledString = unicodeNewLineTabRegex.replace(string) { matchResult ->
            if (doubleSlashIndexes.contains(matchResult.range.first - 1)) {
                matchResult.value
            } else {
                when (matchResult.value) {
                    "\\n" -> "\n"
                    "\\t" -> "\t"
                    else -> matchResult.value.substring(2).toInt(16).toChar().toString()
                }
            }
        }.replace("""\\""", """\""")
        return handledString
    }
}
