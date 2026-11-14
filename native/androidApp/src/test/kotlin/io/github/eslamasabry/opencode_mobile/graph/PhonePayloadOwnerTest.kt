package io.github.eslamasabry.opencode_mobile.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner's phone, 3.0.0+10030: a Claude Code conversation on This phone opened onto "Couldn't open this conversation"
 * (Refused StaleScope, no details), for good. Since "one This phone" the home's agent rows carry the saved helper
 * `<owner>-agents`, and the payload lease checked that helper as a phone of its own (`oc.builtinServerOwner` must be
 * the helper, its secret under `oc.agentHostSecret.<owner>-agents`), so every history read was refused.
 */
class PhonePayloadOwnerTest {
    private val phone = "phone1"
    private val helper = "phone1-agents"
    private val saved = setOf(phone, helper)

    private fun admits(owner: PhoneAgentRules.PayloadOwner?, builtin: String? = phone, ids: Set<String> = saved) =
        PhoneAgentRules.payloadOwnerHolds(owner, builtin, ids)

    @Test
    fun `PAYLOAD-001 the saved helper's history read is taken for its phone, with the secret its starter writes`() {
        val owner = PhoneAgentRules.payloadOwner(helper, transientOwner = null, savedOwner = phone, builtinOwner = phone, saved = saved)
        // The phone is checked against the app's own OpenCode; the secret and helper status are read under the owner
        // the helper was saved for (AppLocalAgentHostStarter: oc.agentHostSecret.<owner>, profile id <owner>-agents).
        assertEquals(PhoneAgentRules.PayloadOwner(phone = phone, helper = phone), owner)
        assertTrue(admits(owner))
        assertTrue(PhoneAgentRules.payloadSecretMatches("a".repeat(64), "a".repeat(64)))
    }

    @Test
    fun `PAYLOAD-002 a helper saved for another owner of the same phone reads that owner's secret`() {
        val owner = PhoneAgentRules.payloadOwner("alias-agents", null, savedOwner = "alias", builtinOwner = phone, saved = setOf(phone, "alias-agents"))
        assertEquals(PhoneAgentRules.PayloadOwner(phone = phone, helper = "alias"), owner)
        assertTrue(admits(owner, ids = setOf(phone, "alias-agents")))
    }

    @Test
    fun `PAYLOAD-003 the transient phone alias still passes as before`() {
        val owner = PhoneAgentRules.payloadOwner("$phone.agents", transientOwner = phone, savedOwner = null, builtinOwner = phone, saved = saved)
        assertEquals(PhoneAgentRules.PayloadOwner(phone, phone), owner)
        assertTrue(admits(owner))
    }

    @Test
    fun `PAYLOAD-004 a really stale payload is still refused`() {
        // Another phone's helper: its phone is not this app's own OpenCode.
        val other = PhoneAgentRules.payloadOwner("other-agents", null, savedOwner = "other", builtinOwner = phone, saved = setOf(phone, "other", "other-agents"))
        assertEquals(PhoneAgentRules.PayloadOwner(phone, "other"), other)
        assertFalse(admits(other, builtin = "other-phone"))
        // The phone profile was deleted.
        val orphan = PhoneAgentRules.payloadOwner(helper, null, savedOwner = phone, builtinOwner = phone, saved = setOf(helper))
        assertFalse(admits(orphan, ids = setOf(helper)))
        // An alias that is neither saved, a helper nor transient has no owner at all.
        assertNull(PhoneAgentRules.payloadOwner("stranger", null, null, phone, saved))
        // A helper id whose saved owner does not match it is not the helper.
        assertEquals(PhoneAgentRules.PayloadOwner(helper, helper), PhoneAgentRules.payloadOwner(helper, null, savedOwner = "someone", builtinOwner = phone, saved = saved))
        assertFalse(admits(PhoneAgentRules.payloadOwner(helper, null, savedOwner = "someone", builtinOwner = phone, saved = saved)))
        // A wrong or missing secret.
        assertFalse(PhoneAgentRules.payloadSecretMatches("a".repeat(64), "b".repeat(64)))
        assertFalse(PhoneAgentRules.payloadSecretMatches(null, "b".repeat(64)))
        assertFalse(PhoneAgentRules.payloadSecretMatches("", ""))
    }
}
