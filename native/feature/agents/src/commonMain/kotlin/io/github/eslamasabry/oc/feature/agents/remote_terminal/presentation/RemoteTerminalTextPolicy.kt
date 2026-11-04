package io.github.eslamasabry.oc.feature.agents.remote_terminal.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/**
 * Re-applied to the complete bounded transcript so a credential split between network frames is
 * masked.
 */
public fun interface RemoteTerminalTextPolicy {
    public fun redact(text: String): String
}

@ContributesTo(AppScope::class)
public interface RemoteTerminalTextPolicies {
    @Multibinds(allowEmpty = true)
    public val remoteTerminalTextPolicies: Set<RemoteTerminalTextPolicy>
}
