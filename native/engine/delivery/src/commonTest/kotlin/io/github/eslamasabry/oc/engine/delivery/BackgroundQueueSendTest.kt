package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueueStore
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * B-146 (run-5 upgrade gate): a prompt queued for a saved server that is not in use is sent once that server
 * answers, by that server's own delivery, and only once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundQueueSendTest {
    private val lanes = DefaultWriterLanes()

    private fun server(): FakeServerGateway =
        FakeServerGateway(listOf(Session(S1))).apply { capabilities.value = ServerCapabilities() }

    private fun delivery(test: TestScope, gateway: FakeServerGateway, kv: KeyValueStore): ConnectionDelivery =
        ConnectionDelivery(
            gateway = gateway,
            profile =
                ServerProfile(
                    P1,
                    "second",
                    BackendKind.OpenCode2,
                    ServerOrigin("http", "10.0.2.2", 4098),
                    ProfileTrust(null, null),
                    managedOnPhone = false,
                    username = null,
                ),
            scope = ConnectionCoroutineScope(test.backgroundScope.coroutineContext),
            clock = TestClock(Instant.fromEpochMilliseconds(NOW_MS)),
            stores = setOf(DeliveryStorage(kv, lanes, ProfileDirectory { true })),
            dispatchers = TestDispatchers(StandardTestDispatcher(test.testScheduler)),
        )

    private fun TestScope.texts(gateway: FakeServerGateway): List<String> {
        runCurrent()
        return gateway.sentPrompts.map { it.second.text }
    }

    @Test
    fun `B-146 a prompt that reaches the queue after the server connected is sent once when its delivery is nudged`() =
        runTest {
            val gateway = server()
            gateway.events.connect()
            val kv = kv(Disk(), this)
            val d = delivery(this, gateway, kv)
            assertTrue(texts(gateway).isEmpty())
            // The adopted 2.1.4 queue (or another connection's queue) lands after this connection's connect pass.
            assertTrue(OfflineQueueStore(kv, lanes.app).save(listOf(prompt("q1", "e2e:echo queued once"))))
            assertTrue(texts(gateway).isEmpty(), "nothing else asks a connected delivery to look again")
            d.sendWaiting()
            assertEquals(listOf("e2e:echo queued once"), texts(gateway))
            d.sendWaiting()
            d.sendWaiting()
            assertEquals(listOf("e2e:echo queued once"), texts(gateway), "never twice")
        }

    @Test
    fun `B-146 a nudge while the server is not connected sends nothing, and the connect sends it once`() = runTest {
        val gateway = server()
        val kv = kv(Disk(), this)
        assertTrue(OfflineQueueStore(kv, lanes.app).save(listOf(prompt("q1", "e2e:echo queued once"))))
        val d = delivery(this, gateway, kv)
        d.sendWaiting()
        assertTrue(texts(gateway).isEmpty())
        gateway.events.connect()
        d.sendWaiting()
        assertEquals(listOf("e2e:echo queued once"), texts(gateway))
    }

    @Test
    fun `B-146 a delivery created after its server connected sends what already waits`() = runTest {
        val gateway = server()
        gateway.events.connect()
        val kv = kv(Disk(), this)
        assertTrue(OfflineQueueStore(kv, lanes.app).save(listOf(prompt("q1", "e2e:echo queued once"))))
        delivery(this, gateway, kv)
        assertEquals(listOf("e2e:echo queued once"), texts(gateway))
    }
}
