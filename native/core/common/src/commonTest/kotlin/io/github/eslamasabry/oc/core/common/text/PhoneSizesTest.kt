package io.github.eslamasabry.oc.core.common.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** One size format for Storage, Export, Manage space and the unsent-work figures: a small file never reads "0 MB". */
class PhoneSizesTest {
    private val mb = 1024L * 1024

    @Test
    fun `a size reads the same wherever it is shown`() {
        assertEquals("⁦0 B⁩", PhoneSizes.text(0))
        assertEquals("⁦1.1 KB⁩", PhoneSizes.text(1127))
        assertEquals("⁦3 MB⁩", PhoneSizes.text(3 * mb))
        assertEquals("⁦410 MB⁩", PhoneSizes.text(412 * mb))
        assertEquals("⁦1.2 GB⁩", PhoneSizes.text(1229 * mb))
    }

    @Test
    fun `memory is whole megabytes below a gigabyte`() {
        assertEquals("⁦412 MB⁩", PhoneSizes.memory(412 * mb))
        assertEquals("⁦1.1 GB⁩", PhoneSizes.memory(1126 * mb))
    }
}
