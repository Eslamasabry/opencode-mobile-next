package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ConversationClipboardModeTest {
    @Test
    fun `B-SEC-145 message and code block Copy keep the ordinary preview without altering text`() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(ClipboardManager::class.java)
        for (text in listOf("A reply to copy", "printf 'sample'\n  next line")) {
            copyConversationText(app, text)
            val clip = manager.primaryClip!!
            assertEquals(text, clip.getItemAt(0).text.toString())
            assertFalse("Message and code copies must be Public", clip.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
        }
    }
}
