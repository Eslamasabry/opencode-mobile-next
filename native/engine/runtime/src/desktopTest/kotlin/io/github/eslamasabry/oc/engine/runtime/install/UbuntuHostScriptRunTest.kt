package io.github.eslamasabry.oc.engine.runtime.install

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Port of test/host_script_install_test.dart: runs [UbuntuHostScript.TEXT] with real bash, a fake `curl` (serving a
 * small archive instead of npm) and a fake `systemctl`, so nothing touches the network or the machine's own services.
 */
class UbuntuHostScriptRunTest {
    private lateinit var root: File
    private lateinit var home: File
    private lateinit var tmp: File
    private lateinit var archive: File
    private lateinit var archiveSha: String
    private lateinit var script: File

    private fun read(name: String): String = File(root, name).let { if (it.exists()) it.readText() else "" }

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("host-script-install-").toFile()
        home = File(root, "home").apply { mkdir() }
        tmp = File(root, "tmp").apply { mkdir() }
        archive = File(root, "release.tar.gz")
        val bin = File(root, "bin").apply { mkdir() }
        File(bin, "curl").writeText(
            """#!/bin/bash
out=""; url=""
while [[ ${'$'}# -gt 0 ]]; do
  case "${'$'}1" in
    -o) out="${'$'}2"; shift 2 ;;
    --retry|--connect-timeout) shift 2 ;;
    -*) shift ;;
    *) url="${'$'}1"; shift ;;
  esac
done
echo "${'$'}url" >> "${'$'}FAKE_ROOT/curl.log"
cp "${'$'}FAKE_ROOT/release.tar.gz" "${'$'}out"
""",
        )
        File(bin, "systemctl").writeText("#!/bin/bash\necho \"\$*\" >> \"\$FAKE_ROOT/systemctl.log\"\n")
        Sh.chmod("755", File(bin, "curl"))
        Sh.chmod("755", File(bin, "systemctl"))
        buildArchive(ManagedRuntime.OpenCode2.pinnedVersion)
        // The engine's script, written where a phone or PC would save it.
        script = File(root, "ubuntu-opencode-pinned.sh").apply { writeText(UbuntuHostScript.TEXT) }
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    /** A release archive holding one `opencode` that reports [version]. */
    private fun buildArchive(version: String) {
        val staging = File(root, "staging").apply { mkdir() }
        File(staging, "package/bin").mkdirs()
        val binary = File(staging, "package/bin/opencode").apply { writeText("#!/bin/sh\necho \"$version\"\n") }
        Sh.chmod("755", binary)
        val tar = Sh.exec("tar", "-czf", archive.path, "-C", staging.path, "package/bin/opencode")
        assertEquals(0, tar.exitCode, tar.stderr)
        archiveSha = Sh.sha256(archive)
    }

    /** A copy of the script whose recorded checksums are the fake archive's. */
    private fun scriptTrustingFakeArchive(): File {
        val sha = Regex("^(readonly OPENCODE_(X64|ARM64)_SHA256=)\"[0-9a-f]{64}\"$", RegexOption.MULTILINE)
        assertEquals(2, sha.findAll(UbuntuHostScript.TEXT).count())
        return File(root, "ubuntu-opencode.sh").apply {
            writeText(UbuntuHostScript.TEXT.replace(sha) { "${it.groupValues[1]}\"$archiveSha\"" })
        }
    }

    /** An `opencode` already on the machine at ~/.local/bin, at [version]. */
    private fun existingOpenCode(version: String): File {
        val dir = File(home, ".local/bin").apply { mkdirs() }
        return File(dir, "opencode").apply {
            writeText("#!/bin/sh\necho \"$version\"\n")
            Sh.chmod("755", this)
        }
    }

    private fun run(script: File, command: UbuntuHostScript.Command): ShRun =
        Sh.run(
            listOf("bash", script.path, command.wireName),
            mapOf(
                "HOME" to home.path,
                "TMPDIR" to tmp.path,
                "PATH" to "${root.path}/bin:/usr/bin:/bin",
                "USER" to "tester",
                "FAKE_ROOT" to root.path,
            ),
        )

    private fun unitExecStart(): String =
        File(home, ".config/systemd/user/opencode-serve.service").readLines().first { it.startsWith("ExecStart=") }

    private fun pinnedAsset(): String = checkNotNull(UbuntuHostScript.assetFor(Sh.exec("uname", "-m").stdout.trim()))

    private val shadowed = listOf("/usr/bin", "/bin").any { File(it, "opencode").exists() }

    @Test
    fun `B-LINUX-075 a download that does not match the checksum installs nothing`() {
        if (shadowed) return
        val result = run(script, UbuntuHostScript.Command.Install)
        assertEquals(1, result.exitCode)
        assertTrue(
            "The OpenCode download does not match its published checksum, so nothing was installed." in result.stderr,
            result.stderr,
        )
        assertTrue("got:      $archiveSha" in result.stderr)
        val asset = pinnedAsset()
        assertEquals(
            "https://registry.npmjs.org/@opencode/${asset.replaceFirst("-2.2.5.tgz", "")}/-/$asset",
            read("curl.log").trim(),
        )
        assertEquals(UbuntuHostScript.downloadUrl(asset), read("curl.log").trim())
        assertFalse(File(home, ".opencode/bin/opencode").exists())
        assertTrue(tmp.listFiles()!!.isEmpty(), "the download is cleaned up")
        assertFalse("enable" in read("systemctl.log"))
    }

    @Test
    fun `B-LINUX-075 a matching download is installed and becomes the service`() {
        if (shadowed) return
        val result = run(scriptTrustingFakeArchive(), UbuntuHostScript.Command.Install)
        assertEquals(0, result.exitCode, result.stderr)
        val binary = "${home.path}/.opencode/bin/opencode"
        assertTrue(File(binary).exists())
        assertTrue("==> Installed OpenCode ${ManagedRuntime.OpenCode2.pinnedVersion} to $binary" in result.stderr)
        assertTrue(unitExecStart().startsWith("ExecStart=$binary serve "))
        assertTrue(
            "UnsetEnvironment=OPENCODE_PASSWORD" in File(home, ".config/systemd/user/opencode-serve.service").readText(),
        )
        assertTrue("--user enable --now opencode-serve" in read("systemctl.log"))
        assertTrue(tmp.listFiles()!!.isEmpty())
        // The password lives in a 0600 file, never in the unit.
        val env = File(home, ".config/opencode-serve.env")
        assertTrue(env.readText().startsWith("OPENCODE_SERVER_PASSWORD="))
        assertEquals("600", Sh.exec("stat", "-c", "%a", env.path).stdout.trim())
    }

    @Test
    fun `B-LINUX-076 install refuses an existing unsupported server without changing it`() {
        if (shadowed) return
        val existing = existingOpenCode("1.19.0")
        val result = run(scriptTrustingFakeArchive(), UbuntuHostScript.Command.Install)
        assertEquals(1, result.exitCode)
        assertTrue("no longer supports" in result.stderr)
        assertTrue("1.19.0" in existing.readText())
        assertEquals("", read("curl.log"))
        assertFalse("enable" in read("systemctl.log"))
        assertEquals(UbuntuHostScript.InstallDecision.RefuseUnsupported, UbuntuHostScript.installDecision("1.19.0"))
    }

    @Test
    fun `B-LINUX-076 install leaves an OpenCode that is already there alone`() {
        if (shadowed) return
        val existing = existingOpenCode("2.0.0")
        val result = run(scriptTrustingFakeArchive(), UbuntuHostScript.Command.Install)
        assertEquals(0, result.exitCode, result.stderr)
        assertTrue("OpenCode 2 is already installed: ${existing.path}" in result.stdout)
        assertEquals("", read("curl.log"))
        assertTrue(unitExecStart().startsWith("ExecStart=${existing.path} serve "))
        assertEquals(UbuntuHostScript.InstallDecision.KeepExisting, UbuntuHostScript.installDecision("2.0.0"))
    }

    @Test
    fun `B-LINUX-077 update moves an older OpenCode 2 to the pinned release`() {
        if (shadowed) return
        existingOpenCode("2.0.28")
        val result = run(scriptTrustingFakeArchive(), UbuntuHostScript.Command.Update)
        assertEquals(0, result.exitCode, result.stderr)
        assertTrue("Moving OpenCode 2.0.28 to the pinned ${ManagedRuntime.OpenCode2.pinnedVersion}" in result.stdout)
        assertTrue(unitExecStart().startsWith("ExecStart=${home.path}/.opencode/bin/opencode serve "))
        assertTrue("--user restart opencode-serve" in read("systemctl.log"))
    }

    @Test
    fun `B-LINUX-077 update does not migrate an existing Older OpenCode`() {
        if (shadowed) return
        val existing = existingOpenCode("1.19.0")
        val result = run(scriptTrustingFakeArchive(), UbuntuHostScript.Command.Update)
        assertEquals(1, result.exitCode)
        assertTrue("Older OpenCode is unchanged" in result.stderr)
        assertEquals("", read("curl.log"))
        assertFalse("restart" in read("systemctl.log"))
        assertTrue("1.19.0" in existing.readText())
    }

    @Test
    fun `B-LINUX-077 update never downgrades a newer OpenCode`() {
        if (shadowed) return
        val existing = existingOpenCode("99.0.0")
        val result = run(scriptTrustingFakeArchive(), UbuntuHostScript.Command.Update)
        assertEquals(0, result.exitCode, result.stderr)
        assertTrue("OpenCode 99.0.0 is already at or past" in result.stdout)
        assertEquals("", read("curl.log"))
        assertTrue(unitExecStart().startsWith("ExecStart=${existing.path} serve "))
    }

    @Test
    fun `B-LINUX-077 update refuses a mismatched download and keeps the old one`() {
        if (shadowed) return
        val existing = existingOpenCode("2.0.28")
        val result = run(script, UbuntuHostScript.Command.Update)
        assertEquals(1, result.exitCode)
        assertTrue("does not match its published checksum" in result.stderr)
        assertFalse(File(home, ".opencode/bin/opencode").exists())
        assertTrue(existing.exists())
        assertFalse("restart" in read("systemctl.log"))
    }

    @Test
    fun `B-LINUX-075 the engine's host script is scripts-host-ubuntu-opencode-sh byte for byte`() {
        assertEquals(File(Sh.repo, "scripts/host/ubuntu-opencode.sh").readText(), UbuntuHostScript.TEXT)
    }
}
