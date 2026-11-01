package io.github.eslamasabry.oc.core.markdown

/**
 * Inline code that may name a file on the server (B-CHAT-064; Flutter `KitMarkdown.looksLikeFilePath`). Conservative:
 * multi-segment, no spaces, no scheme, anchored (`/`, `~/`, `./`) or ending in an extension of at most 8 characters
 * after the dot, length 4–300. `lib/a/b.dart:12` and `/tmp/shots/home.png` match; `and/or`, URLs and lone words
 * do not.
 */
public object PathChipScanner {
    private val pathLike = Regex("""(?:~/|\.{0,2}/)?[A-Za-z0-9_.@+-]+(?:/[A-Za-z0-9_.@+-]+)+(?::\d{1,6})?""")
    private val lineSuffix = Regex(""":(\d{1,6})(?![\s\S])""")

    public fun looksLikeFilePath(s: String): Boolean {
        if (s.length < 4 || s.length > 300) return false
        if (s.contains("://")) return false
        if (pathLike.matchEntire(s) == null) return false
        if (s.startsWith("/") || s.startsWith("~/") || s.startsWith("./")) return true
        val path = stripLineSuffix(s).first
        val name = path.substring(path.lastIndexOf('/') + 1)
        val dot = name.lastIndexOf('.')
        return dot > 0 && dot < name.length - 1 && name.length - dot <= 9
    }

    /** `lib/a.dart:120` → (`lib/a.dart`, 120). Only a trailing `:digits` is taken; `/tmp/c:120/x.png` stays. */
    public fun stripLineSuffix(s: String): Pair<String, Int?> {
        val m = lineSuffix.find(s) ?: return s to null
        return s.substring(0, m.range.first) to m.groupValues[1].toInt()
    }
}
