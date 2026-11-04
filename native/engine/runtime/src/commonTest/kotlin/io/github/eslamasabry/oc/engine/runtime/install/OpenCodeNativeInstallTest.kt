package io.github.eslamasabry.oc.engine.runtime.install

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of the script-text facts of test/opencode_native_install_test.dart (the runs are in desktopTest). */
class OpenCodeNativeInstallTest {
    @Test
    fun `B-LINUX-111 the production script picks the pinned archive for each CPU, checks it before unpacking, and uses no npm`() {
        val script = OpenCodeInstallScripts.nativeInstall()
        val pins = OpenCodePins.pinned
        val arm = script.lines().single { it.trim().startsWith("aarch64|arm64)") }
        val x64 = script.lines().single { it.trim().startsWith("x86_64|amd64)") }
        assertTrue(pins.arm64.url in arm && pins.arm64.sha256 in arm)
        assertTrue(pins.x64.url in x64 && pins.x64.sha256 in x64)
        assertTrue(script.indexOf("oc_download \"\$oc_url\" \"\$oc_file\" \"\$oc_sha\"") < script.indexOf("tar -xzf"))
        assertTrue(script.indexOf("oc_download") >= 0)
        assertNull(Regex("^[^#\\n]*\\bnpm\\s", RegexOption.MULTILINE).find(script), "no npm command at all")
        assertNull(Regex("curl[^\\n]*\\|\\s*(ba)?sh").find(script))
        // The pins scripts/host/ubuntu-opencode.sh carries, and baseline x64.
        assertEquals("f8c5a82075fede9b5b044ed6c8a6d13ad807a7e62caea1cd0a878adef49eb053", OpenCodePins.v2Arm64.sha256)
        assertEquals("873856acbaa71c5ed8df6433e312dfa0c82a86e2cddfb9d80f0a31123a917a08", OpenCodePins.v2X64.sha256)
        assertTrue("cli-linux-x64-baseline-2.2.5.tgz" in OpenCodePins.v2X64.url)
        assertTrue("cli-linux-arm64-2.2.5.tgz" in OpenCodePins.v2Arm64.url)
        // Another version is not installed from an unpinned file.
        val other = OpenCodeInstallScripts.nativeInstall(version = "1.18.30")
        assertTrue(OpenCodeInstallFailure.Unpinned.line in other && "exit 64" in other)
        assertFailsWith<IllegalArgumentException> { OpenCodeInstallScripts.nativeInstall(version = "2.2.5; rm -rf /") }
    }

    @Test
    fun `B-LINUX-111 the in-app component uses the native install and Termux keeps its shared text`() {
        val builtin = OpenCodeInstallScripts.component(SetupHost.Builtin)
        assertEquals(OpenCodeInstallScripts.nativeInstall(), builtin.installScript)
        assertEquals(OpenCodeInstallScripts.nativeCheck(), builtin.checkScript)
        val termux = OpenCodeInstallScripts.component(SetupHost.Termux)
        assertTrue(OpenCodeUbuntuSetup.SCRIPT in termux.installScript)
        assertTrue("bash -s <<'OC_PROOT_SETUP'\n${OpenCodeUbuntuSetup.SCRIPT}OC_PROOT_SETUP\n" in termux.installScript)
        assertTrue("export OC_REQUESTED_VERSION='2.2.5'" in termux.installScript)
        assertTrue("[ \"\$installed\" = '2.2.5' ]" in termux.checkScript)
        // The check only passes for the pinned native program behind the link.
        assertTrue("[ \"\$(readlink -f \"\$oc_bin\")\" = '/opt/opencode2/bin/opencode2' ]" in builtin.checkScript)
        assertTrue(builtin.checkScript.startsWith(InstallPrelude.COMPONENT_UPDATE))
    }

    @Test
    fun `B-LINUX-111 npm installs that remain allow exactly their own package's install scripts`() {
        val text = OpenCodeUbuntuSetup.SCRIPT
        assertTrue("--allow-scripts=\"\$main_package\"" in text)
        assertFalse("main_package=opencode-ai" in text)
        assertTrue("main_package=@opencode/cli" in text)
    }

    @Test
    fun `B-LINUX-112 each failure ends with its own reason, worded by the setup page`() {
        assertEquals(OpenCodeInstallReason.NoProgram, OpenCodeInstallReason.of(OpenCodeInstallFailure.NoProgram.line))
        assertEquals(OpenCodeInstallReason.WontRun, OpenCodeInstallReason.of(OpenCodeInstallFailure.WontRun.line))
        assertEquals(OpenCodeInstallReason.NoStart, OpenCodeInstallReason.of(OpenCodeInstallFailure.NoStart.line))
        assertEquals(
            OpenCodeInstallReason.Checksum,
            OpenCodeInstallReason.of("[oc] The download did not match its checksum and was deleted: http://x/a.tgz"),
        )
        assertEquals(OpenCodeInstallReason.Other, OpenCodeInstallReason.of("[oc] OpenCode could not finish updating. Run setup again."))
        val script = OpenCodeInstallScripts.nativeInstall()
        for (failure in listOf(OpenCodeInstallFailure.NoProgram, OpenCodeInstallFailure.WontRun, OpenCodeInstallFailure.NoStart)) {
            assertTrue("oc_fail \"${failure.line}\"" in script, failure.name)
        }
        // A failure after activation restores both the program folder and its link before saying so.
        val restore = script.substringAfter("oc_activation_failed() {").substringBefore("}\n")
        assertTrue("oc_update_recover \"\$oc_dir\"" in restore && "oc_update_recover \"\$oc_link\"" in restore)
    }
}
