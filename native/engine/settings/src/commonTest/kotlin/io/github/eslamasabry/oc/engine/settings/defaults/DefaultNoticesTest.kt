package io.github.eslamasabry.oc.engine.settings.defaults

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import io.github.eslamasabry.oc.engine.settings.prefs.GatedStore
import io.github.eslamasabry.oc.engine.settings.prefs.TestDisk
import io.github.eslamasabry.oc.engine.settings.prefs.seed
import io.github.eslamasabry.oc.engine.settings.prefs.storeOver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DefaultNoticesTest {
    private val a = ProfileId("a")
    private val b = ProfileId("b")
    private val safe = ProfileId("safe")
    private val profiles = """[{"id":"a"},{"id":"b"},{"id":"safe"}]"""

    private fun <T> Outcome<T>.value(): T = assertIs<Outcome.Ok<T>>(this).value

    private fun Outcome<*>.refusal(): Failure = assertIs<Outcome.Failed>(this).failure

    private fun automaticModel(name: String) = InteractionDefaults.model(listOf(ModelOption("p", name, "Friendly $name")), signedIn = true)

    private suspend fun KeyValueStore.withProfiles(): KeyValueStore = also { seed("oc.profiles" to profiles) }

    @Test
    fun `B-STATE-023 project and once-only notices survive restart, stay per server and delete with the profile`() = runTest {
        val disk = TestDisk()
        val store = storeOver(disk, StandardTestDispatcher(testScheduler)).withProfiles()
        val lanes = DefaultWriterLanes()
        val notices = DefaultNotices(store, lanes)
        notices.rememberProject(a, "second").value()
        val delivery = InteractionDefaults.delivery()
        val claims = listOf(
            async { notices.takeAnnouncement(a, DefaultKind.Delivery, delivery) },
            async { notices.takeAnnouncement(a, DefaultKind.Delivery, delivery) },
        ).awaitAll().map { it.value() }
        assertEquals(listOf("queue", null), claims)

        val restarted = DefaultNotices(storeOver(TestDisk(disk.persisted()), StandardTestDispatcher(testScheduler)), DefaultWriterLanes())
        assertEquals("second", restarted.lastProjectId(a))
        assertNull(restarted.takeAnnouncement(a, DefaultKind.Delivery, delivery).value())

        assertNull(notices.lastProjectId(b))
        assertEquals("queue", notices.takeAnnouncement(b, DefaultKind.Delivery, delivery).value())
        val unresolved = InteractionDefaults.review(mapOf(DefaultReviewScope.Session to null))
        assertNull(notices.takeAnnouncement(a, DefaultKind.Review, unresolved).value())
        val chosen = ModelOption("p", "a", "Friendly a")
        val explicit = InteractionDefaults.model(listOf(chosen), signedIn = true, explicitChoice = chosen)
        assertNull(notices.takeAnnouncement(a, DefaultKind.Model, explicit).value())
        assertEquals("Friendly a", notices.takeAnnouncement(a, DefaultKind.Model, automaticModel("a")).value())

        notices.closeProfile(a)
        notices.forgetProfile(a).value()
        assertTrue(ProfileKeyRule.scopedKeys("a", store.keys("oc."), setOf("a", "b", "safe")).isEmpty())
        assertNull(notices.lastProjectId(a))
        assertEquals(RefusalReason.ProfileClosing, assertIs<Failure.Refused>(notices.takeAnnouncement(a, DefaultKind.Delivery, delivery).refusal()).reason)
        assertNull(notices.takeAnnouncement(b, DefaultKind.Delivery, delivery).value(), "the other server keeps its own")
        assertEquals(DefaultNotices.SHAPES.map { it.prefix }.toSet(), setOf("oc.defaultProject.", "oc.defaultNotices."))
    }

    @Test
    fun `B-STATE-023 refused storage neither remembers a project nor consumes a notice`() = runTest {
        val disk = TestDisk()
        val store = storeOver(disk, StandardTestDispatcher(testScheduler)).withProfiles()
        val notices = DefaultNotices(store, DefaultWriterLanes())
        disk.refuse = { _, _ -> true }
        assertIs<Failure.Storage>(notices.rememberProject(a, "project").refusal())
        assertNull(notices.lastProjectId(a))
        assertIs<Failure.Storage>(notices.takeAnnouncement(a, DefaultKind.Delivery, InteractionDefaults.delivery()).refusal())
        disk.refuse = null
        notices.rememberProject(a, "project").value()
        assertEquals("project", notices.lastProjectId(a))
        assertEquals("queue", notices.takeAnnouncement(a, DefaultKind.Delivery, InteractionDefaults.delivery()).value())
    }

    @Test
    fun `B-STATE-023 sensitive values are never persisted or announced and a refused value does not block the queue`() = runTest {
        val secret = "fake-credential-for-defaults-test"
        val filter = object : SecretFilter {
            override fun containsSecret(text: String) = secret in text
            override fun mask(text: String) = text.replace(secret, "•••")
        }
        val store = storeOver(TestDisk(), StandardTestDispatcher(testScheduler)).withProfiles()
        val notices = DefaultNotices(store, DefaultWriterLanes(), filter)
        assertEquals(StorageProblem.InvalidValue, assertIs<Failure.Storage>(notices.rememberProject(safe, secret).refusal()).problem)
        notices.rememberProject(safe, "safe-project").value()
        val choice = InteractionDefaults.picker(listOf(secret), { it })
        assertEquals("•••", notices.takeAnnouncement(safe, DefaultKind.Project, choice).value())
        for (key in store.keys("oc.")) {
            assertFalse(secret in key)
            assertFalse(secret in store.get(key).let { (it as? io.github.eslamasabry.oc.core.data.kv.PrefValue.Str)?.value.orEmpty() })
            assertFalse(store.get(key).let { (it as? io.github.eslamasabry.oc.core.data.kv.PrefValue.StrList)?.value.orEmpty().any { v -> secret in v } })
        }
        assertIs<Failure.Storage>(notices.rememberProject(ProfileId(secret), "p").refusal())
        assertEquals("safe-project", notices.lastProjectId(safe))
    }

    @Test
    fun `B-SEC-057 default notices have one owner per store and profile`() = runTest {
        val gated = GatedStore(storeOver(TestDisk(), StandardTestDispatcher(testScheduler)).withProfiles())
        val lanes = DefaultWriterLanes()
        val notices = DefaultNotices(gated, lanes)
        val release = CompletableDeferred<Unit>().also { gated.gate = it }
        // Two screens claim the same notice at once: the second waits for the first, then finds it said.
        val first = async { notices.takeAnnouncement(a, DefaultKind.Project, InteractionDefaults.picker(listOf("p"), { it })) }
        val second = async { notices.takeAnnouncement(a, DefaultKind.Project, InteractionDefaults.picker(listOf("p"), { it })) }
        runCurrent()
        assertEquals(1, gated.calls.size, "one writer at a time per profile")
        release.complete(Unit)
        assertEquals("p", first.await().value())
        assertNull(second.await().value())
        assertEquals(1, gated.calls.size)
        // Every write of the profile goes through its lane: closing the lane fences them all.
        lanes.closeProfile(a)
        assertIs<Failure.Refused>(notices.rememberProject(a, "late").refusal())
    }

    @Test
    fun `B-SEC-057 writes for an absent profile are rejected`() = runTest {
        val store = storeOver(TestDisk(), StandardTestDispatcher(testScheduler)).withProfiles()
        val notices = DefaultNotices(store, DefaultWriterLanes())
        val missing = ProfileId("missing")
        assertEquals(RefusalReason.ProfileDeleted, assertIs<Failure.Refused>(notices.rememberProject(missing, "project").refusal()).reason)
        assertNull(store.get("oc.defaultProject.missing"))
        assertIs<Failure.Refused>(notices.takeAnnouncement(missing, DefaultKind.Delivery, InteractionDefaults.delivery()).refusal())
    }

    @Test
    fun `B-SEC-057 profile deletion stops admission and drains a pending notice before removing it`() = runTest {
        val gated = GatedStore(storeOver(TestDisk(), StandardTestDispatcher(testScheduler)).withProfiles(), inLane = true)
        val lanes = DefaultWriterLanes()
        val notices = DefaultNotices(gated, lanes)
        val release = CompletableDeferred<Unit>().also { gated.gate = it }
        val writing = async { notices.takeAnnouncement(a, DefaultKind.Project, InteractionDefaults.picker(listOf("project"), { it })) }
        gated.entered.await()
        notices.closeProfile(a)
        val removal = async { notices.forgetProfile(a) }
        runCurrent()
        assertFalse(removal.isCompleted, "deletion outran the pending write")
        release.complete(Unit)
        assertIs<Outcome.Ok<*>>(writing.await())
        removal.await().value()
        assertNull(gated.get("oc.defaultNotices.a"))
        assertIs<Failure.Refused>(notices.rememberProject(a, "late").refusal())
        // A new screen over the app's lanes cannot reopen admission before the profile row is removed.
        assertIs<Failure.Refused>(DefaultNotices(gated, lanes).rememberProject(a, "late").refusal())
        assertNull(gated.get("oc.defaultProject.a"))
    }
}
