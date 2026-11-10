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
import android.os.SystemClock
import io.github.eslamasabry.oc.engine.runtime.server.OpenCodeIdleSettle
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponentIds
import kotlinx.coroutines.flow.MutableSharedFlow
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.merge
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
    private val log = AppLog.tagged("OcAutoUpdate")

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

                // Refused before it started (not known idle, a job running): the try is not spent. A job that
                // started and failed is left to the manual row; the setup job reports its own failure.
                override suspend fun updateOpenCode(runtime: String): Boolean {
                    val result = setup.update()
                    val refused =
                        (result as? Outcome.Failed)?.failure.let {
                            it is Failure.Runtime && it.problem == RuntimeProblem.SlotBusy
                        }
                    if (refused) log.i { "update refused before it started (work running or not known idle)" }
                    return !refused
                }
            }
        val settled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        // `leases.busy` reads false only after a real status read of a connected server (NativeChatLeases `known`), and
        // goes back to unknown when that read fails; the 30 s settle on top is Flutter's `inAppAutoUpdateSettle`.
        val settle = OpenCodeIdleSettle(appScope, { SystemClock.elapsedRealtime() }) { settled.tryEmit(Unit) }
        // The newest idle reading. The chat-lease map has no entry for the phone server until a chat watch has read it,
        // so a read of its own is made here (while the server is behind the pin), not only when a lease says so.
        val idle =
            java.util.concurrent.atomic.AtomicReference(
                BuiltinRuntime.WorkIdle(idle = false, lease = null, direct = null, agent = false)
            )
        val updater =
            OpenCodeAutoUpdater(
                host,
                jobs,
                log = { log.i { it } },
            ) {
                // Flutter also refuses while `localPhoneAgentWorkBusy`: the agent's `<owner>.agents` reading.
                val reading = idle.get()
                val busy = settle.busy(reading.idle)
                busy
            }
        val reads = flow {
            while (true) {
                val behind = host.installedVersion?.trim()?.removePrefix("v")?.let { it.isNotEmpty() && it != host.pinnedVersion } == true
                if (behind) {
                    val now = runtime.readWorkIdle()
                    if (now != idle.get()) log.i { "work idle=${now.idle} lease=${now.lease} direct=${now.direct} agent=${now.agent}" }
                    idle.set(now)
                }
                emit(Unit)
                delay(30.seconds)
            }
        }
        appScope.launchOpenCodeAutoUpdate(
            updater,
            host,
            merge(combine(runtime.serverFacts, setup.progress, leases.busy) { _, _, _ -> }, settled, reads),
        )
        appScope.launch {
            setup.progress.collect {
                // Only the OpenCode update job (a job of the OpenCode component alone) shows on the Update row.
                val update = it.components.map { c -> c.id } == listOf(SetupComponentIds.OPENCODE)
                runtime.noteUpdateJob(
                    update && it.state == SetupState.Running,
                    update && it.state == SetupState.Failed,
                )
            }
        }
    }
}

@ContributesTo(AppScope::class)
interface PhoneOpenCodeAutoUpdateAccessors {
    val phoneOpenCodeAutoUpdate: PhoneOpenCodeAutoUpdate
}
