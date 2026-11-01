package io.github.eslamasabry.oc.core.model

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.WireJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelTest {
    @Test
    fun `server profile round-trips through WireJson with the Dart backend names`() {
        val profile =
            ServerProfile(
                ProfileId("p1"), "Laptop", BackendKind.OpenCode2, ServerOrigin("http", "100.64.1.2", 4096),
                ProfileTrust(null, 1L), managedOnPhone = false, username = "opencode",
            )
        val json = WireJson.encodeToString(ServerProfile.serializer(), profile)
        assertTrue("\"backend\":\"openCode\"" in json, json)
        assertEquals(profile, WireJson.decodeFromString(ServerProfile.serializer(), json))
    }

    @Test
    fun `B-SEC-153 an origin prints like Dart serverOrigin, brackets around IPv6`() {
        assertEquals("http://example.com:80", ServerOrigin("http", "example.com", 80).toString())
        assertEquals("http://[::1]:4098", ServerOrigin("http", "::1", 4098).toString())
    }

    @Test
    fun `stream status is a closed set that can carry the stopping failure`() {
        val all: List<StreamStatus> =
            listOf(
                StreamStatus.Connecting, StreamStatus.Connected, StreamStatus.Reconnecting(2, 5_000L),
                StreamStatus.Disconnected(7, null), StreamStatus.WaitingForNetwork, StreamStatus.Suspended,
                StreamStatus.Stopped(Failure.Net.Incompatible(Failure.Net.Incompatible.Kind.LegacyOpenCode1)),
            )
        val names =
            all.map {
                when (it) {
                    StreamStatus.Connecting -> "connecting"
                    StreamStatus.Connected -> "connected"
                    is StreamStatus.Reconnecting -> "reconnecting"
                    is StreamStatus.Disconnected -> "disconnected"
                    StreamStatus.WaitingForNetwork -> "waiting"
                    StreamStatus.Suspended -> "suspended"
                    is StreamStatus.Stopped -> "stopped"
                }
            }
        assertEquals(7, names.toSet().size)
        assertEquals(NetworkState.Online.Unknown, NetworkState.Unknown.online)
    }

    @Test
    fun `a part renders exactly like Dart isRenderable`() {
        fun part(type: PartType, text: String = "", synthetic: Boolean = false) =
            Part("p", "m", "s", type, type.name.lowercase(), text = text, synthetic = synthetic)
        assertTrue(part(PartType.Text, "hi").isRenderable)
        assertFalse(part(PartType.Text, "  ").isRenderable)
        assertFalse(part(PartType.Text, "hi", synthetic = true).isRenderable)
        assertTrue(part(PartType.Tool).isRenderable)
        assertFalse(part(PartType.Notice).isRenderable)
    }
}
