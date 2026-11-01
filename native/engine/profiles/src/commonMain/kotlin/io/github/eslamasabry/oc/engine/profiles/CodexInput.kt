package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.security.AddressRules

/** Why a Codex field was refused. Fixed values: the input is never echoed (B-SEC-136, Flutter `validateCodex*`). */
public enum class CodexInputProblem {
    EmptyUrl, IncompleteUrl, WrongScheme, CredentialsInUrl, QueryOrFragment, PathInUrl, CleartextNotLocal,
    Directory, Token,
}

/** Ports of `normalizeCodexServerUrl`, `validateCodexServerUrl`, `validateCodexProjectDirectory`, `validateCodexConnectionToken`. */
public object CodexInput {
    /** A bare authority gains `ws://` for loopback, `wss://` otherwise; anything else is returned as typed. */
    public fun normalizeUrl(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty() || "://" in raw || !BARE.matches(raw)) return raw
        val host = hostOf(raw) ?: return raw
        val authority = if (':' in host && !raw.startsWith("[")) "[$raw]" else raw
        return (if (AddressRules.isLoopback(host)) "ws://" else "wss://") + authority
    }

    public fun validateUrl(value: String): CodexInputProblem? {
        val raw = value.trim()
        if (raw.isEmpty()) return CodexInputProblem.EmptyUrl
        val schemeEnd = raw.indexOf("://")
        if (schemeEnd <= 0) return CodexInputProblem.IncompleteUrl
        val scheme = raw.substring(0, schemeEnd).lowercase()
        val rest = raw.substring(schemeEnd + 3)
        val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        val tail = rest.substring(authorityEnd)
        val hostPart = authority.substringAfterLast('@')
        val host = hostOf(hostPart)
        if (host.isNullOrEmpty()) return CodexInputProblem.IncompleteUrl
        if (scheme != "wss" && scheme != "ws") return CodexInputProblem.WrongScheme
        if ('@' in authority) return CodexInputProblem.CredentialsInUrl
        if ('?' in tail || '#' in tail) return CodexInputProblem.QueryOrFragment
        if (tail.isNotEmpty() && tail != "/") return CodexInputProblem.PathInUrl
        if (scheme == "ws" && !AddressRules.isLoopback(host)) return CodexInputProblem.CleartextNotLocal
        return null
    }

    public fun validateDirectory(value: String): CodexInputProblem? =
        if (value.isEmpty() || value.length > 4096 || hasControl(value) ||
            (!value.startsWith("/") && !WINDOWS_ABSOLUTE.containsMatchIn(value))
        ) {
            CodexInputProblem.Directory
        } else {
            null
        }

    public fun validateToken(value: String): CodexInputProblem? =
        if (value.isEmpty() || value.length > 16384 || hasControl(value) || value.any { it.isWhitespace() }) {
            CodexInputProblem.Token
        } else {
            null
        }

    private fun hasControl(value: String): Boolean = value.any { it.code <= 0x1f || it.code in 0x7f..0x9f }

    private fun hostOf(authority: String): String? = when {
        authority.startsWith("[") -> authority.substringAfter("[").substringBefore("]", "").ifEmpty { null }
        authority.count { it == ':' } > 1 -> authority
        else -> authority.substringBefore(':')
    }

    private val BARE = Regex("""^\[?[A-Za-z0-9._\-:]+]?(:\d{1,5})?$""")
    private val WINDOWS_ABSOLUTE = Regex("""^[A-Za-z]:[\\/]""")
}
