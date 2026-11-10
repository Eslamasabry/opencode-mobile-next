package io.github.eslamasabry.oc.engine.analytics.product

import kotlin.test.Test
import kotlin.test.assertEquals

class ShareInEventTest {
    @Test
    fun `B-ANDR-076 share_in carries a count range and a kind and nothing else`() {
        assertEquals("share_in", ProductEvent.ShareIn.wire)
        val kept =
            cleanProductProps(
                ProductEvent.ShareIn,
                mapOf("count" to "2-5", "kind" to ProductShareKind.Image, "name" to "holiday.png", "uri" to "content://x/1"),
            )
        assertEquals(mapOf("count" to "2-5", "kind" to "image"), kept)
    }

    @Test
    fun `B-ANDR-076 a value outside the list is dropped`() {
        assertEquals(mapOf("count" to "1"), cleanProductProps(ProductEvent.ShareIn, mapOf("count" to "1", "kind" to "photo.png")))
    }
}
