package io.github.eslamasabry.oc.android.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.platform.SharedFile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Share out (plan 11 §14.2, 07 §7.7): the system chooser, never this app; explicit grants for FileProvider URIs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidShareServiceTest {
    private val app = RuntimeEnvironment.getApplication()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun started(): Intent = assertNotNull(shadowOf(activity).nextStartedActivity)

    private fun Intent.target(): Intent = assertNotNull(IntentCompat.getParcelableExtra(this, Intent.EXTRA_INTENT, Intent::class.java))

    @Test
    fun `B-ANDR-077 sharing text out sends text and subject to the system share sheet and reports that it worked`() {
        val share = AndroidShareService(app) { activity }
        assertEquals(Outcome.Ok(Unit), share.shareText("report body", "Problem report"))

        val chooser = started()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.target()
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("report body", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Problem report", send.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun `B-ANDR-074 share out opens the chooser without this app as a target`() {
        AndroidShareService(app) { activity }.shareText("x", null)
        val chooser = started()
        @Suppress("DEPRECATION")
        val excluded = chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)!!.map { it as ComponentName }
        assertEquals(listOf(ComponentName(app.packageName, StaticShortcuts.MAIN_ACTIVITY)), excluded)
        assertNull(chooser.target().getStringExtra(Intent.EXTRA_SUBJECT)) // no subject given, none sent
    }

    @Test
    fun `B-ANDR-077 missing or failing platform answers a failure, never an exception`() {
        val throwing =
            object : ContextWrapper(app) {
                override fun startActivity(intent: Intent?) = throw ActivityNotFoundException()

                override fun startActivity(intent: Intent?, options: Bundle?) = throw ActivityNotFoundException()
            }
        val noChooser = AndroidShareService(throwing) { null }
        assertIs<Failure.Device>((noChooser.shareText("x", null) as Outcome.Failed).failure)

        val share = AndroidShareService(app) { activity }
        assertIs<Outcome.Failed>(share.shareText("", "subject")) // nothing to share
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `B-ANDR-077 with no visible activity the chooser starts from the application in a new task`() {
        val share = AndroidShareService(app) { null }
        assertEquals(Outcome.Ok(Unit), share.shareText("x", null))
        val chooser = assertNotNull(shadowOf(app).nextStartedActivity)
        assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `S2-PLATFORM a FileProvider file is shared with an explicit read grant and ClipData, other URIs are refused`() {
        val share = AndroidShareService(app) { activity }
        val uri = "content://${app.packageName}.localfiles/share/report.zip"
        assertEquals(Outcome.Ok(Unit), share.shareFile(SharedFile(uri, "application/zip", "report.zip")))
        val chooser = started()
        val send = chooser.target()
        assertEquals(Uri.parse(uri), IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals("application/zip", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(Uri.parse(uri), send.clipData?.getItemAt(0)?.uri)
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) // the chooser carries the grant too

        for (bad in listOf("file:///data/data/x/files/server.password", "content://com.other.app/files/x", "https://x/y", "content://")) {
            assertIs<Outcome.Failed>(share.shareFile(SharedFile(bad, "text/plain", null)), bad)
        }
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
