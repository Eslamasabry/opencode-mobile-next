package io.github.eslamasabry.oc.android.widgets

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner

/**
 * Include in the app graph's ProfileResourceCleaner set: public snapshot and follow-up work are
 * embedded state.
 */
public class WidgetProfileCleaner(private val surface: AndroidWidgetSurface) :
    ProfileResourceCleaner {
    override val owner: String = ":android:widgets"
    override val order: Int = DeletionStep.DEVICE_SURFACES

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        when (surface.clearProfile(profileId.raw)) {
            ClearOutcome.NothingToClear -> Outcome.Ok(0)
            ClearOutcome.Cleared -> Outcome.Ok(1)
            ClearOutcome.Failed -> Outcome.Failed(Failure.StorageRefused())
        }
}
