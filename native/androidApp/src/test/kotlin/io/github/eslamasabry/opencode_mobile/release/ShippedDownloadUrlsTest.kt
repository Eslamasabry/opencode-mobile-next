package io.github.eslamasabry.opencode_mobile.release

import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `test/shipped_download_urls_test.dart` (B-SEC-171, issue #87: a manifest shipped with the owner's Tailscale address
 * as its download server). Natively the shipped data is every module's assets, Java resources and bundled Compose
 * files, plus the download manifests that live in source (agent pins, the voice model catalog, the Linux rootfs base,
 * the update feed). UI copy (`values*` strings) is excluded: it shows example addresses, it is never fetched.
 */
class ShippedDownloadUrlsTest {
    private val url = Regex("""https?://[^\s"'<>()\[\]`]+""")
    private val textExtensions = setOf("json", "md", "txt", "xml", "svg", "yaml", "properties")

    /** Why [host] cannot be reached from an arbitrary phone on the internet, or null for a public name or address. */
    private fun privateHostProblem(host: String): String? {
        val name = host.lowercase().removePrefix("[").removeSuffix("]")
        if (name == "localhost" || name.endsWith(".localhost")) return "loopback"
        if (name.endsWith(".local") || name.endsWith(".lan")) return "LAN name"
        if (name.endsWith(".ts.net")) return "Tailscale name"
        if (name.endsWith(".internal") || name.endsWith(".home.arpa")) return "private name"
        val literal = Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(name) || (name.contains(':') && Regex("^[0-9a-f:.]+$").matches(name))
        if (!literal) return null
        val address = try {
            InetAddress.getByName(name) // a literal: parsed, never looked up
        } catch (_: Exception) {
            return null
        }
        if (address.isLoopbackAddress) return "loopback"
        if (address.isLinkLocalAddress) return "link-local"
        val b = address.address.map { it.toInt() and 0xff }
        if (address is Inet4Address) {
            if (b[0] == 0) return "unspecified"
            if (b[0] == 10) return "private (10/8)"
            if (b[0] == 172 && b[1] in 16..31) return "private (172.16/12)"
            if (b[0] == 192 && b[1] == 168) return "private (192.168/16)"
            if (b[0] == 100 && b[1] in 64..127) return "Tailscale/CGNAT"
            return null
        }
        if (address is Inet6Address) {
            if ((b[0] and 0xfe) == 0xfc) return "unique local IPv6"
            if (address.isMulticastAddress) return "multicast"
        }
        return null
    }

    private fun host(match: String): String? =
        Regex("""^https?://(\[[^\]]+]|[^/:?#$@]+)""").find(match)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() && !it.startsWith("$") }

    /** Shipped data files of every module (assets, Java resources, bundled Compose files, res/xml and res/raw). */
    private fun shippedDataFiles(): List<File> =
        RepoFiles.native.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", "spikes", "fixtures", "e2e", "tools", "benchmark") }
            .filter { it.isFile && it.extension in textExtensions }
            .filter { f ->
                val p = f.invariantSeparatorsPath
                val set = Regex("""/src/([A-Za-z]*[Mm]ain|preview|switch|release)/""").containsMatchIn(p)
                set && (p.contains("/assets/") || p.contains("/resources/") || p.contains("/composeResources/files/") ||
                    p.contains("/res/xml/") || p.contains("/res/raw/"))
            }
            .sortedBy { it.path }
            .toList()

    /** Main Kotlin sources that declare a download (pinned archives, model files, release files, rootfs). */
    private fun downloadLiterals(): List<Pair<File, String>> {
        val download = Regex("""/releases/download/|\.tgz\b|\.tar\.(gz|xz)\b|\.zip\b|\.apk\b|\.onnx\b|/resolve/|cdimage\.|downloads\.|registry\.npmjs\.org""")
        return RepoFiles.native.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", "spikes", "test", "commonTest", "desktopTest", "androidUnitTest", "androidHostTest") }
            .filter { it.isFile && it.extension == "kt" && Regex("""/src/[A-Za-z]*[Mm]ain/""").containsMatchIn(it.invariantSeparatorsPath) }
            .flatMap { f -> url.findAll(f.readText()).map { f to it.value } }
            .filter { download.containsMatchIn(it.second) }
            .toList()
    }

    @Test
    fun `B-SEC-171 the private-host check catches the address that shipped in #87`() {
        assertEquals("Tailscale/CGNAT", privateHostProblem("100.101.102.103"))
        assertEquals("loopback", privateHostProblem("127.0.0.1"))
        assertEquals("loopback", privateHostProblem("[::1]"))
        assertEquals("link-local", privateHostProblem("169.254.1.2"))
        assertNotNull(privateHostProblem("192.168.1.20"))
        assertNotNull(privateHostProblem("10.0.0.5"))
        assertNotNull(privateHostProblem("172.20.0.1"))
        assertNotNull(privateHostProblem("dev-pc.tail1234.ts.net"))
        assertNotNull(privateHostProblem("fd7a:115c:a1e0::1"))
        assertNotNull(privateHostProblem("printer.local"))
        assertNull(privateHostProblem("github.com"))
        assertNull(privateHostProblem("100.128.0.1"))
        assertNull(privateHostProblem("172.32.0.1"))
    }

    @Test
    fun `B-SEC-171 no URL in shipped data files points at a private address`() {
        val files = shippedDataFiles()
        assertTrue(files.any { it.name == "aiteam-engine-manifest.json" }, "the scan must reach the Linux assets")
        val problems = mutableListOf<String>()
        for (file in files) {
            for (m in url.findAll(file.readText())) {
                val h = host(m.value) ?: continue
                privateHostProblem(h)?.let { problems += "${file.relativeTo(RepoFiles.native)}: ${m.value} ($it)" }
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun `B-SEC-171 every bundled download manifest is https and public`() {
        val manifests = shippedDataFiles().filter { it.extension == "json" && it.invariantSeparatorsPath.contains("/assets/") }
        assertTrue(manifests.any { it.name == "aiteam-engine-manifest.json" })
        for (file in manifests) {
            for (m in url.findAll(file.readText())) {
                assertTrue(m.value.startsWith("https://"), "${file.name} downloads over plain http: ${m.value}")
                assertNull(privateHostProblem(host(m.value).orEmpty()), "${file.name}: ${m.value}")
            }
        }
    }

    @Test
    fun `B-SEC-171 every download address declared in source is https and public`() {
        val literals = downloadLiterals()
        val files = literals.map { it.first.name }.toSet()
        for (expected in listOf("AgentPinsJson.kt", "ModelCatalog.kt", "BuiltinLinux.kt")) {
            assertTrue(expected in files, "the scan must reach $expected (found $files)")
        }
        val problems = literals.mapNotNull { (file, value) ->
            val h = host(value)
            when {
                !value.startsWith("https://") -> "${file.name}: $value (plain http)"
                h == null -> null // a template whose host is a parameter is judged where it is filled in
                else -> privateHostProblem(h)?.let { "${file.name}: $value ($it)" }
            }
        }
        assertEquals(emptyList(), problems)
    }
}
