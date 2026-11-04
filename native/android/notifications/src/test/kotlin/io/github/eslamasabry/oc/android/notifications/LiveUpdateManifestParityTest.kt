package io.github.eslamasabry.oc.android.notifications

import android.app.Application
import android.app.Notification
import android.content.pm.PackageManager
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Flutter `test/live_update_chip_manifest_test.dart` (B-ANDR-034), on the merged manifest and a rendered row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveUpdateManifestParityTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @Test
    fun `B-ANDR-034 a notification that asks to be promoted has the permission declared`() {
        val content = StatusContent(StatusKind.Working, "Running the tests", conversation = ConversationRef(ProfileId("p1"), SessionId("s1")), turnId = "t1")
        val row = StatusRenderer(app, NotifyWords(app) { "en" }).build(content, ongoing = true, chip = LiveUpdatePolicy.chipFor(content, dismissedRun = false))
        // The renderer asks for promotion…
        assertTrue(row.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        // …so the manifest must declare the permission, or Android never promotes it and the chip never shows.
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue("android.permission.POST_PROMOTED_NOTIFICATIONS" in info.requestedPermissions.orEmpty())
    }
}
