package io.github.eslamasabry.oc.engine.connectors.chat

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.connectors.text.WebAddress

/** Why a pasted callback or code was refused (Dart `McpOAuthCallbackException` copy, mapped by the UI). */
public enum class OAuthCallbackProblem {
    /** "Paste the authorization code or the complete callback URL." */
    Empty,

    /** "The callback state does not match this authorization request." */
    StateMismatch,

    /** "The callback URL does not contain an authorization code." */
    NoCode,

    /** "Authorization failed: …" (the provider's error text is never shown). */
    ProviderError,
}

public sealed interface ParsedCode {
    public data class Code(val value: String) : ParsedCode

    public data class Refused(val problem: OAuthCallbackProblem) : ParsedCode
}

private val LINE_BREAK = Regex("[\r\n]")
private val LOOPBACK = setOf("localhost", "127.0.0.1", "::1", "[::1]")

/** Dart `parseMcpAuthorizationCode`: a full callback URL (state must match) or the bare code. */
public fun parseMcpAuthorizationCode(value: String, expectedState: String): ParsedCode {
    val input = value.trim()
    if (input.isEmpty() || input.length > 8192 || LINE_BREAK.containsMatchIn(input)) {
        return ParsedCode.Refused(OAuthCallbackProblem.Empty)
    }
    val uri = WebAddress.parse(input)
    val code = uri?.queryParameters?.get("code")
    if (uri != null && code != null) {
        val state = uri.queryParameters["state"]
        if (state == null || state != expectedState) return ParsedCode.Refused(OAuthCallbackProblem.StateMismatch)
        if (code.isBlank()) return ParsedCode.Refused(OAuthCallbackProblem.NoCode)
        return ParsedCode.Code(code)
    }
    if (uri != null && uri.hasQuery) {
        val error = uri.queryParameters["error_description"] ?: uri.queryParameters["error"]
        return ParsedCode.Refused(
            if (error?.isNotBlank() == true) OAuthCallbackProblem.ProviderError else OAuthCallbackProblem.NoCode,
        )
    }
    return ParsedCode.Code(input)
}

/** Dart `mcpLoopbackRedirect`: the `redirect_uri` of [authorizationUrl] when it is an http loopback with a port. */
public fun mcpLoopbackRedirect(authorizationUrl: String): WebAddress? {
    val params = WebAddress.parse(authorizationUrl)?.queryParameters ?: return null
    val value = (params["redirect_uri"] ?: params["redirectUri"])?.trim()
    if (value.isNullOrEmpty()) return null
    val redirect = WebAddress.parse(value) ?: return null
    val port = redirect.port ?: return null
    if (redirect.scheme != "http" || !redirect.hasAuthority || redirect.host.lowercase() !in LOOPBACK ||
        redirect.userInfo.isNotEmpty() || port <= 0 || port > 65535
    ) {
        return null
    }
    return redirect
}

/**
 * PORT (Dart `McpOAuthLoopbackListener`): listens on the redirect's loopback host and port for one
 * `GET <path>?state=<expectedState>&code=…`, answers a small fixed HTML page, and closes. Wrong path → 404, wrong
 * state → 400 (keeps waiting), `error`/`error_description` → 400 and [code] fails, missing code → 400 (keeps
 * waiting).
 * After [timeoutMs] [code] fails. Binding fails when the port is taken (the server on this phone may own it).
 */
public fun interface OAuthCallbackPort {
    public suspend fun bind(
        redirect: WebAddress,
        expectedState: String,
        timeoutMs: Long,
    ): Outcome<OAuthCallbackListener>
}

public interface OAuthCallbackListener {
    /** The authorization code, or a failure (timeout, provider error, cancelled). */
    public suspend fun code(): Outcome<String>

    /** Idempotent; a pending [code] fails as cancelled. */
    public suspend fun close()
}
