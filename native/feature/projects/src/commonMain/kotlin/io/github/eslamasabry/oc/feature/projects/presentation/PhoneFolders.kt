package io.github.eslamasabry.oc.feature.projects.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList

/** A read-only view of the phone's shared storage, supplied only by the Android app. */
public interface PhoneFolders {
    public suspend fun list(path: String): Outcome<ImmutableList<PhoneFolderUi>>

    /** True only for a connection to this app's built-in Linux, never a remote SSH tunnel. */
    public fun builtinAvailable(profile: ProfileId): Boolean = false

    public suspend fun listBuiltin(path: String): Outcome<ImmutableList<PhoneFolderUi>> =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    public suspend fun requestAccess(): Outcome<Unit> =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
}

public data class PhoneFolderUi(val name: String, val path: String, val hint: String? = null)

@ContributesBinding(AppScope::class)
@Inject
public class NoPhoneFolders : PhoneFolders {
    override suspend fun list(path: String): Outcome<ImmutableList<PhoneFolderUi>> =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
}
