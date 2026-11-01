package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.Seed.REMOTE
import io.github.eslamasabry.oc.android.migration.ledger.CredentialLedger
import io.github.eslamasabry.oc.android.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.android.migration.readers.prefs.PrefValue
import io.github.eslamasabry.oc.android.migration.readers.secure.FssFormat
import io.github.eslamasabry.oc.android.migration.readers.xml.AndroidPrefsXml
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plan 05 §10.2 `MigrationAuditPrivacyTest` (B-MIGR-011) and the credential ledger of §4.4 (Astra B3). */
class MigrationAuditPrivacyTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.io() = StandardTestDispatcher(testScheduler)

    @Test
    fun `B-MIGR-011 planted secrets, prompt text and server addresses never reach the journal, audit, files or events`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this, corrupt = setOf("oc.codexToken.${Seed.CODEX}")) }
        w.secrets.unavailable = { it == "oc.githubToken.$REMOTE" }
        w.runner(io()).run()

        val planted = Seed.secrets.values.filter { it.length >= 6 } +
            listOf("gho_fixtureAccessToken", "dev-fixture-token", Seed.PROMPT_TEXT, Seed.SERVER_URL, "server.example.test", "keystore said")
        val written = listOf(w.paths.journal, w.paths.audit, w.paths.inventory, w.paths.credentialLedger, w.paths.fssSnapshot)
        for (f in written) {
            val text = f.readText()
            for (p in planted) assertFalse(text.contains(p), "${f.name} holds a planted value")
        }
        val events = (w.signals.done.map { it.toString() } + w.signals.lines + w.signals.updated.map { it.toString() }).joinToString("\n")
        for (p in planted) assertFalse(events.contains(p), "an event holds a planted value")
        // The event's fields are enums and buckets only (16 K-SEC-11).
        val e = w.signals.done.single()
        assertEquals(setOf("outcome", "failedStep", "secretsReentry", "duration"), MigrationDoneEvent::class.java.declaredFields.map { it.name }.toSet())
        assertTrue(e.outcome is Enum<*> && e.failedStep is Enum<*> && e.secretsReentry is Enum<*> && e.duration is Enum<*>)
        assertEquals("Update moved your data: 3 servers, 7 saved sign-ins (2 need the password again).", w.signals.lines.single())
    }

    @Test
    fun `B-MIGR-011 the baseline holds hashes only and is deleted when v1 completes`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this) }
        var baseline = ""
        val peek = object : StepInterrupter {
            override fun reached(point: String) { if (point == "M5") baseline = w.paths.baseline.readText() }
        }
        w.runner(io(), peek).run()
        assertTrue(baseline.isNotEmpty())
        for (p in listOf(Seed.PROMPT_TEXT, Seed.SERVER_URL, "oc.offlineQueue", "oc.profiles", REMOTE)) assertFalse(baseline.contains(p), p)
        assertFalse(w.paths.baseline.exists())
    }

    // ---- the credential ledger ----------------------------------------------------------------------------------

    @Test
    fun `B-MIGR-006 M2 records the FSS ciphertext digest the native value agreed with, never a value`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this) }
        w.runner(io()).run()
        val ledger = CredentialLedger(w.paths.credentialLedger).all()
        val data = AndroidPrefsXml.parse(File(w.paths.sharedPrefs, "FlutterSecureStorage.xml")).androidMap
        val cipher = (data[FssFormat.entryName("pw.$REMOTE")] as RawPref.Str).value
        assertEquals(CredentialLedger.ciphertextDigest(cipher), ledger.getValue("pw.$REMOTE").legacy)
        assertEquals(0L, ledger.getValue("pw.$REMOTE").rev)
        assertNull(ledger["oc.a2aBearer.agent-7"])
    }

    @Test
    fun `Astra B3 a native change persists the ledger, then the R1 stale marker, then the secret store`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this) }
        val runner = w.runner(io())
        runner.run()
        val observed = mutableListOf<String>()
        val order = object : MigrationSecretSink by w.secrets {
            override suspend fun write(name: String, value: String): Boolean {
                val rec = CredentialLedger(w.paths.credentialLedger).all().getValue(name)
                observed += "ledger rev=${rec.rev} legacy=${rec.legacy} queued=${rec.staleQueued}"
                observed += "stale=" + stale(w)
                return w.secrets.write(name, value)
            }
        }
        val changes = MigrationRunner(
            w.paths, w.prefs, { null }, w.unwrapper, order, w.redactor, MigrationSignals.None, StepInterrupter.None,
            { 1L shl 40 }, World.PACKAGE, 10_000, { 0 }, StandardTestDispatcher(testScheduler),
        ).secretChanges()

        assertTrue(changes.write("pw.$REMOTE", "changed-natively"))

        assertEquals(listOf("ledger rev=1 legacy=null queued=true", "stale=[pw.$REMOTE]"), observed)
        assertEquals("changed-natively", w.secrets.values["pw.$REMOTE"])
    }

    @Test
    fun `Astra B3 a refused stale-marker commit leaves the secret store untouched`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this) }
        val runner = w.runner(io())
        runner.run()
        w.prefs.refuse = { true }
        assertFalse(runner.secretChanges().write("pw.$REMOTE", "changed-natively"))
        assertEquals(Seed.PASSWORD, w.secrets.values["pw.$REMOTE"])
        assertEquals(1L, CredentialLedger(w.paths.credentialLedger).all().getValue("pw.$REMOTE").rev)
    }

    @Test
    fun `Astra B3 a sign-out is a tombstone, and a name FSS never held is not queued for the R1 guard`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this) }
        val runner = w.runner(io())
        runner.run()
        assertTrue(runner.secretChanges().delete("oc.codexToken.${Seed.CODEX}"))
        assertTrue(runner.secretChanges().write("pw.9999", "added-natively"))

        val ledger = CredentialLedger(w.paths.credentialLedger).all()
        assertEquals(CredentialLedger.State.Deleted, ledger.getValue("oc.codexToken.${Seed.CODEX}").state)
        assertFalse(ledger.getValue("pw.9999").staleQueued)
        assertEquals(listOf("oc.codexToken.${Seed.CODEX}"), stale(w))
        assertNull(w.secrets.values["oc.codexToken.${Seed.CODEX}"])
    }

    private fun stale(w: World): List<String>? =
        (w.logical()["oc.native.staleLegacySecrets"]?.let { FlutterPrefsCodec.decode(it).value } as? PrefValue.StrList)?.value
}
