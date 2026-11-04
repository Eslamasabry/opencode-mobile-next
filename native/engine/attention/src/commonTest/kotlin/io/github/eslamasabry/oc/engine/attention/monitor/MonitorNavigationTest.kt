package io.github.eslamasabry.oc.engine.attention.monitor

import io.github.eslamasabry.oc.core.testing.TestClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MonitorNavigationTest {
    @Test
    fun `B-ANDR-061 a source edit during request preflight cannot switch the active transport`() =
        runTest {
            val profiles = FakeProfiles(count = 1)
            val late = CompletableDeferred<List<MonitorPending>>()
            val gateway = TestGateway(pendingRead = late)
            val clock = TestClock()
            var created = 0
            val opener =
                MonitorRequestOpener(
                    profiles = profiles,
                    createGateway = {
                        created++
                        gateway
                    },
                    monitorEnabled = { true },
                    isReadable = { true },
                    clock = clock,
                )
            val target = profiles.list.single()
            val route =
                MonitoredRoute(
                    profileId = "profile-1",
                    requestId = "request-1",
                    sessionId = "same-session",
                    kind = MonitoredRequestKind.Permission,
                    createdAt = clock.now(),
                    serverUrl = target.baseUrl,
                    sourceIdentity = ProfileMonitor.routeSourceIdentity(target),
                )
            val preparing = async { opener.prepare(route) }
            runCurrent()
            profiles.edit("profile-1") { it.copy(baseUrl = "https://different.example") }
            late.complete(listOf(pending(1)))
            assertFalse(preparing.await(), "the caller must not connect")
            assertEquals(1, created)
            assertTrue(gateway.isClosed)

            // The same route with an unchanged source is still valid and its preflight gateway is closed too.
            profiles.edit("profile-1") { it.copy(baseUrl = target.baseUrl) }
            val again = TestGateway(requests = listOf(pending(1)))
            val ok = MonitorRequestOpener(profiles, { again }, { true }, { true }, clock).prepare(route)
            assertTrue(ok)
            assertTrue(again.isClosed)
        }

    @Test
    fun `B-ANDR-061 a monitored alert carries an opaque profile route with no reply actions`() {
        for (kind in listOf(MonitoredRequestKind.Permission, MonitoredRequestKind.CheckIn)) {
            val request = MonitoredRequest("request-1", "same-session", kind, title = "Private title", directory = "/private")
            val wire = MonitorAlertPayload.of("profile-1", request, "monitor:profile-1:request-1", "opaque-token").toWire()
            assertEquals("profile-1", wire["profileID"])
            assertEquals("opaque-token", wire["monitorToken"])
            assertEquals(false, wire["allowActions"])
            for (absent in listOf("title", "password", "directory", "prompt")) assertFalse(absent in wire.keys, absent)
            if (kind == MonitoredRequestKind.CheckIn) assertEquals("checkin", wire["kind"])
            val opened = MonitorAlertOpen.fromWire(wire)
            assertEquals("profile-1", opened?.profileId)
            assertEquals("opaque-token", opened?.monitorToken)
        }
    }
}
