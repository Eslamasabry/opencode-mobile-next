package io.github.eslamasabry.oc.feature.servers.pairing

import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Why a pairing code could not be read (Flutter `parsePairingPayload` failures; the words are `e7SetupPair*`). */
public enum class PairingProblem {
    Empty,
    TooLong,
    NotJson,
    Shape,
    NoUrls,
    UrlsType,
    TooMany,
    AddressType,
    AddressLong,
    AddressMissing,
    UsernameType,
    PasswordMissing,
    PasswordType,
}

/**
 * What `opencode2 pair` prints (B-PROTO-064, B-SEC-107): the server's addresses in its order, a username and a password.
 * The password is held carefully: [toString] never shows it or an untrusted username, addresses print without
 * credentials, paths or queries, and [consume] drops the reference once the form has it. [urls] cannot be changed.
 */
public class PairingPayload internal constructor(urls: List<String>, public val username: String, password: String) {
    public val urls: List<String> = urls.toImmutableList()
    private var secret: String? = password

    public val isConsumed: Boolean
        get() = secret == null

    /** The password, once: null after [consume]. */
    public fun password(): String? = secret

    public fun consume() {
        secret = null
    }

    override fun toString(): String =
        "PairingPayload(urls=${urls.map(PairingText::safeOrigin)}, " +
            "username=${if (username == DEFAULT_USERNAME) username else "<redacted>"}, " +
            "password=${if (isConsumed) "<consumed>" else "<redacted>"})"

    public companion object {
        public const val MAX_LENGTH: Int = 8192
        public const val MAX_URLS: Int = 32
        public const val MAX_URL_LENGTH: Int = 2048
        public const val DEFAULT_USERNAME: String = "opencode"
    }
}

/** What a piece of pasted or scanned text turned out to be. */
public sealed interface PairingRead {
    public data class Code(val payload: PairingPayload) : PairingRead

    /** A share link: a server address only, never a credential (B-SEC-157). */
    public data class Link(val origin: String) : PairingRead

    public data class Problem(val problem: PairingProblem) : PairingRead
}

/** The pairing rules (B-PROTO-064…067). Pure: no I/O, no logging, and no failure ever echoes the input. */
public object PairingText {
    private val json = Json { isLenient = false }

    /**
     * True for text that looks like a pairing code — including broken ones, so the app can explain them — and false for
     * ordinary typed text or anything over [PairingPayload.MAX_LENGTH] (B-PROTO-066).
     */
    public fun looksLikePayload(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.length < 2 || trimmed.length > PairingPayload.MAX_LENGTH) return false
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        return "\"urls\"" in trimmed || "'urls'" in trimmed
    }

    /** Reads pasted or scanned text: a pairing code, a share link, or a plain problem. A scan and a paste meet here. */
    public fun read(raw: String): PairingRead {
        ShareLink.origin(raw)?.let { return PairingRead.Link(it) }
        return parse(raw)
    }

    /** Parses a pairing code (B-PROTO-064, B-PROTO-065). */
    public fun parse(raw: String): PairingRead {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return problem(PairingProblem.Empty)
        if (trimmed.length > PairingPayload.MAX_LENGTH) return problem(PairingProblem.TooLong)
        // The JSON reader recurses per level; a pasted wall of brackets must be refused before it can overflow the stack.
        if (nestingTooDeep(trimmed)) return problem(PairingProblem.NotJson)
        val decoded: JsonElement =
            try {
                json.parseToJsonElement(trimmed)
            } catch (_: IllegalArgumentException) { // SerializationException is one
                return problem(PairingProblem.NotJson)
            } catch (_: IllegalStateException) {
                return problem(PairingProblem.NotJson)
            }
        if (decoded !is JsonObject) return problem(PairingProblem.Shape)
        if ("urls" !in decoded) return problem(PairingProblem.NoUrls)
        val rawUrls = decoded["urls"] as? JsonArray ?: return problem(PairingProblem.UrlsType)
        if (rawUrls.size > PairingPayload.MAX_URLS) return problem(PairingProblem.TooMany)
        val urls = ArrayList<String>()
        for (entry in rawUrls) {
            val text = (entry as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return problem(PairingProblem.AddressType)
            val url = text.trim()
            if (url.isEmpty()) continue
            if (url.length > PairingPayload.MAX_URL_LENGTH) return problem(PairingProblem.AddressLong)
            if (url !in urls) urls += url
        }
        // The CLI prints `urls[0] ?? "(none)"`: a server can really send no address. Say what to do about it.
        if (urls.isEmpty()) return problem(PairingProblem.AddressMissing)
        val rawUsername = decoded["username"]
        val username =
            when {
                rawUsername == null || rawUsername is JsonNull -> null
                rawUsername is JsonPrimitive && rawUsername.isString -> rawUsername.content.trim()
                else -> return problem(PairingProblem.UsernameType)
            }
        if ("password" !in decoded) return problem(PairingProblem.PasswordMissing)
        val password =
            (decoded["password"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: return problem(PairingProblem.PasswordType)
        return PairingRead.Code(
            PairingPayload(urls, username?.takeIf { it.isNotEmpty() } ?: PairingPayload.DEFAULT_USERNAME, password),
        )
    }

    /**
     * Which address to dial first (B-PROTO-067): a phone prefers routable addresses over the server's loopback, a desktop
     * prefers loopback; order is kept within each group, and an address that cannot be read counts as routable.
     */
    public fun orderCandidates(urls: Iterable<String>, preferLoopback: Boolean): List<String> {
        val (loopback, routable) = urls.partition { url -> hostOf(url)?.let(::isLoopbackHost) == true }
        return if (preferLoopback) loopback + routable else routable + loopback
    }

    /** `scheme://host:port` of [value] for display, or `<invalid address>`: never credentials, a path or a query. */
    public fun safeOrigin(value: String): String {
        val text = value.trim()
        if (text.isEmpty() || text.length > PairingPayload.MAX_URL_LENGTH) return INVALID
        val match = Regex("""^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)""").find(text) ?: return INVALID
        val scheme = match.groupValues[1].lowercase()
        if (scheme != "http" && scheme != "https") return INVALID
        val hostPort = match.groupValues[2].substringAfterLast('@')
        if (hostPort.isEmpty() || hostPort.any { it.isWhitespace() }) return INVALID
        return "$scheme://${hostPort.lowercase()}"
    }

    private fun hostOf(url: String): String? {
        val authority = Regex("""^[A-Za-z][A-Za-z0-9+.-]*://([^/?#]*)""").find(url.trim())?.groupValues?.get(1) ?: return null
        val hostPort = authority.substringAfterLast('@')
        return if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
    }

    private fun isLoopbackHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "localhost" || h == "::1" || h == "[::1]" || h == "0.0.0.0" || h.startsWith("127.")
    }

    private fun problem(p: PairingProblem): PairingRead = PairingRead.Problem(p)

    /** True when brackets outside strings nest deeper than [MAX_DEPTH]. */
    private fun nestingTooDeep(text: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '[' || c == '{' -> if (++depth > MAX_DEPTH) return true
                c == ']' || c == '}' -> depth--
            }
        }
        return false
    }

    private const val MAX_DEPTH = 32

    private const val INVALID = "<invalid address>"
}

/**
 * The share link `opencode-mobile://session/v2?server=…&instance=…&session=…` (Flutter `SessionAddressLink`,
 * B-SEC-157): a credential-free locator. Strict envelope and keys, valid percent escapes decoded once, bounded ASCII;
 * only a full private (Tailscale `*.ts.net`) HTTPS origin is accepted; anything carrying a credential is refused, never
 * repaired. Only the origin is used: it fills the server address, and the person adds the password.
 */
public object ShareLink {
    private const val MAX_LENGTH = 1024
    private val envelope = Regex("""^opencode-mobile://session/v2\?([^#]+)$""", RegexOption.IGNORE_CASE)
    private val badEscape = Regex("""%(?![0-9a-fA-F]{2})""")
    private val uuid = Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""")
    private val session = Regex("""^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$""")
    private val origin = Regex("""^https://([A-Za-z0-9.-]+)(?::([0-9]{1,5}))?$""", RegexOption.IGNORE_CASE)
    private val label = Regex("""^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$""")

    /** The normalised server origin of a valid link, or null for anything else. */
    public fun origin(raw: String): String? {
        val text = raw.trim()
        if (!boundedAscii(text) || '@' in text || badEscape.containsMatchIn(text)) return null
        val match = envelope.matchEntire(text) ?: return null
        if (!text.substringBefore('?').endsWith("/v2")) return null
        val fields = HashMap<String, String>()
        for (pair in match.groupValues[1].split('&')) {
            val at = pair.indexOf('=')
            if (at <= 0 || pair.indexOf('=', at + 1) >= 0) return null
            val key = pair.substring(0, at)
            if (key !in KEYS || key in fields) return null
            val value = decodeOnce(pair.substring(at + 1)) ?: return null
            if (!boundedAscii(value) || '@' in value) return null
            fields[key] = value
        }
        if (fields.size != KEYS.size) return null
        if (!uuid.matches(fields.getValue("instance")) || !session.matches(fields.getValue("session"))) return null
        return normalizeOrigin(fields.getValue("server"))
    }

    private fun normalizeOrigin(value: String): String? {
        val match = origin.matchEntire(value) ?: return null
        val host = match.groupValues[1].lowercase()
        val labels = host.split('.')
        if (host.length > 253 || labels.size != 4 || labels[2] != "ts" || labels[3] != "net") return null
        if (labels.any { !label.matches(it) }) return null
        val port = match.groupValues[2].ifEmpty { "443" }.toInt()
        if (port !in 1..65535) return null
        return if (port == 443) "https://$host" else "https://$host:$port"
    }

    private fun boundedAscii(value: String): Boolean =
        value.isNotEmpty() && value.length <= MAX_LENGTH && value.all { it.code in 0x21..0x7e }

    /** One `application/x-www-form-urlencoded` decode; null on a broken escape or bytes that are not ASCII. */
    private fun decodeOnce(value: String): String? {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '+' -> out.append(' ')
                c == '%' -> {
                    if (i + 2 >= value.length) return null
                    val code = value.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                    if (code > 0x7e) return null
                    out.append(code.toChar())
                    i += 2
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    private val KEYS = setOf("server", "instance", "session")
}
