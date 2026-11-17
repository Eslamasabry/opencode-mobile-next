package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadRules
import io.github.eslamasabry.oc.feature.chats.presentation.HomeLineWinner
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStage
import kotlin.test.Test
import kotlin.test.assertEquals

/** B-HOME-210: one place for what the phone is doing; when two compete, one wins and nothing stacks. */
class HomeDownloadPlaceTest {
    private fun fact(phase: HomeDownloadPhase) = HomeDownloadFact(HomeDownloadKind.OpenCode, phase)

    @Test
    fun `B-HOME-210 the stage wins over a waiting or failed update, a running update wins over the stage`() {
        val helper = HomeStage.Helper
        assertEquals(HomeLineWinner.Stage, HomeDownloadRules.placeWins(helper, fact(HomeDownloadPhase.Waiting)))
        assertEquals(HomeLineWinner.Stage, HomeDownloadRules.placeWins(helper, fact(HomeDownloadPhase.Failed)))
        val running = fact(HomeDownloadPhase.Running(10))
        assertEquals(HomeLineWinner.Download, HomeDownloadRules.placeWins(helper, running))
    }

    @Test
    fun `B-HOME-210 with no stage the download speaks, with neither the place is empty`() {
        assertEquals(HomeLineWinner.Download, HomeDownloadRules.placeWins(null, fact(HomeDownloadPhase.Waiting)))
        assertEquals(HomeLineWinner.Stage, HomeDownloadRules.placeWins(HomeStage.Linux, null))
        assertEquals(HomeLineWinner.None, HomeDownloadRules.placeWins(null, null))
    }
}
