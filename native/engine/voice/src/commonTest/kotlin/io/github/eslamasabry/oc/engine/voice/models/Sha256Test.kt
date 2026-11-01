package io.github.eslamasabry.oc.engine.voice.models

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {
    @Test
    fun `B-MEDIA-025 the verifier's SHA-256 matches the FIPS vectors, fed in any split`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha("abc".encodeToByteArray()))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
        val million = ByteArray(1_000_000) { 'a'.code.toByte() }
        val split = PureSha256()
        var i = 0
        var step = 1
        while (i < million.size) {
            val n = minOf(step, million.size - i)
            split.update(million, i, n)
            i += n
            step = step * 3 % 997 + 1
        }
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", split.digestHex())
    }
}
