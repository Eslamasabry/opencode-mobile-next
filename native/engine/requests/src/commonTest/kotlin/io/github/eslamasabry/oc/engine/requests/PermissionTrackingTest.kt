package io.github.eslamasabry.oc.engine.requests

import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.core.testing.TestPacer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Flutter `permission_migration_test.dart` 99-172 (B-PROTO-073): concurrent requests are tracked per conversation. */
@OptIn(ExperimentalCoroutinesApi::class)
class PermissionTrackingTest {
    private val port = FakeRequestsPort()

    private fun TestScope.engine(): RequestsEngine =
        RequestsEngine(port, backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)), TestClock(), TestPacer())
            .also { runCurrent() }

    private fun TestScope.ask(request: PermissionRequest) {
        port.permissions = port.permissions + request
        port.events.emit(GatewayEvent.PermissionAsked(request))
        runCurrent()
    }

    @Test
    fun `B-PROTO-073 concurrent requests are tracked and a reply settles only its own request`() = runTest {
        val engine = engine()
        ask(permission("request-1", permission = "bash", patterns = listOf("git status")))
        ask(permission("request-2", permission = "edit", patterns = listOf("lib/main.dart")))
        assertEquals(listOf("request-1", "request-2"), engine.pending.value.map { it.id.raw })
        val mine = engine.forSession(S1).first()
        assertEquals("request-1", mine.current?.id?.raw)
        assertEquals(1, mine.moreWaiting, "both wait in the same conversation")

        port.permissions = port.permissions.filterNot { it.id == RequestId("request-1") }
        port.events.emit(GatewayEvent.PermissionSettled(S1, RequestId("request-1")))
        runCurrent()
        assertEquals(listOf("request-2"), engine.pending.value.map { it.id.raw })
        assertEquals("request-2", engine.forSession(S1).first().current?.id?.raw)
    }

    @Test
    fun `B-PROTO-073 a request keeps its identity, patterns, saved patterns and tool call`() = runTest {
        val engine = engine()
        val request =
            permission(
                "request-1",
                permission = "bash",
                patterns = listOf("git status", "git diff"),
                always = listOf("git *"),
                metadata = mapOf("cwd" to "/workspace"),
                callId = "call-1",
            )
        ask(request)
        val kept = (engine.pending.value.single() as PendingRequest.Permission).request
        assertEquals(request, kept)
        assertEquals("call-1", kept.tool?.callId)
        assertEquals(listOf("git status", "git diff"), kept.patterns)
        assertEquals(listOf("git *"), kept.always)
    }
}
