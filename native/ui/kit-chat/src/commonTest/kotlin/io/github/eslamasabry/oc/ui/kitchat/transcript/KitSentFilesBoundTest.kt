package io.github.eslamasabry.oc.ui.kitchat.transcript

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/** A `data:` address past the viewer's cap is never decoded, so a big photo cannot drain memory. */
class KitSentFilesBoundTest {
    @Test
    fun `B-MEDIA-045 a 20 MB sent photo is never decoded`() {
        assertNull(dataUrlBytes("data:image/png;base64," + "AAAA".repeat(5_242_880)))
        assertContentEquals(
            ByteArray(16_777_216 / 4 * 3),
            dataUrlBytes("data:image/png;base64," + "A".repeat(16_777_216)),
        )
        assertContentEquals(byteArrayOf(1, 2, 3), dataUrlBytes("data:image/png;base64,AQID"))
    }
}
