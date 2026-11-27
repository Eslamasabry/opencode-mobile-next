package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.install.AgentNeedRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the phone reads against this app's pins. A copy newer than its pin (the agent updated outside the app, a
 * release moved a pin back) was listed for "Update" (PhoneChoiceInventory) and put in the automatic update's behind
 * set (AppPhoneAgents.updateFacts), because the versions were only compared as text. Only an older copy is behind.
 */
class PhonePinOrderTest {
    private val catalog = AgentCatalog.builtIn
    private val before = "0.0.1"

    private fun agent(id: String) = catalog.byId(AgentId(id))!!

    private fun pin(id: String): String = agent(id).recipe!!.version

    /** A version after [pin], whatever the pin is when this runs. */
    private fun after(pin: String): String = "${pin.removePrefix("v").substringBefore('.').toInt() + 1}.0.0"

    private fun found(version: String?, pinned: Boolean = false) =
        PhoneAgentFacts.Fact(installed = pinned, found = version, signedIn = null)

    @Test
    fun `B-AGENT-UPD-026 the automatic update's behind set holds only copies older than their pin`() {
        val installed = listOf("claude", "codex", "antigravity", "gemini", "qwen", "kilo").map(::agent)
        val kept =
            mapOf(
                "claude" to found(before),
                "codex" to found(after(pin("codex"))),
                "antigravity" to found(after(pin("antigravity"))),
                "gemini" to found(pin("gemini"), pinned = true),
                "qwen" to found("1.5.x"),
                "kilo" to found(null, pinned = true),
            )

        assertEquals(setOf(AgentId("claude")), PhonePinOrder.behind(installed, kept))
        // Newer than the pin, or a version that cannot be read: never moved, not even for an install record.
        assertEquals(
            setOf(AgentId("codex"), AgentId("antigravity"), AgentId("qwen")),
            PhonePinOrder.ahead(installed, kept),
        )
    }

    @Test
    fun `B-AGENT-UPD-027 the phone's inventory lists an agent only when its copy is older than the pin`() {
        val codex = agent("codex")
        val pin = pin("codex")

        assertTrue(PhoneChoiceRules.agentOffered(codex, before, changed = false), "an older copy is an update")
        assertFalse(PhoneChoiceRules.agentOffered(codex, pin, changed = false), "the pin itself")
        assertFalse(PhoneChoiceRules.agentOffered(codex, "v$pin", changed = false), "the pin with a v")
        assertFalse(PhoneChoiceRules.agentOffered(codex, after(pin), changed = false), "a newer copy")
        assertFalse(PhoneChoiceRules.agentOffered(codex, "unknown", changed = false), "an unreadable version")
        // Issue 147 stays: the pinned version from another artifact is installed again. A newer copy never is.
        assertTrue(PhoneChoiceRules.agentOffered(codex, pin, changed = true))
        assertFalse(PhoneChoiceRules.agentOffered(codex, after(pin), changed = true))
        assertFalse(PhoneChoiceRules.agentOffered(codex, "unknown", changed = true))

        // OpenCode on the phone: the same rule.
        assertTrue(PhoneChoiceRules.openCodeOffered("1.9.9", "2.0.0"))
        assertFalse(PhoneChoiceRules.openCodeOffered("2.0.0", "2.0.0"))
        assertFalse(PhoneChoiceRules.openCodeOffered("2.0.0", "1.9.9"))
        assertFalse(PhoneChoiceRules.openCodeOffered("unknown", "2.0.0"))
        assertFalse(PhoneChoiceRules.openCodeOffered(null, "2.0.0"))
    }

    @Test
    fun `B-AGENT-UPD-028 a newer shared part is left alone unless this app cannot run it`() {
        val tested = listOf("0.16.1", "0.17.3")
        fun shared(helper: String?, node: String?, helperPin: String = "0.17.3") =
            PhoneChoiceRules.shared(helper, node, helperPin, "v24.22.3", tested)
        val none = SharedNeeds(helper = null, node = null)
        val withUpdates = AgentNeedRule.WithAnyUpdate

        // As before: on the pins nothing is listed; a part that is not there, or older, comes with the updates.
        assertEquals(none, shared("0.17.3", "v24.22.3"))
        assertEquals(SharedNeeds(withUpdates, withUpdates), shared(null, null))
        assertEquals(SharedNeeds(withUpdates, withUpdates), shared("0.16.1", "v22.12.0"))
        // As before: an older helper outside the tested list cannot run, so it moves whatever is chosen.
        assertEquals(SharedNeeds(AgentNeedRule.Always, AgentNeedRule.Always), shared("0.15.0", "v22.12.0"))

        // A newer Node.js runs as it is: it was listed as "v25.0.0 → v24.22.3".
        assertEquals(none, shared("0.17.3", "v25.0.0"))
        // A newer helper this app has tested (a pin moved back) runs as it is.
        assertEquals(none, shared("0.17.3", "v24.22.3", helperPin = "0.16.1"))
        // A newer helper outside the tested list cannot run: the one required case, in the needed group.
        assertEquals(SharedNeeds(AgentNeedRule.Always, null), shared("0.18.0", "v24.22.3"))
        // A version that cannot be read moves nothing.
        assertEquals(none, shared("unknown", "vunknown"))
    }
}
