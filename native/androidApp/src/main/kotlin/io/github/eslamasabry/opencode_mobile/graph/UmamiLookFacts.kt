package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.engine.analytics.umami.UmamiFacts
import io.github.eslamasabry.oc.engine.analytics.umami.UmamiField
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the style word for our own analytics server up to date while the app runs: the app's own six styles, as a
 * fixed word (never the colours, never a person's choice of anything else). The word only waits in memory; nothing
 * leaves the phone unless the usage-reports switch is on.
 */
@SingleIn(AppScope::class)
@Inject
class UmamiLookFacts(
    // Lazy: read only when start() runs, so building this changes no start-up order.
    private val preferences: Lazy<AppPreferences>,
    private val appScope: AppCoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        appScope.launch {
            preferences.value.appearance
                .map { it.style.storageName }
                .distinctUntilChanged()
                .collect { style -> UmamiFacts.put(UmamiField.Style, style) }
        }
    }
}

@ContributesTo(AppScope::class)
interface UmamiLookFactsAccessors {
    val umamiLookFacts: UmamiLookFacts
}
