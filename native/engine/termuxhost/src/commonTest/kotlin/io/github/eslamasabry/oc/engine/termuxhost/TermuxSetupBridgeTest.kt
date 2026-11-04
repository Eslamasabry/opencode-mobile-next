package io.github.eslamasabry.oc.engine.termuxhost

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** test/termux_setup_bridge_test.dart. */
class TermuxSetupBridgeTest {
    private val channel = FakeTermuxChannel { _, _ -> ChannelReply.Value(null) }
    private val redactor = Redactor().apply { registerSecret("private-fixture-value") }
    private val bridge = TermuxBridge(true, channel, redactor, RecordingWait())

    @Test
    fun `B-LINUX-246 setup sends redacted metadata and substitutes the pinned native base`() = runTest {
        bridge.startSetup(
            jobId = "job-12",
            components =
                listOf(
                    TermuxBridge.SetupComponent(
                        id = "linux",
                        native = true,
                        script = "must not become the bootstrap",
                        version = "private-fixture-value",
                        data = mapOf("password" to "private-fixture-value"),
                    ),
                ),
            params = mapOf("opencode" to mapOf("runtime" to "opencode2")),
            texts = mapOf("title" to "private-fixture-value"),
        )
        val payload = channel.calls.single().second!!
        @Suppress("UNCHECKED_CAST")
        val component = (payload["components"] as List<Map<String, Any?>>).single()
        assertEquals(TermuxScripts.setupBaseScript, component["script"])
        assertEquals(Redactor.MASK, component["version"])
        assertEquals(emptyMap<String, Any?>(), component["data"])
        assertFalse(payload.containsKey("texts"))
        assertFalse(payload.toString().contains("private-fixture-value"))
    }

    @Test
    fun `B-LINUX-246 unsupported and credential parameters never dispatch`() = runTest {
        for (params in listOf(
            mapOf("opencode" to mapOf("password" to "private-fixture-value")),
            mapOf("opencode" to mapOf("runtime" to "unexpected")),
            mapOf("opencode" to mapOf("version" to "1.2.3; execute")),
            mapOf("unrecognised" to mapOf("value" to "x")),
        )) {
            val answer =
                bridge.startSetup("job-12", listOf(TermuxBridge.SetupComponent(id = "linux", native = true)), params)
            assertIs<TermuxAnswer.Failed>(answer)
        }
        assertTrue(channel.calls.isEmpty())
    }

    @Test
    fun `B-LINUX-246 step acknowledgement sends no arbitrary error text`() = runTest {
        bridge.completeSetupStep(
            jobId = "job-12",
            id = "start",
            ok = false,
            error = "private-fixture-value",
            version = "private-fixture-value",
        )
        assertEquals("completeSetupStep", channel.calls.single().first)
        assertFalse(channel.calls.single().second!!.containsKey("error"))
        assertFalse(channel.calls.single().second.toString().contains("private-fixture-value"))
    }
}
