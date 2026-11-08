package io.github.eslamasabry.oc.feature.chat.composer

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.engine.integrations.usage.ComposerLimitLevel
import io.github.eslamasabry.oc.engine.integrations.usage.ComposerLimitNotice
import io.github.eslamasabry.oc.engine.integrations.usage.ComposerLimitPresenter
import io.github.eslamasabry.oc.engine.integrations.usage.ComposerLimitReset
import io.github.eslamasabry.oc.engine.integrations.usage.ComposerLimitTarget
import io.github.eslamasabry.oc.ui.kitchat.composer.KitLimitNotice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitLimitReset
import kotlinx.coroutines.flow.Flow

/**
 * B-181 (owner 2026-11-08): the "Approaching limit" strip in the composer box's `above` slot. The app binds one over
 * the readings Usage › Plans shows (`PlansSource.snapshot`); without a binding (desktop, tests) there is no strip.
 */
public interface ComposerLimits {
    /** The strip for the open conversation, or null when there is nothing true to say. */
    public fun notice(target: Flow<ComposerLimitTarget?>): Flow<ComposerLimitNotice?>

    /** ✕: hides [notice] until its window resets or reaches the next threshold. */
    public fun dismiss(notice: ComposerLimitNotice)

    /** View usage: the Usage page, for the provider and connection the numbers came from. */
    public fun viewUsage(notice: ComposerLimitNotice, profileId: String)
}

/** [ComposerLimits] over the engine's presenter; [openUsage] is the app's navigation. */
public class PresenterComposerLimits(
    private val presenter: ComposerLimitPresenter,
    private val openUsage: (notice: ComposerLimitNotice, profileId: String) -> Unit,
) : ComposerLimits {
    override fun notice(target: Flow<ComposerLimitTarget?>): Flow<ComposerLimitNotice?> = presenter.notice(target)

    override fun dismiss(notice: ComposerLimitNotice): Unit = presenter.dismiss(notice)

    override fun viewUsage(notice: ComposerLimitNotice, profileId: String): Unit = openUsage(notice, profileId)
}

@ContributesTo(AppScope::class)
public interface ComposerLimitsBindings {
    @Multibinds(allowEmpty = true) public val composerLimits: Set<ComposerLimits>
}

/** The open conversation's provider and model as the strip's target; null while no model is known. */
public fun composerLimitTarget(providerId: String?, modelId: String?, profileId: String?): ComposerLimitTarget? =
    providerId?.takeIf { it.isNotBlank() }?.let { ComposerLimitTarget(it, modelId, connectionId = profileId) }

/** The engine's notice in the kit's words (the strip words it with strings_s24_limit). */
public fun ComposerLimitNotice.toKit(): KitLimitNotice =
    KitLimitNotice(
        reached = level == ComposerLimitLevel.Reached,
        reset =
            when (val r = reset) {
                is ComposerLimitReset.Within -> KitLimitReset.Within(r.hours, r.minutes)
                is ComposerLimitReset.OnDay -> KitLimitReset.OnDay(r.isoWeekday, r.hour, r.minute)
                is ComposerLimitReset.OnDate -> KitLimitReset.OnDate(r.epochMs)
                null -> null
            },
    )
