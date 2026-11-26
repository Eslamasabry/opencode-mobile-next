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
    private const val MAX_FILE_BYTES: Long = 4_096L
    private val SAFE_NUMERIC_IP = Regex("^[0-9a-fA-F:.]+$")

    /**
     * Picks up to [MAX_NAMESERVERS] usable phone DNS addresses in Android's order (glibc honors at most 3),
     * rejecting unspecified, loopback, multicast, link-local, or non-numeric strings. Falls back to
     * [DEFAULT_NAMESERVERS] when none are usable.
     */
    fun selectNameservers(addresses: List<InetAddress>): List<String> {
        val picked =
            addresses
                .asSequence()
                .filter(::isUsableAddress)
                .mapNotNull { it.hostAddress?.trim() }
                .filter { SAFE_NUMERIC_IP.matches(it) }
                .distinct()
                .take(MAX_NAMESERVERS)
                .toList()
        return picked.ifEmpty { DEFAULT_NAMESERVERS }
    }

    /** Formats `/etc/resolv.conf` contents for [addresses]. */
    fun formatResolvConf(addresses: List<InetAddress>): String =
        selectNameservers(addresses).joinToString(separator = "\n", postfix = "\n") { "nameserver $it" }

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
     * following it, and skipping disk writes when the file already matches. Returns true when rewritten.
     */
    @Synchronized
    fun writeResolvConf(rootfs: File, addresses: List<InetAddress>): Boolean {
        val target = safeTarget(rootfs) ?: return false
        val desired = formatResolvConf(addresses)
        val existing = if (target.isFile && target.length() <= MAX_FILE_BYTES) readSafely(target) else null
        return existing != desired && replaceAtomically(target, desired)
    }

    /** Reads the phone's active network DNS and writes `/etc/resolv.conf` under [rootfs]. */
    fun sync(context: Context, rootfs: File): Boolean = writeResolvConf(rootfs, readPhoneDns(context))

    private fun isUsableAddress(address: InetAddress): Boolean {
        val unusable = address.isAnyLocalAddress || address.isLoopbackAddress
        return !unusable && !address.isMulticastAddress && !address.isLinkLocalAddress
    }

    private fun safeTarget(rootfs: File): File? {
        val etc = File(rootfs, "etc")
        val rootSafe = rootfs.isDirectory && !isLink(rootfs)
        val etcSafe = etc.isDirectory && !isLink(etc)
        if (!rootSafe || !etcSafe) return null
        val resolv = File(etc, "resolv.conf")
        if (isLink(resolv)) Files.deleteIfExists(resolv.toPath())
        return resolv.takeUnless { it.exists() && !it.isFile }
    }

    private fun replaceAtomically(target: File, content: String): Boolean {
        val tmp = File(target.parentFile, ".resolv.conf.oc-tmp")
        if (isLink(tmp)) Files.deleteIfExists(tmp.toPath())
        tmp.writeText(content)
        val renamed = tmp.renameTo(target)
        if (!renamed) tmp.delete()
        return renamed
    }

    private fun readSafely(file: File): String? =
        try {
            file.readText()
        } catch (_: IOException) {
            null
        }

    private fun isLink(file: File): Boolean = Files.isSymbolicLink(file.toPath())
}
