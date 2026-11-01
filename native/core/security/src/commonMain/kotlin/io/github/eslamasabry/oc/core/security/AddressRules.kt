package io.github.eslamasabry.oc.core.security

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.ServerOrigin

/** Plan 04 §4.1: one classifier. Tailnet wins over the IPv4 ranges (100.64.0.0/10 is CGNAT, not RFC 1918). */
public enum class HostClass { Loopback, PrivateLan, Tailnet, LinkLocal, Public, Invalid }

/**
 * Address and trust rules (PROTOCOLS-01/02). Android NSC cannot express 100.64.0.0/10, so code decides. Ports of
 * lib/domain/loopback_host.dart, lib/domain/server_trust.dart, ConnectionFailure.isTailnetHost and
 * normalizeServerProfileUrl, with their Flutter test vectors.
 */
public object AddressRules {
    /** Dart isLoopbackHost: exactly localhost, 127.0.0.1 and ::1 (brackets ignored). */
    public fun isLoopback(host: String): Boolean = bare(host) in LOOPBACK

    /** Dart isPrivateNetworkHost: RFC 1918, 169.254/16, fc00::/7, fe80::/10, `*.local`; IPv4-mapped judged inside. */
    public fun isPrivateNetwork(host: String): Boolean {
        val h = bare(host).substringBefore('%').removeSuffix(".")
        if (h.isEmpty()) return false
        val bytes = parseAddress(h)
        if (bytes == null) {
            if (!h.endsWith(".local") || h.length <= ".local".length) return false
            return h.removeSuffix(".local").split('.').all { it.isNotEmpty() }
        }
        if (bytes.size == 4) return privateIpv4(bytes[0], bytes[1])
        if (isMappedV4(bytes)) return privateIpv4(bytes[12], bytes[13])
        return (bytes[0] and 0xFE) == 0xFC || (bytes[0] == 0xFE && (bytes[1] and 0xC0) == 0x80)
    }

    /** A Tailscale address: 100.64.0.0/10 or a MagicDNS `*.ts.net` name. */
    public fun isTailnet(host: String): Boolean {
        val h = bare(host)
        if (h.endsWith(".ts.net")) return true
        val v4 = parseIpv4(h) ?: return false
        return v4[0] == 100 && v4[1] in 64..127
    }

    public fun classify(host: String): HostClass {
        val h = bare(host)
        if (h.isEmpty() || h.any { it.isWhitespace() || it in "/?#@" }) return HostClass.Invalid
        if (isLoopback(h)) return HostClass.Loopback
        if (isTailnet(h)) return HostClass.Tailnet
        val bytes = parseAddress(h.substringBefore('%'))
        val linkLocal =
            bytes != null &&
                when {
                    bytes.size == 4 -> bytes[0] == 169 && bytes[1] == 254
                    isMappedV4(bytes) -> bytes[12] == 169 && bytes[13] == 254
                    else -> bytes[0] == 0xFE && (bytes[1] and 0xC0) == 0x80
                }
        return when {
            linkLocal -> HostClass.LinkLocal
            isPrivateNetwork(h) -> HostClass.PrivateLan
            else -> HostClass.Public
        }
    }

    /**
     * Dart serverOrigin: the canonical trust origin. Trust never follows a path, redirect or port change. Refuses
     * user info (credentials), a query, a fragment, a non-HTTP scheme or an empty host.
     */
    public fun origin(url: String): Outcome<ServerOrigin> {
        val parts = UrlParts.parse(url) ?: return invalid()
        if (parts.scheme != "http" && parts.scheme != "https") return invalid()
        if (!parts.userInfo.isNullOrEmpty()) return Failure.Refused(RefusalReason.CredentialsInAddress).failed()
        if (parts.query != null || parts.fragment != null) return invalid()
        if (classify(parts.host) == HostClass.Invalid) return invalid()
        return ServerOrigin(parts.scheme, parts.host, parts.effectivePort() ?: return invalid()).ok()
    }

    /**
     * What the person typed → origin (Dart normalizeServerProfileUrl, then serverOrigin). A bare authority gains
     * https, except loopback, which gains http (B-SEC-120).
     */
    public fun normalize(input: String): Outcome<ServerOrigin> {
        val raw = input.trim()
        if (raw.isEmpty()) return invalid()
        if ("://" in raw) return origin(raw)
        if (!BARE.matches(raw)) return invalid()
        val probe = UrlParts.parse("http://$raw") ?: return invalid()
        val scheme = if (isLoopback(probe.host)) "http" else "https"
        return origin("$scheme://$raw")
    }

    /** Dart isCleartextChoiceRequired: plain HTTP anywhere but this device needs the person's explicit choice. */
    public fun needsCleartextConsent(origin: ServerOrigin): Boolean = origin.scheme == "http" && !isLoopback(origin.host)

    /** Dart normalizeCertificateFingerprint: 64 hex digits, colons allowed, lower-cased. */
    public fun normalizePin(fingerprint: String): Outcome<String> {
        val normalized = fingerprint.replace(":", "").lowercase()
        return if (PIN.matches(normalized)) normalized.ok() else Failure.Refused(RefusalReason.InvalidAddress).failed()
    }

    /** A pin requires HTTPS (it can never be downgraded to HTTP). */
    public fun checkPin(origin: ServerOrigin, fingerprint: String): Outcome<String> =
        if (origin.scheme != "https") Failure.Refused(RefusalReason.PinNeedsHttps).failed() else normalizePin(fingerprint)

    // ---- parsing (plain common Kotlin: the same verdict on every platform)

    private val LOOPBACK = setOf("localhost", "127.0.0.1", "::1")
    private val BARE = Regex("""^\[?[A-Za-z0-9._\-:]+]?(:\d{1,5})?$""")
    private val PIN = Regex("^[0-9a-f]{64}$")

    private fun invalid(): Outcome<Nothing> = Failure.Refused(RefusalReason.InvalidAddress).failed()

    private fun bare(host: String): String {
        var h = host.trim().lowercase()
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length - 1)
        return h
    }

    private fun privateIpv4(a: Int, b: Int): Boolean = a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)

    private fun isMappedV4(bytes: IntArray): Boolean =
        bytes.size == 16 && (0 until 10).all { bytes[it] == 0 } && bytes[10] == 0xFF && bytes[11] == 0xFF

    private fun parseAddress(host: String): IntArray? = if (':' in host) parseIpv6(host) else parseIpv4(host)

    private fun parseIpv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        return IntArray(4) { i ->
            val p = parts[i]
            if (p.isEmpty() || p.length > 3 || !p.all(Char::isDigit)) return null
            p.toInt().takeIf { it <= 255 } ?: return null
        }
    }

    private fun parseIpv6(host: String): IntArray? {
        if (host.count { it == ':' } < 2 && "::" !in host) return null
        val halves = host.split("::")
        if (halves.size > 2) return null
        fun groups(part: String): List<Int>? {
            if (part.isEmpty()) return emptyList()
            val out = mutableListOf<Int>()
            val pieces = part.split(':')
            for ((i, piece) in pieces.withIndex()) {
                if (i == pieces.lastIndex && '.' in piece) {
                    val v4 = parseIpv4(piece) ?: return null
                    out += (v4[0] shl 8) or v4[1]
                    out += (v4[2] shl 8) or v4[3]
                } else {
                    if (piece.isEmpty() || piece.length > 4) return null
                    out += piece.toIntOrNull(16) ?: return null
                }
            }
            return out
        }
        val head = groups(halves[0]) ?: return null
        val tail = if (halves.size == 2) groups(halves[1]) ?: return null else emptyList()
        val all =
            if (halves.size == 2) {
                if (head.size + tail.size > 7) return null
                head + List(8 - head.size - tail.size) { 0 } + tail
            } else {
                if (head.size != 8) return null
                head
            }
        return IntArray(16) { i -> if (i % 2 == 0) all[i / 2] shr 8 else all[i / 2] and 0xFF }
    }
}
