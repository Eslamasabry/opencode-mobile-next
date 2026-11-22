package io.github.eslamasabry.oc.ui.gate.flicker

import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogoRules
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * The rules on the loader every test scene wears (KitUiTest.setContent installs it): a known agent
 * finds a mark the kit accepts, an unknown id finds none, and the mark holds no web address.
 */
class DeviceLikeLogosTest {
    @Test
    fun `B-FLICKER-013 the test scenes' logo loader gives every known agent a mark the kit accepts`() = runTest {
        for (id in listOf("opencode", "claude", "codex", "antigravity", "paseo")) {
            assertNotNull(
                KitProviderLogoRules.load(DeviceLikeLogos, id, agent = false),
                "no mark the kit accepts for $id",
            )
        }
    }

    @Test
    fun `B-FLICKER-014 the test scenes' logo loader holds no web address`() = runTest {
        val svg = DeviceLikeLogos.svg("claude", false)
        assertNotNull(svg, "claude is a known agent")
        assertFalse(svg.contains("://"), "the mark holds a web address")
    }

    @Test
    fun `B-FLICKER-015 an unknown id gets no mark`() = runTest {
        assertNull(DeviceLikeLogos.svg("nobody", false))
    }
}
