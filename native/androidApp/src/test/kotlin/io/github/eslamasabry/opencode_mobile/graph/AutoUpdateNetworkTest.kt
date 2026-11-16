package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkInfo

/** The one network reading both automatic updates (the agents' and OpenCode's) wait on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AutoUpdateNetworkTest {
    private fun active(type: Int) {
        val app = RuntimeEnvironment.getApplication()
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val info =
            ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                type,
                0,
                true,
                NetworkInfo.State.CONNECTED,
            )
        shadowOf(cm).setActiveNetworkInfo(info)
        shadowOf(cm).setDefaultNetworkActive(true)
    }

    @Test
    fun `B-LINUX-079 mobile data reads as metered and Wi-Fi as unmetered`() {
        val app = RuntimeEnvironment.getApplication()
        active(ConnectivityManager.TYPE_MOBILE)
        assertTrue(activeNetworkMetered(app))
        active(ConnectivityManager.TYPE_WIFI)
        assertFalse(activeNetworkMetered(app))
    }

    @Test
    fun `B-LINUX-079 a network that cannot be read counts as metered`() {
        // An Application with no base context cannot answer for its connectivity service.
        assertTrue(activeNetworkMetered(Application()))
    }
}
