package io.github.eslamasabry.oc.ui.kit.content

import io.github.eslamasabry.oc.ui.kit.content.qr.KitQrs
import io.github.eslamasabry.oc.ui.kit.content.qr.QrEncoder
import io.github.eslamasabry.oc.ui.kit.content.qr.QrMatrix
import io.github.eslamasabry.oc.ui.kit.content.qr.QrPayload
import io.github.eslamasabry.oc.ui.kit.content.qr.QrRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Plan 06 §17 `QrPayloadTest` (B-KIT-026) plus the plan 10 §14.1 encoder, mask and readability rules. */
class QrPayloadTest {
    @Test
    fun `B-KIT-026 an empty QR payload is refused`() {
        assertEquals(QrPayload.Empty, QrPayload.of(""))
        assertFalse(KitQrs.fits(""))
        assertEquals(null, QrEncoder.encode(""))
    }

    @Test
    fun `B-KIT-026 a payload too long for any version is refused as too long, not drawn`() {
        assertIs<QrPayload.Ready>(QrPayload.of("a".repeat(QrEncoder.MAX_BYTES)))
        assertEquals(QrPayload.TooLong, QrPayload.of("a".repeat(QrEncoder.MAX_BYTES + 1)))
        assertFalse(KitQrs.fits("a".repeat(5000)))
    }

    @Test
    fun `MV-8 every fixture payload decodes back to itself with ZXing`() {
        val failed =
            QrFixtures.payloads.mapNotNull { data ->
                val m = assertNotNull(QrEncoder.encode(data), "encodes: ${data.take(40)}")
                val back = runCatching { QrDecode.matrix(m) }.getOrNull()
                if (back == data) null else "${data.encodeToByteArray().size} bytes, version ${m.version}, mask ${m.mask}"
            }
        assertEquals(emptyList(), failed)
    }

    @Test
    fun `MV-8 every version 1 to 40 encodes correctly`() {
        // The longest payload of each version (byte mode, error correction M) and one byte past the previous version.
        val ends = listOf(
            14, 26, 42, 62, 84, 106, 122, 152, 180, 213, 251, 287, 331, 362, 412, 450, 504, 560, 624, 666,
            711, 779, 857, 911, 997, 1059, 1125, 1190, 1264, 1370, 1452, 1538, 1628, 1722, 1809, 1911, 1989, 2099, 2213, 2331,
        )
        ends.forEachIndexed { i, len ->
            val data = (0 until len).map { 'a' + (it * 7 % 26) }.joinToString("")
            val m = QrEncoder.encode(data)!!
            assertEquals(i + 1, m.version, "$len bytes")
            assertEquals(data, QrDecode.modules(m), "modules of version ${i + 1}")
            assertEquals(data, QrDecode.matrix(m, modulePx = 2), "image of version ${i + 1}")
            if (i > 0) assertEquals(i + 1, QrEncoder.encode(data.take(ends[i - 1] + 1))!!.version)
        }
    }

    @Test
    fun `MV-8 the smallest version that holds the data is used`() {
        // Byte mode at error correction M: version 1 holds 14 bytes, version 2 holds 26, version 40 holds 2,331.
        assertEquals(1, QrEncoder.encode("a".repeat(14))!!.version)
        assertEquals(2, QrEncoder.encode("a".repeat(15))!!.version)
        assertEquals(2, QrEncoder.encode("a".repeat(26))!!.version)
        assertEquals(3, QrEncoder.encode("a".repeat(27))!!.version)
        val big = QrEncoder.encode("a".repeat(QrEncoder.MAX_BYTES))!!
        assertEquals(40, big.version)
        assertEquals(177, big.size)
    }

    @Test
    fun `MV-8 the chosen mask is the lowest-penalty trial, the first on a tie`() {
        for (data in QrFixtures.payloads.take(12)) {
            val chosen = QrEncoder.encode(data)!!.mask
            val scores = (0 until 8).map { QrRules.penalty(QrEncoder.encodeWithMask(data, it, test = true)!!) }
            assertEquals(scores.indexOf(scores.min()), chosen, "mask for ${data.take(30)}: $scores")
        }
    }

    @Test
    fun `MV-8 every mask still decodes, so the choice only changes scan ease`() {
        val data = QrFixtures.payloads[3]
        for (mask in 0 until 8) assertEquals(data, QrDecode.matrix(QrEncoder.encodeWithMask(data, mask)!!))
    }

    @Test
    fun `MV-8 the penalty is Dart qr's lost point on a hand-counted matrix`() {
        // All light, 21 × 21: N1 = 361 interior modules × (3 + 8 − 5) = 2,166; N2 = 400 blocks × 3 = 1,200;
        // N3 = 0; N4 = |0 − 50| / 5 × 10 = 100.
        val light = QrMatrix(21, 1, 0, BooleanArray(21 * 21))
        assertEquals(3466.0, QrRules.penalty(light))
        assertEquals(2166.0, QrRules.sameNeighbours(light))
        assertEquals(1200.0, QrRules.solidBlocks(light))
        assertEquals(100.0, QrRules.balance(light))
        // One finder look-alike in a row scores 40 once; a 1 × 7 run has no column match.
        val cells = BooleanArray(21 * 21)
        booleanArrayOf(true, false, true, true, true, false, true).forEachIndexed { i, d -> cells[10 * 21 + 5 + i] = d }
        assertEquals(40.0, QrRules.finderLookAlikes(QrMatrix(21, 1, 0, cells)))
        // Half dark is perfectly balanced.
        assertEquals(0.0, QrRules.balance(QrMatrix(2, 1, 0, booleanArrayOf(true, false, false, true))))
    }

    @Test
    fun `LOOK-21 the side snaps every module to whole physical pixels and never passes the box`() {
        // Version 1 + quiet zone = 29 modules. 240 dp at density 3 = 720 px → 24 px per module → 696 px.
        assertEquals(696f, QrRules.sidePx(10_000f, 21, 3f))
        // A 300 px box: 10 px per module → 290 px.
        assertEquals(290f, QrRules.sidePx(300f, 21, 3f))
        // Too narrow for one pixel per module: keep the box width, fractional modules.
        assertEquals(20f, QrRules.sidePx(20f, 21, 1f))
    }
}
