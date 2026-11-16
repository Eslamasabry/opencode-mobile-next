package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadMapping
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHelperStep
import io.github.eslamasabry.oc.feature.chats.presentation.HomeInstallPhase
import kotlin.test.Test
import kotlin.test.assertEquals

/** DOWNLOAD-CONSENT: how the two updates' readings become the lines Home shows, start to quiet end. */
class HomeDownloadMappingTest {
    private fun facts(
        waiting: Boolean = false,
        running: Boolean = false,
        failed: Boolean = false,
        percent: Int? = null,
        helper: HomeHelperStep = HomeHelperStep.None,
        install: HomeInstallPhase = HomeInstallPhase.None,
        helperPercent: Int? = null,
    ) = HomeDownloadMapping.facts(waiting, running, failed, percent, helper, install, helperPercent)

    @Test
    fun `DOWNLOAD-CONSENT metered and due shows the wait; nothing downloads and nothing is said when not due`() {
        assertEquals(
            listOf(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting)),
            facts(waiting = true),
        )
        assertEquals(
            listOf(HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Waiting)),
            facts(helper = HomeHelperStep.Waiting),
        )
        assertEquals(emptyList(), facts())
    }

    @Test
    fun `DOWNLOAD-CONSENT the wait becomes progress, then the line clears`() {
        // Wi-Fi arrives: the wait is gone and the job runs with its share done.
        assertEquals(
            listOf(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Running(40))),
            facts(running = true, percent = 40),
        )
        // The job ended well: nothing is left to say.
        assertEquals(emptyList(), facts())
    }

    @Test
    fun `DOWNLOAD-CONSENT the helper's install shows its share, or a working mark when the share is not known`() {
        assertEquals(
            listOf(HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Running(25))),
            facts(helper = HomeHelperStep.Updating, install = HomeInstallPhase.Installing, helperPercent = 25),
        )
        assertEquals(
            listOf(HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Running(null))),
            facts(helper = HomeHelperStep.Updating),
        )
    }

    @Test
    fun `DOWNLOAD-CONSENT a failure of either is a failed fact for Try again`() {
        assertEquals(
            listOf(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Failed)),
            facts(failed = true),
        )
        assertEquals(
            listOf(HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Failed)),
            facts(helper = HomeHelperStep.Updating, install = HomeInstallPhase.Failed),
        )
    }

    @Test
    fun `DOWNLOAD-CONSENT the sizes say about 130 MB and about 330 MB`() {
        assertEquals(130, facts(waiting = true).single().megabytes)
        assertEquals(330, facts(helper = HomeHelperStep.Waiting).single().megabytes)
    }
}
