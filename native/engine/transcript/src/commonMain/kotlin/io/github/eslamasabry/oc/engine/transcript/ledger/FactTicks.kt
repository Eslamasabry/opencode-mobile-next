package io.github.eslamasabry.oc.engine.transcript.ledger

import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * The turn facts' clock (CHAT-TRANSCRIPT): the time once while [on] is false, and every [everyMs] while it is true (a
 * run, a retry countdown, a running step's own time). An idle conversation therefore never wakes. Uses the injected
 * [clock]; the wait is the collector's own (virtual time in tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun factTicks(clock: Clock, on: Flow<Boolean>, everyMs: Long = 1_000): Flow<Long> =
    on.distinctUntilChanged().flatMapLatest { ticking ->
        if (ticking) {
            flow {
                while (true) {
                    emit(clock.now().toEpochMilliseconds())
                    delay(everyMs)
                }
            }
        } else {
            flowOf(clock.now().toEpochMilliseconds())
        }
    }

/** `flatMapLatest` for the chat feature, whose files import no experimental coroutine API (K1). */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <T, R> Flow<T>.latest(transform: (T) -> Flow<R>): Flow<R> = flatMapLatest { transform(it) }
