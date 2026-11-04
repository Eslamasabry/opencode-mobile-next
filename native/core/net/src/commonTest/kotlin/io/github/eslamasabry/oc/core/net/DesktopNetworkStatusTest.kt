package io.github.eslamasabry.oc.core.net

import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.UnknownNetworkStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The desktop half of test/network_test.dart (the Android half is :android:platform NetworkTrackerTest). */
class DesktopNetworkStatusTest {
    @Test
    fun `B-ANDR-037 desktop reads an unknown network without any native error and never claims offline`() {
        val state = UnknownNetworkStatus.state.value
        assertEquals(NetworkState.Online.Unknown, state.online)
        assertNull(state.metered)
        assertNull(state.onWifi)
        assertEquals(NetworkState.Unknown, state)
    }
}
