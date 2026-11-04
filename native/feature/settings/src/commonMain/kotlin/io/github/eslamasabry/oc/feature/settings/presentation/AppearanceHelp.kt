package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow

/** App-wide guide record; the adapter retains Flutter's oc.appearanceGuide JSON and last visited question. */
public data class AppearanceHelpFacts(val done: Boolean = false, val step: Int = 0)
public interface AppearanceHelp {
    public val facts: StateFlow<AppearanceHelpFacts?>
    public suspend fun save(step: Int, done: Boolean): Outcome<Unit>
    public suspend fun resetTips(): Outcome<Unit>
}
