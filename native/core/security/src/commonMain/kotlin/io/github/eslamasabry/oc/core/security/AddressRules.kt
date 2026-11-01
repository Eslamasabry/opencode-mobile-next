package io.github.eslamasabry.oc.core.security

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerOrigin

public enum class HostClass { Loopback, PrivateLan, Tailnet, LinkLocal, Public, Invalid }

/** Android NSC cannot express 100.64.0.0/10, so code decides (PROTOCOLS-01/02). */
public object AddressRules {
    public fun classify(host: String): HostClass {
        val h = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        if (h.isEmpty()) return HostClass.Invalid
        if (h == "localhost" || h == "::1") return HostClass.Loopback
        if (h.endsWith(".ts.net")) return HostClass.Tailnet
        if (h.startsWith("fe80:")) return HostClass.LinkLocal
        val octets = h.split('.').map { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) return HostClass.Public
        val (a, b) = octets.requireNoNulls()
        return when {
            a == 127 -> HostClass.Loopback
            a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) -> HostClass.PrivateLan
            a == 100 && b in 64..127 -> HostClass.Tailnet
            a == 169 && b == 254 -> HostClass.LinkLocal
            else -> HostClass.Public
        }
    }

    public fun normalize(input: String): Outcome<ServerOrigin> {
        val text = input.trim().let { if ("://" in it) it else "http://$it" }
        val m = ORIGIN.matchEntire(text) ?: return Outcome.Failed(Failure.Refused(io.github.eslamasabry.oc.core.common.RefusalReason.InvalidAddress))
        val scheme = m.groupValues[1].lowercase()
        if (scheme != "http" && scheme != "https") return Outcome.Failed(Failure.Refused(io.github.eslamasabry.oc.core.common.RefusalReason.InvalidAddress))
        val host = m.groupValues[2].lowercase()
        if (classify(host) == HostClass.Invalid) return Outcome.Failed(Failure.Refused(io.github.eslamasabry.oc.core.common.RefusalReason.InvalidAddress))
        val port = m.groupValues[3].toIntOrNull() ?: if (scheme == "https") 443 else 80
        return Outcome.Ok(ServerOrigin(scheme, host, port))
    }

    private val ORIGIN = Regex("""^([a-zA-Z]+)://(\[[0-9a-fA-F:]+]|[^/:?#\s]+)(?::(\d{1,5}))?/?$""")
}
