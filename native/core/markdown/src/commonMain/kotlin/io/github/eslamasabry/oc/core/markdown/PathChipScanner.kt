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
    private val bareName = Regex("""[A-Za-z0-9_@+-][A-Za-z0-9_.@+-]*\.[A-Za-z0-9]*[A-Za-z][A-Za-z0-9]*(?::\d{1,6})?""")

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

    /**
     * A bare file name with no folder (`notes.txt`, `README.md:3`): one segment, an extension of at most 8 characters
     * with a letter in it (so `3.0.1` and `1.5` are not names). Only a chat candidate: the engine makes it a chip only
     * when it is exactly a file at the conversation's top level, so `console.log` and the like stay plain code.
     */
    public fun looksLikeBareFileName(s: String): Boolean {
        if (s.length < MIN_NAME || s.length > MAX_NAME || bareName.matchEntire(s) == null) return false
        val name = stripLineSuffix(s).first
        return name.length - name.lastIndexOf('.') - 1 <= MAX_EXTENSION
    }

    /** Inline code a chat reply may make a file chip: a path ([looksLikeFilePath]) or a bare file name. */
    public fun looksLikeChatFile(s: String): Boolean = looksLikeFilePath(s) || looksLikeBareFileName(s)

    private const val MIN_NAME = 4
    private const val MAX_NAME = 300
    private const val MAX_EXTENSION = 8

    /** `lib/a.dart:120` → (`lib/a.dart`, 120). Only a trailing `:digits` is taken; `/tmp/c:120/x.png` stays. */
    public fun stripLineSuffix(s: String): Pair<String, Int?> {
        val m = lineSuffix.find(s) ?: return s to null
        return s.substring(0, m.range.first) to m.groupValues[1].toInt()
    }
}
