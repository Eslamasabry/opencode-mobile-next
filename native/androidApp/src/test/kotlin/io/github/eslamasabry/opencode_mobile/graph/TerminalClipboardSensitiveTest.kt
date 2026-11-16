package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TerminalClipboardSensitiveTest {
    @Test
    fun `B-SEC-145 remote terminal selection keeps exact text and hides the preview`() {
        val app = RuntimeEnvironment.getApplication()
        val text = "sample credential line\nwith spacing  "
        assertTrue(AndroidTerminalClipboard(app).copy(text))
        assertClip(app, text)
    }

    @Test
    fun `B-SEC-145 native terminal callback keeps exact text and hides the preview`() {
        val app = RuntimeEnvironment.getApplication()
        val text = "a terminal program's deliberate copy\n"
        KitTerminalTextClipboard(app).copySensitive(text)
        assertClip(app, text)
    }

    @Test
    @Config(sdk = [32])
    fun `B-SEC-145 sensitive terminal copy also works below Android 13`() {
        val app = RuntimeEnvironment.getApplication()
        val text = "older platform copy"
        AndroidTerminalClipboard(app).copy(text)
        assertClip(app, text)
    }

    private fun assertClip(app: Application, text: String) {
        val clip = requireNotNull(app.getSystemService(ClipboardManager::class.java).primaryClip)
        assertEquals(text, clip.getItemAt(0).text.toString())
        assertTrue("Terminal text must be sensitive", clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true)
    }
}
