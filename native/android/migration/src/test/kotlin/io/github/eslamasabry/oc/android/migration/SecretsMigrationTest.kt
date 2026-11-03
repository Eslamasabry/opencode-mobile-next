package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.Seed.CODEX
import io.github.eslamasabry.oc.android.migration.Seed.PHONE
import io.github.eslamasabry.oc.android.migration.Seed.REMOTE
import io.github.eslamasabry.oc.android.migration.ledger.CredentialLedger
import io.github.eslamasabry.oc.android.migration.readers.secure.FssFixtureWriter
import io.github.eslamasabry.oc.android.migration.readers.secure.FssFormat
import io.github.eslamasabry.oc.android.migration.readers.secure.SoftwareRsaOaepUnwrapper
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.SecureRandom
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plan 05 §10.2 `SecretKindsTest`, `FlutterSecretsReaderTest` (runner side) and the M2 retry rules of §7.4 / §4.4. */
class SecretsMigrationTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.io() = StandardTestDispatcher(testScheduler)

    private fun phone(corrupt: Set<String> = emptySet()) = World(tmp.newFolder()).apply { Seed.phone(this, corrupt) }

    private fun World.reentryRecord(id: String): String? = (logical()["oc.secretReentry.$id"] as? RawPref.Str)?.value

    @Test
    fun `B-MIGR-006 every readable secret is re-stored and no server asks for its password`() = runTest {
        val w = phone()
        val open = assertIs<RunnerState.Open>(w.runner(io()).run())

        assertTrue(open.notices.reenter.isEmpty())
        for ((name, value) in Seed.secrets) {
            if (name.startsWith("oc.a2aBearer.")) continue
            assertEquals(value, w.secrets.values[name], name)
        }
        assertEquals(MigrationOutcome.Done, w.signals.done.single().outcome)
        assertTrue(w.logical().keys.none { it.startsWith("oc.secretReentry.") })
    }

    @Test
    fun `B-MIGR-006 an unreadable typed password and token are flagged once, listed by server, never an empty password`() = runTest {
        val w = phone(corrupt = setOf("pw.$REMOTE", "oc.codexToken.$CODEX"))
        val open = assertIs<RunnerState.Open>(w.runner(io()).run())

        assertEquals(
            listOf(ReentryNotice(REMOTE, ReentryKind.Password), ReentryNotice(CODEX, ReentryKind.Token)),
            open.notices.reenter,
        )
        assertNull(w.secrets.values["pw.$REMOTE"])
        assertNull(w.secrets.values["oc.codexToken.$CODEX"])
        assertEquals("""{"v":1,"names":["pw"],"reason":"AuthFailed","at":${w.nowMs}}""", w.reentryRecord(REMOTE))
        assertEquals(MigrationOutcome.ReentryNeeded, w.signals.done.single().outcome)
        assertEquals(CountBucket.Few, w.signals.done.single().secretsReentry)

        // Ask once: after the sheet, the notices are gone for good (each item keeps its own state).
        val runner = w.runner(io())
        runner.run()
        runner.acknowledgeNotices()
        assertTrue(assertIs<RunnerState.Open>(w.runner(io()).run()).notices.reenter.isEmpty())
    }

    @Test
    fun `B-MIGR-006 GitHub and BYO failures get their own rows and never lock the server`() = runTest {
        val w = phone(corrupt = setOf("oc.githubToken.$REMOTE", "oc.byoHostSecrets.$CODEX"))
        val open = assertIs<RunnerState.Open>(w.runner(io()).run())

        assertEquals(
            listOf(ReentryNotice(REMOTE, ReentryKind.GitHubSignIn), ReentryNotice(CODEX, ReentryKind.ByoSetup)),
            open.notices.reenter,
        )
        assertTrue(w.reentryRecord(REMOTE)!!.contains("\"names\":[\"githubToken\"]"))
        assertEquals(Seed.PASSWORD, w.secrets.values["pw.$REMOTE"]) // the server itself still connects
    }

    @Test
    fun `B-MIGR-006 app-made secrets are renewed by their owner and never flagged or listed`() = runTest {
        val appMade = setOf("pw.$PHONE", "oc.agentHostSecret.$PHONE", "oc.quotaToken.$REMOTE", "oc.teamEngineAuth.$REMOTE")
        val w = phone(corrupt = appMade)
        val open = assertIs<RunnerState.Open>(w.runner(io()).run())

        assertTrue(open.notices.reenter.isEmpty())
        assertTrue(w.logical().keys.none { it.startsWith("oc.secretReentry.") })
        assertTrue(w.paths.audit.readText().contains("\"renewedByOwner\":4"))
        assertEquals(MigrationOutcome.Done, w.signals.done.single().outcome)
    }

    @Test
    fun `B-SEC-063 A2A bearers are counted, never copied, and their FSS entry is left alone`() = runTest {
        val w = phone()
        val fss = File(w.paths.sharedPrefs, "FlutterSecureStorage.xml").readBytes().toList()
        w.runner(io()).run()

        assertTrue(w.secrets.values.keys.none { it.startsWith("oc.a2aBearer.") })
        assertTrue(w.paths.audit.readText().contains("\"countedOnly\":1"))
        assertEquals(fss, File(w.paths.sharedPrefs, "FlutterSecureStorage.xml").readBytes().toList())
        assertEquals("fixture-factsbook-token", w.secrets.values["oc.factsbook.$REMOTE"]) // deferred: copied, never listed
    }

    @Test
    fun `B-MIGR-006 a native value is kept and never overwritten, an equal one counts as already migrated`() = runTest {
        val w = phone()
        w.secrets.values["pw.$REMOTE"] = "typed-after-the-update"
        w.secrets.values["oc.codexToken.$CODEX"] = Seed.TOKEN
        w.runner(io()).run()

        assertEquals("typed-after-the-update", w.secrets.values["pw.$REMOTE"])
        val audit = w.paths.audit.readText()
        assertTrue(audit.contains("\"nativeNewer\":1"), audit)
        assertTrue(audit.contains("\"alreadyMigrated\":1"), audit)
    }

    @Test
    fun `B-MIGR-006 v9-era markers flag every typed name without an attempt`() = runTest {
        val w = World(tmp.newFolder())
        w.seedPrefs(Seed.prefs)
        w.seedSecrets(mapOf("pw.$REMOTE" to Seed.PASSWORD), markers = FssFixtureWriter.Markers.Removed)
        val open = assertIs<RunnerState.Open>(w.runner(io()).run())

        assertEquals(listOf(ReentryNotice(REMOTE, ReentryKind.Password)), open.notices.reenter)
        assertTrue(w.reentryRecord(REMOTE)!!.contains("RemovedCipher"))
        assertEquals(0, w.secrets.writes)
    }

    @Test
    fun `B-MIGR-006 the plugin's own _BACKUP entries are skipped`() = runTest {
        val w = World(tmp.newFolder())
        w.seedPrefs(Seed.prefs)
        val files = FssFixtureWriter(World.KEYS.public, SecureRandom()).write(mapOf("pw.$REMOTE" to Seed.PASSWORD), backupCopies = true)
        for ((name, bytes) in files.xmlFiles()) File(w.paths.sharedPrefs, "$name.xml").writeBytes(bytes)
        w.unwrapper = SoftwareRsaOaepUnwrapper(World.KEYS.private)
        w.runner(io()).run()

        assertEquals(mapOf("pw.$REMOTE" to Seed.PASSWORD), w.secrets.values.toMap())
        assertTrue(w.paths.audit.readText().contains("\"backupEntriesSkipped\":1"))
    }

    @Test
    fun `B-MIGR-011 a Keystore error flags SecretStoreUnavailable, the gate opens, and no exception text is kept`() = runTest {
        val w = phone()
        w.secrets.unavailable = { it == "pw.$REMOTE" }
        val open = assertIs<RunnerState.Open>(w.runner(io()).run())

        assertEquals(listOf(ReentryNotice(REMOTE, ReentryKind.Password)), open.notices.reenter)
        assertTrue(w.reentryRecord(REMOTE)!!.contains("SecretStoreUnavailable"))
        for (f in listOf(w.paths.journal, w.paths.audit)) {
            assertFalse(f.readText().contains("keystore said"), f.name)
            assertFalse(f.readText().contains(Seed.PASSWORD), f.name)
        }
    }

    @Test
    fun `B-MIGR-011 every secret and every 8-plus character field of a JSON secret is registered with the Redactor`() = runTest {
        val w = phone()
        w.runner(io()).run()
        for (value in listOf(Seed.PASSWORD, Seed.TOKEN, "gho_fixtureAccessToken", "dev-fixture-token", "ssh-ed25519 AAAAfixture", "fixture-a2a-bearer")) {
            assertTrue(w.redactor.containsSecret("log: $value"), value)
        }
    }

    @Test
    fun `B-MIGR-006 a flagged secret is retried at the next launch and unflagged once it stores`() = runTest {
        val w = phone()
        w.secrets.unavailable = { it == "pw.$REMOTE" }
        w.runner(io()).run()
        w.secrets.unavailable = { false }

        val open = assertIs<RunnerState.Open>(w.runner(io()).run())

        assertEquals(Seed.PASSWORD, w.secrets.values["pw.$REMOTE"])
        assertNull(w.reentryRecord(REMOTE))
        assertTrue(open.notices.reenter.isEmpty())
    }

    @Test
    fun `B-MIGR-006 Astra B3 a reset or a typed value is a ledger record that suppresses the retry`() = runTest {
        val w = phone()
        w.secrets.unavailable = { it == "pw.$REMOTE" || it == "oc.codexToken.$CODEX" }
        w.runner(io()).run()
        w.secrets.unavailable = { false }

        val changes = w.runner(io()).also { it.run() }.secretChanges()
        // (the second launch above already retried; seed a fresh flagged state to exercise the ledger)
        w.secrets.values.remove("pw.$REMOTE")
        w.secrets.values.remove("oc.codexToken.$CODEX")
        w.prefs.commit(
            mapOf(
                "flutter.oc.secretReentry.$REMOTE" to RawPref.Str("""{"v":1,"names":["pw"],"reason":"AuthFailed","at":1}"""),
                "flutter.oc.secretReentry.$CODEX" to RawPref.Str("""{"v":1,"names":["codexToken"],"reason":"AuthFailed","at":1}"""),
            ),
        )
        assertTrue(changes.delete("pw.$REMOTE")) // "Reset saved sign-ins": a tombstone
        assertTrue(changes.write("oc.codexToken.$CODEX", "typed-new-token"))

        w.runner(io()).run()

        assertNull(w.secrets.values["pw.$REMOTE"], "a reset is never refilled from the old Flutter copy")
        assertEquals("typed-new-token", w.secrets.values["oc.codexToken.$CODEX"])
        val ledger = CredentialLedger(w.paths.credentialLedger).all()
        assertEquals(CredentialLedger.State.Deleted, ledger.getValue("pw.$REMOTE").state)
    }

    @Test
    fun `B-MIGR-006 retries stop after three launches`() = runTest {
        val w = phone()
        w.secrets.unavailable = { it == "pw.$REMOTE" }
        w.runner(io()).run()
        var reads = 0
        val blocked = w.secrets.unavailable
        w.secrets.unavailable = { name -> if (name == "pw.$REMOTE") reads++; blocked(name) }
        repeat(3) { w.runner(io()).run() }
        assertEquals(3, reads)
        w.runner(io()).run()
        assertEquals(3, reads)
        assertTrue(w.reentryRecord(REMOTE) != null)
    }

    @Test
    fun `FSS entry names are matched by prefix, not by containment`() = runTest {
        val w = phone()
        val data = File(w.paths.sharedPrefs, "FlutterSecureStorage.xml")
        data.writeText(data.readText().replace("</map>", "    <string name=\"x${FssFormat.ENTRY_PREFIX}_pw.$REMOTE\">junk</string>\n</map>"))
        w.runner(io()).run()
        assertEquals(Seed.PASSWORD, w.secrets.values["pw.$REMOTE"])
        assertTrue(w.secrets.values.keys.none { it.startsWith("x") })
    }

    /**
     * Codex review p12/migration #1: the app's own secret store bypassed the ledger. A GitHub import that failed only
     * for the moment kept its flag with no record; signing in and out through Git then left no trace, and the next
     * launch's retry decrypted the old Flutter token and signed the person back in.
     */
    @Test
    fun `B-SEC-084 a GitHub sign-out after the update is never undone by the retry`() = runTest {
        val w = phone()
        val name = "oc.githubToken.$REMOTE"
        w.secrets.unavailable = { it == name } // the first import fails for the moment
        w.runner(io()).run()
        assertTrue(w.reentryRecord(REMOTE)!!.contains("githubToken"))
        w.secrets.unavailable = { false }

        // The app (gate open) signs in through Git, then signs out: each change asks the runner first.
        val app = w.runner(io())
        assertTrue(app.beforeNativeChange(name, deleted = false))
        w.secrets.values[name] = """{"accessToken":"gho_typedAfterUpdate","refreshToken":null}"""
        assertTrue(app.beforeNativeChange(name, deleted = true))
        w.secrets.values.remove(name)

        val open = assertIs<RunnerState.Open>(w.runner(io()).run())

        assertNull(w.secrets.values[name], "the signed-out account is not restored from the Flutter copy")
        assertEquals(CredentialLedger.State.Deleted, CredentialLedger(w.paths.credentialLedger).all().getValue(name).state)
        assertTrue(open.notices.reenter.none { it.kind == ReentryKind.GitHubSignIn }, "answered natively: not asked again")
    }
}
