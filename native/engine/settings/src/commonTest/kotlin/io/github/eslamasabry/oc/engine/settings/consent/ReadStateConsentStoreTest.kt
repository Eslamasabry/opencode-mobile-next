package io.github.eslamasabry.oc.engine.settings.consent

import app.cash.turbine.test
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.data.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.engine.settings.scope.SettingOriginKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReadStateConsentStoreTest {
    private val old = pid("old")
    private val laptop = pid("laptop")
    private val phone = pid("phone")
    private val sharing = ConsentTopic.ReadStateSharing

    private fun TestScope.kv(initial: Map<String, Any> = emptyMap()) = TestKv(initial, UnconfinedTestDispatcher(testScheduler))

    private fun store(kv: TestKv) = ReadStateConsentStore(kv, kv.lanes)

    private fun journal(ids: List<String>, complete: Boolean = false, legacy: Boolean = true) =
        DartJson.encode(linkedMapOf("version" to 1L, "complete" to complete, "ids" to ids, "legacy" to legacy))

    private fun Outcome<*>.failure(): Failure = (this as Outcome.Failed).failure

    // ---- B-SEC-142: the L3 migration

    @Test
    fun `B-SEC-142 a missing legacy default migrates only the initial saved population`() = runTest {
        val kv = kv()
        val consent = store(kv)
        assertFalse(consent.enabled(old))
        assertIs<Outcome.Ok<Unit>>(consent.migrateExistingProfiles(listOf(old)))
        assertTrue(consent.enabled(old))
        assertEquals(ConsentOrigin.Migrated, consent.origin(old))
        assertEquals(ConsentRead.Granted, consent.read(old, sharing))
        assertFalse(consent.enabled(pid("new")))
        assertIs<Outcome.Ok<Unit>>(consent.migrateExistingProfiles(listOf(old, pid("new"))))
        assertFalse(consent.enabled(pid("new")))
        assertNull(kv.get("oc.shareSessionViews.new"))
        val saved = jsonOf(kv.string("oc.readStateConsentMigration.v1"))!!
        assertEquals(true, saved["complete"])
        assertEquals(emptyList<Any?>(), saved["ids"])
    }

    @Test
    fun `B-SEC-142 a malformed legacy value cannot grant sharing`() = runTest {
        for (malformed in listOf<Any>("true", 1L, listOf("true"))) {
            val kv = kv(mapOf("oc.shareSessionViews" to malformed))
            val consent = store(kv)
            assertIs<Outcome.Ok<Unit>>(consent.migrateExistingProfiles(listOf(old)))
            assertFalse(consent.enabled(old), "$malformed")
            assertEquals(false, kv.bool("oc.shareSessionViews.old"))
            assertEquals(ConsentOrigin.Migrated, consent.origin(old))
            assertEquals(ConsentRead.Refused, consent.read(old, sharing))
        }
    }

    @Test
    fun `B-SEC-142 migration preserves explicit destination choices`() = runTest {
        val consent = store(
            kv(
                mapOf(
                    "oc.shareSessionViews" to false,
                    "oc.shareSessionViews.on" to true,
                    "oc.shareSessionViewsOrigin.on" to "explicit",
                    "oc.shareSessionViews.off" to false,
                ),
            ),
        )
        consent.migrateExistingProfiles(listOf(pid("on"), pid("off"), pid("legacy")))
        assertTrue(consent.enabled(pid("on")))
        assertEquals(ConsentOrigin.Explicit, consent.origin(pid("on")))
        assertFalse(consent.enabled(pid("off")))
        assertEquals(ConsentOrigin.Explicit, consent.origin(pid("off")))
        assertFalse(consent.enabled(pid("legacy")))
    }

    @Test
    fun `B-SEC-142 a refused migration completion stays private and a retry finishes it`() = runTest {
        val kv = kv()
        val consent = store(kv)
        kv.disk.refuse = { key, value ->
            key == "oc.readStateConsentMigration.v1" && (value as? RawPref.Str)?.value?.contains("\"complete\":true") == true
        }
        val failed = consent.migrateExistingProfiles(listOf(old))
        assertEquals(StorageProblem.WriteRefused, assertIs<Failure.Storage>(failed.failure()).problem)
        // The copy reached disk, but an unfinished migration grants nothing new in this process.
        assertEquals(true, kv.bool("oc.shareSessionViews.old"))
        assertFalse(consent.enabled(old))
        assertFalse(consent.saving(old))
        kv.disk.refuse = null
        assertIs<Outcome.Ok<Unit>>(consent.migrateExistingProfiles(listOf(old)))
        assertTrue(consent.enabled(old))
        assertEquals(ConsentOrigin.Migrated, consent.origin(old))
    }

    @Test
    fun `B-SEC-142 an interrupted durable cohort does not absorb later profiles after restart`() = runTest {
        val kv = kv()
        kv.disk.refuse = { key, _ -> key == "oc.shareSessionViews.old" }
        assertIs<Outcome.Failed>(store(kv).migrateExistingProfiles(listOf(old)))
        val firstJournal = jsonOf(kv.string("oc.readStateConsentMigration.v1"))!!
        assertEquals(false, firstJournal["complete"])
        assertEquals(listOf("old"), firstJournal["ids"])

        val restartedKv = kv.restart(UnconfinedTestDispatcher(testScheduler))
        val restarted = store(restartedKv)
        assertIs<Outcome.Ok<Unit>>(restarted.migrateExistingProfiles(listOf(old, pid("later"))))
        assertTrue(restarted.enabled(old))
        assertFalse(restarted.enabled(pid("later")))
        assertNull(restartedKv.get("oc.shareSessionViews.later"))
    }

    @Test
    fun `B-SEC-142 migration never exposes an optimistic true before the disk acknowledged it`() = runTest {
        val kv = kv()
        val consent = store(kv)
        val hold = kv.hold("oc.shareSessionViews.old")
        val migration = async { consent.migrateExistingProfiles(listOf(old)) }
        hold.entered.await()
        assertTrue(consent.saving(old))
        assertFalse(consent.enabled(old))
        assertEquals(ConsentRead.Unknown, consent.read(old, sharing))
        hold.release.complete(Unit)
        assertIs<Outcome.Ok<Unit>>(migration.await())
        assertFalse(consent.saving(old))
        assertTrue(consent.enabled(old))
    }

    // ---- B-SEC-143: opt-out and deletion under storage failure

    @Test
    fun `B-SEC-143 a failed opt-out stays private and an explicit retry persists it`() = runTest {
        val kv = kv(mapOf("oc.shareSessionViews.old" to true))
        val consent = store(kv)
        assertTrue(consent.enabled(old))
        val before = consent.revision.value
        kv.disk.refuse = { key, _ -> key == "oc.shareSessionViews.old" }
        val save = consent.setEnabled(old, false)
        assertEquals(StorageProblem.WriteRefused, assertIs<Failure.Storage>(save.failure()).problem)
        assertFalse(consent.enabled(old))
        assertFalse(consent.saving(old))
        assertTrue(consent.revision.value > before)
        assertEquals(true, kv.bool("oc.shareSessionViews.old"), "the disk still holds the old answer")
        kv.disk.refuse = null
        assertIs<Outcome.Ok<Unit>>(consent.setEnabled(old, false))
        val restarted = store(kv.restart(UnconfinedTestDispatcher(testScheduler)))
        assertFalse(restarted.enabled(old))
        assertEquals(ConsentRead.Refused, restarted.read(old, sharing))
    }

    @Test
    fun `B-SEC-143 deletion drains a pending grant, prunes its journal and removes every key`() = runTest {
        val kv = kv(
            mapOf(
                "oc.readStateConsentMigration.v1" to journal(listOf("old", "retained")),
                "oc.shareSessionViews.retained" to true,
            ),
        )
        val consent = store(kv)
        val hold = kv.hold("oc.shareSessionViews.old")
        val grant = async { consent.setEnabled(old, true) }
        hold.entered.await()
        val closing = async { consent.forgetProfile(old) }
        runCurrent()
        assertFalse(consent.enabled(old))
        assertTrue(consent.isClosed(old))
        assertFalse(closing.isCompleted, "waits for the pending grant")
        hold.release.complete(Unit)
        assertEquals(Failure.Refused(RefusalReason.StaleScope), grant.await().failure())
        assertIs<Outcome.Ok<Unit>>(closing.await())
        assertEquals(listOf("retained"), jsonOf(kv.string("oc.readStateConsentMigration.v1"))!!["ids"])
        assertTrue(kv.keys("oc.shareSessionViews").none { it.endsWith(".old") })
        assertTrue(consent.enabled(pid("retained")))
        assertEquals(Failure.Refused(RefusalReason.StaleScope), consent.setEnabled(old, true).failure())
        consent.reopenProfile(old)
        assertFalse(consent.enabled(old))
        assertIs<Outcome.Ok<Unit>>(consent.forgetProfile(old), "idempotent")
    }

    @Test
    fun `B-SEC-143 a failed journal prune is retryable and never restores consent`() = runTest {
        val kv = kv(
            mapOf(
                "oc.readStateConsentMigration.v1" to journal(listOf("old", "retained")),
                "oc.shareSessionViews.old" to true,
            ),
        )
        val consent = store(kv)
        kv.disk.refuse = { key, _ -> key == "oc.readStateConsentMigration.v1" }
        assertIs<Failure.Storage>(consent.forgetProfile(old).failure())
        assertFalse(consent.enabled(old))
        consent.reopenProfile(old)
        assertFalse(consent.enabled(old))
        kv.disk.refuse = null
        assertIs<Outcome.Ok<Unit>>(consent.forgetProfile(old))
        assertEquals(listOf("retained"), jsonOf(kv.string("oc.readStateConsentMigration.v1"))!!["ids"])
        assertNull(kv.get("oc.shareSessionViews.old"))
    }

    // ---- B-SEC-164: per profile, private by default, private wins every race

    @Test
    fun `B-SEC-164 new profiles start private and consent changes only the named profile`() = runTest {
        val kv = kv()
        val consent = store(kv)
        assertFalse(consent.enabled(laptop))
        assertFalse(consent.enabled(phone))
        assertEquals(ConsentRead.Unknown, consent.read(phone, sharing))
        assertFalse(consent.defaultFor(phone))
        assertIs<Outcome.Ok<Unit>>(consent.setEnabled(laptop, true))
        assertTrue(consent.enabled(laptop))
        assertFalse(consent.enabled(phone))
        assertEquals(true, kv.bool("oc.shareSessionViews.laptop"))
        assertEquals("explicit", kv.string("oc.shareSessionViewsOrigin.laptop"))
        assertEquals(false, kv.bool("oc.shareSessionViewsDefault.laptop"))
        val staleEditor = consent.setEnabledIfCurrent(phone, true) { false }
        assertEquals(Failure.Refused(RefusalReason.StaleScope), staleEditor.failure())
        assertFalse(consent.enabled(phone))
        assertNull(kv.get("oc.shareSessionViews.phone"))
    }

    @Test
    fun `B-SEC-164 revoking sharing reaches every observer at once and stops a waiting receipt`() = runTest {
        val consent = store(kv())
        consent.setEnabled(laptop, true)
        val waiting = assertNotNull(consent.ticket(laptop))
        consent.observe(laptop, sharing).test {
            assertEquals(ConsentRead.Granted, awaitItem())
            consent.setEnabled(laptop, false)
            assertEquals(ConsentRead.Refused, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // The receipt that waited to be sent checks again first: it is never sent.
        assertFalse(consent.stillShared(waiting))
        assertNull(consent.ticket(laptop))
    }

    @Test
    fun `B-SEC-164 a transmitted receipt cannot acknowledge state after revocation`() = runTest {
        val consent = store(kv())
        consent.setEnabled(laptop, true)
        val sent = assertNotNull(consent.ticket(laptop))
        assertTrue(consent.stillShared(sent))
        // The receipt is on the wire; sharing is revoked before its answer lands.
        consent.setEnabled(laptop, false)
        assertFalse(consent.stillShared(sent), "the late answer may not mark anything read")
        assertFalse(consent.enabled(laptop))
    }

    @Test
    fun `B-SEC-164 a delayed opt-in followed by an opt-out never publishes sharing on`() = runTest {
        val kv = kv()
        val consent = store(kv)
        val observed = mutableListOf<ConsentRead>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { consent.observe(laptop, sharing).toList(observed) }
        val hold = kv.hold("oc.shareSessionViews.laptop")
        val enabling = async { consent.setEnabled(laptop, true) }
        hold.entered.await()
        assertFalse(consent.enabled(laptop))
        val disabling = async { consent.setEnabled(laptop, false) }
        runCurrent()
        assertFalse(consent.enabled(laptop))
        hold.release.complete(Unit)
        val superseded = enabling.await()
        assertIs<Outcome.Ok<Unit>>(disabling.await())
        if (superseded is Outcome.Failed) assertIs<Failure.Refused>(superseded.failure)
        assertFalse(ConsentRead.Granted in observed, "observed $observed")
        assertFalse(consent.enabled(laptop))
        assertEquals(false, kv.bool("oc.shareSessionViews.laptop"))
    }

    @Test
    fun `B-SEC-164 a failed opt-out stays private for every observer and advances the shared revision`() = runTest {
        val kv = kv()
        val consent = store(kv)
        consent.setEnabled(laptop, true)
        val before = consent.revision.value
        val receipt = assertNotNull(consent.ticket(laptop))
        kv.disk.refuse = { key, _ -> key == "oc.shareSessionViews.laptop" }
        assertIs<Failure.Storage>(consent.setEnabled(laptop, false).failure())
        assertFalse(consent.enabled(laptop))
        assertEquals(ConsentRead.Refused, consent.read(laptop, sharing))
        assertTrue(consent.revision.value > before)
        assertFalse(consent.stillShared(receipt))
        assertNull(consent.ticket(laptop))
    }

    @Test
    fun `B-SEC-164 the settings facade port reads the same store and the same revision`() = runTest {
        val consent = store(kv())
        val port = consent.asSettingsPort()
        val before = port.epoch.value
        assertIs<Outcome.Ok<Unit>>(port.setEnabledIfCurrent(laptop, true) { true })
        assertTrue(port.enabled(laptop))
        assertEquals(SettingOriginKind.ExplicitValue, port.origin(laptop))
        assertEquals(SettingOriginKind.DefaultValue, port.origin(phone))
        assertTrue(port.epoch.value > before)
        consent.closeProfile(laptop)
        assertTrue(port.isClosed(laptop))
        assertFalse(port.enabled(laptop))
        assertEquals(Failure.Refused(RefusalReason.StaleScope), port.setEnabledIfCurrent(laptop, true) { true }.failure())
    }
}
