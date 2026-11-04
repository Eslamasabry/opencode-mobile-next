package io.github.eslamasabry.oc.feature.projects.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One live connection the projects pages use; [gateway] is that exact live graph's gateway. */
public class ProjectServer(
    public val profile: ProfileId,
    public val name: String,
    public val gateway: ServerGateway,
    public val operations: ServerOperationsGateway?,
    /** Capability to open shared folders on this phone for this connection. */
    public val phoneFolders: Boolean = false,
)

/** The live connections and the active one. The app binds the real one (ProjectsWiring). */
public interface ProjectServers {
    public val servers: StateFlow<ImmutableList<ProjectServer>>

    public val active: StateFlow<ProfileId?>
}

@ContributesBinding(AppScope::class)
@Inject
public class NoProjectServers : ProjectServers {
    override val servers: StateFlow<ImmutableList<ProjectServer>> =
        MutableStateFlow<ImmutableList<ProjectServer>>(persistentListOf()).asStateFlow()
    override val active: StateFlow<ProfileId?> = MutableStateFlow<ProfileId?>(null).asStateFlow()
}

/**
 * The folders the person opened on this phone, newest first, per server (B-ANDR-078). The app
 * stores them under `oc.openedProjects.<profileId>`, so the profile sweep removes them with the
 * server.
 */
public interface OpenedFolders {
    public fun recent(profile: ProfileId): ImmutableList<String>

    public suspend fun remember(profile: ProfileId, directory: String): Outcome<Unit>
}

/** Desktop and tests: kept for the run only. Android replaces it with the stored one. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class SessionOpenedFolders : OpenedFolders {
    private val byProfile = MutableStateFlow<Map<ProfileId, List<String>>>(emptyMap())

    override fun recent(profile: ProfileId): ImmutableList<String> =
        byProfile.value[profile].orEmpty().toImmutableList()

    override suspend fun remember(profile: ProfileId, directory: String): Outcome<Unit> {
        val old = byProfile.value[profile].orEmpty()
        byProfile.value = byProfile.value + (profile to ProjectRules.remembered(old, directory))
        return Unit.ok()
    }
}
