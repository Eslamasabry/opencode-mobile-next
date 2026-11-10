package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.install.ManagedRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What This phone shows about OpenCode's version: the installed one and the one this app pins. */
public data class OpenCodeVersions(val installed: String?, val pinned: String?) {
    public companion object {
        /**
         * Port of the Flutter host's `version` (the phone profile's saved server version, read only while Linux is
         * installed) and `runtime.pinnedVersion` (only OpenCode 2 has a pin). [openCode2] is false while the saved
         * server still needs the move from OpenCode 1.
         */
        public fun of(status: RuntimeStatus?, profileVersion: String?, openCode2: Boolean): OpenCodeVersions =
            OpenCodeVersions(
                installed = if (status?.installed == true) profileVersion?.trim()?.takeIf { it.isNotEmpty() } else null,
                pinned = if (openCode2) ManagedRuntime.OpenCode2.pinnedVersion else null,
            )
    }
}

/**
 * Starts the automatic OpenCode update (port of `InAppAutoUpdater`'s listeners): the host is read once, then the
 * updater decides at start and again on every [changes] value (work started or ended, host state changed). The
 * updater itself keeps the once-per-run and not-while-busy rules.
 */
public fun CoroutineScope.launchOpenCodeAutoUpdate(
    updater: OpenCodeAutoUpdater,
    host: OpenCodeHostFacts,
    changes: Flow<*>,
): Job = launch {
    host.refresh()
    updater.changed()
    changes.collect { updater.changed() }
}
