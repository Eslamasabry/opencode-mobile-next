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
}
