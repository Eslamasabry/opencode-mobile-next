package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** W4 gate (sweep-a P12): the phone's own server is edited by its name; its address and sign-in are the app's. */
class OwnPhoneEditorTest {
    private fun record(ownPhone: Boolean, needsSecret: Boolean = false) =
        ServerEditorRecord(
            ProfileId("p"), "This phone", "http://127.0.0.1:4100", "opencode", BackendKind.OpenCode2, "",
            hasSecret = true, needsSecret = needsSecret, tailscale = false,
            cleartextOrigin = null, certificatePin = null, certificateOrigin = null,
            ownPhone = ownPhone,
        )

    @Test
    fun `W4 the phone's own server shows no address, password or user name`() {
        val own = record(ownPhone = true)
        assertFalse(ServerEditorRules.showsAddress(own))
        assertFalse(ServerEditorRules.showsPassword(own))
        assertFalse(ServerEditorRules.showsUsername(own))
    }

    @Test
    fun `W4 a password the phone's server asks for again is still typed there`() {
        assertTrue(ServerEditorRules.showsPassword(record(ownPhone = true, needsSecret = true)))
    }

    @Test
    fun `W4 any other server and a new one keep every field`() {
        for (other in listOf(record(ownPhone = false), null)) {
            assertTrue(ServerEditorRules.showsAddress(other))
            assertTrue(ServerEditorRules.showsPassword(other))
            assertTrue(ServerEditorRules.showsUsername(other))
        }
    }
}
