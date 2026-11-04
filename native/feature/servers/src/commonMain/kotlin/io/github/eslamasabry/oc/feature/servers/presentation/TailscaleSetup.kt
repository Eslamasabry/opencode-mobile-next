package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.security.ServerUrlRules

/** Package presence is not VPN state. No tailnet inspection or implicit connection. */
public enum class TailscalePresence {
    Installed,
    Missing,
    Unavailable,
    Unsupported,
}

public interface TailscaleSetup {
    public fun check(): TailscalePresence

    public fun open(): Boolean
}

/**
 * Reviews the origin only; remote HTTP still requires the server form's separate explicit consent.
 */
public fun reviewedTailscaleAddress(raw: String): String? {
    if (raw.length > 2048) return null
    val normalized = ServerUrlRules.normalizeText(raw)
    return normalized.takeIf { ServerUrlRules.validate(it) == null }
}
