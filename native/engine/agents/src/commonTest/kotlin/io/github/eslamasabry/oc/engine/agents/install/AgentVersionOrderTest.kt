package io.github.eslamasabry.oc.engine.agents.install

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The order of an installed version against this app's pin. Before, the two were only compared as text ("another
 * version"), so a newer copy read as an update to make: "Version 0.172.0 → 0.171.0 · Update".
 */
class AgentVersionOrderTest {
    private fun order(installed: String?, pin: String?) = AgentVersions.order(installed, pin)

    @Test
    fun `B-AGENT-UPD-024 a version is older, the same, newer or unreadable against the pin`() {
        // installed > pin, and the other way round installed < pin. Parts are numbers: 0.10.0 is after 0.9.9.
        val newer =
            listOf(
                "0.172.0" to "0.171.0", "1.5.17" to "1.5.15", "2.0.0" to "1.9.9", "v22.14.0" to "v22.12.0",
                "0.10.0" to "0.9.9", "10.0.0" to "9.99.99", "v1.5.17" to "1.5.15", "1.5.17" to "v1.5.15",
                "1.0.99999999999999999999" to "1.0.9",
                // A pre-release is before its release; its parts order as numbers, then as text.
                "1.0.0" to "1.0.0-rc.1", "1.0.0-rc.2" to "1.0.0-rc.1", "1.0.0-rc.10" to "1.0.0-rc.9",
                "1.0.0-beta" to "1.0.0-alpha", "1.0.0-alpha.1" to "1.0.0-alpha", "1.0.0-alpha" to "1.0.0-1",
            )
        for ((installed, pin) in newer) {
            assertEquals(AgentVersionOrder.Newer, order(installed, pin), "$installed against the pin $pin")
            assertEquals(AgentVersionOrder.Older, order(pin, installed), "$pin against the pin $installed")
        }

        // installed == pin: a leading "v", space around it and build text after "+" do not make another version.
        val same =
            listOf(
                "1.5.15" to "1.5.15", "v1.5.15" to "1.5.15", "1.5.15" to "v1.5.15", "V24.22.3" to "v24.22.3",
                " 1.5.15\n" to "1.5.15", "1.5.15+build.7" to "1.5.15", "1.0.0-rc.1" to "v1.0.0-rc.1",
                "2" to "2.0.0", "2.1" to "2.1.0",
            )
        for ((installed, pin) in same) {
            assertEquals(AgentVersionOrder.Same, order(installed, pin), "$installed against the pin $pin")
        }

        // Not a version: nothing is known about the order, on either side.
        val unreadable =
            listOf(
                null, "", " ", "unknown", "not a version", "1.5.x", "1..5", ".1.5", "1.5.", "1.2.3.4", "v", "vv1.2.3",
                "1.2.3-", "1.2.3-rc..1", "-1.2.3", "1,2,3", "1.2." + "3".repeat(100),
            )
        for (text in unreadable) {
            assertEquals(AgentVersionOrder.Unparsable, order(text, "1.5.15"), "installed '$text'")
            assertEquals(AgentVersionOrder.Unparsable, order("1.5.15", text), "pin '$text'")
        }
        assertEquals(AgentVersionOrder.Unparsable, order("unknown", "unknown"), "the same text is still no version")
    }

    @Test
    fun `B-AGENT-UPD-025 the pin moves a copy only forward, and back only when the newer one cannot run`() {
        fun move(installed: String?, pin: String?) = AgentPinRule.move(installed, pin)

        // installed < pin: the update, as before. installed == pin and installed > pin: nothing to do.
        assertEquals(AgentPinMove.Update, move("0.171.0", "0.172.0"))
        assertEquals(AgentPinMove.None, move("0.172.0", "0.172.0"))
        assertEquals(AgentPinMove.None, move("v1.5.15", "1.5.15"))
        assertEquals(AgentPinMove.None, move("0.172.0", "0.171.0"))
        assertEquals(AgentPinMove.None, move("1.5.17", "1.5.15"))
        assertEquals(AgentPinMove.None, move("2.0.0", "1.9.9"))
        assertEquals(AgentPinMove.None, move("v22.14.0", "v22.12.0"))

        // A version that cannot be read never moves, whatever else is said about the copy.
        for (text in listOf(null, "", "unknown", "1.5.x")) {
            assertEquals(AgentPinMove.None, move(text, "1.5.15"), "installed '$text'")
            assertEquals(AgentPinMove.None, AgentPinRule.move(text, "1.5.15", runsAsIs = false), "installed '$text'")
            assertEquals(AgentPinMove.None, AgentPinRule.move(text, "1.5.15", reinstall = true), "installed '$text'")
        }
        assertEquals(AgentPinMove.None, move("1.5.15", "unknown"))

        // Issue 147: the same version from another artifact is installed again; a newer copy never is.
        assertEquals(AgentPinMove.Update, AgentPinRule.move("0.172.0", "0.172.0", reinstall = true))
        assertEquals(AgentPinMove.None, AgentPinRule.move("0.173.0", "0.172.0", reinstall = true))

        // The one case that goes back: a newer copy this app cannot run. It is Required, never an Update.
        assertEquals(AgentPinMove.Required, AgentPinRule.move("0.18.0", "0.17.3", runsAsIs = false))
        assertEquals(AgentPinMove.Update, AgentPinRule.move("0.16.1", "0.17.3", runsAsIs = false))
        assertEquals(AgentPinMove.None, AgentPinRule.move("0.17.3", "0.17.3", runsAsIs = false))
    }
}
