package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** Issue #111: a confined launcher's stderr warning must not hide the gate header. */
class NativeGateHeaderTest {
    private val nonce = "0123456789abcdef".repeat(4)
    private fun read(text: String, prefix: String = "OC-INSTALL-1") =
        NativeGateHeader.read(ByteArrayInputStream(text.toByteArray(Charsets.US_ASCII)), prefix)

    // Verbatim shape seen on OC_API37 (Android 17, Landlock tier) with APK 4511.
    private val linkerWarning = "linker: Warning: couldn't read '/linkerconfig/ld.config.txt' for " +
        "'/data/app/~~Mc199SryxNaaDnhRORR3TQ==/io.github.eslamasabry.opencode_mobile-eJeK9rMDy-5tO4l7XV_GGg==" +
        "/lib/x86_64/libproot.so' (using default configuration instead): error reading file " +
        "\"/linkerconfig/ld.config.txt\": Permission denied"

    @Test
    fun headerAfterLinkerWarningsIsFound() {
        assertTrue(linkerWarning.length > 128) // The old reader gave up after 128 bytes without a newline.
        val header = "OC-INSTALL-1 $nonce 3341"
        assertEquals(header, read("$linkerWarning\nWARNING: $linkerWarning\n$header\ninside\n"))
        assertEquals("OC-GATE-1 $nonce 9", read("$linkerWarning\nOC-GATE-1 $nonce 9\n", "OC-GATE-1"))
    }

    @Test
    fun headerAsFirstLineStillWorks() {
        assertEquals("OC-INSTALL-1 $nonce 12", read("OC-INSTALL-1 $nonce 12\nworkload output\n"))
    }

    @Test
    fun missingHeaderEndOfOutputOrUnboundedNoiseFails() {
        assertNull(read(""))
        assertNull(read("$linkerWarning\n")) // Exited before the gate ran.
        assertNull(read("OC-INSTALL-1 $nonce 12")) // No newline: incomplete.
        assertNull(read("x".repeat(NativeGateHeader.MAX_LINE_BYTES + 1) + "\nOC-INSTALL-1 $nonce 12\n"))
        val tooMany = "noise\n".repeat(NativeGateHeader.MAX_LINES) + "OC-INSTALL-1 $nonce 12\n"
        assertNull(read(tooMany))
        val justEnough = "noise\n".repeat(NativeGateHeader.MAX_LINES - 1) + "OC-INSTALL-1 $nonce 12\n"
        assertEquals("OC-INSTALL-1 $nonce 12", read(justEnough))
    }

    @Test
    fun otherGatesHeaderOrPrefixLookalikeIsNotAccepted() {
        assertNull(read("OC-GATE-1 $nonce 12\n"))
        assertNull(read("OC-INSTALL-12 $nonce 12\n"))
        assertNull(read("xOC-INSTALL-1 $nonce 12\n"))
    }

    @Test
    fun missingHeaderNamesAStaticReasonOnly() {
        fun reason(text: String) = (NativeGateHeader.readOutcome(
            ByteArrayInputStream(text.toByteArray(Charsets.US_ASCII)), "OC-INSTALL-1")
            as NativeGateHeader.Outcome.Missing).reason
        // APK 4512 on OC_API37: the sandbox refused a /linkerconfig rule and exited 78.
        assertEquals("gate_header_missing:eof:lines-1:sandbox-invalid_policy", reason("invalid_policy\n"))
        assertEquals("gate_header_missing:eof:lines-0", reason(""))
        assertEquals("gate_header_missing:eof:lines-1", reason("$linkerWarning\n"))
        assertEquals("gate_header_missing:line-too-long:lines-0", reason("y".repeat(600)))
        assertEquals("gate_header_missing:too-many-lines:lines-16", reason("secret value here\n".repeat(20)))
        // Free text never becomes a "code".
        assertEquals("gate_header_missing:eof:lines-1", reason("password=hunter2\n"))
    }

    @Test
    fun sandboxPolicyNeverNamesLinkerconfig() {
        // Issue #111 / APK 4512: the app cannot stat /linkerconfig, so the sandbox
        // refuses a policy that names it (invalid_policy) and nothing can start.
        assertFalse("/linkerconfig" in PhoneSandboxPolicy.readOnlySystemRoots)
        for (root in listOf("/system", "/apex", "/vendor", "/proc", "/sys")) {
            assertTrue(root in PhoneSandboxPolicy.readOnlySystemRoots)
        }
    }
}
