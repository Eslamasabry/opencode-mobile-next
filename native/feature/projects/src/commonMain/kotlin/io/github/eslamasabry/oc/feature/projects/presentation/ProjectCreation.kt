package io.github.eslamasabry.oc.feature.projects.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome

/**
 * Creates one safe folder under the app-managed phone runtime's /root/projects. Remote servers
 * cannot create.
 */
public interface ProjectCreation {
    public fun available(server: ProjectServer): Boolean

    public suspend fun create(server: ProjectServer, name: String): Outcome<String>
}

@ContributesBinding(AppScope::class)
@Inject
public class NoProjectCreation : ProjectCreation {
    override fun available(server: ProjectServer): Boolean = false

    override suspend fun create(server: ProjectServer, name: String): Outcome<String> =
        Outcome.Failed(Failure.Unavailable(FeatureId("projectFolderCreation")))
}
