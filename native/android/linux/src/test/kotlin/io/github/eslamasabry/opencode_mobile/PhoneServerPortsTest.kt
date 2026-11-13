package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneServerPortsTest {
    @Test
    fun `B-232 the dev build takes its own port, not the installed app's`() {
        assertEquals(4102, PhoneServerPorts.default("io.github.eslamasabry.opencode_mobile.native.dev", 4102))
        // Without the build's setting the package still keeps it off 4098.
        assertEquals(4102, PhoneServerPorts.default("io.github.eslamasabry.opencode_mobile.native.dev", null))
        assertEquals(4100, PhoneServerPorts.default("io.github.eslamasabry.opencode_mobile.native", 4100))
        assertEquals(4098, PhoneServerPorts.default("io.github.eslamasabry.opencode_mobile", 4098))
        // A setting outside the phone server's ports is not taken.
        assertEquals(4098, PhoneServerPorts.default("io.github.eslamasabry.opencode_mobile", 80))
    }

    @Test
    fun `B-232 a dev build that saved 4098 before the fix reads its own port`() {
        assertEquals(4102, PhoneServerPorts.saved(4098, default = 4102))
        assertEquals(4098, PhoneServerPorts.saved(4098, default = 4098))
        assertEquals(4100, PhoneServerPorts.saved(null, default = 4100))
    }

    @Test
    fun `B-239 a server that once moved still reports the port it runs on`() {
        assertEquals(4101, PhoneServerPorts.saved(4101, default = 4100))
        assertEquals(4103, PhoneServerPorts.saved(4103, default = 4098))
    }

    @Test
    fun `B-239 B-119 every start tries the build's own port first, so a moved server comes back home`() {
        assertEquals(4098, PhoneServerPorts.candidates(4098).first())
        assertEquals(4100, PhoneServerPorts.candidates(4100).first())
        assertEquals(4102, PhoneServerPorts.candidates(4102).first())
    }

    @Test
    fun `B-027 a held port moves to a spare one before another build's default`() {
        val installed = PhoneServerPorts.candidates(4098)
        assertEquals(4101, installed[1])
        assertTrue(installed.indexOf(4100) > installed.indexOf(4199))
        assertTrue(installed.indexOf(4102) > installed.indexOf(4199))
        val preview = PhoneServerPorts.candidates(4100)
        assertEquals(4101, preview[1])
        assertTrue(preview.indexOf(4102) > preview.indexOf(4199))
        // Every phone server port is still tried once.
        assertEquals((listOf(4098) + (4100..4199)).sorted(), installed.sorted())
        assertEquals(100, preview.size)
    }
}
