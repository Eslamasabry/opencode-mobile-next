package io.github.eslamasabry.oc.engine.migration

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The waiting screens' clock (01 §9.7): false at once, true after [afterMs] ("Still updating" and Try again). Time
 * comes from the collector's dispatcher, so tests run it on virtual time. One shot, not a loop.
 */
public fun slowAfter(afterMs: Long): Flow<Boolean> = flow {
    emit(false)
    delay(afterMs)
    emit(true)
}
