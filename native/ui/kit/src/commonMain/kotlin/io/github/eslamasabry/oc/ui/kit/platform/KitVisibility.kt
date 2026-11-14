package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Whether the app is on screen: the one signal every kit ticker obeys (background CPU 2026-11-14).
 *
 * Compose stops sending frames to a hidden window, but a `delay` in a composition's coroutine (a `LaunchedEffect`, a
 * modifier node's `coroutineScope`) keeps waking: on the device the words' ink sampler of every Glass pane woke ten
 * times a second with the app in the background, about 40 % of a core with nothing on screen. So a loop that exists only
 * to update pixels (a clock label, an ink sample, a log poll) waits through [tick] or [awaitVisible] instead of a bare
 * `delay`: while the app is hidden it parks with no timer armed and wakes once when the app shows again.
 *
 * The app shell sets [App] from its activities' start and stop (`AppVisibility` in `:androidApp`); desktop and tests leave
 * it visible. Parts read [LocalKitVisibility], so a test can hand one its own.
 */
@Stable
public class KitVisibility(visible: Boolean = true) {
    private val state = MutableStateFlow(visible)

    /** True while any of the app's windows is started (on screen). */
    public val visible: StateFlow<Boolean> = state.asStateFlow()

    public fun set(visible: Boolean) {
        state.value = visible
    }

    /** Returns at once while the app shows; otherwise parks, with no timer armed, until it shows again. */
    public suspend fun awaitVisible() {
        if (!state.value) state.first { it }
    }

    /**
     * The pause of a ticker that exists to update pixels: [millis], then however long the app stays hidden. A hidden
     * app so costs one wake at most; back on screen the ticker runs at once, so the person sees a fresh value.
     */
    public suspend fun tick(millis: Long) {
        delay(millis)
        awaitVisible()
    }

    /** [tick] for a [wait] that is a [Duration] (rounded up to whole milliseconds, as `delay` does). */
    public suspend fun tick(wait: Duration) {
        delay(wait)
        awaitVisible()
    }

    public companion object {
        /** The process's own signal, set by the app shell. */
        public val App: KitVisibility = KitVisibility()
    }
}

/** The visibility kit tickers obey; [KitVisibility.App] unless a test provides its own. */
public val LocalKitVisibility: ProvidableCompositionLocal<KitVisibility> = staticCompositionLocalOf { KitVisibility.App }
