package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/** B-620: Open action on automation notifications routes to the chat or servers page cleanly. */
class AutomationAlertEntryTest {
    private val t0 = Instant.fromEpochMilliseconds(1_000L)
    private val p1 = ProfileId("p1")
    private val world = EntryWorld(
        bootstrap = BootstrapPhase.Ready,
        gateOpen = true,
        isAndroid = true,
        activeProfileId = p1,
        savedProfileIds = setOf(p1),
        links = mapOf(p1 to LinkState.Ready),
    )

    @Test
    fun `B-620 parser accepts automation alert kinds and decider opens chat or servers`() {
        val msgFields = IntentFields(
            strings = mapOf(
                EntryExtras.ALERT_KIND to "automation_message",
                EntryExtras.ALERT_PROFILE_ID to "p1",
                EntryExtras.ALERT_SESSION_ID to "ses_42",
            ),
        )
        val parsedMsg = EntryIntentParser.parse(msgFields, EntryOrigin.Internal, t0).single()
        val msgOpen = assertIs<EntryRequest.AlertOpen>(parsedMsg)
        assertEquals(AlertKind.AutomationMessage, msgOpen.kind)
        val goChat = assertIs<EntryDecision.Go>(EntryDecider.decide(msgOpen, world, t0))
        assertEquals(EntryTarget.Chat(ConversationRef(p1, SessionId("ses_42"))), goChat.target)

        val failOpen = EntryRequest.AlertOpen(
            kind = AlertKind.AutomationFailed,
            profileId = p1,
            sessionId = SessionId("ses_42"),
            monitorToken = null,
            reply = false,
            receivedAt = t0,
        )
        val goFail = assertIs<EntryDecision.Go>(EntryDecider.decide(failOpen, world, t0))
        assertEquals(EntryTarget.Chat(ConversationRef(p1, SessionId("ses_42")), landOn = LandOn.Failure), goFail.target)

        val serverOnly = EntryRequest.AlertOpen(
            kind = AlertKind.AutomationPaused,
            profileId = p1,
            sessionId = SessionId("automation:aut_1"),
            monitorToken = null,
            reply = false,
            receivedAt = t0,
        )
        val goServers = assertIs<EntryDecision.Go>(EntryDecider.decide(serverOnly, world, t0))
        assertEquals(EntryTarget.Servers(null), goServers.target)
    }
}
