package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.requests.paseoPermission
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Faults 4 and 5 of the Paseo 0.15.3 device pass, from one real capture ([Paseo0153Frames] `deny*`): OpenCode 2 asked
 * to write shade-probe.txt, the answer was `{behavior: "deny"}`, the call failed "The user declined this tool call"
 * and the turn ended `turn_canceled`.
 */
class Paseo0153DenyTest {
    private val agent = Paseo0153Frames.denyAgentCreated.obj("agent")!!
    private val agentId = agent.str("id")!!

    @Test
    fun `fault 4 OpenCode 2's ask names its file only in the description, and that is the file the shade allows`() {
        val request = Paseo0153Frames.denyPermissionRequest.obj("request")!!
        val permission = paseoPermission(agentId, request)
        assertEquals("edit", permission.permission)
        assertEquals(listOf("shade-probe.txt"), permission.patterns)
        // The notification reads `filePath` for "Edit shade-probe.txt?" with Allow once / Reject (B-SEC-105).
        assertEquals(JsonPrimitive("shade-probe.txt"), permission.metadata["filePath"])
    }

    @Test
    fun `fault 4 a description that is prose is never read as the file`() {
        val request = Paseo0153Frames.denyPermissionRequest.obj("request")!!
        val prose =
            JsonObject(LinkedHashMap(request).apply { put("description", JsonPrimitive("Allow edits outside the project")) })
        val permission = paseoPermission(agentId, prose)
        assertNull(permission.metadata["filePath"])
        assertEquals(emptyList(), permission.patterns)
    }
}
