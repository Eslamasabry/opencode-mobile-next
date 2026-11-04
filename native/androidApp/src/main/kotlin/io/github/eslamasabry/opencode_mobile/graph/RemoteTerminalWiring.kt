package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.agents.remote_terminal.presentation.RemoteTerminalTextPolicy

/**
 * The same registered-secret policy used by the live server client, applied after output chunks are
 * joined.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<RemoteTerminalTextPolicy>())
public class NativeRemoteTerminalTextPolicy(private val redactor: Redactor) :
    RemoteTerminalTextPolicy {
    override fun redact(text: String): String = redactor.redact(text)
}
