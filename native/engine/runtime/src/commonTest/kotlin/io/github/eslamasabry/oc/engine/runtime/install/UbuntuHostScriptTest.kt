package io.github.eslamasabry.oc.engine.runtime.install

import io.github.eslamasabry.oc.engine.runtime.install.UbuntuHostScript.InstallDecision
import io.github.eslamasabry.oc.engine.runtime.install.UbuntuHostScript.UpdateDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules of scripts/host/ubuntu-opencode.sh as data (test/host_script_install_test.dart runs the script itself). */
class UbuntuHostScriptTest {
    @Test
    fun `B-LINUX-075 the pinned archive and its checksum come from the script, per CPU`() {
        assertEquals(UbuntuHostScript.X64_ASSET, UbuntuHostScript.assetFor("x86_64"))
        assertEquals(UbuntuHostScript.X64_ASSET, UbuntuHostScript.assetFor("amd64"))
        assertEquals(UbuntuHostScript.ARM64_ASSET, UbuntuHostScript.assetFor("aarch64"))
        assertEquals(UbuntuHostScript.ARM64_ASSET, UbuntuHostScript.assetFor("arm64"))
        assertNull(UbuntuHostScript.assetFor("armv7l"))
        assertEquals(
            "https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/-/cli-linux-x64-baseline-2.2.5.tgz",
            UbuntuHostScript.downloadUrl(UbuntuHostScript.X64_ASSET),
        )
        assertEquals(
            "https://registry.npmjs.org/@opencode/cli-linux-arm64/-/cli-linux-arm64-2.2.5.tgz",
            UbuntuHostScript.downloadUrl(UbuntuHostScript.ARM64_ASSET),
        )
        // Exactly two recorded checksums, each the one this module pins, and the pin matches the managed runtime's.
        val sha = Regex("^(readonly OPENCODE_(X64|ARM64)_SHA256=)\"([0-9a-f]{64})\"$", RegexOption.MULTILINE)
        val recorded = sha.findAll(UbuntuHostScript.TEXT).associate { it.groupValues[2] to it.groupValues[3] }
        assertEquals(mapOf("X64" to UbuntuHostScript.X64_SHA256, "ARM64" to UbuntuHostScript.ARM64_SHA256), recorded)
        assertTrue("readonly OPENCODE_VERSION=\"${UbuntuHostScript.PINNED_VERSION}\"" in UbuntuHostScript.TEXT)
        assertEquals(ManagedRuntime.OpenCode2.pinnedVersion, UbuntuHostScript.PINNED_VERSION)
        assertEquals(OpenCodePins.v2X64.sha256, UbuntuHostScript.X64_SHA256)
        assertEquals(OpenCodePins.v2Arm64.sha256, UbuntuHostScript.ARM64_SHA256)
    }

    @Test
    fun `B-LINUX-075 the checksum is checked before unpacking and the service never carries the password`() {
        val text = UbuntuHostScript.TEXT
        assertTrue(text.indexOf("sha256sum \"\$WORK_DIR/\$asset\"") < text.indexOf("tar -xzf \"\$WORK_DIR/\$asset\""))
        assertTrue("The OpenCode download does not match its published checksum, so nothing was installed." in text)
        assertTrue("UnsetEnvironment=OPENCODE_PASSWORD" in text)
        assertTrue("ExecStart=\$opencode_bin serve --hostname \$BIND_HOST --port \$PORT" in text)
        assertTrue("systemctl --user enable --now \"\$SERVICE_NAME\"" in text)
        assertTrue(Regex("curl[^\\n]*\\|\\s*(ba)?sh").find(text) == null, "nothing is piped into a shell")
    }

    @Test
    fun `B-LINUX-076 install leaves an OpenCode 2 alone and refuses an unsupported one`() {
        assertEquals(InstallDecision.InstallPinned, UbuntuHostScript.installDecision(null))
        assertEquals(InstallDecision.KeepExisting, UbuntuHostScript.installDecision("2.0.0"))
        assertEquals(InstallDecision.KeepExisting, UbuntuHostScript.installDecision("2.2.5"))
        assertEquals(InstallDecision.RefuseUnsupported, UbuntuHostScript.installDecision("1.19.0"))
        assertEquals(InstallDecision.RefuseUnsupported, UbuntuHostScript.installDecision(""))
        assertTrue("This server runs OpenCode 1, which this app no longer supports." in UbuntuHostScript.TEXT)
    }

    @Test
    fun `B-LINUX-077 update moves only an older OpenCode 2 and never downgrades`() {
        assertEquals(UpdateDecision.MoveToPinned, UbuntuHostScript.updateDecision("2.0.28"))
        assertEquals(UpdateDecision.MoveToPinned, UbuntuHostScript.updateDecision("2.2.4"))
        assertEquals(UpdateDecision.RefuseLegacy, UbuntuHostScript.updateDecision("1.19.0"))
        assertEquals(UpdateDecision.AlreadyCurrent, UbuntuHostScript.updateDecision("99.0.0"))
        assertEquals(UpdateDecision.AlreadyCurrent, UbuntuHostScript.updateDecision("2.2.5"))
        assertEquals(UpdateDecision.AlreadyCurrent, UbuntuHostScript.updateDecision("2.10.0"), "compared as versions, not text")
        assertEquals(UpdateDecision.Unidentified, UbuntuHostScript.updateDecision(""))
        assertEquals(UpdateDecision.NotInstalled, UbuntuHostScript.updateDecision(null, found = false))
        assertTrue("Older OpenCode is unchanged." in UbuntuHostScript.TEXT)
    }
}
