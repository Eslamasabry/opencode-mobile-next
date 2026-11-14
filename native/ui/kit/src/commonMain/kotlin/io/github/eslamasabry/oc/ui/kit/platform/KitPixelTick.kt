package io.github.eslamasabry.oc.ui.kit.platform

import kotlinx.coroutines.delay

/**
 * The pause of a loop that exists only to change pixels (a timer's text once a second): [millis], then nothing else.
 * Every such loop in the kit waits here instead of a bare `delay`, so the one rule "a ticker parks while the app is
 * hidden" is swapped in at one place: when the app's visibility signal (`KitVisibility`, background CPU 2026-11-14) is
 * on this line, this becomes `KitVisibility.App.tick(millis)` and every caller parks with it.
 */
public suspend fun kitPixelTick(millis: Long) {
    delay(millis)
}
