package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.delivery.SendRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Paseo fault 2: a first message refused at once is "not sent, Try again", never "couldn't confirm" + Review. */
class FirstMessageRulesTest {
    private val ref = ConversationRef(ProfileId("paseo"), SessionId("draft-1"))

    private fun request(text: String) = SendRequest(ref, text)

    @Test
    fun theComputerIsUnreachableAndNothingWasSentSoTheWordsComeBack() {
        // What PromptDelivery returns when the link never came back and the server has no offline queue (Paseo).
        assertTrue(FirstMessageRules.notSent(Failure.Network(NetworkWhy.Offline), request("say hi")))
        assertTrue(FirstMessageRules.notSent(Failure.StorageRefused(), request("say hi")))
        // The earlier rule: refusals and unavailable features are not sent, a command included.
        assertTrue(FirstMessageRules.notSent(Failure.Unavailable(FeatureId("agentStart")), request("say hi")))
        assertTrue(FirstMessageRules.notSent(Failure.Unavailable(FeatureId("slashCommands")), request("/init")))
    }

    @Test
    fun whenInDoubtItStaysUnconfirmed() {
        assertFalse(
            FirstMessageRules.notSent(Failure.Refused(RefusalReason.DispatchUncertain), request("say hi"))
        )
        // A typed command may have run before its failure came back.
        assertFalse(FirstMessageRules.notSent(Failure.Network(NetworkWhy.Offline), request("/init")))
        assertFalse(FirstMessageRules.notSent(Failure.Network(NetworkWhy.Offline), request("!ls")))
        // A task sheet's literal words are a prompt even when they start with a slash.
        assertTrue(
            FirstMessageRules.notSent(
                Failure.Network(NetworkWhy.Offline),
                SendRequest(ref, "/init", promptOnly = true),
            )
        )
    }

    @Test
    fun `ONE-OP a phone agent's page opens only after its helper answered, a server's own at once`() {
        assertTrue(FirstMessageRules.opensAtOnce(startsOnPhoneAgents = false), "a server's conversation exists when made")
        assertFalse(FirstMessageRules.opensAtOnce(startsOnPhoneAgents = true), "a phone agent's does not exist until started")
    }

    @Test
    fun `SEND-STALE-2b a record of a phone agent's start is settled once its helper made the agent, never before`() {
        val aliases = mapOf("draft-1" to "real-1")
        val helper: (String) -> String = { aliases[it] ?: it }
        // Back while "Starting <agent>…" left the record; the agent was made: the next start in the folder goes on.
        assertTrue(FirstMessageRules.madeOnHelper("draft-1", helper))
        // Still a draft (its first message never reached the helper, or its answer is not known): it proves nothing.
        assertFalse(FirstMessageRules.madeOnHelper("draft-2", helper))
        // The record written before the conversation existed names none.
        assertFalse(FirstMessageRules.madeOnHelper("", helper))
        assertFalse(FirstMessageRules.madeOnHelper("draft-3") { "" })
    }
}
