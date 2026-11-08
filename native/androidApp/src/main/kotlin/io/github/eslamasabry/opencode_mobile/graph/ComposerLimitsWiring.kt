package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.integrations.usage.ComposerLimitNotice
import io.github.eslamasabry.oc.engine.integrations.usage.ComposerLimitPresenter
import io.github.eslamasabry.oc.engine.integrations.usage.ComposerLimitTarget
import io.github.eslamasabry.oc.feature.chat.composer.ComposerLimits
import io.github.eslamasabry.oc.feature.chat.composer.PresenterComposerLimits
import io.github.eslamasabry.oc.feature.plans.presentation.PlansSource
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTimeZone
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.UsageRoute
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow

/*
 * B-181: the chat composer's limit strip reads the same readings the Usage page shows (PlansSource.snapshot), on the
 * phone's own clock and zone. One app-wide presenter, so a dismissal holds across conversations for this app run.
 * View usage opens Usage for the conversation's server, where Limits lists that provider's windows.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ComposerLimits>())
@Inject
class AppComposerLimits(
    plans: PlansSource,
    clock: Clock,
    zone: UsageTimeZone,
    navigator: AppNavigator,
) : ComposerLimits {
    private val delegate =
        PresenterComposerLimits(
            ComposerLimitPresenter(plans.snapshot, clock, { zone.id() ?: java.util.TimeZone.getDefault().id }),
        ) { _, profileId -> navigator.open(usageRouteFor(profileId)) }

    override fun notice(target: Flow<ComposerLimitTarget?>): Flow<ComposerLimitNotice?> = delegate.notice(target)

    override fun dismiss(notice: ComposerLimitNotice): Unit = delegate.dismiss(notice)

    override fun viewUsage(notice: ComposerLimitNotice, profileId: String): Unit = delegate.viewUsage(notice, profileId)
}

/** Usage for the conversation's own server: its Limits tab lists the provider whose window the strip named. */
internal fun usageRouteFor(profileId: String): UsageRoute =
    UsageRoute.Hub(profile = ProfileId(profileId))
