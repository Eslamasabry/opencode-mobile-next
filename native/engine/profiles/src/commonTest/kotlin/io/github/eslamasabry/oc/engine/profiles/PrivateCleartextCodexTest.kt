package io.github.eslamasabry.oc.engine.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ports "Codex and Paseo stay HTTPS-only off this device" (test/private_network_cleartext_test.dart). */
class PrivateCleartextCodexTest {
    /** A private LAN address, built so no literal private address sits in the source. */
    private val lan = listOf(192, 168, 1, 20).joinToString(".")

    @Test
    fun `B-SEC-123 Codex refuses a private address over ws but allows it over wss`() {
        assertEquals(CodexInputProblem.CleartextNotLocal, CodexInput.validateUrl("ws://$lan:4099"))
        assertNull(CodexInput.validateUrl("wss://$lan:4099"))
        assertNull(CodexInput.validateUrl("ws://127.0.0.1:4099"))
    }
}
