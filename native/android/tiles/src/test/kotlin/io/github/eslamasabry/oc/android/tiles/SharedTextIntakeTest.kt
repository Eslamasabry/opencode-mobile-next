package io.github.eslamasabry.oc.android.tiles

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedTextIntakeTest {

    private fun share(text: String?, subject: String? = null, type: String = "text/plain"): Intent =
        Intent(Intent.ACTION_SEND).apply {
            this.type = type
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
            if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
        }

    @Test
    fun `B-ANDR-075 a share is delivered once and then consumed`() {
        val intent = share("  fix the build  ")
        val shared = SharedTextIntake.consume(intent)
        assertEquals("fix the build", shared?.text)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertFalse(intent.hasExtra(Intent.EXTRA_TEXT))
        assertNull(SharedTextIntake.consume(intent))
    }

    @Test
    fun `B-ANDR-075 empty shares and other intents are ignored`() {
        assertNull(SharedTextIntake.consume(share("   ")))
        assertNull(SharedTextIntake.consume(share("x", type = "image/png")))
        assertNull(SharedTextIntake.consume(Intent(Intent.ACTION_MAIN).putExtra(Intent.EXTRA_TEXT, "x")))
        assertNull(SharedTextIntake.consume(null))
        val empty = share("  ")
        SharedTextIntake.consume(empty)
        assertEquals(Intent.ACTION_SEND, empty.action)
    }

    @Test
    fun `B-ANDR-074 the subject becomes the first line unless the body starts with it`() {
        assertEquals("Bug\nIt crashes", SharedTextIntake.consume(share("It crashes", " Bug "))?.text)
        assertEquals("Bug: it crashes", SharedTextIntake.consume(share("Bug: it crashes", "Bug"))?.text)
        val subjectOnly = SharedTextIntake.consume(share(null, "Only subject"))
        assertEquals("Only subject", subjectOnly?.text)
        assertEquals("", subjectOnly?.body)
    }

    @Test
    fun `B-ANDR-074 shared text is capped`() {
        val big = "a".repeat(SharedTextIntake.MAX_CHARS + 50)
        assertEquals(SharedTextIntake.MAX_CHARS, SharedTextIntake.consume(share(big))?.text?.length)
    }
}
