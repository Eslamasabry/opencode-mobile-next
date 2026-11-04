package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.android.terminal.AndroidRemoteTerminalRenderer
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.domain.RemoteTerminalRenderer
import io.github.eslamasabry.oc.core.security.Redactor

/**
 * The Android app chooses the actual emulator; feature screens see only the portable view handle.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<RemoteTerminalRenderer>())
class AppRemoteTerminalRenderer(app: Application, dispatchers: AppDispatchers, redactor: Redactor) :
    RemoteTerminalRenderer by AndroidRemoteTerminalRenderer(app, dispatchers, redactor)
