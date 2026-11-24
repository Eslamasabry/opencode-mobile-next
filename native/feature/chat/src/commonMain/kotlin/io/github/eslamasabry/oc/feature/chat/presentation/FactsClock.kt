package io.github.eslamasabry.oc.feature.chat.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The chat's 1-second clock, held as one instance per [TurnFactsPresenter] and compared by identity so a clock tick
 * alone never changes [TurnFactsUi] equality; only composables that draw a time collect [nowMs].
 */
public class FactsClock(initialMs: Long = 0L) {
    private val current = MutableStateFlow(initialMs)

    public val nowMs: StateFlow<Long> = current.asStateFlow()

    internal fun set(ms: Long) {
        current.value = ms
    }

    public companion object {
        public val Still: FactsClock = FactsClock(0L)

        public fun at(ms: Long): FactsClock = FactsClock(ms)
    }
}
