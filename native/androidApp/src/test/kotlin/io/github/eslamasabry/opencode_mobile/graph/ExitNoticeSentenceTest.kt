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

    @Test
    fun stillDownLeadsToThisPhoneAndKeepsKeepItRunning() {
        val open = io.github.eslamasabry.oc.core.domain.platform.AppNoticeAction.OpenThisPhone
        val keep = io.github.eslamasabry.oc.core.domain.platform.AppNoticeAction.KeepRunning
        assertEquals(listOf(open, keep), exitActions(ExitSentence.Down, offersKeepAlive = true))
        assertEquals(listOf(open, keep), exitActions(ExitSentence.DownWithTeam, offersKeepAlive = true))
        // A crash: nothing about the phone's settings, but the way to start it again stays.
        assertEquals(listOf(open), exitActions(ExitSentence.Down, offersKeepAlive = false))
        // Running or starting by itself: nothing to start.
        assertEquals(listOf(keep), exitActions(ExitSentence.Back, offersKeepAlive = true))
        assertEquals(listOf(keep), exitActions(ExitSentence.StartingWithTeam, offersKeepAlive = true))
        assertEquals(emptyList<io.github.eslamasabry.oc.core.domain.platform.AppNoticeAction>(), exitActions(ExitSentence.Back, offersKeepAlive = false))
    }
}
