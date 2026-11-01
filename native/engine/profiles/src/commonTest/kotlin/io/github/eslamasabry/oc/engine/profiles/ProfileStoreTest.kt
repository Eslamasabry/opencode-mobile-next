package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.BackendKind
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileStoreTest {
    private fun TestScope.harness(secrets: FakeSecrets = FakeSecrets(), platform: SecretPlatform = SecretPlatform.Android) =
        Harness(StandardTestDispatcher(testScheduler), secrets, platform = platform)

    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this).value

    private fun Outcome<*>.failure(): Failure = assertIs<Outcome.Failed>(this).failure

    private val workstation = StoredProfile(id("server-1"), "Workstation", "https://server.example:4096", username = "dev")

    @Test
    fun `B-STATE-065 profile metadata is kept when its password is unreadable`() = runTest {
        val h = harness(FakeSecrets().apply { failAllReads = true })
        h.seed(
            "oc.profiles" to """[{"id":"server-1","name":"Workstation","baseUrl":"https://server.example:4096","username":"dev"}]""",
            "oc.activeProfile" to "server-1",
        )

        val loaded = h.store.load().ok().single()

        assertEquals(id("server-1"), loaded.id)
        assertEquals("Workstation", loaded.profile.name)
        assertEquals("https://server.example:4096", loaded.profile.baseUrl)
        assertEquals("dev", loaded.profile.username)
        assertEquals(BackendKind.OpenCode2, loaded.profile.backend)
        assertFalse(loaded.hasSecret)
        assertEquals(SecretReentry(password = true, agentToken = false), loaded.reentry)
        assertEquals(id("server-1"), h.store.activeId.value)
        // L4: a row without "flavor":"v2" still needs its OpenCode 2 migration.
        assertTrue(loaded.profile.requiresOpenCode2Migration)
    }

    @Test
    fun `B-STATE-065 the AI Team plugin config persists with the profile and turning it off drops only its own`() = runTest {
        val h = harness()
        val team = assertNotNull(
            OpaqueJson.parse(
                """{"provider":"gascity","url":"http://team-host.example:8080","city":"bright-lights","hostMode":"phone",""" +
                    """"hostKind":"phone","front":false,"enabledAt":"2026-09-11T09:00:00.000Z"}""",
            ),
        )
        val laptop = assertNotNull(
            OpaqueJson.parse("""{"provider":"gascity","url":"http://team-laptop.example:8372","city":"","hostMode":"computer","hostKind":"laptop","front":false}"""),
        )
        h.store.upsert(StoredProfile(id("team"), "Team host", "https://server.example:4096", orchestration = team)).ok()
        h.store.upsert(StoredProfile(id("laptop"), "Laptop", "https://l:4096", orchestration = laptop)).ok()
        h.store.upsert(StoredProfile(id("plain"), "Plain", "https://p:4096")).ok()

        val restarted = Harness(StandardTestDispatcher(testScheduler), h.secrets, h.backend)
        val reloaded = restarted.store
        val profiles = reloaded.load().ok().associateBy { it.id.raw }
        assertEquals(team, profiles.getValue("team").profile.orchestration)
        assertEquals(laptop, profiles.getValue("laptop").profile.orchestration)
        assertNull(profiles.getValue("plain").profile.orchestration)

        reloaded.upsert(profiles.getValue("team").profile.copy(orchestration = null)).ok()
        val stored = assertNotNull(restarted.string("oc.profiles"))
        assertEquals(1, Regex("orchestration").findAll(stored).count())
        assertFalse(""""hostMode":"phone"""" in stored)
        assertTrue(""""hostKind":"laptop"""" in stored)
        // The config is written back byte for byte, in its stored key order.
        assertTrue(laptop.encoded in stored)
    }

    @Test
    fun `B-STATE-065 a native write keeps the stored key order and every field it does not know`() = runTest {
        val h = harness()
        val future = """"futureField":{"a":[1,2.5,"x\n"],"b":null}"""
        h.seed("oc.profiles" to """[{"id":"s","name":"Old","baseUrl":"https://s:1","flavor":"v2",$future}]""")
        val loaded = h.store.load().ok().single()

        h.store.upsert(loaded.profile.copy(name = "New")).ok()

        assertEquals(
            """[{"id":"s","name":"New","baseUrl":"https://s:1","backend":"openCode","username":"","flavor":"v2",""" +
                """"requiresOpenCode2Migration":false,"openCode2HistoryPending":false,$future}]""",
            h.string("oc.profiles"),
        )
    }

    @Test
    fun `B-STATE-098 a failed secure write leaves profile metadata and the published list unchanged`() = runTest {
        val h = harness()
        h.store.load().ok()
        h.store.upsert(workstation.copy(name = "Original", baseUrl = "https://old.example:4096"), SecretEdit.Set("old-secret-1")).ok()

        h.secrets.failWrites = true
        val failure = h.store.upsert(
            workstation.copy(name = "Changed", baseUrl = "https://new.example:4096"),
            SecretEdit.Set("new-secret-2"),
        ).failure()

        assertEquals(Failure.SecretUnavailable(), failure)
        assertEquals("Original", h.store.profiles.value.single().profile.name)
        val stored = assertNotNull(h.string("oc.profiles"))
        assertTrue("Original" in stored && "Changed" !in stored)
        assertEquals("old-secret-1", h.secrets.values["pw.server-1"])
        // The secret never reaches the failure or a log line.
        assertFalse("new-secret-2" in failure.toString())
        assertTrue(h.redactor.containsSecret("x new-secret-2 y"))
    }

    @Test
    fun `B-SEC-135 the Codex token is stored apart from the password and the profile JSON`() = runTest {
        val h = harness()
        val codex = StoredProfile(id("codex-1"), "Codex", "wss://codex.example:4141", BackendKind.Codex, codexDirectory = "/work/acme")

        h.store.upsert(codex, SecretEdit.Set("codex-secret")).ok()

        assertEquals("codex-secret", h.secrets.values["oc.codexToken.codex-1"])
        assertFalse("pw.codex-1" in h.secrets.values)
        val saved = assertNotNull(h.string("oc.profiles"))
        assertTrue(""""backend":"codex"""" in saved && """"codexDirectory":"/work/acme"""" in saved)
        assertFalse("codex-secret" in saved)

        val restored = Harness(StandardTestDispatcher(testScheduler), h.secrets, h.backend).store
        val loaded = restored.load().ok().single()
        assertTrue(loaded.hasSecret)
        assertEquals(SecretReentry.None, loaded.reentry)
        assertEquals("codex-secret", restored.secret(id("codex-1")).ok())
    }

    @Test
    fun `B-SEC-135 a missing Codex token asks for re-entry and the metadata stays`() = runTest {
        val h = harness()
        h.store.upsert(StoredProfile(id("codex-2"), "Codex", "wss://codex.example:4141", BackendKind.Codex, codexDirectory = "/work/acme")).ok()

        val loaded = Harness(StandardTestDispatcher(testScheduler), h.secrets, h.backend).store.load().ok().single()

        assertFalse(loaded.hasSecret)
        assertEquals(SecretReentry(password = false, agentToken = true), loaded.reentry)
        assertEquals("/work/acme", loaded.profile.codexDirectory)
        // A Paseo daemon may run without a password: no re-entry for it.
        h.store.upsert(StoredProfile(id("paseo"), "Paseo", "ws://localhost:6767", BackendKind.Paseo)).ok()
        val paseo = Harness(StandardTestDispatcher(testScheduler), h.secrets, h.backend).store.load().ok().first { it.id.raw == "paseo" }
        assertEquals(SecretReentry.None, paseo.reentry)
    }

    @Test
    fun `B-SEC-135 a Codex save rolls the metadata back when its token cannot be stored`() = runTest {
        val h = harness(FakeSecrets().apply { failWrites = true })
        val codex = StoredProfile(id("codex-3"), "Codex", "wss://codex.example:4141", BackendKind.Codex, codexDirectory = "/work/acme")

        assertIs<Failure.SecretUnavailable>(h.store.upsert(codex, SecretEdit.Set("codex-secret")).failure())

        assertTrue(h.store.profiles.value.isEmpty())
        assertNull(h.string("oc.profiles"))
    }

    @Test
    fun `B-SEC-135 Codex deletion removes only its token, and a refused delete keeps the metadata`() = runTest {
        val h = harness()
        val codex = StoredProfile(id("codex-4"), "Codex", "wss://codex.example:4141", BackendKind.Codex, codexDirectory = "/work/acme")
        h.store.upsert(codex, SecretEdit.Set("codex-secret")).ok()
        h.secrets.values["oc.codexToken.other"] = "other-secret"

        h.secrets.failDeletes += "oc.codexToken.codex-4"
        val kept = h.deletion().delete(codex.id).ok()
        assertFalse(kept.removedProfile)
        assertEquals(listOf(ProfileKeys.OWNER), kept.failedOwners)
        assertEquals("codex-4", h.store.profiles.value.single().id.raw)
        assertTrue(""""codex"""" in assertNotNull(h.string("oc.profiles")))

        h.secrets.failDeletes.clear()
        val removed = h.deletion().delete(codex.id).ok()
        assertTrue(removed.complete)
        assertFalse("oc.codexToken.codex-4" in h.secrets.values)
        assertFalse("pw.codex-4" in h.secrets.values)
        assertEquals("other-secret", h.secrets.values["oc.codexToken.other"])
        assertTrue(h.store.profiles.value.isEmpty())
    }

    @Test
    fun `B-STATE-098 a failed secure delete preserves an active profile`() = runTest {
        val h = harness()
        h.store.upsert(workstation, SecretEdit.Set("secret-pw")).ok()
        h.store.setActive(workstation.id).ok()

        h.secrets.failAllDeletes = true
        val result = h.deletion().delete(workstation.id).ok()

        assertFalse(result.removedProfile)
        assertEquals("server-1", h.store.profiles.value.single().id.raw)
        assertEquals(workstation.id, h.store.activeId.value)
        assertEquals("server-1", h.string("oc.activeProfile"))
        assertTrue("Workstation" in assertNotNull(h.string("oc.profiles")))
        // The profile is usable again: saves are admitted.
        h.secrets.failAllDeletes = false
        h.store.upsert(workstation.copy(name = "Renamed")).ok()
    }

    @Test
    fun `B-SEC-134 a keyring failure gets the Linux wording on Linux and the device wording elsewhere`() = runTest {
        val linux = harness(FakeSecrets().apply { failWrites = true; failAllReads = true }, SecretPlatform.LinuxDesktop)
        val failure = linux.store.upsert(workstation, SecretEdit.Set("typed-password")).failure()
        assertEquals(SecureStorageProblem.LinuxKeyringMissing, linux.store.problemOf(failure))
        // The half-written profile was rolled back with the secret.
        assertTrue(linux.store.profiles.value.isEmpty())
        assertNull(linux.string("oc.profiles"))

        val android = harness(FakeSecrets().apply { failWrites = true; failAllReads = true }, SecretPlatform.Android)
        val other = android.store.upsert(workstation, SecretEdit.Set("typed-password")).failure()
        assertEquals(SecureStorageProblem.DeviceSecretStorageUnavailable, android.store.problemOf(other))
        assertNull(android.store.problemOf(Failure.Refused(RefusalReason.InvalidAddress)))

        // The probe reports only a Linux desktop without a keyring.
        assertEquals(SecureStorageProblem.LinuxKeyringMissing, linux.store.secureStorageProblem())
        assertNull(harness(platform = SecretPlatform.LinuxDesktop).store.secureStorageProblem())
        assertNull(android.store.secureStorageProblem())
    }

    @Test
    fun `B-SEC-136 Codex origins and bounds are validated without echoing input`() {
        assertEquals("ws://localhost:4141", CodexInput.normalizeUrl("localhost:4141"))
        assertEquals("wss://codex.example:4141", CodexInput.normalizeUrl("codex.example:4141"))
        assertEquals("ws://[::1]:4141", CodexInput.normalizeUrl("[::1]:4141"))
        assertNull(CodexInput.validateUrl("ws://localhost:4141"))
        assertNull(CodexInput.validateUrl("wss://codex.example:4141"))
        assertEquals(CodexInputProblem.CleartextNotLocal, CodexInput.validateUrl("ws://codex.example:4141"))
        assertEquals(CodexInputProblem.CredentialsInUrl, CodexInput.validateUrl("wss://user:secret@codex.example"))
        assertEquals(CodexInputProblem.QueryOrFragment, CodexInput.validateUrl("wss://codex.example/?token=secret"))
        assertEquals(CodexInputProblem.PathInUrl, CodexInput.validateUrl("wss://codex.example/project"))
        assertNull(CodexInput.validateDirectory("/work/acme"))
        assertNull(CodexInput.validateDirectory("C:\\work\\acme"))
        assertEquals(CodexInputProblem.Directory, CodexInput.validateDirectory("relative/project"))
        assertEquals(CodexInputProblem.Directory, CodexInput.validateDirectory("/work\nacme"))
        assertEquals(CodexInputProblem.Directory, CodexInput.validateDirectory("/work\u0085acme"))
        assertNull(CodexInput.validateToken("token_123"))
        assertEquals(CodexInputProblem.Token, CodexInput.validateToken("token with space"))
        assertEquals(CodexInputProblem.Token, CodexInput.validateToken("token\u0085secret"))
        assertNull(CodexInput.validateToken("x".repeat(32)))
        // The problem is an enum: it cannot carry the input.
        assertFalse("x".repeat(32) in CodexInput.validateToken("x".repeat(32) + " ").toString())
    }

    @Test
    fun `B-SEC-136 profile deletion sweeps the BYO vault and metadata only for that profile`() = runTest {
        val h = harness()
        h.store.upsert(StoredProfile(id("phone-a"), "A", "https://a.example")).ok()
        h.store.upsert(StoredProfile(id("phone-b"), "B", "https://b.example")).ok()
        h.seed("oc.byoHost.phone-a" to "record-a", "oc.byoHost.phone-b" to "record-b")
        h.secrets.values["oc.byoHostSecrets.phone-a"] = "private-a"
        h.secrets.values["oc.byoHostSecrets.phone-b"] = "private-b"

        assertTrue(h.deletion().delete(id("phone-a")).ok().complete)

        assertNull(h.string("oc.byoHost.phone-a"))
        assertNull(h.secrets.values["oc.byoHostSecrets.phone-a"])
        assertEquals("record-b", h.string("oc.byoHost.phone-b"))
        assertEquals("private-b", h.secrets.values["oc.byoHostSecrets.phone-b"])
        // BYO identity goes first (its refusal keeps the server for a retry).
        assertEquals("oc.byoHostSecrets.phone-a", h.secrets.deleted.first())
    }

    @Test
    fun `B-SEC-136 a private transport lease cannot persist its address or credential`() = runTest {
        val h = harness()
        val lease = StoredProfile(id("lease"), "Host", "http://127.0.0.1:19000", transientTransport = true)

        val failure = h.store.upsert(lease, SecretEdit.Set("must-stay-in-byo-vault")).failure()

        assertIs<Failure.StorageRefused>(failure)
        assertNull(h.string("oc.profiles"))
        assertTrue(h.secrets.values.isEmpty())
        assertTrue(h.store.profiles.value.isEmpty())
    }

    @Test
    fun `B-SEC-121 cleartext consent applies to one exact origin and an address edit drops it in the same commit`() = runTest {
        val h = harness()
        val lan = StoredProfile(id("lan"), "LAN", "http://203.0.113.20:4096")
        assertTrue(h.store.upsert(lan).ok().cleartextUnconfirmed)

        val confirmed = h.store.upsert(lan, trust = TrustInput(cleartextOrigin = "http://203.0.113.20:4096")).ok()
        assertFalse(confirmed.cleartextUnconfirmed)
        assertEquals("http://203.0.113.20:4096", h.string("oc.cleartextOk.lan"))
        // A rebuilt profile (no trust given) keeps it; a reload keeps it.
        h.store.upsert(lan.copy(name = "Renamed")).ok()
        val reloaded = Harness(StandardTestDispatcher(testScheduler), h.secrets, h.backend).store.load().ok().single()
        assertFalse(reloaded.cleartextUnconfirmed)

        // Another address (another port) is not covered: the consent is gone with the edit.
        val moved = h.store.upsert(lan.copy(baseUrl = "http://203.0.113.20:4097")).ok()
        assertTrue(moved.cleartextUnconfirmed)
        assertNull(h.string("oc.cleartextOk.lan"))
        // Loopback never needs it.
        assertFalse(h.store.upsert(StoredProfile(id("me"), "Me", "http://127.0.0.1:4096")).ok().cleartextUnconfirmed)
    }

    @Test
    fun `B-SEC-154 a certificate pin survives a rebuild and reload, never moves to another origin or profile`() = runTest {
        val h = harness()
        val sha = "AB".repeat(32)
        val server = StoredProfile(id("pinned"), "Pinned", "https://work.example:8443/path")
        val saved = h.store.upsert(server, trust = TrustInput(pin = CertificatePinRecord("https://work.example:8443", sha))).ok()
        assertEquals(CertificatePinRecord("https://work.example:8443", sha.lowercase()), saved.trust.pin)
        assertEquals("""{"origin":"https://work.example:8443","sha256":"${sha.lowercase()}"}""", h.string("oc.certificatePin.pinned"))

        h.store.upsert(server.copy(name = "Rebuilt")).ok()
        val reloaded = Harness(StandardTestDispatcher(testScheduler), h.secrets, h.backend).store
        reloaded.load().ok()
        assertEquals(sha.lowercase(), reloaded.trust(server.id).pin?.leafSha256)
        assertNull(reloaded.trust(id("other")).pin)

        // Host, port or scheme change removes the pin in the same commit.
        h.store.upsert(server.copy(baseUrl = "https://work.example:9443")).ok()
        assertNull(h.string("oc.certificatePin.pinned"))
        assertNull(h.store.trust(server.id).pin)

        // A malformed or wrong-origin stored pin never restores trust; a malformed new pin is refused before writing.
        h.seed("oc.certificatePin.pinned" to """{"origin":"https://elsewhere:9443","sha256":"$sha"}""")
        assertNull(Harness(StandardTestDispatcher(testScheduler), h.secrets, h.backend).store.load().ok().single().trust.pin)
        val bad = h.store.upsert(server, trust = TrustInput(pin = CertificatePinRecord("https://work.example:8443", "nothex"))).failure()
        assertEquals(Failure.Refused(RefusalReason.InvalidAddress), bad)
    }

    @Test
    fun `B-AGENT-244 Tailscale guidance and the HTTPS profile survive reload and deletion removes the guidance`() = runTest {
        val h = harness()
        h.store.load().ok()
        h.store.upsert(StoredProfile(id("tail"), "Private server", "https://work.example.ts.net")).ok()
        h.store.setTailscaleGuidance(id("tail"), true).ok()

        val restarted = Harness(StandardTestDispatcher(testScheduler), h.secrets, h.backend)
        val loaded = restarted.store.load().ok().single()
        assertEquals("https://work.example.ts.net", loaded.profile.baseUrl)
        assertTrue(loaded.tailscale)
        assertTrue("oc.tailscale.tail" in restarted.scope.scopedKeys(id("tail")))

        assertTrue(restarted.deletion().delete(id("tail")).ok().complete)
        assertTrue(restarted.store.profiles.value.isEmpty())
        assertNull(restarted.kv.get("oc.tailscale.tail"))
    }

    @Test
    fun `B-STATE-065 a plain address as a name reads as This phone or Computer at`() {
        assertEquals("This phone", StoredProfileJson.plainServerName("127.0.0.1"))
        assertEquals("This phone", StoredProfileJson.plainServerName("[::1]"))
        assertEquals("Computer at 203.0.113.4", StoredProfileJson.plainServerName("203.0.113.4"))
        assertEquals("Workstation", StoredProfileJson.plainServerName("Workstation"))
    }
}
