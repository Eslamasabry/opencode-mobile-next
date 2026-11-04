package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingIds
import io.github.eslamasabry.oc.engine.settings.teaching.TipController
import io.github.eslamasabry.oc.engine.settings.teaching.TipMoment
import io.github.eslamasabry.oc.engine.settings.teaching.TipVisit
import io.github.eslamasabry.oc.feature.projects.terminal.NoTerminalTeaching
import io.github.eslamasabry.oc.feature.projects.terminal.TerminalTeaching
import io.github.eslamasabry.oc.feature.projects.terminal.TerminalTipVisit
import kotlin.time.Clock
import kotlinx.coroutines.flow.map

/**
 * One teaching owner for contextual offers, Help replay and Show tips again. Reads follow store
 * warm-up.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object TeachingBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun controller(stores: PreviewStores, clock: Clock, scope: AppCoroutineScope): TipController =
        TipController(stores.prefs, stores.lanes, clock, scope)
}

@Inject
@ContributesBinding(AppScope::class, replaces = [NoTerminalTeaching::class])
class AndroidTerminalTeaching(private val stores: PreviewStores, private val tips: TipController) :
    TerminalTeaching {
    override fun visit(): TerminalTipVisit =
        object : TerminalTipVisit {
            private val visit = TipVisit()
            override val visible = tips.active.map { it?.id == TeachingIds.TerminalSelection }

            override suspend fun offer(blocked: () -> Boolean) {
                if (stores.warm() is Outcome.Failed) return
                tips.offer(
                    TeachingIds.TerminalSelection,
                    visit = visit,
                    moment = TipMoment(questionWaiting = blocked()),
                )
            }

            override fun used() {
                tips.markUsed(TeachingIds.TerminalSelection)
            }

            override fun dismiss() {
                tips.dismiss(TeachingIds.TerminalSelection)
            }
        }
}
