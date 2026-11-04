package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.procs.DetectedDevelopmentServers
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentServices
import kotlinx.coroutines.flow.Flow

/**
 * App bindings own storage and exact live-graph identity; this feature never changes the chat
 * gateway location.
 */
public interface LinuxServicesSource {
    public val changes: Flow<Unit>

    public suspend fun open(
        profile: ProfileId,
        directory: String,
        workspace: String?,
        current: () -> Boolean,
    ): Outcome<LinuxServicesSession>
}

public interface LinuxServicesSession {
    public val services: DevelopmentServices
    public val detected: DetectedDevelopmentServers
    public val supported: Boolean

    /** Admitted starts finish their ownership receipt even after the calling route closes. */
    public fun redact(text: String): String

    public suspend fun command(block: suspend () -> Unit)
}
