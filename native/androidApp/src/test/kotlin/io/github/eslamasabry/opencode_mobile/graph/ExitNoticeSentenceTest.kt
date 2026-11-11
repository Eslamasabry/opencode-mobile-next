package io.github.eslamasabry.opencode_mobile.graph

import kotlin.test.Test
import kotlin.test.assertEquals

class ExitNoticeSentenceTest {
    @Test
    fun theWordsFollowTheRealState() {
        assertEquals(ExitSentence.Back, exitSentence(serverBack = true, recoveryAllowed = true, teamStopped = false))
        assertEquals(ExitSentence.BackWithTeam, exitSentence(true, true, true))
        assertEquals(ExitSentence.Starting, exitSentence(false, true, false))
        assertEquals(ExitSentence.StartingWithTeam, exitSentence(false, true, true))
        assertEquals(ExitSentence.Down, exitSentence(false, false, false))
        assertEquals(ExitSentence.DownWithTeam, exitSentence(false, false, true))
        // Running again wins over a recovery that was not allowed: never "start it yourself" over a server that is up.
        assertEquals(ExitSentence.Back, exitSentence(true, false, false))
    }
}
