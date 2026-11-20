package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.text.PhoneSizes
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadSizes
import kotlin.test.Test
import kotlin.test.assertEquals

/** B-AGENT-SIZE-001: one unit for a download's size, so the sheet, Home's line, the ask and the room check agree. */
class DownloadSizeUnitTest {
    private fun words(megabytes: String) = "\u2066$megabytes\u00A0MB\u2069"

    @Test
    fun `B-AGENT-SIZE-001 the update sheet says the same OpenCode size as Home's line`() {
        val sheet = PhoneSizes.text(HomeDownloadSizes.bytesOf(HomeDownloadKind.OpenCode))
        assertEquals(words("130"), sheet, "the sheet reads 130,000,000 bytes as 130 MB; Home says 130")
        assertEquals(130, HomeDownloadSizes.of(HomeDownloadKind.OpenCode))
    }

    @Test
    fun `B-AGENT-SIZE-002 the helper's size reads the same in the sheet and on Home`() {
        assertEquals(words("330"), PhoneSizes.text(HomeDownloadSizes.bytesOf(HomeDownloadKind.Helper)))
    }

    @Test
    fun `B-AGENT-SIZE-003 the room a stopped download names reads in the unit PhoneSizes reads`() {
        assertEquals(700, HomeDownloadSizes.megabytesDown(700_000_000))
        assertEquals(700, HomeDownloadSizes.megabytesUp(700_000_000))
        assertEquals(701, HomeDownloadSizes.megabytesUp(700_000_001))
        assertEquals(words("700"), PhoneSizes.text(700_000_000))
        assertEquals(words("300"), PhoneSizes.text(300_000_000), "a 300 MB floor reads 300")
    }
}
