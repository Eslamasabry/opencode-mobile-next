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
                InetAddress.getByName("224.0.0.251"),
            )

        assertEquals(BuiltinDnsConfig.DEFAULT_NAMESERVERS, BuiltinDnsConfig.selectNameservers(unusable))
        assertEquals(BuiltinDnsConfig.DEFAULT_NAMESERVERS, BuiltinDnsConfig.selectNameservers(emptyList()))
        assertTrue(BuiltinDnsConfig.formatResolvConf(emptyList()).endsWith("nameserver 1.1.1.1\nnameserver 8.8.8.8\n"))
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
                assertTrue(resolv.readText().endsWith("nameserver 192.0.2.10\n"))
                assertEquals("keep", victim.readText())
                assertFalse(BuiltinDnsConfig.writeResolvConf(rootfs, corporate))

                val carrier = listOf(InetAddress.getByName("198.51.100.53"))
                assertTrue(BuiltinDnsConfig.writeResolvConf(rootfs, carrier))
                assertTrue(resolv.readText().endsWith("nameserver 198.51.100.53\n"))
            } finally {
                victim.delete()
            }
        }

    @Test
    fun `B-SETUP-032 sync preserves user-customized resolv conf and keeps existing file when phone DNS is empty`() =
        withRootfs { rootfs ->
            val resolv = File(rootfs, "etc/resolv.conf")
            val custom = "# my custom resolver\nnameserver 203.0.113.99\n"
            resolv.writeText(custom)
            val corporate = listOf(InetAddress.getByName("192.0.2.10"))

            assertFalse(
                "user-customized resolv.conf must not be overwritten",
                BuiltinDnsConfig.writeResolvConf(rootfs, corporate),
            )
            assertEquals(custom, resolv.readText())

            // Managed resolv.conf must not be overwritten with public fallback when Android reports no DNS.
            resolv.delete()
            assertTrue(BuiltinDnsConfig.writeResolvConf(rootfs, corporate))
            val managedText = resolv.readText()
            assertTrue(managedText.contains("nameserver 192.0.2.10\n"))
            assertFalse(
                "empty phone DNS must not overwrite existing resolv.conf",
                BuiltinDnsConfig.writeResolvConf(rootfs, emptyList()),
            )
            assertEquals(managedText, resolv.readText())

            // Legacy app default is preserved when offline, and upgraded once phone DNS is available.
            val legacyDefault = "nameserver 1.1.1.1\nnameserver 8.8.8.8\n"
            resolv.writeText(legacyDefault)
            assertFalse(BuiltinDnsConfig.writeResolvConf(rootfs, emptyList()))
            assertEquals(legacyDefault, resolv.readText())
            assertTrue(BuiltinDnsConfig.writeResolvConf(rootfs, corporate))
            assertTrue(resolv.readText().contains("nameserver 192.0.2.10\n"))
        }

    @Test
    fun `B-SETUP-033 link-local resolver is kept when it is the only network-supplied DNS`() {
        val linkLocal6 =
            InetAddress.getByAddress(
                byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            )
        val linkLocal4 =
            InetAddress.getByAddress(byteArrayOf(169.toByte(), 254.toByte(), 1, 1))
        val corporate = InetAddress.getByName("192.0.2.53")

        val onlyLinkLocal6 =
            BuiltinDnsConfig.selectNameservers(
                listOf(InetAddress.getByName("0.0.0.0"), InetAddress.getByName("127.0.0.1"), linkLocal6),
            )
        assertEquals(listOf(linkLocal6.hostAddress), onlyLinkLocal6)

        val onlyLinkLocal4 = BuiltinDnsConfig.selectNameservers(listOf(linkLocal4))
        assertEquals(listOf(linkLocal4.hostAddress), onlyLinkLocal4)

        val mixed = BuiltinDnsConfig.selectNameservers(listOf(linkLocal6, corporate))
        assertEquals(listOf("192.0.2.53"), mixed)
    }
}
