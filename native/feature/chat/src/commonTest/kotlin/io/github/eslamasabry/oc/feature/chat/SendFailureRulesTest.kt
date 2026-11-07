package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.feature.chat.presentation.SendFailureRules
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendFailureRulesTest {
    @Test
    fun `B-CHAT-131 B-063 a message refused for a missing project folder is said in its own words`() {
        assertTrue(SendFailureRules.folderMissing(Failure.NotFound(WireTag("LocationNotFoundError"))))
        assertFalse(SendFailureRules.folderMissing(Failure.NotFound(WireTag("SessionNotFoundError"))))
        assertFalse(SendFailureRules.folderMissing(Failure.NotFound()))
        assertFalse(SendFailureRules.folderMissing(Failure.Server(500)))
    }
}
