package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.engine.analytics.product.ProductStarter
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StarterAnalyticsTest {
    @Test
    fun `B-NEXT-063 every starter kind has a word of its own, so a new kind cannot be sent as other by accident`() {
        val words = StarterKind.entries.map { ProductStarter.of(it.name) }
        words.forEach { assertNotEquals(ProductStarter.Other, it) }
        assertEquals(StarterKind.entries.size, words.toSet().size)
    }
}
