package io.github.eslamasabry.oc.engine.agents.signin

/**
 * What an agent's own sign-in printed for the person to act on (port of lib/domain/agent_sign_in_output.dart,
 * AGENTS-BACKENDS-13/14): the page to sign in on and, for a device sign-in, the one-time code to enter there.
 *
 * Read from what the terminal shows and never sent anywhere: the screen only turns these into "Open sign-in page"
 * (through ExternalLinkOpener) and "Copy code" (sensitive copy) buttons. `toString()` never prints either value.
 */
public class SignInOutput(
    /** The newest https page that is not this phone's own (no localhost or loopback callback). */
    public val page: SignInPage? = null,
    /** The one-time code the agent asked the person to enter on [page]. */
    public val code: String? = null,
) {
    public val isEmpty: Boolean get() = page == null && code == null

    /** [next] read over this one: what scrolled out is kept, and a new page brings its own code (or none). */
    public fun merge(next: SignInOutput): SignInOutput {
        val newPage = next.page != null && next.page != page
        return SignInOutput(page = next.page ?: page, code = if (newPage) next.code else (next.code ?: code))
    }

    override fun equals(other: Any?): Boolean = other is SignInOutput && other.page == page && other.code == code

    override fun hashCode(): Int = (page?.hashCode() ?: 0) * 31 + (code?.hashCode() ?: 0)

    override fun toString(): String = "SignInOutput(page=${page != null}, code=${code != null})"
}

/** A checked https sign-in page (no user info, not this phone). [url] is exactly the text the agent printed. */
public class SignInPage internal constructor(
    public val url: String,
    public val host: String,
    private val query: String?,
) {
    /** One decoded query parameter, as Dart's `Uri.queryParameters` reads it (the last of repeated names wins). */
    public fun queryParameter(name: String): String? {
        val q = query ?: return null
        var found: String? = null
        for (pair in q.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val key = decode(if (eq < 0) pair else pair.substring(0, eq))
            if (key == name) found = decode(if (eq < 0) "" else pair.substring(eq + 1))
        }
        return found
    }

    override fun equals(other: Any?): Boolean = other is SignInPage && other.url == url

    override fun hashCode(): Int = url.hashCode()

    override fun toString(): String = "SignInPage(•••)"

    private companion object {
        fun decode(text: String): String {
            val plus = text.replace('+', ' ')
            if ('%' !in plus) return plus
            val bytes = ArrayList<Byte>()
            var i = 0
            while (i < plus.length) {
                val c = plus[i]
                if (c == '%' && i + 2 < plus.length) {
                    val hex = plus.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex != null) {
                        bytes += hex.toByte()
                        i += 3
                        continue
                    }
                }
                bytes += c.toString().encodeToByteArray().toList()
                i++
            }
            return bytes.toByteArray().decodeToString()
        }
    }
}

/** One terminal row: its text, and whether it continues the row above (the terminal wrapped it there). */
public data class SignInRow(val text: String, val wrapped: Boolean = false) {
    override fun toString(): String = "SignInRow(${text.length} chars, wrapped=$wrapped)"
}

public object SignInOutputReader {
    /**
     * Reads [rows] (oldest first) for a sign-in page and code. [width] is the terminal's width in columns; with it, a
     * page a program broke across rows itself (it filled a row to the edge) is put back together.
     */
    public fun read(rows: Iterable<SignInRow>, width: Int? = null): SignInOutput {
        val lines = logicalLines(rows, width)
        var page: SignInPage? = null
        for (line in lines) {
            for (match in URL.findAll(line.text)) signInPage(match.value)?.let { page = it }
        }
        var code: String? = null
        val fromPage = page?.queryParameter("user_code")
        if (fromPage != null && isCode(fromPage)) {
            code = fromPage
        } else {
            val plain = lines.map { it.text.replace(URL, " ") }
            for (i in plain.indices) {
                // A code is only one the output calls a code: on that line or the two after it.
                val near = mentionsCode(plain[i]) || (i > 0 && mentionsCode(plain[i - 1])) ||
                    (i > 1 && mentionsCode(plain[i - 2]))
                if (!near) continue
                for (match in CODE.findAll(plain[i])) if (isCode(match.value)) code = match.value
            }
        }
        return SignInOutput(page, code)
    }

    /** Removes terminal control sequences: colours and cursor moves (CSI), titles and links (OSC), other escapes. */
    public fun stripTerminalControls(text: String): String =
        text.replace(OSC, "").replace(CSI, "").replace(ESCAPE, "").replace(CONTROL, "")

    private val OSC = Regex("""\x1B\][^\x07\x1B]*(?:\x07|\x1B\\)?""")
    private val CSI = Regex("""\x1B\[[0-?]*[ -/]*[@-~]""")
    private val ESCAPE = Regex("""\x1B[@-Z\\-_]?""")
    private val CONTROL = Regex("""[\x00-\x08\x0B-\x1F\x7F]""")
    private val URL = Regex("""https?://[^\s"'<>`]+""", RegexOption.IGNORE_CASE)
    private val CODE = Regex("""(?<![A-Za-z0-9-])[A-Z0-9]{4,}(?:-[A-Z0-9]{4,})+(?![A-Za-z0-9-])""")
    private val LETTER = Regex("[A-Z]")
    private val URL_CHARS = Regex("""^[A-Za-z0-9\-._~:/?#\[\]@!${'$'}&'()*+,;=%]+$""")
    private val URL_STRUCTURE = Regex("""[/?=&%.#]""")
    private val WHITESPACE = Regex("""\s+""")
    private const val TRAILING = ".,;:!?)]}>'\""

    private fun isCode(token: String): Boolean =
        token.length <= 24 && CODE.containsMatchIn(token) && LETTER.containsMatchIn(token)

    private fun mentionsCode(line: String): Boolean = "code" in line.lowercase()

    private fun signInPage(raw: String): SignInPage? {
        var value = raw
        while (value.isNotEmpty() && value.last() in TRAILING) value = value.dropLast(1)
        val schemeEnd = value.indexOf("://")
        if (schemeEnd < 0 || value.substring(0, schemeEnd).lowercase() != "https") return null
        val rest = value.substring(schemeEnd + 3)
        val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        if ('@' in authority) return null
        val host = hostOf(authority)?.lowercase() ?: return null
        if (host.isEmpty() || isThisPhone(host)) return null
        val afterAuthority = rest.substring(authorityEnd)
        val query = if ('?' in afterAuthority) afterAuthority.substringAfter('?').substringBefore('#') else null
        return SignInPage(value, host, query)
    }

    /** Dart `Uri.host`: brackets dropped from an IPv6 literal, the port removed. Null when unparseable. */
    private fun hostOf(authority: String): String? {
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            return authority.substring(1, close)
        }
        val colon = authority.lastIndexOf(':')
        if (colon >= 0) {
            val port = authority.substring(colon + 1)
            if (port.any { !it.isDigit() }) return null
            return authority.substring(0, colon)
        }
        return authority
    }

    private fun isThisPhone(host: String): Boolean =
        host == "localhost" || host.endsWith(".localhost") || host == "0.0.0.0" || host == "::1" ||
            host.startsWith("127.")

    private class Line(var text: String, var lastRow: Int)

    private fun logicalLines(rows: Iterable<SignInRow>, width: Int?): List<Line> {
        val lines = mutableListOf<Line>()
        for (row in rows) {
            val text = stripTerminalControls(row.text).trimEnd()
            val last = lines.lastOrNull()
            if (row.wrapped && last != null) {
                last.text += text
                last.lastRow = text.length
            } else {
                lines += Line(text, text.length)
            }
        }
        if (width == null || width < 20) return lines
        // A program that breaks a long page across rows itself fills each row to the edge.
        val joined = mutableListOf<Line>()
        for (line in lines) {
            val last = joined.lastOrNull()
            if (last != null && continuesUrl(last, line.text, width)) {
                last.text += line.text.trimStart()
                last.lastRow = line.lastRow
            } else {
                joined += line
            }
        }
        return joined
    }

    private fun continuesUrl(above: Line, next: String, width: Int): Boolean {
        if (above.lastRow < width - 1) return false
        val urls = URL.findAll(above.text).toList()
        if (urls.isEmpty() || urls.last().range.last + 1 != above.text.length) return false
        val rest = next.trim()
        if (rest.isEmpty()) return false
        val first = rest.split(WHITESPACE).first()
        if (!URL_CHARS.matches(first)) return false
        return first == rest || URL_STRUCTURE.containsMatchIn(first)
    }
}
