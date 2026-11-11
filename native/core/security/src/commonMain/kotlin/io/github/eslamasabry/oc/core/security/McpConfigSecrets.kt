package io.github.eslamasabry.oc.core.security

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The secrets an MCP server's configuration carries, registered by VALUE wherever [Redactor.registerCredentialValues]
 * walks a config tree (every `/config` read: setup, the configuration preview, the agent catalogue, the setup
 * assistant). The walker alone registers only values under credential-NAMED members, and MCP names are free: an
 * `environment` entry `GITHUB_TOKEN` or a header `X-Custom-Token` was never registered, so its value could reach a
 * preview, a tool row or a log line in plain. The same values of an LSP server (`env`), a formatter (`environment`) and
 * the custom `headers` of a provider, a model, a model variant or an agent request are read by the same rule.
 *
 * Registered (each subject to the redactor's own 6-character minimum):
 * - every `environment`/`env` value and every `headers` value (as a map, a `NAME=value` / `Name: value` list, or a
 *   list of `{name, value}`), and for an `Authorization`-style value ("Bearer …") the credential after the scheme
 *   too, unless it is plainly ordinary under an ordinary name ([ConfigValues.isOrdinaryConfigText]) or is a
 *   non-token value of a standard protocol header ([STANDARD_HEADERS]);
 * - the literal text around a `{env:…}` reference ("{env:USER}:literal" registers "literal");
 * - in `command`/`args` words (a string command is split like a shell would, quotes kept together): the value of a
 *   credential-named `NAME=value`, `--name=value` and `--name value` (and the token after a scheme word there), an
 *   environment-style `NAME=value` by the env rule, every header value after `--header`/`-H`, and any token-like word;
 * - in URLs (the server's `url`, OAuth URLs, URL-shaped values and command words): the userinfo password (or a
 *   token-like userinfo with no password: `https://ghp_…@host`), credential-named or token-like query and fragment
 *   values, a token-like fragment, and token-like path segments (a webhook or a per-user MCP link keeps its secret in
 *   the path).
 *
 * Never registered: env/header NAMES, server names, program names, a URL as a whole or its host, a lone scheme word
 * ("Bearer"), an on/off word, a pure reference, and an ordinary value under an ordinary name. The redactor masks a
 * registered value everywhere, inside longer words too, so registering an ordinary word ("OpenCode", "production")
 * corrupts every text that contains it.
 */
internal fun Redactor.registerMcpServers(container: JsonObject) {
    for ((name, value) in container) {
        val item = value as? JsonObject ?: continue
        when {
            // OpenCode 2 nests the servers one level down: `mcp.servers.<name>` (2.1.1 and 2.2.5 Config.InfoEncoded).
            // Checked first: a server the person named `env` or `headers` must not make the map look like one server.
            name == "servers" && !hasServerShape(item) ->
                for (server in item.values) {
                    if (server is JsonObject && isMcpServer(server)) registerServerValues(server)
                }
            isMcpServer(item) -> registerServerValues(item)
        }
    }
}

/**
 * LSP servers (`lsp.<name>`: `command`, `env`) and formatters (`formatter.<name>`: `command`, `environment`), 2.2.5
 * Config.LSP.ServerEncoded / Config.Formatter.EntryEncoded: the same values as a local MCP server.
 */
internal fun Redactor.registerToolServers(container: JsonObject) {
    for (item in container.values) if (item is JsonObject) registerServerValues(item)
}

/**
 * Every `headers` member under a provider, model, model variant or agent request of a `/config` tree (2.2.5
 * `providers.<id>.headers`, `.models.<id>.headers`, `.models.<id>.variants[].headers`, `agents.<name>.request.headers`;
 * older `provider.<id>.options.headers`), by the header rule.
 */
internal fun Redactor.registerHeaderMaps(subtree: JsonElement) {
    when (subtree) {
        is JsonArray -> subtree.forEach { registerHeaderMaps(it) }
        is JsonObject ->
            for ((name, field) in subtree) {
                if (name.equals("headers", ignoreCase = true)) registerPairs(field, HEADER_SEPARATORS, headers = true)
                if (field is JsonObject || field is JsonArray) registerHeaderMaps(field)
            }
        else -> Unit
    }
}

/**
 * A local or remote server object: a known `type`, a `command`, a `url` (2.2.5 Mcp.Local/RemoteConfigEncoded), or an
 * environment/headers map or list (a partial entry is still a server's values).
 */
private fun isMcpServer(item: JsonObject): Boolean =
    hasServerShape(item) || PAIR_MEMBERS.any { item[it] is JsonObject || item[it] is JsonArray }

/** A known `type` string, a `command` (list or string) or a `url` string: a server, never a map of servers. */
private fun hasServerShape(item: JsonObject): Boolean {
    val type = (item["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.lowercase()
    val command = item["command"]
    return (type != null && type in SERVER_TYPES) ||
        command is JsonArray ||
        (command as? JsonPrimitive)?.isString == true ||
        (item["url"] as? JsonPrimitive)?.isString == true
}

private fun Redactor.registerServerValues(server: JsonObject) {
    registerPairs(server["environment"], ENV_SEPARATORS, headers = false)
    registerPairs(server["env"], ENV_SEPARATORS, headers = false)
    registerPairs(server["headers"], HEADER_SEPARATORS, headers = true)
    val words = ArrayList<String>()
    when (val command = server["command"]) {
        is JsonArray -> words += commandWords(strings(command))
        is JsonPrimitive -> if (command.isString) words += shellWords(command.content)
        else -> Unit
    }
    (server["args"] as? JsonArray)?.let { words += commandWords(strings(it)) }
    registerCommandWords(words)
    (server["url"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { registerUrlCredentials(it) }
    // Mcp.OAuthConfigEncoded: client_secret is credential-named (the walker has it); its URLs may carry a key.
    (server["oauth"] as? JsonObject)?.values?.forEach { value ->
        (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { registerUrlCredentials(it) }
    }
}

/** Each element, and the shell words of an element that holds a whole command line (`["sh", "-c", "… --token x"]`). */
private fun commandWords(elements: List<String>): List<String> =
    elements.flatMap { element -> if (element.any(Char::isWhitespace)) listOf(element) + shellWords(element) else listOf(element) }

private fun strings(array: JsonArray): List<String> =
    array.mapNotNull { (it as? JsonPrimitive)?.takeIf { word -> word.isString }?.content }

/** `NAME=value` pairs as a map, a list of `NAME=value` (or `Name: value`) strings, or a list of `{name, value}`. */
private fun Redactor.registerPairs(member: JsonElement?, separators: String, headers: Boolean) {
    val pairs: List<Pair<String, String>> =
        when (member) {
            is JsonObject -> member.mapNotNull { (name, value) -> scalar(value)?.let { name to it } }
            is JsonArray ->
                member.mapNotNull { item ->
                    when (item) {
                        is JsonPrimitive -> {
                            val line = item.takeIf { it.isString }?.content ?: return@mapNotNull null
                            val at = line.indexOfFirst { it in separators }
                            if (at > 0) line.substring(0, at).trim() to line.substring(at + 1) else null
                        }
                        is JsonObject -> {
                            val name = scalar(item["name"]) ?: scalar(item["key"]) ?: return@mapNotNull null
                            scalar(item["value"])?.let { name to it }
                        }
                        else -> null
                    }
                }
            else -> emptyList()
        }
    for ((name, value) in pairs) registerConfigValue(name, value, standardHeader = headers && name.lowercase() in STANDARD_HEADERS)
}

/** A string or number value; never a boolean, null, object or list. */
private fun scalar(value: JsonElement?): String? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    if (!primitive.isString && (primitive.content == "true" || primitive.content == "false")) return null
    return primitive.content
}

/**
 * An env or header value: registered whole unless plainly ordinary under an ordinary name, plus its scheme-less token,
 * the literal text around references, and its URL credentials. A URL under an ordinary name is an address: only its
 * credentials are registered, so its host stays readable. A standard header (`Content-Type`, `User-Agent`, `X-Title`
 * …) is protocol text: only URL credentials in it are registered.
 */
private fun Redactor.registerConfigValue(name: String, value: String, standardHeader: Boolean = false) {
    val text = value.trim()
    if (text.isEmpty() || ConfigValues.isReference(text) || ConfigValues.isSchemeOrSwitchWord(text)) return
    if (ConfigValues.containsReference(text)) {
        // "{env:USER}:literal" reaches the server and its output as "<user>:literal": the literal is what shows.
        for (piece in ConfigValues.literalPieces(text)) registerConfigValue(name, piece, standardHeader)
        registerUrlCredentials(ConfigValues.withoutReferences(text))
    }
    if (!ConfigValues.isCredentialName(name)) {
        // A token-shaped value is registered whatever the header is called; anything else under a standard header is
        // protocol text.
        val tokenShaped = ConfigValues.looksLikeToken(text) || AUTH_SCHEME.matches(text)
        if ((standardHeader && !tokenShaped) || ConfigValues.isOrdinaryConfigText(text)) {
            registerUrlCredentials(text)
            return
        }
        if (URL_START.containsMatchIn(text) && text.none(Char::isWhitespace)) {
            registerUrlCredentials(text)
            return
        }
    }
    registerSecret(value)
    registerSecret(text)
    AUTH_SCHEME.matchEntire(text)?.let { registerSecret(it.groupValues[2].trim()) }
    registerUrlCredentials(text)
}

private fun Redactor.registerCommandWords(words: List<String>) {
    for ((index, word) in words.withIndex()) {
        val next = words.getOrNull(index + 1)
        val equals = word.indexOf('=')
        val flag = word.startsWith("-")
        if (equals > 0) {
            val name = word.substring(0, equals).trimStart('-')
            val value = word.substring(equals + 1)
            when {
                flag && name.lowercase() in HEADER_FLAGS -> registerHeaderLine(value)
                ConfigValues.isCredentialName(name) -> registerConfigValue(name, value)
                // `env NAME=value cmd`, `docker run -e NAME=value`: an environment value, by the env rule.
                !flag && ENV_STYLE_NAME.matches(name) -> registerConfigValue(name, value)
                ConfigValues.looksLikeToken(value) -> registerSecret(value)
            }
        } else if (flag && next != null && !next.startsWith("-")) {
            val name = word.trimStart('-')
            when {
                word in HEADER_FLAG_WORDS -> registerHeaderLine(next)
                ConfigValues.isCredentialName(name) -> {
                    registerConfigValue(name, next)
                    // `--token Bearer abc`: the scheme word is not the secret, the word after it is.
                    if (ConfigValues.isSchemeOrSwitchWord(next)) words.getOrNull(index + 2)?.let { registerConfigValue(name, it) }
                }
            }
        } else if (!flag && ConfigValues.looksLikeToken(word)) {
            registerSecret(word)
        }
        registerUrlCredentials(word)
    }
}

/** `Name: value` after a header flag; every header value is registered by the header rule, as in a `headers` map. */
private fun Redactor.registerHeaderLine(line: String) {
    val colon = line.indexOf(':')
    if (colon <= 0) return
    val name = line.substring(0, colon).trim()
    registerConfigValue(name, line.substring(colon + 1), standardHeader = name.lowercase() in STANDARD_HEADERS)
}

/** The credential parts of every URL in [text], never the URL or its host: those stay readable around the mask. */
private fun Redactor.registerUrlCredentials(text: String) {
    for (match in URL.findAll(text)) for (part in urlSecrets(match.value)) registerSecret(part)
}

/**
 * The userinfo password, credential-named or token-like query and fragment values, a token-like fragment and
 * token-like path segments of [url].
 */
private fun urlSecrets(url: String): List<String> {
    val rest = url.substringAfter("://")
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    val out = ArrayList<String>()
    val at = authority.lastIndexOf('@')
    if (at > 0) {
        val user = authority.substring(0, at)
        val colon = user.indexOf(':')
        // `user:password@`: the password. A bare `name@` is a user name, unless it looks like a token (`ghp_…@host`).
        if (colon >= 0) out += user.substring(colon + 1)
        else if (ConfigValues.looksLikeToken(user)) out += user
    }
    val afterAuthority = rest.substring(authority.length)
    val path = afterAuthority.substringBefore('?').substringBefore('#')
    path.split('/').filterTo(out) { ConfigValues.looksLikeToken(it) }
    val query = afterAuthority.substringBefore('#').substringAfter('?', "")
    // An OAuth implicit-flow or a share link keeps its token in the fragment: `#access_token=…` or `#<token>`.
    val fragment = afterAuthority.substringAfter('#', "")
    if (fragment.isNotEmpty() && '=' !in fragment && ConfigValues.looksLikeToken(fragment)) out += fragment
    for (pair in query.split('&') + fragment.split('&', ';')) {
        val equals = pair.indexOf('=')
        if (equals <= 0) continue
        val name = pair.substring(0, equals)
        val value = pair.substring(equals + 1)
        if (ConfigValues.isCredentialName(name) || ConfigValues.looksLikeToken(value)) out += value
    }
    return out.filter { it.isNotEmpty() }
}

/** A command line split into words as a POSIX shell would: whitespace separates, quotes and backslashes join. */
private fun shellWords(line: String): List<String> {
    val out = ArrayList<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var started = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            quote != null ->
                when {
                    c == quote -> quote = null
                    c == '\\' && quote == '"' && i + 1 < line.length -> current.append(line[++i])
                    else -> current.append(c)
                }
            c == '\'' || c == '"' -> {
                quote = c
                started = true
            }
            c == '\\' && i + 1 < line.length -> {
                current.append(line[++i])
                started = true
            }
            c.isWhitespace() ->
                if (started || current.isNotEmpty()) {
                    out += current.toString()
                    current.clear()
                    started = false
                }
            else -> current.append(c)
        }
        i++
    }
    if (started || current.isNotEmpty()) out += current.toString()
    return out
}

/**
 * The shape rules for configuration values and typed answers. Conservative by design: anything not plainly ordinary is
 * treated as a possible secret.
 */
public object ConfigValues {
    /**
     * A name that announces a credential: `*TOKEN`, `*SECRET`, `*PASSWORD`/`*PASSWD`/`*PASS`/`*PWD`, `*KEY` (API_KEY,
     * SSH_KEY, x-api-key), `*CREDENTIAL(S)`, `*AUTH` (NOTION_AUTH, X-Auth), `*AUTHORIZATION`, `*COOKIE`,
     * `*PASSPHRASE`/`*PASSCODE`, a `PIN`/`OTP` part, `pat`, `sig`/`signature`, `jwt`. Case, dashes, underscores and
     * leading `--` are ignored. `MAX_TOKENS` is not one (a plural count, not a token), nor `PWD`/`OLDPWD` (folders) or
     * `author`.
     */
    public fun isCredentialName(name: String): Boolean {
        val trimmed = name.trim().trimStart('-')
        val plain = trimmed.lowercase().replace(SEPARATORS, "")
        if (plain.isEmpty()) return false
        if (plain == "pwd" || plain == "oldpwd") return false
        return plain in CREDENTIAL_WORDS || CREDENTIAL_ENDINGS.any { plain.endsWith(it) } || SHORT_SECRET_PART.containsMatchIn(trimmed)
    }

    /**
     * A value that is plainly not a secret, so registering it would only mask ordinary text: a short number (fewer than
     * eight digits: `8080`, `300000`), a version or IP (`1.2.3`, `127.0.0.1`), a date or time (`2025-06-18`), a media
     * type (`application/json`), a cloud region (`us-east-1`, `us-central1`), one of a short list of ordinary words
     * (`production`, `default`, `localhost`, `enabled` …), a file path or a `:`-separated list of paths with no
     * token-like or password-shaped part (`/usr/bin/node`, `~/.config/app`), or only a reference to another value
     * ([isReference]). Everything else is not plainly ordinary: a long number (an account number, a PIN, a card), a
     * bare `$word` (it may be a password that starts with a dollar sign), a UUID. Typed sign-in answers use this rule
     * as is; configuration values add [isOrdinaryConfigText].
     */
    public fun isPlainlyNotSecret(value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty()) return true
        if (SHORT_NUMBER.matches(text) || VERSION.matches(text) || DATE_TIME.matches(text) || PORT_RANGE.matches(text)) return true
        if (MEDIA_TYPE.matches(text) || REGION.matches(text)) return true
        if (text.lowercase() in ORDINARY_WORDS) return true
        return isPath(text) || isReference(text)
    }

    /**
     * An env or header value that reads as ordinary words, for a configuration value under a name that is not
     * credential-like: [isPlainlyNotSecret], or
     * - one natural word of letters only, at most 20 long (all lower case, Capitalised, at most two inner capitals like
     *   `OpenCode`/`GitHub`, or all capitals up to 12), with no run of six consonants;
     * - two or three such words with single spaces (`OpenCode Mobile`), at most 32 long, not starting with a scheme;
     * - a host name with an optional port (`mcp.example.com`, `localhost:4096`), no label token-like;
     * - a locale (`en_US.UTF-8`) or a list of media types (`application/json, text/event-stream`);
     * - a lower-case model-or-name slug (`gpt-4o-mini`, `claude-sonnet-4-5`) with a word part of three letters or more
     *   and a short part of three characters or fewer, at most 40 long, no part mixing letters and digits over 7.
     * A random-case or digit-bearing run (`xKqPzLmN`, `ab3dE5fG`) and anything longer is not ordinary.
     */
    public fun isOrdinaryConfigText(value: String): Boolean {
        val text = value.trim()
        if (isPlainlyNotSecret(text)) return true
        if (isNaturalWord(text)) return true
        val words = text.split(' ')
        if (words.size in 2..3 && text.length <= 32 && words.all(::isNaturalWord) && words.first().lowercase() !in AUTH_SCHEMES)
            return true
        if (HOST.matches(text) && text.substringBefore(':').split('.').none { looksLikeToken(it) || isPasswordShaped(it) }) return true
        if (LOCALE.matches(text) || MEDIA_LIST.matches(text)) return true
        return isSlug(text)
    }

    /** Only a reference to a value kept elsewhere: `{env:NAME}`, `{file:path}` or `${NAME}`, after an optional scheme. */
    public fun isReference(value: String): Boolean {
        val text = value.trim()
        val rest = REFERENCE.replace(text, "").trim()
        return rest != text && (rest.isEmpty() || rest.lowercase() in AUTH_SCHEMES)
    }

    /** Holds at least one `{env:…}`/`{file:…}`/`${…}` reference. */
    public fun containsReference(value: String): Boolean = REFERENCE.containsMatchIn(value)

    /** The literal text between references, stripped of the punctuation that joins it to them. */
    public fun literalPieces(value: String): List<String> =
        REFERENCE.split(value).map { it.trim(*PIECE_JOINERS) }.filter { it.isNotEmpty() }

    /** [value] with each reference replaced by a neutral word, so a URL around a reference still parses. */
    public fun withoutReferences(value: String): String = REFERENCE.replace(value, "ref")

    /** A lone auth scheme word ("Bearer") or an on/off word ("true", "enabled"): never a secret, whatever the name. */
    public fun isSchemeOrSwitchWord(value: String): Boolean = value.trim().lowercase().let { it in AUTH_SCHEMES || it in SWITCH_WORDS }

    /** A random-looking run: 16+ token characters mixing letters and digits, or 32+ of them. */
    public fun looksLikeToken(part: String): Boolean {
        if (!TOKEN_CHARS.matches(part)) return false
        if (part.length >= 32) return true
        return part.length >= 16 && part.any(Char::isDigit) && part.any(Char::isLetter)
    }

    /** 8+ letters and digits with both cases and a digit, no separator: a password or a short key, not a name. */
    private fun isPasswordShaped(part: String): Boolean =
        part.length >= 8 && part.all(Char::isLetterOrDigit) && part.any(Char::isDigit) && part.any(Char::isUpperCase) &&
            part.any(Char::isLowerCase)

    private fun isNaturalWord(word: String): Boolean {
        if (!LETTERS.matches(word)) return false
        val inner = word.drop(1).count(Char::isUpperCase)
        val naturalCase = inner <= 2 || (word.all(Char::isUpperCase) && word.length <= 12)
        return naturalCase && !CONSONANT_RUN.containsMatchIn(word)
    }

    private fun isSlug(text: String): Boolean {
        if (text.length > 40 || !SLUG.matches(text)) return false
        val parts = text.split('-', '_', '.')
        return parts.any { it.length >= 3 && it.all(Char::isLetter) } &&
            parts.any { it.length <= 3 } &&
            parts.none { it.length > 7 && it.any(Char::isDigit) && it.any(Char::isLetter) } &&
            parts.none { looksLikeToken(it) }
    }

    private fun isPath(text: String): Boolean =
        text.split(':').let { parts ->
            // `C:\…` splits at its drive colon: rejoin it.
            val paths = if (WINDOWS_DRIVE.containsMatchIn(text)) listOf(text) else parts
            paths.all { path ->
                PATH.matches(path) &&
                    path.split('/', '\\').none { looksLikeToken(it) || isPasswordShaped(it) }
            }
        }

    private val SEPARATORS = Regex("[-_.\\s]")
    private val CREDENTIAL_WORDS =
        setOf(
            "key", "auth", "authorization", "proxyauthorization", "cookie", "setcookie", "pat", "sig", "signature",
            "jwt", "credential", "credentials", "passwd", "password", "secret", "token", "pass", "pin", "otp",
        )
    private val CREDENTIAL_ENDINGS =
        listOf(
            "token", "secret", "password", "passwd", "pass", "pwd", "passphrase", "passcode", "key", "credential",
            "credentials", "auth", "authorization", "cookie",
        )

    /** A `PIN`/`OTP`/`PINCODE` part of a separated name (`SLACK_PIN`, `--otp`), not letters inside a word (`SPIN`). */
    private val SHORT_SECRET_PART = Regex("""(?:^|[-_.\s])(?:pin|pincode|otp)$""", RegexOption.IGNORE_CASE)
    private val SHORT_NUMBER = Regex("""^[+-]?\d{1,7}(?:\.\d+)?$""")
    private val VERSION = Regex("""^\d{1,4}(?:\.\d{1,6}){1,3}(?::\d{1,5})?$""")
    private val DATE_TIME =
        Regex("""^\d{4}-\d{2}(?:-\d{2})?(?:[T ]\d{2}:\d{2}(?::\d{2})?(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?)?$|^\d{1,2}:\d{2}(?::\d{2})?$""")
    private val PORT_RANGE = Regex("""^\d{1,5}-\d{1,5}$""")
    private val MEDIA_TYPE =
        Regex(
            """^(?:application|text|image|audio|video|multipart|font)/[A-Za-z0-9.+\-]+""" +
                """(?:\s*;\s*[A-Za-z0-9\-]+=[A-Za-z0-9.\-]+)*$"""
        )
    private const val MEDIA_ITEM = """(?:\*|[a-z]+)/(?:\*|[A-Za-z0-9.+\-]+)(?:\s*;\s*[A-Za-z0-9\-]+=[A-Za-z0-9.\-]+)*"""
    private val MEDIA_LIST = Regex("""^$MEDIA_ITEM(?:\s*,\s*$MEDIA_ITEM)*$""")
    private val REGION =
        Regex(
            """^[a-z]{2,12}(?:-gov)?-""" +
                """(?:north|south|east|west|central|northeast|northwest|southeast|southwest)-?\d{1,2}$"""
        )
    private val ORDINARY_WORDS =
        setOf(
            "true", "false", "yes", "no", "on", "off", "enabled", "disabled", "enable", "disable", "none", "null",
            "default", "production", "development", "staging", "testing", "local", "localhost", "global", "public",
            "private", "verbose", "warning", "silent", "always", "never", "stdout", "stderr", "inherit", "readonly",
        )
    private val SWITCH_WORDS =
        setOf("true", "false", "yes", "no", "on", "off", "enabled", "disabled", "enable", "disable", "none", "null")
    private val LETTERS = Regex("""^[A-Za-z]{1,20}$""")
    private val CONSONANT_RUN = Regex("""[b-df-hj-np-tv-xzB-DF-HJ-NP-TV-XZ]{6}""")
    private val HOST =
        Regex("""^(?:localhost|(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,24})(?::\d{1,5})?$""")
    private val LOCALE = Regex("""^(?:[a-z]{2,3}(?:[_-][A-Z]{2})?|C)(?:\.[A-Za-z0-9-]{2,12})?$""")
    private val SLUG = Regex("""^[a-z0-9]+(?:[-_.][a-z0-9]+)+$""")
    private val PATH = Regex("""^(?:~|\.{1,2})?[/\\][A-Za-z0-9._@\-/\\]*$|^[A-Za-z]:[/\\][A-Za-z0-9._@\-/\\]*$""")
    private val WINDOWS_DRIVE = Regex("""^[A-Za-z]:[/\\]""")
    private val REFERENCE = Regex("""\{(?:env|file):[^}\s]+\}|\$\{[A-Za-z_][A-Za-z0-9_]*\}""")
    private val TOKEN_CHARS = Regex("""^[A-Za-z0-9_\-.~+=%]+$""")
    private val AUTH_SCHEMES = setOf("bearer", "basic", "token", "bot", "digest", "apikey", "key")
    private val PIECE_JOINERS = charArrayOf(' ', '\t', ':', ';', ',', '@', '/', '=', '&', '?', '#', '\'', '"')
}

/**
 * Standard protocol headers whose values are not secrets (lower case): registering `X-Title: OpenCode` masked the
 * app's own name on every screen. Only a token-shaped value ([ConfigValues.looksLikeToken], or a scheme and a
 * credential) and URL credentials inside them (a Referer with `user:password@`) are registered.
 */
private val STANDARD_HEADERS =
    setOf(
        "content-type", "accept", "accept-language", "accept-encoding", "accept-charset", "user-agent", "x-title",
        "http-referer", "referer", "origin", "cache-control", "connection", "content-language", "content-encoding",
        "pragma", "dnt", "x-requested-with", "mcp-protocol-version", "anthropic-version", "anthropic-beta",
        "openai-beta", "originator",
    )
private val SERVER_TYPES = setOf("local", "remote", "stdio", "http", "sse", "streamable-http", "streamablehttp")
private val PAIR_MEMBERS = listOf("environment", "env", "headers")
private const val ENV_SEPARATORS = "="
private const val HEADER_SEPARATORS = ":="
private val HEADER_FLAGS = setOf("header", "h", "headers")
private val HEADER_FLAG_WORDS = setOf("--header", "-H", "--headers")
private val ENV_STYLE_NAME = Regex("""^[A-Z_][A-Z0-9_]*$""")
private val URL_START = Regex("""^[A-Za-z][A-Za-z0-9+.\-]*://""")
private val URL = Regex("""[A-Za-z][A-Za-z0-9+.\-]*://[^\s"'<>`]+""")
private val AUTH_SCHEME =
    Regex("""^(bearer|basic|token|bot|digest|apikey|key)\s+(\S.*)$""", RegexOption.IGNORE_CASE)
