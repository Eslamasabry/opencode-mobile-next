package io.github.eslamasabry.oc.feature.projects.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.devservers.ProjectDevServer

/** One project-scoped reader, retired with its page or connection. */
public interface ProjectSignalsHost {
    public fun devServer(profile: ProfileId, directory: String): ProjectDevServer?

    public suspend fun applyNewChatDefault(
        server: ProjectServer,
        session: io.github.eslamasabry.oc.core.common.ids.SessionId,
    ): io.github.eslamasabry.oc.core.common.Outcome<Unit> =
        io.github.eslamasabry.oc.core.common.Outcome.Ok(Unit)
}

@ContributesBinding(AppScope::class)
@Inject
public class NoProjectSignalsHost : ProjectSignalsHost {
    override fun devServer(profile: ProfileId, directory: String): ProjectDevServer? = null
}
