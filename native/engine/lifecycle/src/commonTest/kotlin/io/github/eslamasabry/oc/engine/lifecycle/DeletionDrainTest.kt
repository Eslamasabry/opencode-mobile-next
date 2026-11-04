package io.github.eslamasabry.oc.engine.lifecycle

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports of test/audit2_builtin_owner_deletion_test.dart and test/audit2_managed_recovery_deletion_test.dart. */
class DeletionDrainTest {
    /** SharedPreferences as the plugin behaves: the cache moves before the durable write is acknowledged. */
    private class Disk(initial: Map<String, String> = emptyMap()) : OwnerPreferences, RecoveryPreferences {
        val durable = initial.toMutableMap()
        val cache = initial.toMutableMap()
        var refuseClaim = false
        var refuseClear = false
        var failReads = false
        var holdKey: String? = null
        var holdValue: String? = null
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override fun cached(key: String): String? = cache[key]

        override fun get(key: String): String? = cache[key]

        override suspend fun write(key: String, value: String): Boolean = set(key, value)

        override suspend fun set(key: String, value: String): Boolean {
            cache[key] = value
            if (key == BuiltinServerOwner.KEY && value == "candidate" && refuseClaim) return false
            if (key == BuiltinServerOwner.KEY && value == "" && refuseClear) return false
            if (key == holdKey && (holdValue == null || value == holdValue)) {
                holdKey = null
                entered.complete(Unit)
                release.await()
            }
            durable[key] = value
            return true
        }

        override suspend fun remove(key: String): Boolean {
            cache.remove(key)
            durable.remove(key)
            return true
        }

        override suspend fun reload(): Boolean {
            if (failReads) return false
            cache.clear()
            cache.putAll(durable)
            return true
        }
    }

    private class Profiles(vararg ids: String) : DeletableProfiles {
        val ids = ids.toMutableList()

        override fun isReadable(profileId: String): Boolean = profileId in ids

        override suspend fun remove(profileId: String): Boolean = ids.remove(profileId).let { true }
    }

    @Test
    fun `B-SEC-011 a refused or failed claim cannot hide another durable owner from deletion`() = runTest {
        val disk = Disk(mapOf(BuiltinServerOwner.KEY to "retained")).apply { refuseClaim = true }
        val owner = BuiltinServerOwner(disk)
        assertIs<Outcome.Failed>(owner.claim("candidate") { true })
        assertEquals("retained", disk.cached(BuiltinServerOwner.KEY))
        assertIs<Outcome.Ok<Unit>>(owner.clearProfile("retained"))
        disk.reload()
        assertEquals("", disk.cached(BuiltinServerOwner.KEY))
    }

    @Test
    fun `B-SEC-011 an unreadable cache after a failed claim blocks clears until reconciled`() = runTest {
        val disk = Disk(mapOf(BuiltinServerOwner.KEY to "retained")).apply { refuseClaim = true }
        val owner = BuiltinServerOwner(disk)
        disk.failReads = true
        assertIs<Outcome.Failed>(owner.claim("candidate") { true })
        // The optimistic cache says "candidate"; it is no proof the retained profile stopped owning the runtime.
        assertEquals("candidate", disk.cached(BuiltinServerOwner.KEY))
        assertIs<Outcome.Failed>(owner.clearProfile("retained"))
        assertIs<Outcome.Failed>(owner.claim("another") { true })
        disk.failReads = false
        assertIs<Outcome.Ok<Unit>>(owner.clearProfile("retained"))
        disk.reload()
        assertEquals("", disk.cached(BuiltinServerOwner.KEY))
    }

    @Test
    fun `B-SEC-011 a refused owner clear aborts deletion and the deletion can be retried`() = runTest {
        val disk = Disk(mapOf(BuiltinServerOwner.KEY to "phone")).apply { refuseClear = true }
        val owner = BuiltinServerOwner(disk)
        val profiles = Profiles("phone")
        val deletion = PhoneProfileDeletion(profiles, owner)
        assertIs<Outcome.Failed>(deletion.delete("phone"))
        assertEquals(listOf("phone"), profiles.ids)
        assertTrue(profiles.isReadable("phone"))
        disk.reload()
        assertEquals("phone", disk.cached(BuiltinServerOwner.KEY))
        // The completed abort reopens the owner: a fresh explicit claim is allowed; the retry must still clear it.
        assertIs<Outcome.Ok<Unit>>(owner.claim("phone") { profiles.isReadable("phone") })
        disk.refuseClear = false
        val result = deletion.delete("phone")
        assertEquals(true, (result as Outcome.Ok).value.removedProfile)
        disk.reload()
        assertEquals("", disk.cached(BuiltinServerOwner.KEY))
        assertIs<Outcome.Failed>(owner.claim("phone") { true })
    }

    @Test
    fun `B-SEC-011 deletion drains a delayed manual-start owner claim and leaves no claim`() = runTest {
        val disk = Disk().apply { holdKey = BuiltinServerOwner.KEY; holdValue = "phone" }
        val owner = BuiltinServerOwner(disk)
        val profiles = Profiles("phone")
        val writing = async { owner.claim("phone") { profiles.isReadable("phone") } }
        disk.entered.await()
        var deleted = false
        val deletion = async { PhoneProfileDeletion(profiles, owner).delete("phone").also { deleted = true } }
        repeat(100) { runCurrent() }
        val deletedBeforeClaimSettled = deleted
        disk.release.complete(Unit)
        assertIs<Outcome.Failed>(writing.await())
        val result = deletion.await()
        disk.reload()
        assertFalse(deletedBeforeClaimSettled)
        val removed = (result as Outcome.Ok).value
        assertTrue(removed.removedProfile)
        assertTrue(removed.failures.isEmpty())
        assertFalse(disk.cached(BuiltinServerOwner.KEY) == "phone")
        assertIs<Outcome.Failed>(owner.claim("phone") { true })
        disk.reload()
        assertFalse(disk.cached(BuiltinServerOwner.KEY) == "phone")
    }

    private val record = """{"enabled":true,"token":"","attempts":1,"operation":"original","pendingOperation":""}"""

    private class Policy : RestartPolicyRead, RestartPolicyPause {
        val paused = mutableSetOf<String>()
        val writes = mutableListOf<String>()

        override fun allowsRestart(profileId: String): Boolean = true

        override fun pauseForDeletion(profileId: String) {
            paused += profileId
        }

        override fun cancelDeletion(profileId: String) {
            paused -= profileId
        }
    }

    @Test
    fun `B-SEC-012 deletion drains a stored or loaded recovery record while policy edits are paused`() = runTest {
        for (loaded in listOf(false, true)) {
            val key = RecoveryPolicyRegistry.preferenceKey("local")
            val disk = Disk(mapOf(key to record))
            val policy = Policy()
            val commands = mutableListOf<String>()
            val registry = RecoveryPolicyRegistry(disk, { commands += it }, policy)
            if (loaded) registry.forProfile("local")
            val profiles = Profiles("local")
            val result = PhoneProfileDeletion(profiles, BuiltinServerOwner(disk), registry, policy).delete("local")
            val done = (result as Outcome.Ok).value
            assertTrue(done.removedProfile)
            assertTrue(done.failures.isEmpty())
            assertTrue(profiles.ids.isEmpty())
            assertNull(disk.get(key))
            assertTrue(commands.isEmpty())
            assertTrue(policy.writes.isEmpty())
        }
    }

    @Test
    fun `B-SEC-012 deleting a stored or loaded alias keeps the surviving owner's permit`() = runTest {
        for (loadedAlias in listOf(false, true)) {
            val shared = """{"enabled":true,"token":"test-shared-permit","attempts":1,"operation":"original","pendingOperation":""}"""
            val disk =
                Disk(mapOf(RecoveryPolicyRegistry.preferenceKey("keeper") to shared, RecoveryPolicyRegistry.preferenceKey("alias") to shared))
            val commands = mutableListOf<String>()
            val registry = RecoveryPolicyRegistry(disk, { commands += it }, Policy())
            val keeper = registry.forProfile("keeper")
            if (loadedAlias) registry.forProfile("alias")
            registry.prepareForProfileDeletion("alias")
            assertTrue(keeper.ownsInstallation)
            assertTrue(keeper.enabled)
            assertEquals(0, commands.size)
            assertEquals(shared, disk.get(RecoveryPolicyRegistry.preferenceKey("keeper")))
        }
    }

    @Test
    fun `B-SEC-012 an owning profile's deletion revokes its permit`() = runTest {
        val shared = """{"enabled":true,"token":"test-permit","attempts":0,"operation":"","pendingOperation":""}"""
        val disk = Disk(mapOf(RecoveryPolicyRegistry.preferenceKey("only") to shared))
        val commands = mutableListOf<String>()
        RecoveryPolicyRegistry(disk, { commands += it }, Policy()).prepareForProfileDeletion("only")
        assertEquals(listOf("test-permit"), commands)
    }

    @Test
    fun `B-SEC-012 an aborted deletion keeps the policy and reopens the retained recovery owner`() = runTest {
        val disk = Disk()
        val policy = Policy()
        val registry = RecoveryPolicyRegistry(disk, {}, policy)
        val recovery = registry.forProfile("local")
        assertTrue(recovery.enabled)
        policy.pauseForDeletion("local")
        registry.prepareForProfileDeletion("local")
        assertFalse(recovery.enabled)
        assertTrue(policy.writes.isEmpty())
        policy.cancelDeletion("local")
        registry.syncProfiles(listOf("local"))
        assertTrue(registry.forProfile("local") === recovery)
        assertTrue(recovery.enabled)
    }

    @Test
    fun `B-SEC-012 deletion drains an admitted manual-recovery or alias-budget write before sweeping`() = runTest {
        for (aliasWrite in listOf(false, true)) {
            val localKey = RecoveryPolicyRegistry.preferenceKey("local")
            val disk = Disk()
            val registry = RecoveryPolicyRegistry(disk, {}, Policy())
            val profiles = if (aliasWrite) Profiles("local", "keeper") else Profiles("local")
            registry.forProfile("local")
            var alias: RecoveryPolicyRegistry.RecoveryPolicy? = null
            if (aliasWrite) {
                registry.syncProfiles(listOf("local", "keeper"))
                alias = registry.forProfile("keeper").also { it.attempts = 1 }
            }
            disk.holdKey = localKey
            val writing = async { alias?.retryCheck() ?: registry.resumeAfterManualStart("local") }
            disk.entered.await()
            var deleted = false
            val deletion =
                async { PhoneProfileDeletion(profiles, BuiltinServerOwner(disk), registry).delete("local").also { deleted = true } }
            repeat(100) { runCurrent() }
            val deletedBeforeWriteSettled = deleted
            disk.release.complete(Unit)
            writing.await()
            val result = deletion.await()
            assertFalse(deletedBeforeWriteSettled)
            assertTrue((result as Outcome.Ok).value.removedProfile)
            assertFalse(localKey in disk.durable)
            // A late manual-start callback or retry must not reopen the deleted id or publish fresh metadata.
            registry.resumeAfterManualStart("local")
            registry.forProfile("local").retryCheck()
            assertFalse(localKey in disk.durable)
            if (aliasWrite) {
                assertTrue(""""attempts":1""" in disk.durable.getValue(RecoveryPolicyRegistry.preferenceKey("keeper")))
            }
        }
    }
}
