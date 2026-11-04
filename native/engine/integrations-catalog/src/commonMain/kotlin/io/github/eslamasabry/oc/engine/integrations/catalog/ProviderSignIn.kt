package io.github.eslamasabry.oc.engine.integrations.catalog

// Flutter lib/ui/widgets/connect_methods.dart (providerKeyPageUrl) and
// lib/ui/screens/library/integration_tiles.dart
// (parseAuthorizationUrl, providerOAuthCompletionCode). The returned links are opened only through
// ExternalLinkPolicy → ExternalLinkOpener; nothing here opens anything.

/**
 * The provider's official page for creating an API key, for providers whose subscription sign-in
 * cannot load on an OpenCode server and whose terms allow only an API key in other apps; null for
 * every other provider.
 */
public fun providerKeyPageUrl(integrationId: String): String? =
    when (integrationId.lowercase()) {
        "anthropic" -> "https://console.anthropic.com/settings/keys"
        "google" -> "https://aistudio.google.com/apikey"
        else -> null
    }

/**
 * A server-provided authorization URL, accepted only on an HTTPS origin with a host and without
 * embedded credentials; null (the caller shows "The server returned an unsafe authorization link")
 * for anything else.
 */
public fun parseAuthorizationUrl(value: String): String? {
    val trimmed = value.trim()
    if (
        trimmed.isEmpty() ||
            trimmed.length > 8192 ||
            trimmed.any { it.isWhitespace() || it.code < 32 || it.code in 127..159 || it == '\\' }
    )
        return null
    val authority =
        trimmed
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    if ('@' in authority) return null
    val url = CatalogUrl.parse(trimmed) ?: return null
    if (
        url.scheme != "https" ||
            !url.hasAuthority ||
            url.host.trim().isEmpty() ||
            url.userInfo.isNotEmpty()
    )
        return null
    return trimmed
}

/**
 * Either the short code a provider shows, or the `code` of the callback URL a headless browser
 * leaves behind.
 */
public fun providerOAuthCompletionCode(value: String): String {
    val trimmed = value.trim()
    val fromUrl = CatalogUrl.parse(trimmed)?.query?.get("code")?.trim()
    return if (!fromUrl.isNullOrEmpty()) fromUrl else trimmed
}
