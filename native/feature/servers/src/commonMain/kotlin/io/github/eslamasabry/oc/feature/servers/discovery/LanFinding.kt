package io.github.eslamasabry.oc.feature.servers.discovery

/** How a server was found: advertised on the network, or answered a probe on a scanned address. */
public enum class LanSource { Advertised, Scanned }

/**
 * A computer on the same network that looks like an OpenCode server. It carries no credential and no
 * server-authored text beyond a display name (never rendered as a link).
 */
public data class LanFinding(
    /** Host name or IP address, no scheme. */
    val host: String,
    val port: Int,
    /** A name the computer advertised, when it did; never an IP address. */
    val name: String? = null,
    val version: String? = null,
    /** The server answered but asked for a password. */
    val needsPassword: Boolean = false,
    val source: LanSource = LanSource.Scanned,
) {
    /** The address the editor is filled with. Plain HTTP: the editor's own consent step follows. */
    val address: String get() = "http://${hostPort}"

    /** `host:port` for display. */
    val hostPort: String
        get() = "${if (':' in host && !host.startsWith("[")) "[$host]" else host}:$port"

    /** A title for a row. */
    val title: String get() = name ?: host
}

/** Merging what the two discovery routes report into one list, one row per computer. */
public object LanFindings {
    /** Adds [incoming] to [current]: the same host and port is one row that keeps the richest facts. */
    public fun merge(current: List<LanFinding>, incoming: LanFinding): List<LanFinding> {
        val key = key(incoming)
        val index = current.indexOfFirst { key(it) == key }
        if (index < 0) return current + incoming
        val old = current[index]
        val merged =
            old.copy(
                name = old.name ?: incoming.name,
                version = old.version ?: incoming.version,
                needsPassword = old.needsPassword || incoming.needsPassword,
                source = if (old.source == LanSource.Scanned) old.source else incoming.source,
            )
        return current.toMutableList().also { it[index] = merged }
    }

    /** Named computers first, then by name, then by port. */
    public fun sorted(findings: List<LanFinding>): List<LanFinding> =
        findings.sortedWith(
            compareBy<LanFinding> { it.name == null }.thenBy { it.title.lowercase() }.thenBy { it.port }
        )

    /** `opencode.local` becomes `opencode`; never returns an IP address. */
    public fun nameFrom(advertised: String): String? {
        val trimmed = advertised.trim().removeSuffix(".").removeSuffix(".local").trim()
        return trimmed.takeIf { it.isNotEmpty() && !isIp(it) }
    }

    /** Is this advertised service plausibly OpenCode? (Name or host contains "opencode".) */
    public fun looksLikeOpenCode(vararg labels: String?): Boolean =
        labels.any { it?.contains("opencode", ignoreCase = true) == true }

    private fun key(f: LanFinding) = "${f.host.lowercase()}:${f.port}"

    private fun isIp(host: String) = host.isNotEmpty() && host.all { it.isDigit() || it == '.' || it == ':' }
}
