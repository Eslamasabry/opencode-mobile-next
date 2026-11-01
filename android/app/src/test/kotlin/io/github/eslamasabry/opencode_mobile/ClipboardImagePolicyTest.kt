package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardImagePolicyTest {
    @Test fun imageTypesAreAcceptedAndOthersRefused() {
        assertTrue(ClipboardImagePolicy.isImageMime("image/png"))
        assertTrue(ClipboardImagePolicy.isImageMime("IMAGE/JPEG; charset=x"))
        assertFalse(ClipboardImagePolicy.isImageMime("text/plain"))
        assertFalse(ClipboardImagePolicy.isImageMime("image/"))
        assertFalse(ClipboardImagePolicy.isImageMime(null))
    }

    @Test fun firstImageMimeSkipsNonImagesAndNulls() {
        assertEquals("image/webp", ClipboardImagePolicy.firstImageMime(listOf("text/uri-list", null, "image/webp", "image/png")))
        assertNull(ClipboardImagePolicy.firstImageMime(listOf("text/plain", null)))
    }

    @Test fun readLimitedReturnsBytesAtExactlyTheCap() {
        val data = ByteArray(100) { it.toByte() }
        assertArrayEquals(data, ClipboardImagePolicy.readLimited(data.inputStream(), maxBytes = 100))
    }

    @Test fun readLimitedRefusesOneByteOverTheCap() {
        assertThrows(ClipboardImagePolicy.TooLargeException::class.java) {
            ClipboardImagePolicy.readLimited(ByteArray(101).inputStream(), maxBytes = 100)
        }
    }

    @Test fun capMatchesTheComposerAttachmentLimit() {
        assertEquals(10 * 1024 * 1024, ClipboardImagePolicy.MAX_BYTES)
    }
}
