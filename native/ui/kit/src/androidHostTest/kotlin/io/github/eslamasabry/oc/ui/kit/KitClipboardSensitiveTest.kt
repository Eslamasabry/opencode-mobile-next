package io.github.eslamasabry.oc.ui.kit

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import io.github.eslamasabry.oc.ui.kit.foundation.KitAndroidClipboard
import io.github.eslamasabry.oc.ui.kit.foundation.KitClipboardMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import io.github.eslamasabry.oc.ui.kit.foundation.KitAndroidClipboardBoundary
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class KitClipboardSensitiveTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `B-SEC-145 native Compose selection and cut entries carry the sensitive flag`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        var clipboard: Clipboard? = null
        compose.setContent {
            KitAndroidClipboardBoundary(app, KitClipboardMode.Sensitive) {
                clipboard = LocalClipboard.current
            }
        }
        compose.waitForIdle()
        clipboard!!.setClipEntry(ClipEntry(ClipData.newPlainText("", "selected file contents")))
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals("selected file contents", clip.getItemAt(0).text.toString())
        assertTrue(clip.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    }

    @Test
    fun `B-SEC-145 preview mode does not redact or change copied text`() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(ClipboardManager::class.java)
        val text = "verbatim project contents\n  whitespace"
        KitAndroidClipboard.write(app, text, KitClipboardMode.Sensitive)
        assertEquals(text, manager.primaryClip!!.getItemAt(0).text.toString())
        assertTrue(manager.primaryClip!!.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
        KitAndroidClipboard.write(app, "Plain title", KitClipboardMode.Public)
        assertEquals("Plain title", manager.primaryClip!!.getItemAt(0).text.toString())
        assertFalse(manager.primaryClip!!.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    }
}
