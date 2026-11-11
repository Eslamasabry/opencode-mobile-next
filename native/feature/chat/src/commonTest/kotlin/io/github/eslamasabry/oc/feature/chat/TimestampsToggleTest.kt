package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayChoices
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** C10: the chat offers "Show timestamps" only where the device can save the choice. */
class TimestampsToggleTest {
    private class ReadOnly : TranscriptDisplayPreferences {
        override val value: StateFlow<TranscriptDisplayChoices> = MutableStateFlow(TranscriptDisplayChoices())
    }

    private class Writable : TranscriptDisplayPreferences {
        val state = MutableStateFlow(TranscriptDisplayChoices())
        override val value: StateFlow<TranscriptDisplayChoices> = state
        override val canSetTimestamps: Boolean = true

        override fun setTimestamps(visible: Boolean) {
            state.value = state.value.copy(timestampsVisible = visible)
        }
    }

    @Test
    fun `a read-only source is not offered the toggle and ignores a write`() {
        val prefs = ReadOnly()
        assertFalse(prefs.canSetTimestamps)
        prefs.setTimestamps(true)
        assertFalse(prefs.value.value.timestampsVisible)
    }

    @Test
    fun `a writable source flips the shown choice`() {
        val prefs = Writable()
        prefs.setTimestamps(!prefs.value.value.timestampsVisible)
        assertEquals(true, prefs.value.value.timestampsVisible)
    }
}
