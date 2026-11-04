package io.github.eslamasabry.oc.android.tiles

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phone-setup notification taps reach the app (Flutter phone_setup_notification_route_test.dart, channel group). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SetupNotificationTapTest {
    /** SetupService's extra (`AppComponents.EXTRA_LAUNCH_ACTION`, :android:linux) is this module's [LaunchActions.EXTRA]. */
    private fun tap(action: String) = Intent(Intent.ACTION_MAIN).putExtra("oc.shortcut", action)

    @Test
    fun `B-ANDR-050 a tap that started the app (cold start) is drained once`() {
        val launch = tap("phone_setup")
        assertSame(LaunchAction.PhoneSetup, LaunchActions.consume(launch))
        assertFalse("the extra is consumed", launch.hasExtra(LaunchActions.EXTRA))
        assertNull("a recreated activity never replays it", LaunchActions.consume(launch))
    }

    @Test
    fun `B-ANDR-050 a tap while the app runs arrives live through the new intent`() {
        val live = tap("phone_setup_done")
        assertSame(LaunchAction.PhoneSetupDone, LaunchActions.consume(live))
        assertNull(LaunchActions.consume(live))
    }

    @Test
    fun `B-ANDR-050 every action the setup notifications send is one the app understands`() {
        for (wire in listOf("phone_setup", "phone_setup_done")) assertNotNull(wire, LaunchActions.parse(wire))
    }
}
