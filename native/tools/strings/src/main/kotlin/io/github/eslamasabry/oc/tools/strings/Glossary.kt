package io.github.eslamasabry.oc.tools.strings

import java.io.File

/**
 * The copy glossary gates, ported from Dart `test/ui_glossary_test.dart` (B-L10N-021, B-L10N-022, B-L10N-023) and run
 * over the native English strings (`:ui:words` `composeResources/values/strings_*.xml`).
 *
 * - Labels (short, no sentence punctuation, at most four words) use the glossary verb: "Try again" not "Retry",
 *   "Refresh" not "Check again"/"Check status", "Stop" not "Abort".
 * - Labels say "conversation", never "session" or "chat".
 * - AI Team rows and cards use the person's words, never the engine's (convoy, formula, bead, rig, …, 127.0.0.1, a
 *   version).
 * - No text names two contradicting states at once (Working with stopped, Paused with idle, Connected with
 *   reconnecting).
 * - Technical words (addresses, ports, paths, versions, status codes, engine words) are counted per key; the committed
 *   baseline only shrinks.
 */
object Glossary {
    /** Stands in for a placeholder in a plain variant of a message; a placeholder counts as one word. */
    const val SLOT: String = "§"

    val BANNED_TERMS: List<String> = listOf("Retry", "Check again", "Check status", "Abort")
    val BANNED_NOUNS: List<String> = listOf("session", "sessions", "chat", "chats")
    val TEAM_SURFACE_PREFIXES: List<String> =
        listOf("teamUiCard", "teamUiHome", "teamUiRun", "teamUiTask", "teamUiAgentRole", "teamUiAgentsList", "teamUiHostPhrase", "teamBoard")
    val ENGINE_WORDS: Regex =
        Regex(
            """\b(convoys?|formulas?|beads?|rigs?|city|polecats?|refinery|sling|wisps?|mayor|gastown)\b|127\.0\.0\.1|\{version}|\b\d+\.\d+\.\d+\b""",
            RegexOption.IGNORE_CASE,
        )
    val CONTRADICTIONS: List<Pair<String, String>> =
        listOf("working" to "stopped", "paused" to "idle", "connected" to "reconnecting")

    private const val MAX_LABEL_WORDS = 4
    private val SENTENCE_PUNCTUATION = Regex("""[.!?:;,…]""")

    fun isShortActionLabel(value: String): Boolean {
        val t = value.trim()
        if (t.isEmpty() || SENTENCE_PUNCTUATION.containsMatchIn(t)) return false
        return t.split(Regex("""\s+""")).size <= MAX_LABEL_WORDS
    }

    fun bannedTermIn(value: String): String? =
        BANNED_TERMS.firstOrNull { Regex("^" + Regex.escape(it) + "\\b").containsMatchIn(value.trim()) }

    fun bannedNounIn(value: String): String? =
        BANNED_NOUNS.firstOrNull { Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(value) }

    fun contradictionIn(text: String): Pair<String, String>? =
        CONTRADICTIONS.firstOrNull { (a, b) ->
            Regex("\\b$a\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
                Regex("\\b$b\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }

    private val TECHNICAL: Map<String, Regex> =
        linkedMapOf(
            "engine word" to
                Regex("""\b(convoys?|formulas?|beads?|rigs?|city|polecats?|refinery|sling|wisps?|mayor|gastown|PTY|SSE)\b""", RegexOption.IGNORE_CASE),
            "address" to Regex("""(?<![\w.])\d{1,3}(?:\.\d{1,3}){3}(?:/\d{1,2})?(?![\w.])|\blocalhost\b"""),
            "port" to Regex(""":\d{4,5}\b|\bport \d{2,5}\b""", RegexOption.IGNORE_CASE),
            "path" to
                Regex("""(?:^|[\s("'`])(?:~|\.{1,2})?/[\w.-]+/[\w./-]*|~/|(?<![\w/:.-])[\w.-]+/[\w./-]*\.[A-Za-z]{1,5}\b"""),
            "version" to
                Regex(
                    """(?<![\d.])v?\d+\.\d+\.\d+\b(?!\.\d)""" +
                        """|(?:\bv|\b[Vv]ersion\s+|\b[A-Z][\w-]*\s)\d+\.\d+(?![.\d])""" +
                        """(?!\s*(?:%|[KMGT]i?B\b|(?:mins?|minutes?|secs?|seconds?|hours?|days?|weeks?|times)\b))""" +
                        """|\{\w*[vV]ersion\w*}""",
                ),
            "status code" to Regex("""\b(?:HTTP|status|code|error)\s*[1-5]\d{2}\b""", RegexOption.IGNORE_CASE),
        )

    /** Every technical hit in [value], one per match. "Gas City" is a product name, so its "City" is not an engine word. */
    fun technicalIn(value: String): List<String> {
        val text = value.replace(Regex("Gas City", RegexOption.IGNORE_CASE), "")
        return TECHNICAL.flatMap { (name, re) -> re.findAll(text).map { "$name \"${it.value.trim()}\"" }.toList() }
    }

    // ---- ICU / Android format variants ----

    private const val MAX_VARIANTS = 64

    /** Every plain wording of an ICU [message]: each plural or select branch is a variant; placeholders become [SLOT]. */
    fun variants(message: String): List<String> = parseIcu(message, 0, inBranch = false).first.map { it.trim() }

    private fun parseIcu(s: String, start: Int, inBranch: Boolean): Pair<List<String>, Int> {
        var variants = listOf("")
        fun append(text: String) {
            variants = variants.map { it + text }
        }
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (c == '}' && inBranch) return variants to i
            if (c == '#' && inBranch) {
                append(SLOT)
                i++
                continue
            }
            if (c != '{') {
                append(c.toString())
                i++
                continue
            }
            val close = argumentEnd(s, i)
            val inner = s.substring(i + 1, close)
            val comma = inner.indexOf(',')
            if (comma < 0) {
                append(SLOT)
                i = close + 1
                continue
            }
            val rest = inner.substring(comma + 1)
            val typeComma = rest.indexOf(',')
            val type = (if (typeComma < 0) rest else rest.substring(0, typeComma)).trim()
            if (typeComma < 0 || type !in setOf("plural", "select", "selectordinal")) {
                append(SLOT)
                i = close + 1
                continue
            }
            var j = i + 1 + comma + 1 + typeComma + 1
            val branches = mutableListOf<String>()
            while (j < close) {
                val open = s.indexOf('{', j)
                if (open < 0 || open > close) break
                val (branch, end) = parseIcu(s, open + 1, inBranch = true)
                branches += branch
                j = end + 1
            }
            if (branches.isEmpty()) branches += SLOT
            variants = variants.flatMap { v -> branches.map { v + it } }.take(MAX_VARIANTS)
            i = close + 1
        }
        return variants to i
    }

    private fun argumentEnd(s: String, open: Int): Int {
        var depth = 0
        for (i in open until s.length) {
            if (s[i] == '{') depth++
            if (s[i] == '}') {
                depth--
                if (depth == 0) return i
            }
        }
        return s.length - 1
    }

    /** The words of a plain variant ([SLOT] is one word). */
    fun wordsOf(variant: String): List<String> =
        variant.replace(SLOT, " $SLOT ").split(Regex("""[\s·—–]+""")).filter { it.isNotEmpty() && it.any(Char::isLetterOrDigit) || it == SLOT }

    // ---- the native English strings ----

    private val STRING = Regex("""<string name="([\w.]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val PLURALS = Regex("""<plurals name="([\w.]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
    private val ITEM = Regex("""<item quantity="(\w+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    private val FORMAT_ARG = Regex("""%(\d+\$)?[sd]""")

    /**
     * The English strings of [valuesDir] as key → plain text, Android format arguments as [SLOT]. A plural item is its own
     * entry `key#quantity`; an `__eqN` helper string (the converter's exact-count branch) counts as its plural's.
     */
    fun englishStrings(valuesDir: File): Map<String, String> {
        val out = linkedMapOf<String, String>()
        valuesDir.listFiles().orEmpty().filter { it.name.startsWith("strings") && it.extension == "xml" }.sortedBy { it.name }.forEach { f ->
            val xml = f.readText()
            STRING.findAll(xml).forEach { out[it.groupValues[1]] = plain(it.groupValues[2]) }
            PLURALS.findAll(xml).forEach { p ->
                ITEM.findAll(p.groupValues[2]).forEach { out["${p.groupValues[1]}#${it.groupValues[1]}"] = plain(it.groupValues[2]) }
            }
        }
        return out
    }

    private fun plain(raw: String): String =
        XmlText.cmpUnescape(raw.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")).replace(FORMAT_ARG, SLOT)

    /** The Flutter key a native key came from (`kitSinceAge__eq0` and `kitSinceAge#other` → `kitSinceAge`). */
    fun baseKey(key: String): String = key.substringBefore('#').substringBefore("__")

    // ---- the gates ----

    fun actionLabelOffenders(strings: Map<String, String>, allowed: Set<String>): List<String> =
        strings.mapNotNull { (k, v) ->
            if (baseKey(k) in allowed || !isShortActionLabel(v)) return@mapNotNull null
            bannedTermIn(v)?.let { "$k: \"$v\" starts with \"$it\"" }
        }

    fun nounOffenders(strings: Map<String, String>, allowed: Set<String>): List<String> =
        strings.mapNotNull { (k, v) ->
            if (baseKey(k) in allowed || !isShortActionLabel(v)) return@mapNotNull null
            bannedNounIn(v)?.let { "$k: \"$v\" contains \"$it\"" }
        }

    fun engineOffenders(strings: Map<String, String>, allowed: Set<String>): List<String> =
        strings.mapNotNull { (k, v) ->
            if (TEAM_SURFACE_PREFIXES.none { k.startsWith(it) } || baseKey(k) in allowed) return@mapNotNull null
            ENGINE_WORDS.find(v)?.let { "$k: \"$v\" contains \"${it.value}\"" }
        }

    fun contradictionOffenders(strings: Map<String, String>, allowed: Set<String>): List<String> =
        strings.mapNotNull { (k, v) ->
            if (baseKey(k) in allowed) return@mapNotNull null
            contradictionIn(v)?.let { (a, b) -> "$k: \"$v\" says $a and $b" }
        }

    /** Technical-word counts per key (the ratchet's unit). */
    fun technicalCounts(strings: Map<String, String>): Map<String, Int> =
        strings.mapNotNull { (k, v) -> technicalIn(v).size.takeIf { it > 0 }?.let { k to it } }.toMap()
}
