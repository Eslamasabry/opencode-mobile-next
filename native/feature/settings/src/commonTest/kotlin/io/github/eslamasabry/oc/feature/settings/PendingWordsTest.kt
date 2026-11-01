package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.engine.analytics.ConsentState
import io.github.eslamasabry.oc.engine.analytics.NoOpSink
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollectSource
import io.github.eslamasabry.oc.feature.settings.ui.PendingWords
import kotlin.test.Test
import kotlin.test.assertTrue

class PendingWordsTest {
    @Test
    fun `plan 18 6 every event the schema can send has its plain words on the page`() {
        val view = WhatWeCollectSource.build(ConsentState(), NoOpSink)
        val missing = (view.basicEvents + view.detailedEvents).map { it.name }.filter { PendingWords.events[it].isNullOrBlank() }
        assertTrue(missing.isEmpty(), "events with no words: $missing")
        assertTrue(view.envelope.all { PendingWords.envelope(it).isNotBlank() })
        assertTrue(view.never.all { PendingWords.never(it).isNotBlank() })
    }
}
