package io.github.eslamasabry.oc.engine.updates

import kotlinx.coroutines.delay

/** How long "Updated to version X" holds on screen before it leaves by itself (B-217). */
public const val UPDATED_NOTICE_HOLD_MS: Long = 6_000L

/** Waits out the hold of the "Updated to version X" line; the caller then dismisses it as Not now would. */
public suspend fun holdUpdatedNotice() {
    delay(UPDATED_NOTICE_HOLD_MS)
}
