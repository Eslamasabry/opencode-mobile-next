package io.github.eslamasabry.oc.engine.lifecycle

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of test/phone_own_server_icon_test.dart (emulator QA F14). */
class PhoneOwnServerTest {
    private fun at(id: String, url: String, backend: PhoneServerBackend = PhoneServerBackend.OpenCode) =
        PhoneServerCandidate(id, url, backend)

    @Test
    fun `B-NAV-040 only the phone's own servers count as this phone`() {
        val android = PhoneHostSupport.Android
        assertTrue(PhoneOwnServers.isPhoneOwnServer(at("in-app", PhoneOwnServers.IN_APP_URL), android))
        assertTrue(PhoneOwnServers.isPhoneOwnServer(at("termux", PhoneOwnServers.TERMUX_URL), android))
        assertFalse(PhoneOwnServers.isPhoneOwnServer(at("forwarded", "http://127.0.0.1:4123"), android))
        assertFalse(PhoneOwnServers.isPhoneOwnServer(at("local", "http://localhost:4096"), android))
        assertFalse(PhoneOwnServers.isPhoneOwnServer(at("lan", "https://laptop.example"), android))
    }

    @Test
    fun `B-NAV-040 the agent daemon on this phone is the phone's, other places draw every server as a server`() {
        val daemon = at("agents", "ws://localhost:6767", PhoneServerBackend.Paseo)
        assertTrue(PhoneOwnServers.isPhoneOwnServer(daemon, PhoneHostSupport.Android))
        assertFalse(PhoneOwnServers.isPhoneOwnServer(at("agents2", "ws://localhost:6768", PhoneServerBackend.Paseo), PhoneHostSupport.Android))
        assertFalse(PhoneOwnServers.isPhoneOwnServer(at("https-in-app", "https://127.0.0.1:4097"), PhoneHostSupport.Android))
        assertFalse(PhoneOwnServers.isPhoneOwnServer(at("in-app", PhoneOwnServers.IN_APP_URL), PhoneHostSupport.Elsewhere))
        assertFalse(PhoneOwnServers.isPhoneOwnServer(daemon, PhoneHostSupport.Elsewhere))
    }
}
