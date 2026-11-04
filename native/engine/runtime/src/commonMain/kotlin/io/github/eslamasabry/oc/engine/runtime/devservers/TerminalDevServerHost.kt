package io.github.eslamasabry.oc.engine.runtime.devservers

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The current project for a terminal, using its explicit server or the app's selected server. */
public class TerminalDevServerSession(
    public val profile: ProfileId,
    public val signal: ProjectDevServer,
)

public interface TerminalDevServerHost {
    public fun sessions(profile: ProfileId? = null): Flow<TerminalDevServerSession?>
}

@ContributesBinding(AppScope::class)
@Inject
public class NoTerminalDevServerHost : TerminalDevServerHost {
    override fun sessions(profile: ProfileId?): Flow<TerminalDevServerSession?> = flowOf(null)
}
