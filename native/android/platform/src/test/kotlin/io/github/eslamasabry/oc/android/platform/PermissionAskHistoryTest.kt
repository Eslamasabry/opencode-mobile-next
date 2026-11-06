package io.github.eslamasabry.oc.android.platform

import android.content.Context
import io.github.eslamasabry.oc.android.platform.PermissionAskHistory.Asked
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * E00-S05: the Flutter build's "already asked" flags (MainActivityMedia.kt), read from the preference XML it left on
 * the phone. Each test installs the fixture before anything opens that file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionAskHistoryTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val history = PermissionAskHistory(context)

    /** Writes `src/test/resources/flutter-prefs/<name>.xml` where Android keeps `<name>` preferences. */
    private fun installFlutterPrefs(name: String) {
        val xml = javaClass.getResourceAsStream("/flutter-prefs/$name.xml")!!.readBytes()
        val dir = File(context.dataDir, "shared_prefs").apply { mkdirs() }
        File(dir, "$name.xml").writeBytes(xml)
    }

    @Test
    fun `B-MIGR-005 the camera asked by Flutter reads as already asked`() {
        installFlutterPrefs("camera_permissions")
        assertTrue(history.asked(Asked.Camera))
        assertFalse(history.asked(Asked.Microphone))
    }

    @Test
    fun `B-MIGR-005 the microphone asked by Flutter reads as already asked`() {
        installFlutterPrefs("voice_permissions")
        assertTrue(history.asked(Asked.Microphone))
        assertFalse(history.asked(Asked.Camera))
    }

    @Test
    fun `B-MIGR-005 never asked on either build reads as not asked`() {
        assertFalse(history.asked(Asked.Camera))
        assertFalse(history.asked(Asked.Microphone))
    }

    @Test
    fun `B-MIGR-005 native writes the flag where Flutter reads it, so a rollback still knows`() {
        history.markAsked(Asked.Microphone)
        val flutter = context.getSharedPreferences("voice_permissions", Context.MODE_PRIVATE)
        assertTrue(flutter.getBoolean("microphone_requested", false))
        history.markAsked(Asked.Camera)
        assertTrue(context.getSharedPreferences("camera_permissions", Context.MODE_PRIVATE).getBoolean("camera_requested", false))
    }

    @Test
    fun `B-MIGR-005 asked before with no rationale is permanently denied, as in Flutter`() {
        assertEquals(PermissionStatus.Granted, PermissionAskHistory.statusOf(granted = true, asked = true, rationale = false))
        assertEquals(PermissionStatus.PermanentlyDenied, PermissionAskHistory.statusOf(granted = false, asked = true, rationale = false))
        assertEquals(PermissionStatus.Denied, PermissionAskHistory.statusOf(granted = false, asked = true, rationale = true))
        // Never asked: Android also shows no rationale yet, which must not read as blocked.
        assertEquals(PermissionStatus.Denied, PermissionAskHistory.statusOf(granted = false, asked = false, rationale = false))
    }
}
