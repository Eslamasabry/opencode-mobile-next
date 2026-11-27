package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.net.ConnectivityManager
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.nio.file.Files

/**
 * Writes `/etc/resolv.conf` inside the built-in Ubuntu rootfs from the phone's active network DNS (B-488),
 * falling back to public resolvers only when the phone reports no usable nameservers.
 */
internal object BuiltinDnsConfig {
    val DEFAULT_NAMESERVERS: List<String> = listOf("1.1.1.1", "8.8.8.8")
    const val MAX_NAMESERVERS: Int = 3
    const val MANAGED_HEADER: String = "# Managed by OpenCode (phone DNS)"
    private const val LEGACY_DEFAULT_CONTENT: String = "nameserver 1.1.1.1\nnameserver 8.8.8.8\n"
    private const val MAX_FILE_BYTES: Long = 4_096L
    private val SAFE_NUMERIC_IP = Regex("^[0-9a-fA-F:.]+(?:%[A-Za-z0-9_.:-]+)?$")
    private val MANAGED_NS_LINE = Regex("^nameserver [0-9a-fA-F:.]+(?:%[A-Za-z0-9_.:-]+)?$")

    /**
     * Picks up to [MAX_NAMESERVERS] usable phone DNS addresses in Android's order (glibc honors at most 3),
     * preferring non-link-local resolvers, keeping link-local resolvers when they are the only network-supplied
     * DNS, and rejecting unspecified, loopback, multicast, or non-numeric strings.
     */
    fun selectPhoneNameservers(addresses: List<InetAddress>): List<String> {
        val candidates =
            addresses.filter { !it.isAnyLocalAddress && !it.isLoopbackAddress && !it.isMulticastAddress }
        val primary = formatAddresses(candidates.filterNot { it.isLinkLocalAddress })
        return primary.ifEmpty { formatAddresses(candidates.filter { it.isLinkLocalAddress }) }
    }

    /** Picks phone DNS servers, falling back to [DEFAULT_NAMESERVERS] only when none are usable. */
    fun selectNameservers(addresses: List<InetAddress>): List<String> =
        selectPhoneNameservers(addresses).ifEmpty { DEFAULT_NAMESERVERS }

    /** Formats managed `/etc/resolv.conf` contents for [addresses]. */
    fun formatResolvConf(addresses: List<InetAddress>): String = formatServers(selectNameservers(addresses))

    /** Reads DNS servers from Android's active network; empty when offline, denied, or unavailable. */
    fun readPhoneDns(context: Context): List<InetAddress> =
        try {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val active = manager?.activeNetwork
            val link = if (active != null) manager.getLinkProperties(active) else null
            link?.dnsServers.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: IllegalStateException) {
            emptyList()
        }

    /**
     * Syncs `/etc/resolv.conf` under [rootfs] to [addresses], replacing any guest symlink safely without
     * following it. Never overwrites a user-customized file, and never overwrites an existing file with public
     * fallback servers when the phone reports no usable DNS. Returns true when rewritten.
     */
    @Synchronized
    fun writeResolvConf(rootfs: File, addresses: List<InetAddress>): Boolean {
        val target = safeDnsTarget(rootfs)
        val phoneServers = selectPhoneNameservers(addresses)
        return when {
            target == null -> false
            target.isFile -> updateManagedResolvConf(target, phoneServers)
            else -> replaceAtomically(target, formatServers(phoneServers.ifEmpty { DEFAULT_NAMESERVERS }))
        }
    }

    /** Reads the phone's active network DNS and writes `/etc/resolv.conf` under [rootfs]. */
    fun sync(context: Context, rootfs: File): Boolean = writeResolvConf(rootfs, readPhoneDns(context))

    private fun updateManagedResolvConf(target: File, phoneServers: List<String>): Boolean {
        val existing =
            if (phoneServers.isEmpty() || target.length() > MAX_FILE_BYTES) null else readTextSafely(target)
        val desired = if (existing != null && isManagedResolv(existing)) formatServers(phoneServers) else null
        return desired != null && existing != desired && replaceAtomically(target, desired)
    }

    private fun formatAddresses(addresses: List<InetAddress>): List<String> =
        addresses
            .asSequence()
            .mapNotNull { it.hostAddress?.trim() }
            .filter { SAFE_NUMERIC_IP.matches(it) }
            .distinct()
            .take(MAX_NAMESERVERS)
            .toList()

    private fun formatServers(servers: List<String>): String =
        buildString {
            append(MANAGED_HEADER).append('\n')
            for (server in servers) append("nameserver ").append(server).append('\n')
        }

    private fun isManagedResolv(content: String): Boolean {
        val trimmedLines = content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val matchesManaged =
            trimmedLines.firstOrNull() == MANAGED_HEADER &&
                trimmedLines.drop(1).all { MANAGED_NS_LINE.matches(it) }
        return content.isBlank() || content == LEGACY_DEFAULT_CONTENT || matchesManaged
    }
}

private fun safeDnsTarget(rootfs: File): File? {
    val etc = File(rootfs, "etc")
    val rootSafe = rootfs.isDirectory && !Files.isSymbolicLink(rootfs.toPath())
    val etcSafe = etc.isDirectory && !Files.isSymbolicLink(etc.toPath())
    val resolv = if (rootSafe && etcSafe) File(etc, "resolv.conf") else null
    if (resolv != null && Files.isSymbolicLink(resolv.toPath())) Files.deleteIfExists(resolv.toPath())
    return resolv?.takeUnless { it.exists() && !it.isFile }
}

private fun replaceAtomically(target: File, content: String): Boolean {
    val tmp = File(target.parentFile, ".resolv.conf.oc-tmp")
    if (Files.isSymbolicLink(tmp.toPath())) Files.deleteIfExists(tmp.toPath())
    tmp.writeText(content)
    val renamed = tmp.renameTo(target)
    if (!renamed) tmp.delete()
    return renamed
}

private fun readTextSafely(file: File): String? =
    try {
        file.readText()
    } catch (_: IOException) {
        null
    }
