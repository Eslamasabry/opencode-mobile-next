package io.github.eslamasabry.oc.android.camera.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrFixtureDecodeTest {
    @Test
    fun `B-PROTO-069 a scanned pairing QR yields exactly the text the paste path receives`() {
        val decoder = ZxingCoreDecoder()
        assertEquals(QrFixtures.PAIRING, decoder.decode(QrFixtures.frame("pairing.png")))
        assertEquals(QrFixtures.PAIRING_TWO_ADDRESSES, decoder.decode(QrFixtures.frame("pairing-two-addresses.png")))
    }

    @Test
    fun `B-PROTO-069 a dark terminal's inverted QR is read with the pairing options`() {
        val inverted = QrFixtures.frame("pairing-inverted.png")
        assertTrue(QrScanOptions.Pairing.tryInvert, "the pairing options must try inverted codes (plan 10 §14.2)")
        assertEquals(QrFixtures.PAIRING, ZxingCoreDecoder(QrScanOptions.Pairing).decode(inverted))
        assertNull(ZxingCoreDecoder(QrScanOptions.Pairing.copy(tryInvert = false)).decode(inverted))
    }

    @Test
    fun `B-PROTO-069 a code held sideways is read`() {
        assertEquals(QrFixtures.PAIRING, ZxingCoreDecoder().decode(QrFixtures.frame("pairing-rotated.png")))
    }

    @Test
    fun `B-PROTO-069 codes that are not pairing codes still decode, so the host can reject them plainly`() {
        assertEquals(QrFixtures.URL, ZxingCoreDecoder().decode(QrFixtures.frame("url.png")))
        assertEquals(QrFixtures.WIFI, ZxingCoreDecoder().decode(QrFixtures.frame("wifi.png")))
    }

    @Test
    fun `B-ANDR-049 a frame with no code decodes to nothing, never an invented value`() {
        assertNull(ZxingCoreDecoder().decode(QrFixtures.frame("blank-640x480.png")))
    }

    @Test
    fun `the pairing options are QR only, invert and rotate on, try-harder off, as plan 10 §14 2 sketches`() {
        val o = QrScanOptions.Pairing
        assertEquals(QrScanOptions(tryInvert = true, tryRotate = true, tryHarder = false), o)
    }
}
