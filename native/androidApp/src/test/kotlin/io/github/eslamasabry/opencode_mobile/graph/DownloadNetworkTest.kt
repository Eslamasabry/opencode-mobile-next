package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import kotlin.test.Test
import kotlin.test.assertEquals

/** DOWNLOAD-CONSENT: the one reading both big downloads wait on is the voice download's: only unmetered is free. */
class DownloadNetworkTest {
    @Test
    fun `DOWNLOAD-CONSENT only an unmetered network is free, mobile data, unknown and offline count as paid`() {
        assertEquals(false, downloadNetworkMetered(SetupNetwork.Unmetered))
        assertEquals(true, downloadNetworkMetered(SetupNetwork.Metered))
        assertEquals(true, downloadNetworkMetered(SetupNetwork.Unknown))
        assertEquals(true, downloadNetworkMetered(SetupNetwork.Offline))
    }

    @Test
    fun `B-ANDR-103 waiting for Wi-Fi is not said before the network was read`() {
        assertEquals(false, downloadWaitingSaid(waiting = true, networkRead = false))
        assertEquals(true, downloadWaitingSaid(waiting = true, networkRead = true))
        assertEquals(false, downloadWaitingSaid(waiting = false, networkRead = true))
    }
}
