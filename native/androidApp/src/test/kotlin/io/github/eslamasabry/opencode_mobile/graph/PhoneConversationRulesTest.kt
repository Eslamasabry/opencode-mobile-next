package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneConversationRulesTest {
    @Test
    fun `a row names the agent the daemon says runs it`() {
        val byModel = Session(id = SessionId("a"), model = "claude/opus")
        val bySelection = Session(id = SessionId("b"), selection = SessionSelection(ModelRef("codex", "gpt"), "", null, modelKnown = true, agentKnown = true))
        assertEquals("claude", PhoneConversationRules.agentOf(byModel)?.first)
        assertEquals("codex", PhoneConversationRules.agentOf(bySelection)?.first)
    }

    @Test
    fun `a row that does not say stays unmarked`() {
        assertNull(PhoneConversationRules.agentOf(Session(id = SessionId("c"))))
    }

    @Test
    fun `an installed agent with no saved source is adopted at start, nothing else is`() {
        assertTrue(PhoneConversationRules.shouldAdopt(available = true, needsRestart = false, installed = 1, alreadySaved = false))
        assertFalse(PhoneConversationRules.shouldAdopt(available = true, needsRestart = false, installed = 0, alreadySaved = false))
        assertFalse(PhoneConversationRules.shouldAdopt(available = false, needsRestart = false, installed = 1, alreadySaved = false))
        assertFalse(PhoneConversationRules.shouldAdopt(available = true, needsRestart = true, installed = 1, alreadySaved = false))
        assertFalse(PhoneConversationRules.shouldAdopt(available = true, needsRestart = false, installed = 1, alreadySaved = true))
    }
}
