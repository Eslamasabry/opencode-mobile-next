package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.engine.runtime.install.ManagedRuntime
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.server.OpenCodeAutoUpdater
import io.github.eslamasabry.oc.engine.runtime.server.OpenCodeHostFacts
import io.github.eslamasabry.oc.engine.runtime.server.OpenCodeUpdateJobs
import io.github.eslamasabry.oc.engine.runtime.server.launchOpenCodeAutoUpdate
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the phone server on the OpenCode version this app pins, by itself (Flutter `inAppAutoUpdateProvider`): the
 * same job as This phone's "Update OpenCode" row, once per app run, only when nothing is working. It looks at
 * start and whenever the host, the setup job or the work on the server changes.
 */
@SingleIn(AppScope::class)
@Inject
class PhoneOpenCodeAutoUpdate(
    private val runtime: BuiltinRuntime,
    private val setup: NativePhoneSetup,
    private val leases: NativeChatLeases,
    private val appScope: AppCoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        val host =
            object : OpenCodeHostFacts {
                override suspend fun refresh() = this@PhoneOpenCodeAutoUpdate.runtime.refreshServer()

                override val installedVersion: String? get() = this@PhoneOpenCodeAutoUpdate.runtime.serverFacts.value.installedVersion
                override val pinnedVersion: String get() = ManagedRuntime.OpenCode2.pinnedVersion
                override val runtime: String get() = ManagedRuntime.OpenCode2.wireName
                override val switchPending: Boolean get() = this@PhoneOpenCodeAutoUpdate.runtime.switchPending()
                override val installProblem: Boolean
                    get() = this@PhoneOpenCodeAutoUpdate.runtime.serverFacts.value.status?.phase == RuntimePhase.Failed
            }
        val jobs =
            object : OpenCodeUpdateJobs {
                override val running: Boolean get() = setup.progress.value.state == SetupState.Running
                override val failed: Boolean get() = setup.progress.value.state == SetupState.Failed

                // Left to the manual row on failure; the setup job reports its own.
                override suspend fun updateOpenCode(runtime: String) {
                    setup.update()
                }
            }
        val updater = OpenCodeAutoUpdater(host, jobs) { !runtime.workKnownIdle() }
        appScope.launchOpenCodeAutoUpdate(
            updater,
            host,
            combine(runtime.serverFacts, setup.progress, leases.busy) { _, _, _ -> },
        )
        appScope.launch {
            setup.progress.collect {
                runtime.noteUpdateJob(it.state == SetupState.Running, it.state == SetupState.Failed)
            }
        }
    }
}

@ContributesTo(AppScope::class)
interface PhoneOpenCodeAutoUpdateAccessors {
    val phoneOpenCodeAutoUpdate: PhoneOpenCodeAutoUpdate
}
