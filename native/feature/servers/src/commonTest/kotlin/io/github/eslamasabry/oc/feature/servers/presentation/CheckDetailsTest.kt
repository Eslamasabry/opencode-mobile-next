package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.model.ServerOrigin
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class CheckDetailsTest {
    private val origin = ServerOrigin("http", "192.0.2.10", 4096)

    @Test
    fun `a timed out check says the address, what was tried and what happened in words`() {
        val text = checkDetailsText(origin, Failure.TimedOut())
        assertContains(text, "Address: 192.0.2.10:4096")
        assertContains(text, "Tried: a connection over http")
        assertContains(text, "the server did not answer in time")
    }

    @Test
    fun `a refused connection reads as words, not a class name`() {
        val text = checkDetailsText(origin, Failure.Network(NetworkWhy.Refused))
        assertContains(text, "nothing accepted the connection")
        assertFalse(text.contains("Exception"))
    }

    @Test
    fun `no address means no address line`() {
        assertFalse(checkDetailsText(null, Failure.TimedOut()).contains("Address"))
    }
}
