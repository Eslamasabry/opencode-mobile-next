package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.text.PhoneSizes
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadSizes
import kotlin.test.Test
import kotlin.test.assertEquals

/** B-AGENT-SIZE-001: one unit for a download's size, so the sheet, Home's line, the ask and the room check agree. */
class DownloadSizeUnitTest {
    @Test
    fun `B-AGENT-SIZE-001 the update sheet says the same OpenCode size as Home's line`() {
        val sheet = PhoneSizes.text(HomeDownloadSizes.bytesOf(HomeDownloadKind.OpenCode))
        assertEquals("130 MB", sheet, "the sheet reads 130,000,000 bytes as 130 MB; Home says 130")
        assertEquals(130, HomeDownloadSizes.of(HomeDownloadKind.OpenCode))
    }

    @Test
    fun `B-AGENT-SIZE-002 the helper's size reads the same in the sheet and on Home`() {
        assertEquals("330 MB", PhoneSizes.text(HomeDownloadSizes.bytesOf(HomeDownloadKind.Helper)))
    }

    @Test
    fun `B-AGENT-SIZE-003 the room a stopped download names reads in the unit PhoneSizes reads`() {
        assertEquals(700, HomeDownloadSizes.megabytesDown(700 * mib))
        assertEquals(700, HomeDownloadSizes.megabytesUp(700 * mib))
        assertEquals(701, HomeDownloadSizes.megabytesUp(700 * mib + 1))
        assertEquals("700 MB", PhoneSizes.text(700 * mib))
    }
}
