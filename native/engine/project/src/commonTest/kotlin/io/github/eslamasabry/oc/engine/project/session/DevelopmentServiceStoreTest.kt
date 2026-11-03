package io.github.eslamasabry.oc.engine.project.session

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevelopmentServiceStoreTest {
    private val sample = DevelopmentService(
        id = "vite",
        name = "Shopfront preview",
        command = "npm run dev",
        directory = "/work/shopfront",
        url = "http://shopfront.example.invalid:5173",
    )

    @Test
    fun `B-STATE-015 profile deletion blocks a late receipt without recreating preferences`() = runTest {
        val inner = storeOver(SessionTestDisk(), StandardTestDispatcher(testScheduler))
        val store = HookedStore(inner)
        var writable = true
        val services = DevelopmentServiceStore(store, WriterLane("p"), ProfileId("p"), canWrite = { writable })
        assertEquals(Outcome.Ok(Unit), services.save(sample))
        // The start receipt is on its way when the profile is deleted and swept.
        store.onCommit = {
            writable = false
            inner.commit(WriterLane("app")) { remove(services.key) }
        }
        assertIs<Outcome.Failed>(services.updateRun("vite", DevelopmentServiceRun(ownerToken = "owner-1", shellId = "sh_1")))
        assertNull(store.get(services.key))
        // A receipt asked for after the deletion is refused before anything is written.
        assertIs<Outcome.Failed>(services.updateRun("vite", null))
        assertNull(store.get(services.key))
    }

    @Test
    fun `B-STATE-015 the standard deletion sweep includes services but preserves peers`() = runTest {
        val store = storeOver(SessionTestDisk(), StandardTestDispatcher(testScheduler))
        val lane = WriterLane("app")
        val services = DevelopmentServiceStore(store, WriterLane("p"), ProfileId("p"), canWrite = { true })
        services.save(sample)
        store.commit(lane) { putString("oc.developmentServices.peer", "retained") }
        assertEquals("npm run dev", (services.load() as Outcome.Ok).value.single().command)
        val doomed = ProfileKeyRule.scopedKeys("p", store.keys("oc."), setOf("p", "peer"))
        assertTrue(services.key in doomed)
        store.commit(lane) { doomed.forEach(::remove) }
        assertNull(store.get(services.key))
        assertEquals("retained", store.string("oc.developmentServices.peer"))
    }

    @Test
    fun `B-STATE-015 corrupt saved configuration is not silently overwritten`() = runTest {
        val store = storeOver(SessionTestDisk(), StandardTestDispatcher(testScheduler))
        store.commit(WriterLane("seed")) { putString(DevelopmentServiceStore.keyFor(ProfileId("p")), "{broken") }
        val services = DevelopmentServiceStore(store, WriterLane("p"), ProfileId("p"), canWrite = { true })
        assertIs<Outcome.Failed>(services.load())
        assertIs<Outcome.Failed>(services.save(sample))
        assertEquals("{broken", store.string(services.key))
    }
}
