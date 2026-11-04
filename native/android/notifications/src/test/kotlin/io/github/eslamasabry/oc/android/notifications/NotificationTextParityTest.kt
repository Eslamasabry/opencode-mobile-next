package io.github.eslamasabry.oc.android.notifications

import io.github.eslamasabry.oc.android.notifications.ReplyExcerpt.Message
import io.github.eslamasabry.oc.android.notifications.ReplyExcerpt.Part
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The `firstReplyLine` and `stoppedReasonToken` cases of the Flutter `test/notification_text_test.dart` (B-ANDR-048). */
class NotificationTextParityTest {
    private fun assistant(text: String, synthetic: Boolean = false) = Message(true, listOf(Part(text, synthetic)))

    private fun user(text: String) = Message(false, listOf(Part(text)))

    @Test
    fun `B-ANDR-048 takes the first line of the newest assistant reply`() {
        assertEquals(
            "Dark theme added to Settings.",
            ReplyExcerpt.firstLine(listOf(assistant("Old reply"), user("next"), assistant("\n## Dark theme added to Settings.\nMore"))),
        )
    }

    @Test
    fun `B-ANDR-048 a line that introduces a list is joined with the first item`() {
        assertEquals(
            "Done. The site now follows the system theme by default: Light and dark both work",
            ReplyExcerpt.firstLine(listOf(assistant("Done. The site now follows the system theme by default:\n\n- Light and dark both work\n- The toggle stays"))),
        )
        assertEquals("Changes made", ReplyExcerpt.firstLine(listOf(assistant("Changes made:"))))
        assertTrue(ReplyExcerpt.firstLine(listOf(assistant("Intro that ends with a colon:\n- ${"x".repeat(200)}")), maxLength = 60).length <= 60)
    }

    @Test
    fun `B-ANDR-048 skips user and synthetic text, and is empty with nothing`() {
        assertEquals("", ReplyExcerpt.firstLine(listOf(user("hello"))))
        assertEquals("", ReplyExcerpt.firstLine(listOf(assistant("hidden", synthetic = true))))
        assertEquals("", ReplyExcerpt.firstLine(emptyList()))
        // A synthetic part beside a real one never wins.
        assertEquals("Real", ReplyExcerpt.firstLine(listOf(Message(true, listOf(Part("injected", synthetic = true), Part("Real"))))))
    }

    @Test
    fun `B-ANDR-048 never carries a secret from the reply`() {
        assertFalse("ghp_" in ReplyExcerpt.firstLine(listOf(assistant("Set token=ghp_ABCDEFGHIJKLMNOPQRSTUV now"))))
        assertFalse("ghp_" in ReplyExcerpt.firstLine(listOf(assistant("Use ghp_ABCDEFGHIJKLMNOPQRSTUV now"))))
    }

    @Test
    fun `B-ANDR-048 stopped reasons become plain tokens, never raw text`() {
        assertEquals("auth", NotificationText.stoppedReason("ProviderAuthError").wire)
        assertEquals("context", NotificationText.stoppedReason("ContextOverflowError").wire)
        assertEquals("length", NotificationText.stoppedReason("MessageOutputLengthError").wire)
        assertEquals("model", NotificationText.stoppedReason("ModelNotFoundError").wire)
        assertEquals("other", NotificationText.stoppedReason("SomethingNew").wire)
        assertEquals("other", NotificationText.stoppedReason(null).wire)
    }
}
