package io.github.eslamasabry.opencode_mobile.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * After the update from the Flutter app, "This phone" kept the old in-app address (port 4097) while this app's server
 * came back on its own port, so the home said "Not answering" until the person tapped Restart OpenCode (owner's phone
 * and the emulator, 3.0.0+10020). The profile's address is now moved as soon as the app looks at its server.
 */
class PhoneProfileAddressTest {
    @Test
    fun `the Flutter in-app address moves to this app's port`() {
        assertEquals("http://127.0.0.1:4098", PhoneProfileAddress.adopted("http://127.0.0.1:4097", 4098))
        assertEquals("http://127.0.0.1:4098", PhoneProfileAddress.adopted("http://127.0.0.1:4097/", 4098))
        assertEquals("http://127.0.0.1:4101", PhoneProfileAddress.adopted("http://localhost:4097", 4101))
    }

    @Test
    fun `an address that is already right or is not the old in-app one is left alone`() {
        assertNull(PhoneProfileAddress.adopted("http://127.0.0.1:4098", 4098))
        assertNull(PhoneProfileAddress.adopted("http://192.168.1.20:4097", 4098), "a computer on the same port number")
        assertNull(PhoneProfileAddress.adopted("https://127.0.0.1:4097", 4098), "not the in-app scheme")
        assertNull(PhoneProfileAddress.adopted("http://127.0.0.1:4096", 4098), "another local server the person added")
        assertNull(PhoneProfileAddress.adopted("http://127.0.0.1:4097", 4097), "a build that keeps the old port")
    }
}
