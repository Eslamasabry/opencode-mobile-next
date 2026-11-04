package io.github.eslamasabry.oc.engine.settings.teaching

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The three release-tour targets Flutter actually wired; chat paste/mic targets were absent. */
public enum class ShowMeTarget {
    Updates,
    Agents,
    AppearanceText,
}

/**
 * A transient manual arrival owns the same teaching slot as contextual tips. Nothing is polled or
 * persisted.
 */
public class ShowMeTargets(private val tips: TipController, private val scope: CoroutineScope) {
    private val requested = MutableStateFlow<ShowMeTarget?>(null)
    private var expiry: Job? = null
    private var generation = 0L
    public val active: StateFlow<ShowMeTarget?> =
        combine(requested, tips.active) { target, tip ->
                target.takeIf { tip?.id == TeachingIds.WhatsNew }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    public fun request(target: ShowMeTarget) {
        val token = ++generation
        expiry?.cancel()
        requested.value = target
        tips.replay(TeachingIds.WhatsNew)
        expiry = scope.launch {
            delay(30_000)
            if (generation == token) dismiss(target)
        }
    }

    public fun dismiss(target: ShowMeTarget) {
        if (requested.value != target) return
        generation++
        requested.value = null
        expiry?.cancel()
        expiry = null
        tips.dismiss(TeachingIds.WhatsNew)
    }
}
