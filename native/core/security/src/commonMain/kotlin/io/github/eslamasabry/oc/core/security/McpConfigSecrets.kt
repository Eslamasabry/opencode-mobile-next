package io.github.eslamasabry.oc.core.security

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The secrets an MCP server's configuration carries, registered by VALUE wherever [Redactor.registerCredentialValues]
 * walks a config tree (every `/config` read: setup, the configuration preview, the agent catalogue, the setup
 * assistant). The walker alone registers only values under credential-NAMED members, and MCP names are free: an
 * `environment` entry `GITHUB_TOKEN` or a header `X-Custom-Token` was never registered, so its value could reach a
 * preview, a tool row or a log line in plain.
 *
 * Registered (each subject to the redactor's own 6-character minimum):
 * - every `environment`/`env` value and every `headers` value, whatever its name, and for an `Authorization`-style
 *   value ("Bearer …") the credential after the scheme too;
 * - in `command`/`args` words: the value of `NAME=value`, `--name=value` and `--name value` when NAME is
 *   credential-like, and every header value after `--header`/`-H`;
 * - in URLs (the server's `url`, URL-shaped values and command words): the userinfo password (or a token-like userinfo
 *   with no password: `https://ghp_…@host`), the values of credential-named query parameters, and token-like path
 *   segments or query values (a webhook or a per-user MCP link keeps its secret in the path).
 *
 * Never registered: env/header NAMES, server names, program names, a URL as a whole or its host, and a value
 * [ConfigValues.isPlainlyNotSecret] calls ordinary (unless its name is credential-like). The redactor masks a
 * registered value everywhere, inside longer words too, so registering an ordinary word ("project", "production")
 * corrupts every text that contains it.
 */
internal fun Redactor.registerMcpServers(container: JsonObject) {
    for ((name, value) in container) {
        val item = value as? JsonObject ?: continue
        when {
            isMcpServer(item) -> registerMcpServer(item)
            // OpenCode 2 nests the servers one level down: `mcp.servers.<name>` (2.1.1 and 2.2.5 Config.InfoEncoded).
            name == "servers" ->
                for (server in item.values) {
                    if (server is JsonObject && isMcpServer(server)) registerMcpServer(server)
                }
        }
    }
}

/**
 * A local or remote server object: a known `type`, a `command`, a `url` (2.2.5 Mcp.Local/RemoteConfigEncoded), or an
 * environment/headers map (a partial entry is still a server's values).
 */
private fun isMcpServer(item: JsonObject): Boolean {
    val type = (item["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.lowercase()
    val command = item["command"]
    return (type != null && type in SERVER_TYPES) ||
        command is JsonArray ||
        (command as? JsonPrimitive)?.isString == true ||
        (item["url"] as? JsonPrimitive)?.isString == true ||
        PAIR_MEMBERS.any { item[it] is JsonObject }
}

private fun Redactor.registerMcpServer(server: JsonObject) {
    for (member in PAIR_MEMBERS) {
        val pairs = server[member] as? JsonObject ?: continue
        for ((name, value) in pairs) {
            val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            registerConfigValue(name, text)
        }
    }
    val words = ArrayList<String>()
    when (val command = server["command"]) {
        is JsonArray -> words += strings(command)
        is JsonPrimitive -> if (command.isString) words += command.content.split(WHITESPACE).filter { it.isNotEmpty() }
        else -> Unit
    }
    (server["args"] as? JsonArray)?.let { words += strings(it) }
    registerCommandWords(words)
    (server["url"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { registerUrlCredentials(it) }
}

private fun strings(array: JsonArray): List<String> =
    array.mapNotNull { (it as? JsonPrimitive)?.takeIf { word -> word.isString }?.content }

/**
 * An env or header value: registered whole unless plainly ordinary, plus its scheme-less token and URL credentials. A
 * URL under an ordinary name is an address: only its credentials are registered, so its host stays readable.
 */
private fun Redactor.registerConfigValue(name: String, value: String) {
    val text = value.trim()
    if (text.isEmpty() || ConfigValues.isReference(text)) return
    if (!ConfigValues.isCredentialName(name)) {
        if (ConfigValues.isPlainlyNotSecret(text)) return
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
        if (equals > 0) {
            val name = word.substring(0, equals).trimStart('-')
            val value = word.substring(equals + 1)
            when {
                name.lowercase() in HEADER_FLAGS -> registerHeaderLine(value)
                ConfigValues.isCredentialName(name) -> registerConfigValue(name, value)
            }
        } else if (word.startsWith("-") && next != null && !next.startsWith("-")) {
            val name = word.trimStart('-')
            when {
                word in HEADER_FLAG_WORDS -> registerHeaderLine(next)
                ConfigValues.isCredentialName(name) -> registerConfigValue(name, next)
            }
        }
        registerUrlCredentials(word)
    }
}

/** `Name: value` after a header flag; every header value is registered, as in a `headers` map. */
private fun Redactor.registerHeaderLine(line: String) {
    val colon = line.indexOf(':')
    if (colon <= 0) return
    registerConfigValue(line.substring(0, colon).trim(), line.substring(colon + 1))
}

/** The credential parts of every URL in [text], never the URL or its host: those stay readable around the mask. */
private fun Redactor.registerUrlCredentials(text: String) {
    for (match in URL.findAll(text)) for (part in urlSecrets(match.value)) registerSecret(part)
}

/** The userinfo password, credential-named or token-like query values and token-like path segments of [url]. */
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
    val query = afterAuthority.substringAfter('?', "").substringBefore('#')
    for (pair in query.split('&')) {
        val equals = pair.indexOf('=')
        if (equals <= 0) continue
        val name = pair.substring(0, equals)
        val value = pair.substring(equals + 1)
        if (ConfigValues.isCredentialName(name) || ConfigValues.looksLikeToken(value)) out += value
    }
    return out.filter { it.isNotEmpty() }
}

/**
 * The shape rules for configuration values and typed answers. Conservative by design: anything not plainly ordinary is
 * treated as a possible secret.
 */
public object ConfigValues {
    /**
     * A name that announces a credential: `*TOKEN`, `*SECRET`, `*PASSWORD`/`*PASSWD`, `*KEY` (API_KEY, SSH_KEY,
     * x-api-key), `*CREDENTIAL(S)`, `Authorization`, `Cookie`, `auth`, `pat`, `sig`/`signature`, `jwt`. Case, dashes,
     * underscores and leading `--` are ignored. `MAX_TOKENS` is not one (a plural count, not a token).
     */
    public fun isCredentialName(name: String): Boolean {
        val plain = name.trim().trimStart('-').lowercase().replace(SEPARATORS, "")
        if (plain.isEmpty()) return false
        return plain in CREDENTIAL_WORDS || CREDENTIAL_ENDINGS.any { plain.endsWith(it) }
    }

    /**
     * A value that is plainly not a secret, so registering it would only mask ordinary text: a number, version, date or
     * IP (`300000`, `1.2.3`, `2025-06-18`, `127.0.0.1`), a media type (`application/json`), a cloud region (`us-east-1`,
     * `us-central1`), one of a short list of ordinary words (`production`, `default`, `localhost`, `enabled` …), a
     * file path or a `:`-separated list of paths with no token-like part (`/usr/bin/node`, `~/.config/app`), or only a
     * reference to another value ([isReference]). Everything else, a bare `$word` included (it may be a password that
     * starts with a dollar sign), is not plainly ordinary.
     */
    public fun isPlainlyNotSecret(value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty()) return true
        if (NUMBER.matches(text) || MEDIA_TYPE.matches(text) || REGION.matches(text)) return true
        if (text.lowercase() in ORDINARY_WORDS) return true
        return isPath(text) || isReference(text)
    }

    /** Only a reference to a value kept elsewhere: `{env:NAME}`, `{file:path}` or `${NAME}`, after an optional scheme. */
    public fun isReference(value: String): Boolean {
        val text = value.trim()
        val rest = REFERENCE.replace(text, "").trim()
        return rest != text && (rest.isEmpty() || rest.lowercase() in AUTH_SCHEMES)
    }

    /** A random-looking run: 16+ token characters mixing letters and digits, or 32+ of them. */
    public fun looksLikeToken(part: String): Boolean {
        if (!TOKEN_CHARS.matches(part)) return false
        if (part.length >= 32) return true
        return part.length >= 16 && part.any(Char::isDigit) && part.any(Char::isLetter)
    }

    private fun isPath(text: String): Boolean =
        text.split(':').let { parts ->
            // `C:\…` splits at its drive colon: rejoin it.
            val paths = if (WINDOWS_DRIVE.containsMatchIn(text)) listOf(text) else parts
            paths.all { path ->
                PATH.matches(path) &&
                    path.split('/', '\\').none { looksLikeToken(it) }
            }
        }

    private val SEPARATORS = Regex("[-_.\\s]")
    private val CREDENTIAL_WORDS =
        setOf(
            "key", "auth", "authorization", "proxyauthorization", "cookie", "setcookie", "pat", "sig", "signature",
            "jwt", "credential", "credentials", "passwd", "password", "secret", "token", "pass",
        )
    private val CREDENTIAL_ENDINGS =
        listOf("token", "secret", "password", "passwd", "key", "credential", "credentials", "authorization", "cookie")
    private val NUMBER = Regex("""^[+-]?\d+(?:[.,_:/\-]\d+)*$""")
    private val MEDIA_TYPE =
        Regex(
            """^(?:application|text|image|audio|video|multipart|font)/[A-Za-z0-9.+\-]+""" +
                """(?:\s*;\s*[A-Za-z0-9\-]+=[A-Za-z0-9.\-]+)*$"""
        )
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
    private val PATH = Regex("""^(?:~|\.{1,2})?[/\\][A-Za-z0-9._@\-/\\]*$|^[A-Za-z]:[/\\][A-Za-z0-9._@\-/\\]*$""")
    private val WINDOWS_DRIVE = Regex("""^[A-Za-z]:[/\\]""")
    private val REFERENCE = Regex("""\{(?:env|file):[^}\s]+\}|\$\{[A-Za-z_][A-Za-z0-9_]*\}""")
    private val TOKEN_CHARS = Regex("""^[A-Za-z0-9_\-.~+=]+$""")
    private val AUTH_SCHEMES = setOf("bearer", "basic", "token", "bot", "digest", "apikey", "key")
}

private val SERVER_TYPES = setOf("local", "remote", "stdio", "http", "sse", "streamable-http", "streamablehttp")
private val PAIR_MEMBERS = listOf("environment", "env", "headers")
private val HEADER_FLAGS = setOf("header", "h", "headers")
private val HEADER_FLAG_WORDS = setOf("--header", "-H", "--headers")
private val WHITESPACE = Regex("""\s+""")
private val URL_START = Regex("""^[A-Za-z][A-Za-z0-9+.\-]*://""")
private val URL = Regex("""[A-Za-z][A-Za-z0-9+.\-]*://[^\s"'<>`]+""")
private val AUTH_SCHEME =
    Regex("""^(bearer|basic|token|bot|digest|apikey|key)\s+(\S.*)$""", RegexOption.IGNORE_CASE)
