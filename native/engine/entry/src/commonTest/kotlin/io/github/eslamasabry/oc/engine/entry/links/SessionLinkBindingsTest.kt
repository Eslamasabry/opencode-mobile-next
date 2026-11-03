package io.github.eslamasabry.oc.engine.entry.links

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/** The session-link binding store over a fake disk (test/session_link_bindings_test.dart). */
class SessionLinkBindingsTest {
    private val profile = "phone"
    private val key = "oc.sessionLinkBinding.phone"
    private val origin = "https://workstation.example-tailnet.example.invalid"
    private val instance = "9e30af6d-422d-4d89-baad-006ac07cb9d1"
    private val verified = 1_790_553_600_000L

    private fun binding(origin: String = this.origin, id: String = instance) = SessionLinkBinding(origin, id, verified)

    /** The preference file, its profile list, and the ways it fails. */
    private class Disk(existing: String? = null) : SessionLinkStorage, SessionLinkProfiles {
        val data = mutableMapOf<String, String>()
        val profiles = mutableSetOf("phone")
        var refuse = false
        var throwWrite = false
        var failReads = false
        var failReadsAfterWrite = false

        /** A refused write the platform still kept (SharedPreferences caches before it confirms). */
        var keepRefused = false
        var hold: CompletableDeferred<Unit>? = null
        val entered = CompletableDeferred<Unit>()
        var onConfirm: () -> Unit = {}

        init {
            if (existing != null) data["oc.sessionLinkBinding.phone"] = existing
        }

        override suspend fun read(key: String): Outcome<String?> =
            if (failReads) Outcome.Failed(Failure.StorageRefused()) else data[key].ok()

        override suspend fun write(key: String, value: String): Outcome<Unit> {
            hold?.let {
                hold = null
                entered.complete(Unit)
                it.await()
            }
            if (failReadsAfterWrite) failReads = true
            if (throwWrite) error("synthetic failure")
            if (refuse) {
                if (keepRefused) data[key] = value
                return Outcome.Failed(Failure.StorageRefused())
            }
            data[key] = value
            return Unit.ok()
        }

        override fun isListed(profileId: String): Boolean = profileId in profiles

        override suspend fun confirm(profileId: String): Outcome<Boolean> {
            onConfirm()
            return if (failReads) Outcome.Failed(Failure.StorageRefused()) else (profileId in profiles).ok()
        }
    }

    private class Secrets : SecretRecognizer {
        val known = mutableSetOf<String>()

        override fun containsSecret(text: String): Boolean = known.any { it in text }
    }

    private class Rig(existing: String? = null) {
        val disk = Disk(existing)
        val secrets = Secrets()
        val bindings = SessionLinkBindings(disk, disk, secrets)

        fun owner(id: String = "phone"): SessionLinkBindingOwner =
            (bindings.forProfile(id) as? SessionLinkResult.Ok)?.value ?: fail("no owner for $id")
    }

    private fun failed(failure: SessionLinkFailure) = SessionLinkResult.Failed(failure)

    @Test
    fun `B-STATE-108 the shared owner stores only the canonical verified binding`() = runTest {
        val rig = Rig()
        val owner = rig.owner()
        assertSame(owner, rig.owner())
        assertEquals(SessionLinkResult.Ok(null), owner.read())
        assertEquals(SessionLinkResult.Ok(Unit), owner.save(binding(origin = "https://WORKSTATION.example-tailnet.example.invalid:443")))
        val stored = Json.parseToJsonElement(rig.disk.data.getValue(key)).jsonObject
        assertEquals(setOf("schemaVersion", "origin", "instanceId", "verifiedAt"), stored.keys)
        assertEquals(origin, stored.getValue("origin").jsonPrimitive.content)
        val read = (owner.read() as SessionLinkResult.Ok).value!!
        assertEquals(instance, read.instanceId)
        assertEquals(verified, read.verifiedAtMillis)
        assertEquals(1, owner.revision.value)
        owner.save(binding())
        assertEquals(2, owner.revision.value)
    }

    @Test
    fun `B-STATE-108 a different origin or installation cannot overwrite a binding`() = runTest {
        val owner = Rig().owner()
        owner.save(binding())
        assertEquals(failed(SessionLinkFailure.InstanceMismatch), owner.save(binding(origin = "https://other.example-tailnet.example.invalid")))
        assertEquals(failed(SessionLinkFailure.InstanceMismatch), owner.save(binding(id = "9e30af6d-422d-4d89-baad-006ac07cb9d2")))
        val read = (owner.read() as SessionLinkResult.Ok).value!!
        assertEquals(instance, read.instanceId)
        assertEquals(origin, read.origin)
    }

    @Test
    fun `B-STATE-108 an absent profile cannot load or save even through a stale owner`() = runTest {
        val rig = Rig()
        val owner = rig.owner()
        assertEquals(failed(SessionLinkFailure.ProfileMissing), rig.bindings.forProfile("absent"))
        assertEquals(failed(SessionLinkFailure.InvalidLink), rig.bindings.forProfile("bad id/with slash"))
        rig.disk.profiles.clear()
        assertFalse(owner.isAvailable)
        assertEquals(failed(SessionLinkFailure.ProfileMissing), owner.save(binding()))
        assertEquals(failed(SessionLinkFailure.ProfileMissing), owner.read())
        assertFalse(key in rig.disk.data)
    }

    @Test
    fun `B-STATE-109 closing stops writes at once but drains the write already on disk`() = runTest {
        val rig = Rig()
        val release = CompletableDeferred<Unit>()
        rig.disk.hold = release
        val owner = rig.owner()
        val saving = async { owner.save(binding()) }
        rig.disk.entered.await()
        rig.bindings.closeProfile(profile)
        assertFalse(owner.isAvailable)
        assertEquals(1, owner.revision.value)
        assertEquals(failed(SessionLinkFailure.ProfileMissing), rig.bindings.forProfile(profile))
        val draining = async { rig.bindings.drain(profile) }
        runCurrent()
        assertFalse(draining.isCompleted)
        release.complete(Unit)
        assertEquals(failed(SessionLinkFailure.ProfileMissing), saving.await())
        draining.await()
        // The deletion sweep runs, then the profile is gone for good.
        rig.disk.data.remove(key)
        rig.disk.profiles.clear()
        assertEquals(failed(SessionLinkFailure.ProfileMissing), owner.save(binding()))
        assertFalse(key in rig.disk.data)
        rig.bindings.cancelDeletion(profile)
        assertEquals(failed(SessionLinkFailure.ProfileMissing), rig.bindings.forProfile(profile))
    }

    @Test
    fun `B-STATE-109 an aborted deletion reopens only a fresh owner after durable membership`() = runTest {
        val rig = Rig()
        val old = rig.owner()
        old.save(binding())
        rig.bindings.closeProfile(profile)
        rig.bindings.drain(profile)
        rig.disk.failReads = true
        rig.bindings.cancelDeletion(profile)
        assertEquals(failed(SessionLinkFailure.ProfileMissing), rig.bindings.forProfile(profile))
        rig.disk.failReads = false
        rig.bindings.cancelDeletion(profile)
        val fresh = rig.owner()
        assertNotSame(old, fresh)
        assertEquals(instance, (fresh.read() as SessionLinkResult.Ok).value!!.instanceId)
        assertEquals(SessionLinkResult.Ok(Unit), fresh.save(binding()))
        assertEquals(failed(SessionLinkFailure.ProfileMissing), old.save(binding()))
    }

    @Test
    fun `B-STATE-110 refused or thrown persistence never exposes the proposed binding`() = runTest {
        for (throws in listOf(false, true)) {
            val rig = Rig()
            val owner = rig.owner()
            rig.disk.refuse = !throws
            rig.disk.throwWrite = throws
            assertEquals(failed(SessionLinkFailure.Storage), owner.save(binding()), "throws=$throws")
            assertFalse(owner.isAvailable)
            assertEquals(failed(SessionLinkFailure.Storage), owner.read())
            assertFalse(key in rig.disk.data)
            rig.disk.refuse = false
            rig.disk.throwWrite = false
            assertEquals(failed(SessionLinkFailure.Storage), owner.save(binding()))
        }
    }

    @Test
    fun `B-STATE-110 a failed write and a failed reload never publish the value the platform kept`() = runTest {
        val rig = Rig()
        val owner = rig.owner()
        rig.disk.refuse = true
        rig.disk.keepRefused = true
        rig.disk.failReadsAfterWrite = true
        assertEquals(failed(SessionLinkFailure.Storage), owner.save(binding()))
        assertTrue(key in rig.disk.data) // the platform may keep its proposed value; consumers use the owner
        assertFalse(owner.isAvailable)
        rig.disk.failReads = false
        assertEquals(failed(SessionLinkFailure.Storage), owner.read())
    }

    @Test
    fun `B-STATE-110 unreadable storage fails closed before any write`() = runTest {
        val rig = Rig()
        val owner = rig.owner()
        rig.disk.failReads = true
        assertEquals(failed(SessionLinkFailure.Storage), owner.save(binding()))
        assertFalse(owner.isAvailable)
        assertEquals(failed(SessionLinkFailure.Storage), owner.read())
        assertFalse(key in rig.disk.data)
    }

    @Test
    fun `B-STATE-110 corrupt or unknown stored shapes are unavailable and left untouched`() = runTest {
        val shapes =
            listOf(
                "{",
                """{"schemaVersion":2}""",
                """{"schemaVersion":1,"origin":"$origin","instanceId":"$instance","verifiedAt":0,"extra":"forbidden"}""",
                """{"schemaVersion":"1","origin":"$origin","instanceId":"$instance","verifiedAt":0}""",
                """{"schemaVersion":1,"origin":"https://evil.example.invalid","instanceId":"$instance","verifiedAt":0}""",
                "[]",
            )
        for (raw in shapes) {
            val rig = Rig(existing = raw)
            val owner = rig.owner()
            assertEquals(failed(SessionLinkFailure.Storage), owner.read(), raw)
            assertEquals(failed(SessionLinkFailure.Storage), owner.save(binding()), raw)
            assertEquals(raw, rig.disk.data[key])
        }
    }

    @Test
    fun `B-SEC-162 credentials and recognized secrets never enter binding storage`() = runTest {
        val rig = Rig()
        val owner = rig.owner()
        assertEquals(
            failed(SessionLinkFailure.Credentials),
            owner.save(binding(origin = "https://user:password@workstation.example-tailnet.example.invalid")),
        )
        rig.secrets.known += instance
        assertEquals(failed(SessionLinkFailure.InvalidLink), owner.save(binding()))
        // The normalized (lower-cased) id is checked too.
        assertEquals(failed(SessionLinkFailure.InvalidLink), owner.save(binding(id = instance.uppercase())))
        assertFalse(key in rig.disk.data)
    }

    @Test
    fun `B-SEC-162 a secret that becomes known while a write waits is still kept out`() = runTest {
        val rig = Rig()
        val owner = rig.owner()
        rig.disk.onConfirm = { rig.secrets.known += instance }
        assertEquals(failed(SessionLinkFailure.InvalidLink), owner.save(binding()))
        assertFalse(key in rig.disk.data)
        assertNull(rig.disk.data[key])
    }
}
