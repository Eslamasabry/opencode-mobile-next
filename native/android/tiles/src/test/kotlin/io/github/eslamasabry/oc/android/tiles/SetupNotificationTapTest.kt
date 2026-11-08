package io.github.eslamasabry.oc.android.tiles

import android.content.Context
import android.content.Intent
import io.github.eslamasabry.oc.core.common.launch.LaunchToken
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Phone-setup notification taps reach the app (Flutter phone_setup_notification_route_test.dart, channel group). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SetupNotificationTapTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    /**
     * SetupService's extra (`AppComponents.EXTRA_LAUNCH_ACTION`, :android:linux) is this module's [LaunchActions.EXTRA];
     * its `AppComponents.mainActivity` intent carries the launch token (B-175).
     */
    private fun tap(action: String) =
        LaunchToken.stamp(context, Intent(Intent.ACTION_MAIN).putExtra("oc.shortcut", action))

    /** What MainActivity does with an arriving intent. */
    private fun route(intent: Intent) = LaunchActions.consume(intent, LaunchToken.consume(context, intent))

    @Test
    fun `B-ANDR-050 a tap that started the app (cold start) is drained once`() {
        val launch = tap("phone_setup")
        assertSame(LaunchAction.PhoneSetup, route(launch))
        assertFalse("the extra is consumed", launch.hasExtra(LaunchActions.EXTRA))
        assertNull("a recreated activity never replays it", route(launch))
    }

    @Test
    fun `B-ANDR-050 a tap while the app runs arrives live through the new intent`() {
        val live = tap("phone_setup_done")
        assertSame(LaunchAction.PhoneSetupDone, route(live))
        assertNull(route(live))
    }

    @Test
    fun `B-ANDR-050 every action the setup notifications send is one the app understands`() {
        for (wire in listOf("phone_setup", "phone_setup_done")) assertNotNull(wire, LaunchActions.parse(wire))
    }
}
