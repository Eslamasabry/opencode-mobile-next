package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** B-CHAT-130: the sending bubble and the echoed message show the same thumbnail, with no blank frame between. */
@OptIn(ExperimentalTestApi::class)
class SentFilesThumbnailTest {
    private val png =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    private val file = KitSentFile("shot.png", "image/png", png)

    @Test
    fun `B-CHAT-130 a picture from this phone draws as a thumbnail, other files as chips`() = runComposeUiTest {
        val seen = mutableStateOf<KitSentFiles?>(null)
        setContent {
            ChatTestTheme {
                seen.value = rememberKitSentFiles(listOf(file, KitSentFile("notes.txt", "text/plain", "file:///x")))
            }
        }
        waitUntil(timeoutMillis = 5_000L) { seen.value?.photos?.singleOrNull()?.image != null }
        assertEquals("shot.png", seen.value!!.photos.single().label)
        assertEquals(listOf("notes.txt"), seen.value!!.attachments.map { it.label })
    }

    @Test
    fun `B-CHAT-130 the message that replaces the bubble starts with the bubble's own bitmap`() = runComposeUiTest {
        val echoed = mutableStateOf(false)
        val bubble = mutableStateOf<KitSentFiles?>(null)
        var firstFrame: KitSentFiles? = null
        setContent {
            ChatTestTheme {
                if (!echoed.value) bubble.value = rememberKitSentFiles(listOf(file))
                else rememberKitSentFiles(listOf(file)).also { if (firstFrame == null) firstFrame = it }
            }
        }
        waitUntil(timeoutMillis = 5_000L) { bubble.value?.photos?.singleOrNull()?.image != null }
        val shown = bubble.value!!.photos.single().image
        assertNotNull(shown)
        // The echoed prompt row is a new composition: it has the picture on its very first frame.
        echoed.value = true
        waitForIdle()
        assertSame(shown, firstFrame!!.photos.single().image)
    }

    @Test
    fun `B-CHAT-130 a picture that is not here is a chip and never a thumbnail`() = runComposeUiTest {
        val seen = mutableStateOf<KitSentFiles?>(null)
        setContent { ChatTestTheme { seen.value = rememberKitSentFiles(listOf(KitSentFile("a.png", "image/png", "file:///a.png"))) } }
        waitForIdle()
        assertNull(seen.value!!.photos.firstOrNull())
        assertEquals(KitPromptAttachmentKind.Image, seen.value!!.attachments.single().kind)
    }
}
