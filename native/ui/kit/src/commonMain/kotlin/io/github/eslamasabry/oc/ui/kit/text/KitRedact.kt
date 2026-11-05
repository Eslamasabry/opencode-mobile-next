package io.github.eslamasabry.oc.ui.kit.text

import kotlinx.serialization.json.*
import kotlin.concurrent.atomics.AtomicReference
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Display/copy masking, ported from kit_redact.dart at 8302de537. */
public object KitRedact {
    private val secrets = AtomicReference<List<String>>(emptyList())

    /** Registers a known secret (≥ 6 characters, like Dart). Longest secrets are masked first. */
    public fun registerKnownSecret(value: String) {
        if (value.length < MIN_SECRET) return
        while (true) {
            val current = secrets.load()
            if (value in current) return
            if (secrets.compareAndSet(current, (current + value).sortedByDescending { it.length })) return
        }
    }

    public fun clearKnownSecrets() { secrets.store(emptyList()) }

    /** Registers every credential-named string in a config tree (Dart registerCredentialValues; B-PROTO-104). */
    public fun registerCredentialValues(value: JsonElement) {
        when (value) {
            is JsonArray -> value.forEach(::registerCredentialValues)
            is JsonObject ->
                for ((key, field) in value) {
                    val name = key.lowercase().replace(Regex("[-_]"), "")
                    if (field is JsonPrimitive && field.isString && name in CREDENTIAL_FIELDS) {
                        registerKnownSecret(field.content)
                        if (name == "authorization" || name == "proxyauthorization") {
                            val space = field.content.indexOf(' ')
                            if (space >= 0) registerKnownSecret(field.content.substring(space + 1))
                        }
                    }
                    if (field is JsonObject || field is JsonArray) registerCredentialValues(field)
                }
            else -> Unit
        }
    }

    /** KitRedact.text: known secrets, private keys, auth headers, bearer, named values, URL user info, keys, JWTs. */
    public fun text(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        for (secret in secrets.load()) out = out.replace(secret, MASK)
        out = PRIVATE_KEY.replace(out, MASK)
        out = scan(out, AUTH_HEADER, ::authValue)
        out = BEARER.replace(out) { "${it.groupValues[1]} $MASK" }
        out = scan(out, NAMED_VALUE, ::namedValueEnd)
        out = URL_USER_INFO.replace(out) { "${it.groupValues[1]}$MASK@" }
        for (key in PROVIDER_KEYS) out = key.replace(out) { "${it.groupValues[1]}$MASK" }
        return maskJwts(out)
    }

    public fun containsSecret(value: String): Boolean = text(value) != value

    /** Long digit-bearing tokens (≥ 32 chars) → mask; letters-only code names stay. */
    public fun opaqueTokens(text: String): String = OPAQUE_TOKEN.replace(text, MASK)

    private class Masked(val text: String, val end: Int)

    private fun scan(s: String, re: Regex, value: (String, MatchResult) -> Masked?): String {
        val out = StringBuilder()
        var cursor = 0
        var from = 0
        while (from < s.length) {
            val m = re.find(s, from) ?: break
            val hit = value(s, m)
            if (hit == null) {
                from = if (m.range.last + 1 > m.range.first) m.range.last + 1 else m.range.first + 1
                continue
            }
            out.append(s, cursor, m.range.first).append(m.value).append(hit.text)
            cursor = hit.end
            from = hit.end
        }
        out.append(s, cursor, s.length)
        return out.toString()
    }

    private fun authValue(s: String, m: MatchResult): Masked? {
        val start0 = m.range.first
        if (inPath(s, start0)) return null
        val lead = m.groupValues[1]
        val keyQuote = m.groupValues[2] // F: lib/ui/kit/kit_redact.dart:229
        if (lead.isEmpty()) {
            var lineStart = start0
            while (lineStart > 0 && s[lineStart - 1] != '\r' && s[lineStart - 1] != '\n') lineStart--
            if (s.substring(lineStart, start0).isNotBlank()) return null
        }
        val start = m.range.last + 1
        if (lead.isNotEmpty() && keyQuote.isEmpty()) {
            val close = closingQuote(s, start, lead[0])
            return masked(start, if (close < 0) lineEnd(s, start) else close)
        }
        if (start < s.length && isQuote(s[start])) return quoted(s, start)
        if (lead.isNotEmpty()) {
            var end = start
            while (end < s.length && s[end] !in ",}]\r\n") end++
            return masked(start, end)
        }
        return masked(start, lineEnd(s, start))
    }

    private fun namedValueEnd(s: String, m: MatchResult): Masked? {
        if (inPath(s, m.range.first)) return null
        val name = m.groupValues[1]
        if (!CREDENTIAL_NAME.matches(name) && !ENV_NAME.matches(name)) return null
        val start = m.range.last + 1
        if (start < s.length && s[start] == '\\') serializedQuoted(s, start)?.let { return it }
        if (start < s.length && isQuote(s[start])) return quoted(s, start)
        if (INVOCATION.matchAt(s, start) != null && DECLARATION.containsMatchIn(s.substring(0, m.range.first))) return null
        var end = start
        while (end < s.length && s[end] !in " \t\r\n\"',;&#<>") {
            if (s.startsWith("*/", end)) break
            end++
        }
        return masked(start, end)
    }

    private fun serializedQuoted(s: String, open: Int): Masked? {
        var quoteAt = open
        while (quoteAt < s.length && s[quoteAt] == '\\') quoteAt++
        if (quoteAt == s.length || !isQuote(s[quoteAt])) return null
        val width = quoteAt - open
        val delimiter = s.substring(open, quoteAt + 1)
        var i = quoteAt + 1
        while (i < s.length && s[i] != '\r' && s[i] != '\n') {
            if (s[i] != '\\') {
                i++
                continue
            }
            val run = i
            while (i < s.length && s[i] == '\\') i++
            if (i < s.length && s[i] == s[quoteAt] && i - run == width) {
                return if (run > quoteAt + 1) Masked("$delimiter$MASK$delimiter", i + 1) else Masked("$delimiter$delimiter", i + 1)
            }
            if (i < s.length) i++
        }
        return null
    }

    private fun masked(start: Int, end: Int): Masked? = if (end > start) Masked(MASK, end) else null

    private fun quoted(s: String, open: Int): Masked? {
        val q = s[open]
        val close = closingQuote(s, open + 1, q)
        if (close < 0) {
            val end = lineEnd(s, open + 1)
            return if (end > open + 1) Masked("$q$MASK", end) else null
        }
        return if (close > open + 1) Masked("$q$MASK$q", close + 1) else null
    }

    private fun closingQuote(s: String, from: Int, quote: Char): Int {
        var i = from
        while (i < s.length) {
            val c = s[i]
            if (c == '\\') {
                i += 2 // F: lib/ui/kit/kit_redact.dart:335
                continue
            }
            if (c == quote) return i
            i++
        }
        return -1
    }

    private fun lineEnd(s: String, from: Int): Int {
        var i = from
        while (i < s.length && s[i] != '\n' && s[i] != '\r') i++
        return i
    }

    private fun isQuote(c: Char) = c == '"' || c == '\''

    private fun inPath(s: String, i: Int): Boolean {
        var start = i
        while (start > 0 && s[start - 1] !in " \t\r\n\"'`,;(){}[]<>|") start--
        val prefix = s.substring(start, i)
        if (prefix == "//" || prefix == "/*") return false
        val scheme = URL_SCHEME.find(prefix)
        if (scheme != null && QUERY_MARK.containsMatchIn(prefix.substring(scheme.range.last + 1))) return false
        return '/' in prefix || '\\' in prefix
    }

    private fun maskJwts(s: String): String {
        val out = StringBuilder()
        var cursor = 0
        var from = 0
        while (from < s.length) {
            val m = JWT_CANDIDATE.find(s, from) ?: break
            if (!isJwt(m.groupValues[1], m.groupValues[2])) { // F: lib/ui/kit/kit_redact.dart:380
                from = m.range.first + 1
                continue
            }
            out.append(s, cursor, m.range.first).append(MASK)
            cursor = m.range.last + 1
            from = m.range.last + 1
        }
        out.append(s, cursor, s.length)
        return out.toString()
    }

    private fun isJwt(header: String, claims: String): Boolean =
        (header.startsWith("eyJ") && claims.startsWith("eyJ")) || (isJsonObject(header) && isJsonObject(claims))

    @OptIn(ExperimentalEncodingApi::class)
    private fun isJsonObject(segment: String): Boolean =
        try {
            val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(segment)
            Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)) is JsonObject
        } catch (_: IllegalArgumentException) {
            false // also SerializationException (a subclass) and CharacterCodingException on bad UTF-8
        } catch (_: CharacterCodingException) {
            false
        }

    public const val MASK: String = "•••"
    private const val MIN_SECRET = 6 // F: lib/ui/kit/kit_redact.dart:49

    private val CREDENTIAL_FIELDS =
        setOf(
            "key", "apikey", "password", "passwd", "secret", "clientsecret", "token", "accesstoken", "refreshtoken",
            "access", "refresh", "authorization", "proxyauthorization", "xapikey", "cookie", "setcookie",
        )

    private val AUTH_HEADER =
        Regex("""(["']?)\b(?:(?:Proxy-)?Authorization|(?:Set-)?Cookie|x-api-key)(["']?)\s*[:=]\s*""", RegexOption.IGNORE_CASE)
    private val BEARER = Regex("""\b(Bearer)\s+[A-Za-z0-9\-._~+/]+=*""", RegexOption.IGNORE_CASE)
    private val NAMED_VALUE =
        Regex(
            """(?<![A-Za-z0-9_\-])([A-Za-z0-9_\-]*?(?:api[_\-]?key|apikey|_key|token|secret|password|passwd))["']?\s*[:=]\s*""",
            RegexOption.IGNORE_CASE,
        )
    private val ENV_NAME = Regex("""^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*_(?:KEY|SECRET|TOKEN|PASSWORD)$""")
    private val CREDENTIAL_NAME =
        Regex(
            """^(?:api[_\-]?key|apikey|access_key|secret_key|private_key|x-api-key|[A-Za-z0-9_\-]*(?:token|secret|password|passwd))$""",
            RegexOption.IGNORE_CASE,
        )
    private val URL_USER_INFO = Regex("""\b([A-Za-z][A-Za-z0-9+.\-]*://)[^\s/?#@]+@""") // NV: URL regex character classes match scheme and user info
    private val URL_SCHEME = Regex("""(?:\b[A-Za-z][A-Za-z0-9+.\-]*:|^|=)//""") // NV: URL regex character classes match scheme and user info
    private val QUERY_MARK = Regex("[?&#]")
    private val PRIVATE_KEY = Regex("""-----BEGIN ((?:[A-Z0-9]+ )*PRIVATE KEY)-----[\s\S]*?-----END \1-----""")
    private val DECLARATION = Regex("""\b(?:const|let|var|final)\s+$""")
    private val INVOCATION = Regex("""(?:await\s+)?[A-Za-z_$][A-Za-z0-9_$.]*\s*\(""")
    private val JWT_CANDIDATE =
        Regex("""(?<![A-Za-z0-9_\-])([A-Za-z0-9_\-]{2,})\.([A-Za-z0-9_\-]{2,})\.[A-Za-z0-9_\-]*(?![A-Za-z0-9_\-])""")
    private val PROVIDER_KEYS =
        listOf(
            Regex("""(?<![A-Za-z0-9_\-])(sk-ant-)[A-Za-z0-9_\-]{8,}"""),
            Regex("""(?<![A-Za-z0-9_\-])(sk-proj-)[A-Za-z0-9_\-]{8,}"""),
            Regex("""(?<![A-Za-z0-9_\-])(sk-)[A-Za-z0-9_\-]{16,}"""),
            Regex("""(?<![A-Za-z0-9_\-])(AIza)[A-Za-z0-9_\-]{30,}"""),
            Regex("""(?<![A-Za-z0-9_\-])(github_pat_)[A-Za-z0-9_]{20,}"""),
            Regex("""(?<![A-Za-z0-9_\-])(gh[pousr]_)[A-Za-z0-9]{20,}"""),
            Regex("""(?<![A-Za-z0-9_\-])(xox[abpr]-)[A-Za-z0-9\-]{10,}"""),
        )
    private val OPAQUE_TOKEN = Regex("""\b(?=[A-Za-z0-9_+./=-]*[0-9])[A-Za-z0-9_+./=-]{32,}\b""")

}
