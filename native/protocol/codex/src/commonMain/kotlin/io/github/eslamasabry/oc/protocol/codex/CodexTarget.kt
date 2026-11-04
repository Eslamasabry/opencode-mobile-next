package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.AddressRules
import io.ktor.http.Url
import io.ktor.http.encodedPath

/** Validated input only; bearer material is never included in its representation. */
public class CodexTarget
private constructor(
    public val url: String,
    public val directory: String,
    internal val bearer: String,
    public val transport: TransportSpec,
) {
    override fun toString(): String = "CodexTarget"

    public companion object {
        public fun parse(address: String, directory: String, token: String): Outcome<CodexTarget> {
            val raw = address.trim()
            val dir = directory.trim()
            fun invalid(): Outcome<Nothing> = Failure.Refused(RefusalReason.InvalidAddress).failed()
            if (
                raw.isEmpty() ||
                    raw.length > 8192 ||
                    raw.any { it.isWhitespace() || it.code < 32 || it.code in 127..159 } ||
                    dir.isEmpty() ||
                    dir.length > 4096 ||
                    dir.any { it.code < 32 || it.code in 127..159 } ||
                    !(dir.startsWith('/') || Regex("""^[A-Za-z]:[\\/].*""").matches(dir))
            )
                return invalid()
            if (
                token.isEmpty() ||
                    token.length > 16384 ||
                    token.any { it.isWhitespace() || it.code < 32 || it.code in 127..159 }
            )
                return Failure.SignIn(SignInWhy.TokenMissing).failed()
            val normalized =
                if ("://" in raw) raw
                else
                    when (val origin = AddressRules.normalize(raw)) {
                        is Outcome.Failed -> return origin
                        is Outcome.Ok -> origin.value.toString().replaceFirst("http", "ws")
                    }
            val url =
                try {
                    Url(normalized)
                } catch (_: Exception) {
                    return invalid()
                }
            if (
                url.protocol.name !in setOf("ws", "wss") ||
                    !url.user.isNullOrEmpty() ||
                    !url.password.isNullOrEmpty() ||
                    url.encodedPath !in setOf("", "/") ||
                    url.parameters.names().isNotEmpty() ||
                    url.fragment.isNotEmpty() ||
                    '?' in normalized ||
                    '#' in normalized
            )
                return invalid()
            if (url.protocol.name == "ws" && !AddressRules.isLoopback(url.host))
                return Failure.Refused(RefusalReason.HostNotAllowedForBackend).failed()
            val http = normalized.replaceFirst(Regex("^ws"), "http")
            val origin =
                when (val value = AddressRules.origin(http)) {
                    is Outcome.Failed -> return value
                    is Outcome.Ok -> value.value
                }
            return CodexTarget(
                    normalized,
                    dir,
                    token,
                    TransportSpec(
                        origin,
                        AddressRules.classify(origin.host),
                        cleartextPermitted = AddressRules.isLoopback(origin.host),
                    ),
                )
                .ok()
        }
    }
}
