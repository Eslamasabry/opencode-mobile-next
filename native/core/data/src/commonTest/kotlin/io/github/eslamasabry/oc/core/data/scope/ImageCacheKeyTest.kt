package io.github.eslamasabry.oc.core.data.scope

import kotlin.test.Test
import kotlin.test.assertEquals

class ImageCacheKeyTest {
    @Test
    fun `the picture cache name oc imageCache profileId is swept with its profile only`() {
        val keys = listOf("oc.imageCache.p1", "oc.imageCache.p2", "oc.profiles")
        assertEquals(setOf("oc.imageCache.p1"), ProfileKeyRule.scopedKeys("p1", keys, setOf("p1", "p2")))
    }
}
