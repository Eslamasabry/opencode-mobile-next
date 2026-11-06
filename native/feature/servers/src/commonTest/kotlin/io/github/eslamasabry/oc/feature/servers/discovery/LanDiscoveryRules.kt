package io.github.eslamasabry.oc.feature.servers.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Subnet arithmetic and result merging for "Find on my network". (Not named *Test.kt: this module's build excludes those.) */
class LanDiscoveryRules {
    @Test
    fun slash24ListsEveryNeighbourExceptSelfNearestFirst() {
        val hosts = LanSubnet.hosts("192.168.1.20", 24)!!
        assertEquals(253, hosts.size)
        assertFalse("192.168.1.20" in hosts)
        assertFalse("192.168.1.0" in hosts)
        assertFalse("192.168.1.255" in hosts)
        assertEquals(listOf("192.168.1.19", "192.168.1.21"), hosts.take(2).sorted())
    }

    @Test
    fun slash22IsTheWidestScanned() {
        assertEquals(1021, LanSubnet.hosts("10.0.5.9", 22)!!.size)
        assertNull(LanSubnet.hosts("10.0.5.9", 21))
        assertNull(LanSubnet.hosts("10.0.5.9", 16))
    }

    @Test
    fun noNeighboursOrBadAddressMeansNoScan() {
        assertNull(LanSubnet.hosts("192.168.1.2", 31))
        assertNull(LanSubnet.hosts("192.168.1.2", 32))
        assertNull(LanSubnet.hosts("fe80::1", 24))
        assertNull(LanSubnet.hosts("192.168.1.300", 24))
    }

    @Test
    fun onlyOwnSubnetIsInside() {
        assertTrue(LanSubnet.contains("192.168.1.20", 24, "192.168.1.200"))
        assertFalse(LanSubnet.contains("192.168.1.20", 24, "192.168.2.20"))
        assertFalse(LanSubnet.contains("192.168.1.20", 24, "8.8.8.8"))
        assertFalse(LanSubnet.contains("10.0.0.1", 8, "10.9.9.9")) // too wide to be scanned at all
    }

    @Test
    fun portsKeepDefaultsFirstAndDropRepeatsAndNonsense() {
        assertEquals(listOf(4096, 4097), LanSubnet.ports(emptySet()))
        assertEquals(listOf(4096, 4097, 8080), LanSubnet.ports(setOf(4097, 8080, 0, 70000)))
    }

    @Test
    fun theSameComputerFoundTwiceIsOneRowWithTheRichestFacts() {
        val advertised = LanFinding("192.168.1.5", 4096, name = "opencode", source = LanSource.Advertised)
        val scanned = LanFinding("192.168.1.5", 4096, version = "1.2.3", needsPassword = true)
        val merged = LanFindings.merge(LanFindings.merge(emptyList(), advertised), scanned)
        assertEquals(1, merged.size)
        assertEquals("opencode", merged[0].name)
        assertEquals("1.2.3", merged[0].version)
        assertTrue(merged[0].needsPassword)
    }

    @Test
    fun otherPortsAreOtherRowsAndNamedOnesSortFirst() {
        var list = emptyList<LanFinding>()
        list = LanFindings.merge(list, LanFinding("192.168.1.9", 4097))
        list = LanFindings.merge(list, LanFinding("192.168.1.9", 4096))
        list = LanFindings.merge(list, LanFinding("192.168.1.2", 4096, name = "desk"))
        val sorted = LanFindings.sorted(list)
        assertEquals(3, sorted.size)
        assertEquals("desk", sorted[0].title)
        assertEquals(listOf(4096, 4097), sorted.drop(1).map { it.port })
    }

    @Test
    fun advertisedNamesNeverBecomeIpAddressesAndAddressesFormat() {
        assertEquals("opencode", LanFindings.nameFrom("opencode.local"))
        assertNull(LanFindings.nameFrom("192.168.1.5"))
        assertTrue(LanFindings.looksLikeOpenCode("My OpenCode box"))
        assertFalse(LanFindings.looksLikeOpenCode("Printer", null))
        assertEquals("http://192.168.1.5:4096", LanFinding("192.168.1.5", 4096).address)
    }
}
