package io.github.eslamasabry.oc.android.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RobolectricOfflineTest {
    @Test
    fun `B-CI-342 media unit tests read the Robolectric jar from Gradle, never from the network`() {
        assertEquals("robolectric.offline", "true", System.getProperty("robolectric.offline"))
        val dir = checkNotNull(System.getProperty("robolectric.dependency.dir")) { "no robolectric.dependency.dir" }
        assertTrue("no SDK 35 jar", File(dir, "android-all-instrumented-15-robolectric-13954326-i7.jar").isFile)
    }
}
