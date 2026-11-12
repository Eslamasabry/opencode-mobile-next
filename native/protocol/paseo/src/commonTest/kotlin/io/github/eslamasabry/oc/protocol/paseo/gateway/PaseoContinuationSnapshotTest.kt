@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The list's read of "reopen needs a new chat" is an immutable snapshot the lane publishes, never the lane's maps. */
class PaseoContinuationSnapshotTest {
    @Test
    fun `the read answers unknown until the lane has published, then the whole picture at once`() = runTest {
        val h = GatewayHarness(this, FakeDaemon().seedDefaults())
        val read = async { h.gateway.allSessions() }
        // The lane has not run: nothing is held, and the read does not touch the lane's maps.
        assertNull(h.gateway.reopenNeedsNewChat(SessionId("a1")))
        read.await()
        assertNotNull(h.gateway.reopenNeedsNewChat(SessionId("a1")))
        assertNull(h.gateway.reopenNeedsNewChat(SessionId("nobody")))
    }

    @Test
    fun `a read between two lane steps sees one published snapshot and an unchanged picture is not replaced`() = runTest {
        val h = GatewayHarness(this, FakeDaemon().seedDefaults())
        h.gateway.allSessions()
        val first = h.gateway.reopenNeedsNewChat(SessionId("a1"))
        val pending = async { h.gateway.allSessions() }
        // Mid-update (the lane is queued, not run): the earlier answer stands.
        assertEquals(first, h.gateway.reopenNeedsNewChat(SessionId("a1")))
        pending.await()
        assertEquals(first, h.gateway.reopenNeedsNewChat(SessionId("a1")))
    }

    @Test
    fun `forgetting an agent drops it from the snapshot`() = runTest {
        val h = GatewayHarness(this, FakeDaemon().seedDefaults())
        h.gateway.allSessions()
        assertNotNull(h.gateway.reopenNeedsNewChat(SessionId("a1")))
        kotlinx.coroutines.withContext(h.lane) { h.gateway.forget("a1") }
        assertNull(h.gateway.reopenNeedsNewChat(SessionId("a1")))
    }
}
