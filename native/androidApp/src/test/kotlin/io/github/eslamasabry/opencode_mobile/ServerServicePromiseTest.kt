package io.github.eslamasabry.opencode_mobile

import io.github.eslamasabry.opencode_mobile.release.RepoFiles
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A service started with `startForegroundService` owes Android its notification. When the phone server's service
 * refused a start (the update restore, a policy rejection) it stopped without showing one, and Android then ends the
 * whole app: "Context.startForegroundService() did not then call Service.startForeground()". On the owner's phone the
 * first open after the update to 3.0.0+10020 crashed that way's category (an error type outside the app's own list).
 *
 * The service only runs on a device, so this reads the source: every refused start keeps the promise first.
 */
class ServerServicePromiseTest {
    private val source =
        RepoFiles.text("android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinServerService.kt")

    @Test
    fun `a refused start keeps the foreground promise before it stops`() {
        val refuse = source.substringAfter("private fun refuseStart(").substringBefore("\n    }\n")
        val keep = refuse.indexOf("keepPromise()")
        assertTrue(keep >= 0, "refuseStart must call keepPromise()")
        for (stop in listOf("stopSelf(", "stopRuntime(")) {
            val at = refuse.indexOf(stop)
            assertTrue(at < 0 || keep < at, "keepPromise() comes before $stop")
        }
    }

    @Test
    fun `keeping the promise shows a notification and tolerates a refusal`() {
        val keep = source.substringAfter("private fun keepPromise()").substringBefore("\n    }\n")
        assertTrue("if (!promised || foregroundShown) return" in keep, "only an unkept promise is acted on")
        assertTrue("startForeground(StatusNotification.ID, bare" in keep)
        assertTrue("catch (_: Throwable)" in keep, "a refused notification must not crash the refusal path")
    }
}
