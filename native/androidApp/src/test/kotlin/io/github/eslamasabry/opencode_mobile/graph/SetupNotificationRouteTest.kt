package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.tiles.LaunchAction
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.LinuxRoute
import io.github.eslamasabry.opencode_mobile.release.RepoFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Where a setup notification tap lands, and the native half it relies on (phone_setup_notification_route_test.dart). */
class SetupNotificationRouteTest {
    private val service = RepoFiles.text("android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupService.kt")
    private val runner = RepoFiles.text("android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupRunner.kt")

    @Test
    fun `B-ANDR-050 the ongoing setup row opens the setup's progress page`() {
        assertEquals(SetupTap.Open(LinuxRoute.Home), SetupTapRoutes.routeFor(LaunchAction.PhoneSetup, ChatsRoute.Home))
        assertEquals(SetupTap.Open(LinuxRoute.Home), SetupTapRoutes.routeFor(LaunchAction.PhoneSetup, null))
    }

    @Test
    fun `B-ANDR-050 a tap while the setup page is on top changes nothing`() {
        assertEquals(SetupTap.Stay, SetupTapRoutes.routeFor(LaunchAction.PhoneSetup, LinuxRoute.Home))
    }

    @Test
    fun `B-ANDR-050 a finished job just brings the app back and other actions are not setup taps`() {
        assertEquals(SetupTap.Land, SetupTapRoutes.routeFor(LaunchAction.PhoneSetupDone, LinuxRoute.Home))
        assertEquals(SetupTap.NotSetup, SetupTapRoutes.routeFor(LaunchAction.Connect, null))
    }

    @Test
    fun `B-ANDR-050 both notifications open MainActivity with a whitelisted action`() {
        assertTrue("LAUNCH_ACTION_PROGRESS = \"phone_setup\"" in service)
        assertTrue("LAUNCH_ACTION_DONE = \"phone_setup_done\"" in service)
        assertTrue(".putExtra(AppComponents.EXTRA_LAUNCH_ACTION, action)" in service)
        assertTrue("AppComponents.mainActivity(context)" in service)
        // The ongoing notification (start and every update) opens progress.
        assertEquals(2, Regex("""build\([^)]*ongoing = true, LAUNCH_ACTION_PROGRESS\)""").findAll(service).count())
        // Extras alone do not make PendingIntents different: one request code per action, updated in place.
        assertTrue("if (action == LAUNCH_ACTION_DONE) 3 else 2" in service)
        assertTrue("PendingIntent.FLAG_UPDATE_CURRENT" in service)
        // The extra SetupService writes is the one the app consumes.
        val shim = RepoFiles.text("android/linux/src/shims/kotlin/io/github/eslamasabry/opencode_mobile/AppComponents.kt")
        assertTrue("EXTRA_LAUNCH_ACTION = \"oc.shortcut\"" in shim)
        assertEquals("oc.shortcut", io.github.eslamasabry.oc.android.tiles.LaunchActions.EXTRA)
    }

    @Test
    fun `B-ANDR-050 the result notification says whether the job finished`() {
        assertTrue("val action = if (done) LAUNCH_ACTION_DONE else LAUNCH_ACTION_PROGRESS" in service)
        assertTrue("done = ended == \"done\"" in runner)
    }

    @Test
    fun `B-ANDR-050 the app routes both setup actions`() {
        val platform = RepoFiles.text("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt")
        assertTrue("LaunchAction.PhoneSetup, LaunchAction.PhoneSetupDone ->" in platform)
        assertTrue("SetupTapRoutes.routeFor(action, navigator.top.value)" in platform)
    }
}
