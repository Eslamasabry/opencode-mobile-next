package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-488: built-in Linux uses the phone's own network DNS, falling back to public resolvers only when absent. */
class BuiltinDnsConfigTest {
    private fun withRootfs(block: (File) -> Unit) {
        val rootfs = Files.createTempDirectory("oc-dns-rootfs").toFile()
        File(rootfs, "etc").mkdirs()
        try {
            block(rootfs)
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun `B-SETUP-019 phone DNS addresses replace hardcoded public nameservers in resolv conf`() {
        val servers =
            listOf(
                InetAddress.getByName("192.0.2.53"),
                InetAddress.getByName("198.51.100.1"),
                InetAddress.getByName("192.0.2.53"),
                InetAddress.getByName("2001:db8::53"),
                InetAddress.getByName("203.0.113.53"),
            )

        val picked = BuiltinDnsConfig.selectNameservers(servers)
        assertEquals(listOf("192.0.2.53", "198.51.100.1", "2001:db8:0:0:0:0:0:53"), picked)
        assertEquals(
            "nameserver 192.0.2.53\nnameserver 198.51.100.1\nnameserver 2001:db8:0:0:0:0:0:53\n",
            BuiltinDnsConfig.formatResolvConf(servers),
        )
    }

    @Test
    fun `B-SETUP-020 unusable addresses are filtered and empty phone DNS falls back to public resolvers`() {
        val unusable =
            listOf(
                InetAddress.getByName("0.0.0.0"),
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("::1"),
                InetAddress.getByAddress(byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)),
                InetAddress.getByName("224.0.0.251"),
            )

        assertEquals(BuiltinDnsConfig.DEFAULT_NAMESERVERS, BuiltinDnsConfig.selectNameservers(unusable))
        assertEquals(BuiltinDnsConfig.DEFAULT_NAMESERVERS, BuiltinDnsConfig.selectNameservers(emptyList()))
        assertEquals("nameserver 1.1.1.1\nnameserver 8.8.8.8\n", BuiltinDnsConfig.formatResolvConf(emptyList()))
    }

    @Test
    fun `B-SETUP-021 resolv conf write unlinks guest symlink safely and skips unchanged rewrites`() =
        withRootfs { rootfs ->
            val victim = Files.createTempFile("oc-dns-victim", ".txt").toFile().apply { writeText("keep") }
            try {
                val resolv = File(rootfs, "etc/resolv.conf")
                Files.createSymbolicLink(resolv.toPath(), victim.toPath())
                val corporate = listOf(InetAddress.getByName("192.0.2.10"))

                assertTrue(BuiltinDnsConfig.writeResolvConf(rootfs, corporate))
                assertFalse(Files.isSymbolicLink(resolv.toPath()))
                assertEquals("nameserver 192.0.2.10\n", resolv.readText())
                assertEquals("keep", victim.readText())
                assertFalse(BuiltinDnsConfig.writeResolvConf(rootfs, corporate))

                val carrier = listOf(InetAddress.getByName("198.51.100.53"))
                assertTrue(BuiltinDnsConfig.writeResolvConf(rootfs, carrier))
                assertEquals("nameserver 198.51.100.53\n", resolv.readText())
            } finally {
                victim.delete()
            }
        }
}
