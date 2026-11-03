package io.github.eslamasabry.oc.feature.about.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which one-time tips were used up. Android replaces [InMemoryTipsStore] with one over `:engine:settings`
 * TipController (+ the nudges), so "Show tips again" resets the real records.
 */
public interface TipsStore {
    /** Ids whose tip is done (shown its times, or the gesture was used). */
    public val done: StateFlow<Set<String>>

    /** Puts every tip, tutorial and one-time card back, so each shows again at its moment. */
    public suspend fun showTipsAgain(): Outcome<Unit>
}

/** Desktop and tests: kept for the run only. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class InMemoryTipsStore : TipsStore {
    private val mutableDone = MutableStateFlow<Set<String>>(emptySet())
    override val done: StateFlow<Set<String>> = mutableDone.asStateFlow()

    public fun markDone(id: String) {
        mutableDone.value = mutableDone.value + id
    }

    override suspend fun showTipsAgain(): Outcome<Unit> {
        mutableDone.value = emptySet()
        return Unit.ok()
    }
}

/**
 * Runs a mini tutorial on its own screen ("Make it yours" opens Appearance and runs there; What's new opens its
 * sheet). Nothing is counted for a replay. False when this build cannot run it: the page then shows the tutorial's
 * offer sentence in place, so Show me is never a dead button.
 */
public fun interface TourLauncher {
    public fun start(id: String): Boolean
}

/** Desktop and tests: no tutorial screens are wired. */
@ContributesBinding(AppScope::class)
@Inject
public class NoTourLauncher : TourLauncher {
    override fun start(id: String): Boolean = false
}
