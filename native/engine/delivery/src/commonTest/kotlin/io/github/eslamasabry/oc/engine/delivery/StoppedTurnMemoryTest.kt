package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoppedTurnMemoryTest {
    @Test
    fun `a stop survives a restart and a new turn clears it`() = runTest {
        val disk = Disk()
        val first = World(disk, this)
        val memory = StoppedTurnMemory(first.kv, first.lanes.app, P1, backgroundScope)
        memory.add(REF)
        memory.settled()

        val after = World(disk, this)
        val reopened = StoppedTurnMemory(after.kv, after.lanes.app, P1, backgroundScope)
        assertEquals(setOf(REF), reopened.refs.value)

        reopened.remove(REF)
        reopened.settled()
        val again = World(disk, this)
        assertTrue(StoppedTurnMemory(again.kv, again.lanes.app, P1, backgroundScope).refs.value.isEmpty())
    }

    @Test
    fun `B-STOP-CUT-005 persisted stop ids are readable while the connection is offline`() = runTest {
        val disk = Disk()
        val first = World(disk, this)
        val memory = StoppedTurnMemory(first.kv, first.lanes.app, P1, backgroundScope)
        memory.add(REF)
        memory.settled()

        val offline = World(disk, this)
        assertEquals(setOf(REF.sessionId), ConnectionDelivery.readStoppedByPerson(offline.kv, P1))
    }

    /** The delivery over a real store, with the store kept for the offline read ([ConnectionDelivery.running] is where a session status arrives). */
    private fun TestScope.stoppedRunDelivery(
        disk: Disk,
        server: FakeServerGateway = FakeServerGateway(listOf(Session(S1))),
    ): Pair<ConnectionDelivery, PrefsKeyValueStore> {
        server.events.connect()
        val store = kv(disk, this)
        val delivery =
            ConnectionDelivery(
                gateway = server,
                profile =
                    ServerProfile(
                        P1,
                        "local",
                        BackendKind.OpenCode2,
                        ServerOrigin("http", "127.0.0.1", 4097),
                        ProfileTrust(null, null),
                        managedOnPhone = false,
                        username = null,
                    ),
                scope = ConnectionCoroutineScope(backgroundScope.coroutineContext),
                clock = TestClock(Instant.fromEpochMilliseconds(NOW_MS)),
                stores = setOf(DeliveryStorage(store, DefaultWriterLanes(), ProfileDirectory { true })),
                dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
            )
        return delivery to store
    }

    @Test
    fun `B-STOP-CUT-006 a busy status the server reports after the person's stop clears the stored stop`() =
        runTest {
            val (d, store) = stoppedRunDelivery(Disk())
            testScheduler.runCurrent()
            d.markCutByStop(listOf(REF))
            assertTrue(REF in d.stoppedByPerson.value, "the person's own stop is stored first")

            // A running turn this app did not send (a schedule, another device): the live truth wins.
            d.running(REF, busy = true)
            assertFalse(REF in d.stoppedByPerson.value, "the stop is no longer reported for the conversation")
            testScheduler.runCurrent()
            assertTrue(
                ConnectionDelivery.readStoppedByPerson(store, P1).isEmpty(),
                "the stored stop is gone from disk too",
            )
        }

    @Test
    fun `B-STOP-CUT-006 busy statuses that are not a newer run keep the person's stop`() = runTest {
        val (d, store) = stoppedRunDelivery(Disk())
        testScheduler.runCurrent()
        d.markCutByStop(listOf(REF))
        d.running(REF, busy = false)
        assertTrue(REF in d.stoppedByPerson.value, "no newer run: the stop still speaks")

        // The turn this app sees running, then the person's own Stop of it.
        d.running(REF, busy = true)
        d.engine!!.stopTurn(REF)
        d.running(REF, busy = true) // the stopped run still winding down
        assertTrue(REF in d.stoppedByPerson.value, "the run being stopped is not a newer run")
        d.running(REF, busy = false) // it ends
        assertTrue(REF in d.stoppedByPerson.value, "the person's stop still names how it ended")
        assertTrue(
            REF.sessionId in ConnectionDelivery.readStoppedByPerson(store, P1),
            "and it is still on disk",
        )
    }
}
