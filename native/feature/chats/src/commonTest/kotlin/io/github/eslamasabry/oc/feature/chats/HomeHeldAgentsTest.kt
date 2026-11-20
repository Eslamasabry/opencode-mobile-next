package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadMapping
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadRules
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadSizes
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldAgents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** DOWNLOAD-LINE: agent updates held on mobile data are said in Home's one downloads row, never in a new one. */
class HomeHeldAgentsTest {
    private val held = HomeHeldAgents(count = 3, megabytes = 660)

    @Test
    fun `B-DL-401 only agents held shows their own line, with no mobile-data action phase`() {
        val shown = HomeDownloadRules.shown(HomeDownloadMapping.withHeldAgents(emptyList(), held))
        assertNotNull(shown)
        assertEquals(HomeDownloadPhase.AgentsWaiting(held), shown.phase)
    }

    @Test
    fun `B-DL-402 an own line that waits keeps its line and carries the agents as its second sentence`() {
        val openCode = HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting)
        val shown = HomeDownloadRules.shown(HomeDownloadMapping.withHeldAgents(listOf(openCode), held))
        assertNotNull(shown)
        assertEquals(HomeDownloadKind.OpenCode, shown.kind)
        assertEquals(HomeDownloadPhase.Waiting, shown.phase)
        assertEquals(held, shown.moreAgents)
        assertEquals(HomeDownloadSizes.OPEN_CODE_MB, shown.megabytes)
    }

    @Test
    fun `B-DL-403 nothing held changes nothing, and a download that runs still wins the row`() {
        assertEquals(emptyList(), HomeDownloadMapping.withHeldAgents(emptyList(), null))
        val running = HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Running(40))
        assertEquals(running, HomeDownloadRules.shown(HomeDownloadMapping.withHeldAgents(listOf(running), held)))
    }

    @Test
    fun `B-DL-404 sizes are decimal megabytes, whole below 100 and to the nearest 10 above`() {
        assertEquals(660, HomeDownloadSizes.roundedMb(662_400_000))
        assertEquals(360, HomeDownloadSizes.roundedMb(358_000_000))
        assertEquals(42, HomeDownloadSizes.roundedMb(41_700_000))
        assertNull(HomeDownloadMapping.withHeldAgents(emptyList(), HomeHeldAgents(0, 0)).firstOrNull())
    }
}
