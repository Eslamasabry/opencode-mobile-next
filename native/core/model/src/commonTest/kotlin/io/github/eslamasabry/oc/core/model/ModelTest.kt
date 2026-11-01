package io.github.eslamasabry.oc.core.model

import io.github.eslamasabry.oc.core.common.WireJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelTest {
    @Test
    fun `server profile round-trips through WireJson`() {
        val profile =
            ServerProfile(
                ProfileId("p1"), "Laptop", BackendKind.OpenCode2, ServerOrigin("http", "100.64.1.2", 4096),
                ProfileTrust(null, 1L), managedOnPhone = false, username = "opencode",
            )
        val json = WireJson.encodeToString(ServerProfile.serializer(), profile)
        assertEquals(profile, WireJson.decodeFromString(ServerProfile.serializer(), json))
    }
}
