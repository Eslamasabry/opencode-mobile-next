package io.github.eslamasabry.opencode_mobile

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Review 2026-11-13 #17: the first frames were a flat system grey with nothing on it. */
class LaunchGroundTest {
    private fun res(path: String) = File("src/main/res/$path").readText()

    @Test
    fun `LAUNCH the app theme sets the window to the app ground and mark, on every Android version`() {
        assertTrue("android:windowBackground\">@drawable/launch_ground" in res("values/styles.xml").substringAfter("NativeAppTheme").substringBefore("</style>"))
        val v31 = res("values-v31/styles.xml")
        assertTrue("windowSplashScreenBackground\">@color/launcher_background" in v31)
        assertTrue("windowSplashScreenAnimatedIcon" in v31)
        val ground = res("drawable/launch_ground.xml")
        assertTrue("@color/launcher_background" in ground && "@drawable/ic_launcher_foreground" in ground)
    }

    @Test
    fun `LAUNCH the plain line shows only after two seconds without a first page`() {
        assertFalse(launchLineShows(0))
        assertFalse(launchLineShows(1_999))
        assertTrue(launchLineShows(2_000))
    }
}
