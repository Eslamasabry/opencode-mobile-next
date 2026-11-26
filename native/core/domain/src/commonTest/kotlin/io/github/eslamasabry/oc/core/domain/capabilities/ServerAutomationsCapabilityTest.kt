package io.github.eslamasabry.oc.core.domain.capabilities

import kotlin.test.Test
import kotlin.test.assertEquals

/** B-605 / B-AUTO-001: capability flag for OpenCode 2.4+ server automations exists and defaults to false. */
class ServerAutomationsCapabilityTest {
    @Test
    fun `B-AUTO-001 ServerCapabilities exposes serverAutomations defaulting to false`() {
        assertEquals(false, ServerCapabilities().flags()["serverAutomations"])
        assertEquals(false, ServerCapabilities.Unknown.flags()["serverAutomations"])
    }
}
