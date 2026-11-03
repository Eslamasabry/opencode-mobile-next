package io.github.eslamasabry.oc.android.widgets.runner

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.PermissionTool
import io.github.eslamasabry.oc.core.model.PromptDelivery
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of the live-connection cases of test/widget_v2_contract_test.dart (`handleNativeWidgetAction`). */
class WidgetLiveActionsRowsTest {
    /** Test-only live connection: one busy conversation `s1` started at 1000, permissions by id. */
    private class Live(
        override val promptSteering: Boolean = true,
        var mode: String = if (promptSteering) "steer" else "afterReply",
    ) : WidgetLiveWorld {
        val calls = mutableListOf<String>()
        var delivery: PromptDelivery? = PromptDelivery.Queue
        var fail = false
        var profile = "p1"
        var scope = "scope-1"
        val permissions = mutableMapOf<String, WidgetPendingAsk>()

        override fun isCurrent(tap: WidgetTap) = tap.profileId == profile && tap.scopeToken == scope

        override fun pendingPermission(requestId: String) = permissions[requestId]

        override fun startedAt(session: String) = if (session == "s1") 1000L else 0L

        override fun steerMode(session: String) = mode

        override suspend fun answer(session: String, request: String, allowOnce: Boolean): Outcome<Unit> {
            calls.add("permission:$request:${if (allowOnce) "once" else "reject"}")
            permissions.remove(request)
            return Outcome.Ok(Unit)
        }

        override suspend fun stop(session: String): Outcome<Unit> {
            calls.add("stop:$session")
            return Outcome.Ok(Unit)
        }

        override suspend fun send(session: String, text: String, delivery: PromptDelivery?): Outcome<Unit> {
            calls.add("prompt:$session")
            this.delivery = delivery
            return if (fail) Outcome.Failed(Failure.Unexpected()) else Outcome.Ok(Unit)
        }
    }

    private fun actionFor(kind: String, id: String = "tap1") =
        mapOf<String, Any?>(
            "id" to id,
            "action" to kind,
            "profileID" to "p1",
            "scopeToken" to "scope-1",
            "sessionID" to "s1",
            "expectedStartedAt" to 1000L,
            "text" to "Adjust the approach",
        )

    // ---- B-AGENT-317: steer where supported, after-reply otherwise, never when unknown, never replayed ----

    @Test
    fun `B-AGENT-317 supported steering selects steer delivery`() = runTest {
        val live = Live(promptSteering = true)
        assertEquals("accepted", WidgetLiveActions(live).handle(actionFor("steer"))["status"])
        assertEquals(PromptDelivery.Steer, live.delivery)
    }

    @Test
    fun `B-AGENT-317 unsupported steering stays an ordinary after-reply send`() = runTest {
        val live = Live(promptSteering = false)
        assertEquals("accepted", WidgetLiveActions(live).handle(actionFor("steer"))["status"])
        assertNull(live.delivery)
    }

    @Test
    fun `B-AGENT-317 an unknown mixed-runtime behaviour never sends a prompt`() = runTest {
        for (mode in listOf("unknown", "restart")) {
            val live = Live(promptSteering = false, mode = mode)
            assertEquals("unsupported", WidgetLiveActions(live).handle(actionFor("steer"))["error"])
            assertTrue(live.calls.isEmpty())
        }
    }

    @Test
    fun `B-AGENT-317 an uncertain send is not replayed and never exposes remote text`() = runTest {
        val live = Live().apply { fail = true }
        val actions = WidgetLiveActions(live)
        val result = actions.handle(actionFor("steer"))
        assertEquals("uncertainDelivery", result["error"])
        assertEquals("uncertain", result["status"])
        assertFalse(result.toString().contains("Adjust the approach"))
        actions.handle(actionFor("steer"))
        assertEquals(listOf("prompt:s1"), live.calls)
    }

    // ---- B-SEC-183: allow once approves only the exact pending permission ----

    @Test
    fun `B-SEC-183 allow once confirms only the exact pending permission`() = runTest {
        val live = Live()
        live.permissions["r1"] = WidgetPendingAsk("r1", "s1")
        live.permissions["r2"] = WidgetPendingAsk("r2", "s1")
        val result = WidgetLiveActions(live).handle(actionFor("allowOnce") + ("requestID" to "r1"))
        assertEquals("accepted", result["status"])
        assertEquals(listOf("permission:r1:once"), live.calls)
        assertFalse(live.permissions.containsKey("r1"))
        assertTrue(live.permissions.containsKey("r2"))
    }

    @Test
    fun `B-SEC-183 a missing exact permission cannot approve another request`() = runTest {
        val live = Live()
        live.permissions["r1"] = WidgetPendingAsk("r1", "s1")
        for (payload in
            listOf(
                actionFor("allowOnce") + ("requestID" to "old"),
                actionFor("allowOnce", id = "tap2"),
                actionFor("allowOnce", id = "tap3") + ("requestID" to "r1") + ("sessionID" to "s2"),
            )) {
            assertEquals("staleRequest", WidgetLiveActions(live).handle(payload)["error"])
        }
        assertTrue(live.calls.isEmpty())
    }

    // ---- B-ANDR-121: call-only requests, malformed actions, once, stale profile/scope/run, location switch ----

    @Test
    fun `B-ANDR-121 a request that names only its call learns the tool and file`() {
        val calls = WidgetToolCalls()
        calls.note(
            Part(
                id = "p1",
                messageId = "msg_1",
                sessionId = "s1",
                type = PartType.Tool,
                rawType = "tool",
                callId = "call_1",
                toolName = "write",
                tool =
                    ToolState(
                        ToolStatus.Running,
                        input =
                            JsonObject(
                                mapOf(
                                    "filePath" to JsonPrimitive("/root/outside-test.txt"),
                                    "content" to JsonPrimitive("hi"),
                                )
                            ),
                    ),
            )
        )
        val request =
            PermissionRequest(
                id = RequestId("per_1"),
                sessionId = SessionId("s1"),
                permission = "external_directory",
                patterns = persistentListOf("/root/*"),
                metadata = JsonObject(emptyMap()),
                always = persistentListOf(),
                tool = PermissionTool(MessageId("msg_1"), "call_1"),
                canAlwaysAllow = false,
                message = null,
            )
        val call = calls.of(request)
        assertEquals("write", call?.tool)
        assertEquals("/root/outside-test.txt", call?.target)
        assertNull(calls.of(request.copy(tool = null)))
    }

    @Test
    fun `B-ANDR-121 malformed actions cannot enter background dispatch`() = runTest {
        assertNull(WidgetTap.from(mapOf("id" to "tap", "action" to "stop")))
        assertNull(WidgetTap.from(mapOf("id" to "tap", "action" to "delete", "profileID" to "p", "scopeToken" to "s")))
        assertNull(WidgetTap.from(actionFor("stop") + ("expectedStartedAt" to -1L)))
        assertNull(WidgetTap.from(actionFor("steer") + ("text" to "x".repeat(8001))))
        val live = Live()
        val result = WidgetLiveActions(live).handle(mapOf("id" to "tap", "action" to "stop"))
        assertEquals(mapOf("id" to "", "action" to "", "status" to "rejected", "error" to "invalidRequest"), result)
        assertTrue(live.calls.isEmpty())
    }

    @Test
    fun `B-ANDR-121 the same action is dispatched once and its result is remembered`() = runTest {
        val live = Live()
        val actions = WidgetLiveActions(live)
        assertEquals("accepted", actions.handle(actionFor("stop"))["status"])
        assertEquals("accepted", actions.handle(actionFor("stop"))["status"])
        assertEquals(listOf("stop:s1"), live.calls)
    }

    @Test
    fun `B-ANDR-121 a stale profile or scope never stops a conversation`() = runTest {
        val live = Live()
        assertEquals("staleRequest", WidgetLiveActions(live).handle(actionFor("stop") + ("profileID" to "other"))["error"])
        assertEquals("staleRequest", WidgetLiveActions(live).handle(actionFor("stop") + ("scopeToken" to "old"))["error"])
        assertTrue(live.calls.isEmpty())
    }

    @Test
    fun `B-ANDR-121 an old run start never stops a replacement run`() = runTest {
        val live = Live()
        assertEquals("staleRequest", WidgetLiveActions(live).handle(actionFor("stop") + ("expectedStartedAt" to 1L))["error"])
        assertEquals("staleRequest", WidgetLiveActions(live).handle(actionFor("stop") + ("sessionID" to "idle"))["error"])
        assertTrue(live.calls.isEmpty())
    }

    @Test
    fun `B-ANDR-121 a location switch revokes existing launcher actions`() = runTest {
        val live = Live()
        val actions = WidgetLiveActions(live)
        live.scope = "scope-after-directory-change"
        assertEquals("staleRequest", actions.handle(actionFor("stop"))["error"])
        assertTrue(live.calls.isEmpty())
    }
}
