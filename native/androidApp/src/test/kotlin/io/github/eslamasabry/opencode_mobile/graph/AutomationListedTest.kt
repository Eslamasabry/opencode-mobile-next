package io.github.eslamasabry.opencode_mobile.graph

import kotlin.test.Test
import kotlin.test.assertEquals

class AutomationListedTest {
    @Test
    fun `W2-018 the phone agents helper is not listed as a second This phone under what runs by itself`() {
        val profiles = listOf("phone", "phone-helper", "paseo")
        val listed = automationListed(profiles) { it == "phone-helper" }
        assertEquals(listOf("phone", "paseo"), listed)
    }
}
