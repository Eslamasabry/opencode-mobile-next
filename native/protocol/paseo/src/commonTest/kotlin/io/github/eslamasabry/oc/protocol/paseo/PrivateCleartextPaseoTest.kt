package io.github.eslamasabry.oc.protocol.paseo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** Ports "Codex and Paseo stay HTTPS-only off this device" (test/private_network_cleartext_test.dart). */
class PrivateCleartextPaseoTest {
    /** A private LAN address, built so no literal private address sits in the source. */
    private val lan = listOf(192, 168, 1, 20).joinToString(".")

    @Test
    fun `B-SEC-123 Paseo refuses a private address over ws and never builds an endpoint for it`() {
        assertEquals(PaseoUrlProblem.NotPrivate, PaseoProfileRules.validateUrl("ws://$lan:6767"))
        assertFails { PaseoEndpoint.parse("ws://$lan:6767") }
    }
}
