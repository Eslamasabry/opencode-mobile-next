package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.engine.settings.teaching.ConversationNudgeWatcher
import io.github.eslamasabry.oc.feature.chat.nudges.ChatNudgeInputs
import io.github.eslamasabry.oc.feature.chat.nudges.NoChatNudges
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentMapOf

/**
 * S28 story 14 (Flutter nudge_slot.dart, B-NAV-039): the chat hands the nudge rules its facts as the engine reads
 * them; without a bound registry the chat shows no nudge at all.
 */
class S28NudgeInputsTest {
    @Test
    fun `B-NAV-S28-10 the chat's facts reach the nudge rules as they are`() {
        val facts =
            ChatNudgeInputs(
                busy = false,
                hasReply = true,
                pendingPermissions = persistentMapOf("r1" to "bash"),
                approvalsAvailable = true,
                runChangedFiles = true,
                reviewAvailable = true,
                contextPercent = 85,
                compactAvailable = true,
                suppressed = true,
            ).facts()
        assertTrue(facts.hasAssistantReply && facts.approvalsAvailable && facts.runChangedFiles)
        assertEquals(mapOf("r1" to "bash"), facts.pendingPermissions)
        assertEquals(0.85, facts.contextUsage)
        assertTrue(facts.contextUsage!! >= ConversationNudgeWatcher.CONTEXT_THRESHOLD)
        assertTrue(facts.suppressed)
        // "You can leave" needs a finished-run notice that would reach the person: never claimed here.
        assertFalse(facts.finishedRunNotificationsReady)
        assertNull(ChatNudgeInputs(busy = true, hasReply = false).facts().contextUsage)
    }

    @Test
    fun `B-NAV-S28-11 without the app's registry there is no nudge`() {
        var got: Any? = "unset"
        suspend { NoChatNudges().registry() }.startCoroutine(Continuation(EmptyCoroutineContext) { got = it.getOrThrow() })
        assertNull(got)
    }
}
