package io.github.eslamasabry.opencode_mobile.telemetry

import io.github.eslamasabry.oc.engine.analytics.product.ProductScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every screen view names its page from the closed list, in the `screen` dimension as well as Analytics' screen name. */
class ScreenViewParamsTest {
    @Test
    fun `B-JOURNEY-040 a screen view carries the screen dimension and the screen name, both the same closed word`() {
        val closed = ProductScreen.entries.map { it.wire }.toSet()
        for (route in listOf("ChatRoute(profile=p_secret)", "ChatsRoute.Home", "ServersRoute.Edit", "Mystery", "")) {
            val word = ProductScreen.of(route).wire
            val params = screenViewParams(word)
            assertEquals(word, params["screen"])
            assertEquals(word, params["screen_name"])
            assertTrue(word in closed, route)
        }
    }
}
