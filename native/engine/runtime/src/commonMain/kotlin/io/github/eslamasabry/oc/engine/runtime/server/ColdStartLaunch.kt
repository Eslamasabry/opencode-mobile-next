package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * B-486: On cold start, phone-agent helpers boot alongside OpenCode instead of waiting behind the
 * primary server's boot and initial connection.
 */
public object ColdStartLaunch {
    /**
     * Seeds [active] and every shown [phoneHelpers], starts [phoneHelpers] and [adoptHelpers]
     * alongside [connectPrimary], completes [onPrimaryComplete], and then runs [syncRemaining]
     * for any other secondary servers.
     */
    public suspend fun <T> run(
        active: ProfileId?,
        phoneHelpers: List<ProfileId>,
        seed: suspend (ProfileId) -> Unit,
        startHelper: (ProfileId) -> Unit,
        adoptHelpers: () -> Unit,
        connectPrimary: suspend (ProfileId) -> T,
        emptyPrimary: () -> T,
        onPrimaryComplete: (T) -> Unit,
        syncRemaining: suspend () -> Unit,
    ) {
        if (active != null) seed(active)
        for (helper in phoneHelpers) seed(helper)
        for (helper in phoneHelpers) startHelper(helper)
        adoptHelpers()
        val primary = if (active != null) connectPrimary(active) else emptyPrimary()
        onPrimaryComplete(primary)
        syncRemaining()
    }
}
