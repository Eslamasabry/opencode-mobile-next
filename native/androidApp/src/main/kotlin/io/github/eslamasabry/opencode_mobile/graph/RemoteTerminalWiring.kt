package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.terminal.AndroidRemoteTerminalRenderer
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.domain.RemoteTerminalRenderer
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

/**
 * The Android app chooses the actual emulator; feature screens see only the portable view handle.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<RemoteTerminalRenderer>())
class AppRemoteTerminalRenderer(app: Application, dispatchers: AppDispatchers, redactor: Redactor) :
    RemoteTerminalRenderer by AndroidRemoteTerminalRenderer(app, dispatchers, redactor)
