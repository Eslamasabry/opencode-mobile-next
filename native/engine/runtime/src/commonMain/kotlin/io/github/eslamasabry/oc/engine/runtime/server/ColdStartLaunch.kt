package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * B-486: On cold start, phone-agent helpers boot alongside OpenCode instead of waiting behind the
 * primary server's boot and initial connection.
 */
public object ColdStartLaunch {
    /** The active server and any shown phone-agent helpers to prepare on cold start. */
    public class Targets(
        public val active: ProfileId?,
        public val phoneHelpers: List<ProfileId>,
    )

    /** Callbacks that seed saved chats and boot phone-agent helpers before primary connect awaits. */
    public class HelperHooks(
        public val seed: suspend (ProfileId) -> Unit,
        public val start: (ProfileId) -> Unit,
        public val adopt: () -> Unit,
    )

    /** Callbacks for connecting the primary server and syncing any remaining secondary servers. */
    public class PrimaryHooks<T>(
        public val connect: suspend (ProfileId) -> T,
        public val empty: () -> T,
        public val onComplete: (T) -> Unit,
        public val syncRemaining: suspend () -> Unit,
    )

    /**
     * Seeds [targets], starts shown phone helpers and adoption alongside primary connect, completes
     * [PrimaryHooks.onComplete], and then runs [PrimaryHooks.syncRemaining].
     */
    public suspend fun <T> run(
        targets: Targets,
        helpers: HelperHooks,
        primary: PrimaryHooks<T>,
    ) {
        if (targets.active != null) helpers.seed(targets.active)
        for (helper in targets.phoneHelpers) helpers.seed(helper)
        for (helper in targets.phoneHelpers) helpers.start(helper)
        helpers.adopt()
        val result = if (targets.active != null) primary.connect(targets.active) else primary.empty()
        primary.onComplete(result)
        primary.syncRemaining()
    }
}
