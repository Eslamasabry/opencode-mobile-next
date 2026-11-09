package io.github.eslamasabry.oc.android.tiles

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedStreamsIntakeTest {
    private val own = "io.github.eslamasabry.opencode_mobile"
    private fun uri(n: Int) = Uri.parse("content://media.example/item/$n")

    private fun send(type: String, stream: Uri?, text: String? = null): Intent =
        Intent(Intent.ACTION_SEND).apply {
            this.type = type
            if (stream != null) putExtra(Intent.EXTRA_STREAM, stream)
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
        }

    private fun many(type: String, uris: List<Uri>): Intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            this.type = type
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }

    @Test
    fun `B-ANDR-076 one shared picture is read and the share is consumed`() {
        val intent = send("image/png", uri(1))
        val shared = SharedStreamsIntake.consume(intent, own)!!
        assertEquals(listOf(uri(1)), shared.uris)
        assertNull(shared.text)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertFalse(intent.hasExtra(Intent.EXTRA_STREAM))
        assertNull("consumed once", SharedStreamsIntake.consume(intent, own))
    }

    @Test
    fun `B-ANDR-076 several pictures keep their order`() {
        val shared = SharedStreamsIntake.consume(many("image/*", listOf(uri(3), uri(1), uri(2))), own)!!
        assertEquals(listOf(uri(3), uri(1), uri(2)), shared.uris)
        assertEquals(3, shared.arrived)
    }

    @Test
    fun `B-ANDR-076 mixed pictures and files in one share arrive together`() {
        val shared = SharedStreamsIntake.consume(many("*/*", listOf(uri(1), uri(2))), own)!!
        assertEquals(2, shared.uris.size)
    }

    @Test
    fun `B-ANDR-076 text with a picture carries the caption`() {
        val shared = SharedStreamsIntake.consume(send("image/jpeg", uri(1), "  look at this "), own)!!
        assertEquals(listOf(uri(1)), shared.uris)
        assertEquals("look at this", shared.text)
    }

    @Test
    fun `B-ANDR-076 a text file shared with text slash plain still reads as a file`() {
        val shared = SharedStreamsIntake.consume(send("text/plain", uri(9)), own)!!
        assertEquals(listOf(uri(9)), shared.uris)
    }

    @Test
    fun `B-ANDR-076 a text only share is left to the text intake`() {
        val intent = send("text/plain", null, "hello")
        assertNull(SharedStreamsIntake.consume(intent, own))
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertNull(SharedStreamsIntake.consume(Intent(Intent.ACTION_MAIN), own))
        assertNull(SharedStreamsIntake.consume(null, own))
    }

    @Test
    fun `B-ANDR-076 file addresses and our own provider are refused but counted`() {
        val ours = Uri.parse("content://$own.localfiles/secret")
        val intent = many("*/*", listOf(Uri.parse("file:///data/data/x/secret"), ours, uri(1)))
        val shared = SharedStreamsIntake.consume(intent, own)!!
        assertEquals(listOf(uri(1)), shared.uris)
        assertEquals(2, shared.refused)
        assertEquals(3, shared.arrived)
    }

    @Test
    fun `B-ANDR-076 a clip only share is read from the clip`() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                clipData = ClipData.newRawUri("", uri(5))
            }
        assertEquals(listOf(uri(5)), SharedStreamsIntake.consume(intent, own)!!.uris)
    }

    @Test
    fun `B-ANDR-076 a runaway share is cut at the item cap and duplicates collapse`() {
        val big = List(50) { uri(it % 30) }
        val shared = SharedStreamsIntake.consume(many("*/*", big), own)!!
        assertEquals(SharedStreamsIntake.MAX_ITEMS, shared.uris.size)
        assertTrue(shared.uris.toSet().size == shared.uris.size)
    }

    @Test
    fun `B-ANDR-076 the description never prints an address`() {
        val text = SharedStreamsIntake.consume(send("image/png", uri(1), "private words"), own).toString()
        assertFalse(text.contains("media.example"))
        assertFalse(text.contains("private"))
    }
}
