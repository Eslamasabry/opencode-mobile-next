package io.github.eslamasabry.opencode_mobile.migration

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.profiles.StoredProfileJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `test/oc1_profile_upgrade_test.dart` (B-MIGR-002) on the codec the native app reads the adopted `oc.profiles` rows
 * with: a row written by the Flutter app, read natively, saved and read again ("through saves" and "after restart").
 */
class Oc1ProfileUpgradeTest {
    private fun legacy(flavor: String? = null, backend: String? = null): Map<String, Any?> =
        buildMap {
            put("id", "phone")
            put("name", "This phone")
            put("baseUrl", "http://127.0.0.1:4096")
            flavor?.let { put("flavor", it) }
            backend?.let { put("backend", it) }
        }

    private fun roundTrip(profile: StoredProfile): StoredProfile = assertNotNull(StoredProfileJson.decode(StoredProfileJson.encode(profile)))

    @Test
    fun `B-MIGR-002 old managed metadata keeps its migration requirement through saves`() {
        for (old in listOf(null, "v1", "unknown")) {
            val profile = assertNotNull(StoredProfileJson.decode(legacy(flavor = old)))
            assertTrue(profile.requiresOpenCode2Migration, "flavor $old")
            assertEquals("v2", StoredProfileJson.encode(profile)["flavor"], "every profile is written as the supported protocol")
            val restored = roundTrip(profile)
            assertTrue(restored.requiresOpenCode2Migration, "flavor $old")
            assertEquals(ProfileId("phone"), restored.id)
            assertEquals("http://127.0.0.1:4096", restored.baseUrl)
        }
    }

    @Test
    fun `B-MIGR-002 a completed migration and a supported profile stay completed`() {
        val done = assertNotNull(StoredProfileJson.decode(legacy(flavor = "v1"))).copy(requiresOpenCode2Migration = false)
        assertFalse(roundTrip(done).requiresOpenCode2Migration)
        assertFalse(assertNotNull(StoredProfileJson.decode(legacy(flavor = "v2"))).requiresOpenCode2Migration)
    }

    @Test
    fun `B-MIGR-002 agent profiles never acquire an OpenCode migration requirement`() {
        for (backend in listOf("codex", "paseo")) {
            val profile = assertNotNull(StoredProfileJson.decode(legacy(backend = backend)))
            assertFalse(profile.requiresOpenCode2Migration, backend)
            assertTrue(profile.usesAgentSocket, backend)
            assertFalse(roundTrip(profile).requiresOpenCode2Migration, backend)
        }
    }

    @Test
    fun `B-MIGR-002 fresh profiles default to the supported protocol`() {
        val fresh = StoredProfile(id = ProfileId("new"), name = "Server", baseUrl = "https://example.com")
        assertEquals(BackendKind.OpenCode2, fresh.backend)
        assertFalse(fresh.requiresOpenCode2Migration)
        assertEquals("v2", StoredProfileJson.encode(fresh)["flavor"])
        assertFalse(roundTrip(fresh).requiresOpenCode2Migration)
    }

    @Test
    fun `B-MIGR-002 copied history stays pending after a restart until it is confirmed`() {
        val profile = assertNotNull(StoredProfileJson.decode(legacy(flavor = "v1")))
            .copy(requiresOpenCode2Migration = false, openCode2HistoryPending = true)
        val restored = roundTrip(profile)
        assertEquals(profile.id, restored.id)
        assertFalse(restored.requiresOpenCode2Migration)
        assertTrue(restored.openCode2HistoryPending)
        assertFalse(roundTrip(restored.copy(openCode2HistoryPending = false)).openCode2HistoryPending, "confirmed clears it")
    }
}
