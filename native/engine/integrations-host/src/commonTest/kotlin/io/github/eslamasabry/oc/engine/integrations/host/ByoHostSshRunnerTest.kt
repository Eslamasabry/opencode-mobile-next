package io.github.eslamasabry.oc.engine.integrations.host

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Flutter test/byo_host/ssh_runner_test.dart, over in-memory private files and a scripted shell. */
class ByoHostSshRunnerTest {
    private val fs = MemFs()
    private val loopback = FakeLoopback()
    private val shell = FakeShell(fs, loopback)
    private val signer = FakeSigner(fs)
    private var http: (LoopbackRequest) -> LoopbackReply = { LoopbackReply(500, ByteArray(0)) }
    private val fakeHttp = FakeHttp { http(it) }
    private val runner = newRunner()

    private fun newRunner() = BuiltinByoHostSshRunner(shell, fs, signer, fakeTailnet(), loopback, fakeHttp)

    private val identity = ByoHostIdentity("oc.byoHostSsh.profile_1", devicePublicKey())
    private val pin get() = ByoHostKey.fromPublicKey(hostPublicKey())!!
    private val server = ByoHostTarget.parse("ubuntu@server.example")!!
    private val bundle =
        ByoHostBundle.of("1.1.0", "1.18.32", mapOf("x64" to ByoHostArtifact.of("https://downloads.example.invalid/pinned.tar.gz", "a".repeat(64))!!))!!

    private suspend fun install(login: ByoHostLogin, target: ByoHostTarget = server, token: String = "DEVICE_TOKEN_SENTINEL_1234567890123456") =
        runner.install("profile_1", target, pin, login, identity, token, bundle)

    private suspend fun forward(target: ByoHostTarget = server) = runner.forward("profile_1", target, pin, identity, 4096)

    private fun test(body: suspend TestScope.() -> Unit) = runTest { try { body() } finally { runner.dispose() } }

    @Test
    fun `B-SEC-030 scans only a public key and computes SHA256 from the SSH wire encoding`() =
        test {
            val expected = ByoHostKey.fromPublicKey(hostPublicKey())!!
            val key = runner.inspectKey(server).ok()
            assertEquals(expected.publicKey, key.publicKey)
            assertEquals(expected.fingerprint, key.fingerprint)
            // Fingerprint is SHA256 over the wire bytes, base64 without padding.
            val wire = Base64.decode(hostPublicKey().split(' ')[1])
            assertEquals("SHA256:" + Base64.encode(hexBytes(DartJson.sha256Hex(wire))).replace("=", ""), key.fingerprint)
            val command = shell.privateCommands.single()
            assertTrue("ssh-keyscan -T 10 -t ed25519" in command)
            assertFalse("ssh -" in command)
            assertTrue("'${ip4(100, 64, 0, 10)}'" in command)
            assertFalse("server.example" in command)
            assertTrue(fs.tmpEntries().isEmpty())
            assertNull(ByoHostKey.fromPublicKey("ssh-ed25519 " + Base64.encode(ByteArray(51))))
        }

    @Test
    fun `B-SEC-030 key generation never returns secrets through a native command or output`() =
        test {
            val generated = runner.generateIdentity("profile_1").ok()
            assertEquals("oc.byoHostSsh.profile_1", generated.keyAlias)
            assertEquals(devicePublicKey(), generated.publicKey)
            assertEquals(listOf("profile_1"), signer.ensured)
            assertTrue(shell.privateCommands.isEmpty())
            assertTrue(shell.observedFiles.isEmpty())
            assertFalse("PRIVATE_KEY_SENTINEL" in shell.nativeScripts.joinToString("\n"))
            assertTrue(fs.tmpEntries().isEmpty())
        }

    @Test
    fun `B-SEC-031 installation uses private stdin, a strict host pin and the bundled digest before execution`() =
        test {
            val login = ByoHostLogin(password = "PASSWORD_SENTINEL")
            assertEquals("machine_1", install(login).ok().hostId)
            assertNull(login.password)
            val scripts = shell.nativeScripts.joinToString("\n")
            for (secret in listOf("PASSWORD_SENTINEL", "DEVICE_KEY_SENTINEL", "DEVICE_TOKEN_SENTINEL")) assertFalse(secret in scripts)
            val command = shell.privateCommands.last()
            for (option in listOf(
                "StrictHostKeyChecking=yes", "IdentityAgent=none", "ForwardAgent=no", "PermitLocalCommand=no",
                "HostKeyAlias=oc-byo-host", "GlobalKnownHostsFile=/dev/null",
            )) {
                assertTrue(option in command, option)
            }
            assertFalse("PASSWORD_SENTINEL" in command)
            assertTrue("'${ip4(100, 64, 0, 10)}'" in command)
            assertFalse("server.example" in command)
            assertFalse(shell.observedFiles.keys.any { it.endsWith("/identity") || it.endsWith("/private_key") })
            assertTrue(signer.ensured.isEmpty())
            assertTrue(signer.agents.isEmpty())
            val input = shell.installInput!!
            assertTrue("DEVICE_TOKEN_SENTINEL" in input)
            assertTrue(input.indexOf("sha256sum -c") < input.indexOf("sh \"\$work/install.sh\""))
            assertTrue("a".repeat(64) in input)
            assertFalse(".sha256" in input)
            assertTrue(fs.tmpEntries().isEmpty())
        }

    @Test
    fun `B-SEC-031 failed install hides remote output, consumes the login and deletes private files`() =
        test {
            shell.failInstall = true
            val login = ByoHostLogin(password = "PASSWORD_SENTINEL")
            val result = install(login)
            assertEquals(HostResult.Failed(ByoHostFailureCode.Uncertain), result)
            assertFalse("PRIVATE_ERROR_SENTINEL" in result.toString())
            assertNull(login.password)
            assertTrue(fs.tmpEntries().isEmpty())
        }

    @Test
    fun `B-SEC-024 changed host key is rejected before bootstrap success`() =
        test {
            shell.failInstall = true
            shell.failureOutput = "Host key verification failed"
            val login = ByoHostLogin(password = "PASSWORD_SENTINEL")
            assertEquals(HostResult.Failed(ByoHostFailureCode.HostKeyChanged), install(login))
            assertNull(login.password)
            assertTrue(signer.agents.isEmpty())
            assertTrue(fs.tmpEntries().isEmpty())
        }

    @Test
    fun `B-SEC-032 unavailable runner consumes initial credentials without shell activity`() =
        test {
            shell.supported = false
            val login = ByoHostLogin(password = "PASSWORD_SENTINEL")
            assertEquals(HostResult.Failed(ByoHostFailureCode.Unavailable), install(login))
            assertNull(login.password)
            assertTrue(shell.nativeScripts.isEmpty())
        }

    @Test
    fun `B-SEC-032 ambiguous password plus passphrase authentication is refused`() =
        test {
            val login = ByoHostLogin(password = "PASSWORD_SENTINEL", passphrase = "PHRASE_SENTINEL")
            assertEquals(HostResult.Failed(ByoHostFailureCode.Authentication), install(login))
            assertTrue(shell.privateCommands.isEmpty())
            assertNull(login.passphrase)
        }

    @Test
    fun `B-SEC-032 imported key or passphrase is refused without writing anything`() =
        test {
            for (login in listOf(ByoHostLogin(privateKey = "-".repeat(5) + "BEGIN OPENSSH PRIVATE KEY" + "-".repeat(5)), ByoHostLogin(passphrase = "PHRASE_SENTINEL"))) {
                assertEquals(HostResult.Failed(ByoHostFailureCode.Authentication), install(login))
                assertNull(login.privateKey)
                assertNull(login.passphrase)
                assertTrue(shell.nativeScripts.isEmpty())
                assertTrue(shell.observedFiles.isEmpty())
                assertTrue(signer.ensured.isEmpty())
            }
        }

    @Test
    fun `B-SEC-027 non-tailnet SSH is refused before scan, login or signer activity`() =
        test {
            val target = ByoHostTarget.parse("ubuntu@203.0.113.7")!!
            val denial = HostResult.Failed(ByoHostFailureCode.TailnetRequired)
            assertEquals(denial, runner.inspectKey(target))
            val login = ByoHostLogin(password = "PASSWORD_SENTINEL")
            assertEquals(denial, install(login, target, token = "t".repeat(32)))
            assertEquals(denial, forward(target))
            assertNull(login.password)
            assertTrue(shell.nativeScripts.isEmpty())
            assertTrue(signer.ensured.isEmpty())
            assertTrue(shell.listeners.isEmpty())
        }

    @Test
    fun `B-SEC-030 tunnel uses the public key plus the native agent and never writes a private key`() =
        test {
            val tunnel = forward().ok()
            assertEquals(1, shell.listeners.size)
            val last = shell.nativeScripts.last()
            assertTrue("-N -L" in last)
            assertTrue("'${ip4(100, 64, 0, 10)}'" in last)
            assertFalse("server.example" in last)
            assertTrue("127.0.0.1:${tunnel.localPort}:127.0.0.1:4096" in last)
            assertFalse("DEVICE_KEY_SENTINEL" in shell.nativeScripts.joinToString("\n"))
            val dirs = fs.tmpEntries()
            assertEquals(2, dirs.size)
            val work = "${fs.tmp}/" + dirs.single { it.startsWith("oc-byo-") }
            assertEquals("${devicePublicKey()}\n", fs.read("$work/device.pub"))
            assertEquals(HostEntryKind.Missing, fs.kind("$work/identity"))
            assertEquals(listOf("profile_1"), signer.agents.keys.toList())
            assertTrue("IdentityAgent=/tmp/.oa-" in last)
            assertTrue("PubkeyAcceptedAlgorithms=ecdsa-sha2-nistp256" in last)
            assertFalse(shell.observedFiles.keys.any { it.endsWith("/identity") || it.endsWith("/private_key") })
            assertFalse("PRIVATE KEY" in shell.observedFiles.values.joinToString("\n"))
            assertFalse("ssh-keygen" in shell.privateCommands.joinToString("\n"))
            tunnel.close()
            tunnel.close()
            assertTrue(shell.listeners.isEmpty())
            assertTrue(signer.agents.isEmpty())
            assertEquals(1, shell.stopped.size)
            assertTrue(fs.tmpEntries().isEmpty())
            assertTrue(runner.describe(tunnel, "profile_1", "t".repeat(32)) is HostResult.Failed)
        }

    @Test
    fun `B-SEC-033 startup recovers only this profile's orphan, preserving live and foreign data`() =
        test {
            val tunnel = forward().ok()
            val live = "${fs.tmp}/" + fs.tmpEntries().single { it.startsWith("oc-byo-") }
            val stale = live.dropLast(24) + "a".repeat(24)
            fs.nodes[stale] = MemFs.Dir
            fs.write("$stale/identity", "OLD_SECRET")
            val foreign = "${fs.tmp}/foreign"
            fs.nodes[foreign] = MemFs.Dir
            runner.generateIdentity("profile_1").ok()
            assertEquals(HostEntryKind.Missing, fs.kind(stale))
            assertEquals(HostEntryKind.Directory, fs.kind(live))
            assertEquals(HostEntryKind.Directory, fs.kind(foreign))
            assertTrue("byo-${"a".repeat(24)}" in shell.stopped)
            tunnel.close()
        }

    @Test
    fun `B-SEC-033 fresh runner cleanup erases the orphan without reconnecting or creating credentials`() =
        test {
            val digest = DartJson.sha256Hex("profile_1").substring(0, 16)
            val orphan = "${fs.tmp}/oc-byo-$digest-${"b".repeat(24)}"
            fs.nodes[orphan] = MemFs.Dir
            fs.write("$orphan/identity", "OLD_SECRET")
            val foreign = "${fs.tmp}/oc-byo-foreign"
            fs.nodes[foreign] = MemFs.Dir
            val fresh = newRunner()
            try {
                fresh.cleanup("profile_1").ok()
                fresh.cleanup("profile_1").ok()
                assertEquals(HostEntryKind.Missing, fs.kind(orphan))
                assertEquals(HostEntryKind.Directory, fs.kind(foreign))
                assertEquals(listOf("byo-${"b".repeat(24)}"), shell.stopped)
                assertTrue(shell.nativeScripts.isEmpty())
                assertTrue(signer.ensured.isEmpty())
                assertEquals(listOf("profile_1", "profile_1"), signer.deleted)
            } finally {
                fresh.dispose()
            }
        }

    @Test
    fun `B-SEC-033 explicit cleanup preserves active keys until the tunnel is closed`() =
        test {
            val tunnel = forward().ok()
            assertEquals(HostResult.Failed(ByoHostFailureCode.Busy), runner.cleanup("profile_1"))
            assertEquals(listOf("profile_1"), signer.agents.keys.toList())
            assertTrue(signer.deleted.isEmpty())
            assertEquals(1, shell.listeners.size)
            assertEquals(2, fs.tmpEntries().size)
            tunnel.close()
            runner.cleanup("profile_1").ok()
            assertTrue(fs.tmpEntries().isEmpty())
        }

    @Test
    fun `B-SEC-033 unavailable cleanup fails closed instead of claiming erased secrets`() =
        test {
            shell.supported = false
            assertEquals(HostResult.Failed(ByoHostFailureCode.Unavailable), runner.cleanup("profile_1"))
            assertTrue(shell.nativeScripts.isEmpty())
            assertTrue(signer.deleted.isEmpty())
        }

    @Test
    fun `B-SEC-031 installer errors become fixed typed failures without stderr`() =
        test {
            for ((wire, code) in mapOf(
                "unsupportedHost" to ByoHostFailureCode.UnsupportedHost,
                "checksumMismatch" to ByoHostFailureCode.Checksum,
                "userServiceUnavailable" to ByoHostFailureCode.Unavailable,
                "hostBusy" to ByoHostFailureCode.Busy,
                "sshPolicyRequired" to ByoHostFailureCode.SshPolicyRequired,
                "invalidPairing" to ByoHostFailureCode.Protocol,
                "unsupportedBundle" to ByoHostFailureCode.BundleUnavailable,
            )) {
                shell.failInstall = true
                shell.failureOutput = "PRIVATE_ERROR_SENTINEL\n{\"error\":\"$wire\"}\n"
                val result = install(ByoHostLogin(password = "PASSWORD_SENTINEL"))
                assertEquals(HostResult.Failed(code), result, wire)
                assertFalse("PRIVATE_ERROR_SENTINEL" in result.toString())
                assertTrue(fs.tmpEntries().isEmpty())
            }
        }

    @Test
    fun `B-SEC-034 target validation refuses shell and SSH option injection`() {
        for (target in listOf("ubuntu@-oProxyCommand=evil", "ubuntu@host;evil", "ubuntu@\$(evil)", "-o@host", "ubuntu@host\nother")) {
            assertNull(ByoHostTarget.parse(target), target)
        }
    }

    @Test
    fun `B-SEC-034 a symlinked shared-files boundary is rejected before any key write`() =
        test {
            fs.deleteTree(fs.tmp)
            fs.nodes[fs.tmp] = MemFs.LinkNode(fs.support)
            assertEquals(HostResult.Failed(ByoHostFailureCode.Storage), runner.generateIdentity("profile_1"))
            assertTrue(shell.nativeScripts.isEmpty())
            assertTrue(signer.ensured.isEmpty())
        }

    @Test
    fun `B-SEC-035 descriptor and revoke use authenticated loopback without redirects`() =
        test {
            http = { request ->
                assertEquals("Basic " + Base64.encode("profile_1:${"t".repeat(32)}".encodeToByteArray()), request.authorization)
                val body =
                    if (request.path == "/_oc/host") {
                        """{"hostId":"machine_1","bundleVersion":"1.1.0","openCodeVersion":"1.18.32","port":4096}"""
                    } else {
                        """{"revoked":true}"""
                    }
                LoopbackReply(200, body.encodeToByteArray())
            }
            val tunnel = TestTunnel(41_000)
            assertEquals("machine_1", runner.describe(tunnel, "profile_1", "t".repeat(32)).ok().hostId)
            assertTrue(runner.revoke(tunnel, "profile_1", "t".repeat(32)))
            assertEquals(listOf("/_oc/host", "/_oc/revoke"), fakeHttp.seen.map { it.path })
            assertTrue(fakeHttp.seen.all { it.port == 41_000 })
            assertEquals(listOf("GET", "POST"), fakeHttp.seen.map { it.method })
            assertFalse("t".repeat(32) in fakeHttp.seen.first().toString())
        }

    @Test
    fun `B-SEC-035 a redirect cannot exfiltrate Basic credentials and a failed revoke returns false`() =
        test {
            http = { LoopbackReply(307, ByteArray(0)) }
            assertFalse(runner.revoke(TestTunnel(41_001), "profile_1", "t".repeat(32)))
            assertEquals(1, fakeHttp.seen.size)
            assertEquals(HostResult.Failed(ByoHostFailureCode.Protocol), runner.describe(TestTunnel(41_001), "profile_1", "t".repeat(32)))
            // An oversized body is refused too.
            http = { LoopbackReply(200, ByteArray(16_385) { 'x'.code.toByte() }) }
            assertEquals(HostResult.Failed(ByoHostFailureCode.Protocol), runner.describe(TestTunnel(41_001), "profile_1", "t".repeat(32)))
        }
}
