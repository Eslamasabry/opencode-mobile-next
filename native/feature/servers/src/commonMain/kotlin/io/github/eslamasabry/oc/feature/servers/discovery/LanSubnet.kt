package io.github.eslamasabry.oc.feature.servers.discovery

/**
 * The addresses worth asking on the phone's own Wi-Fi network. Pure arithmetic: nothing here touches
 * a socket, and nothing outside the phone's own subnet is ever returned.
 */
public object LanSubnet {
    /** A subnet wider than /22 (1,022 hosts) is not scanned: it is a campus network, not a home. */
    public const val WIDEST_PREFIX: Int = 22

    /** Ports the OpenCode servers listen on by default. */
    public val DEFAULT_PORTS: List<Int> = listOf(4096, 4097)

    private const val ALL_ONES: Long = 0xFFFFFFFFL

    /**
     * Every usable host address of [selfAddress]/[prefix] except the phone itself, nearest first (a
     * computer is usually a few addresses away from the phone). Null when the prefix is wider than
     * [WIDEST_PREFIX], when there are no neighbours (/31, /32), or when [selfAddress] is not IPv4.
     */
    public fun hosts(selfAddress: String, prefix: Int): List<String>? {
        val self = parse(selfAddress) ?: return null
        if (prefix < WIDEST_PREFIX || prefix > 30) return null
        val mask = (ALL_ONES shl (32 - prefix)) and ALL_ONES
        val network = self and mask
        val broadcast = (network or (mask.inv() and ALL_ONES))
        return ((network + 1)..(broadcast - 1))
            .filter { it != self }
            .sortedBy { kotlin.math.abs(it - self) }
            .map(::format)
    }

    /** Is [candidate] inside the phone's own subnet? Discovery refuses anything else. */
    public fun contains(selfAddress: String, prefix: Int, candidate: String): Boolean {
        val self = parse(selfAddress) ?: return false
        val other = parse(candidate) ?: return false
        if (prefix < WIDEST_PREFIX || prefix > 32) return false
        val mask = (ALL_ONES shl (32 - prefix)) and ALL_ONES
        return (self and mask) == (other and mask)
    }

    /** The default ports plus any port the person already used, in a stable order without repeats. */
    public fun ports(extra: Collection<Int>): List<Int> =
        (DEFAULT_PORTS + extra.filter { it in 1..65535 }).distinct()

    internal fun parse(text: String): Long? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255 || part.length > 3) return null
            value = (value shl 8) or octet.toLong()
        }
        return value
    }

    internal fun format(value: Long): String =
        listOf(24, 16, 8, 0).joinToString(".") { ((value shr it) and 0xFF).toString() }
}
