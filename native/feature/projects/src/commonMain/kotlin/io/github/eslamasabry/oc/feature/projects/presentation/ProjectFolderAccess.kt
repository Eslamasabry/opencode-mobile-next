package io.github.eslamasabry.oc.feature.projects.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/** This phone's OS gate, scoped to the exact selected saved server and project. */
public interface ProjectFolderAccess {
    public suspend fun blocked(profile: ProfileId, directory: String): Boolean

    public suspend fun request(profile: ProfileId, directory: String): Outcome<Unit>
}

@ContributesTo(AppScope::class)
public interface ProjectFolderAccessBindings {
    @Multibinds(allowEmpty = true) public val projectFolderAccess: Set<ProjectFolderAccess>
}
