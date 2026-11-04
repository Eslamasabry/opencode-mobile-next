package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxIdleSource
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxIdleUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidLinuxIdleSource(private val runtime: BuiltinRuntime) : LinuxIdleSource {
    private val mutable = MutableStateFlow(LinuxIdleUi())
    override val state = mutable.asStateFlow()
    private val lane = Mutex()

    override suspend fun refresh() {
        lane.withLock { publish(runtime.runtimeStatus()) }
    }

    override suspend fun configure(enabled: Boolean, minutes: Int): Outcome<Unit> = lane.withLock {
        if (minutes !in 1..60) return@withLock failed()
        publish(runtime.idleConfigure(enabled, minutes))
    }

    override suspend fun resume(): Outcome<Unit> = lane.withLock { publish(runtime.idleResume()) }

    private fun publish(result: Outcome<RuntimeStatus>): Outcome<Unit> {
        if (result is Outcome.Failed) {
            mutable.value = mutable.value.copy(failed = true)
            return result
        }
        val status = (result as Outcome.Ok).value
        val valid = status.serverIdlePolicySupported && status.serverIdleReceiptValid
        mutable.value =
            LinuxIdleUi(
                supported = valid,
                enabled = valid && status.serverIdleEnabled,
                minutes = status.serverIdleMinutes.takeIf { valid },
                stopped = valid && status.serverIdleStopped,
                helperStopped = valid && status.serverIdleHelperStopped,
                failed = status.serverIdlePolicySupported && !valid,
            )
        return if (mutable.value.failed) failed() else Outcome.Ok(Unit)
    }

    private fun failed(): Outcome.Failed =
        Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
}
