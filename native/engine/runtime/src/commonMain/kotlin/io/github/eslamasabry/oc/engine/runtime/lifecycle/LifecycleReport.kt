package io.github.eslamasabry.oc.engine.runtime.lifecycle

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.recovery.LaunchFacts
import io.github.eslamasabry.oc.engine.runtime.recovery.PreviousExitKind
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Current answers only: a historical PID never proves that a server recovered. */
public enum class LifecycleReadiness {
    Unchecked,
    Checking,
    Unavailable,
    Stopped,
    Unreachable,
    Partial,
    Ready,
}

public data class LifecycleReport(
    val readiness: LifecycleReadiness = LifecycleReadiness.Unchecked,
    val exitKind: PreviousExitKind? = null,
    val stoppedAt: Instant? = null,
    val hadServices: Boolean = false,
    val dismissed: Boolean = false,
) {
    public val showNotice: Boolean
        get() = !dismissed && hadServices && exitKind?.notable == true

    public val everythingBack: Boolean
        get() = showNotice && readiness == LifecycleReadiness.Ready
}

/**
 * An opaque generation of the selected, saved built-in profile. No credentials cross this boundary.
 */
public data class LifecycleTarget(val identity: String, val teamConfigured: Boolean)

public interface LifecyclePorts {
    public fun target(): LifecycleTarget?

    public suspend fun history(): Outcome<LaunchFacts>

    public suspend fun status(): Outcome<RuntimeStatus>

    public suspend fun authenticatedHealth(target: LifecycleTarget): Outcome<Boolean>

    public suspend fun teamSupervisorHealth(): Outcome<Boolean>

    public suspend fun teamCityHealth(): Outcome<Boolean>
}

/** B-STATE-025/027. Foreground observation only: no starts, timers, persistence or retries. */
public class LifecycleReportController(private val ports: LifecyclePorts) {
    private val mutable = MutableStateFlow(LifecycleReport())
    public val value: StateFlow<LifecycleReport> = mutable.asStateFlow()
    private var history: LaunchFacts? = null
    private var epoch: Long = 0
    private var disposed = false
    private var dismissed = false

    public fun invalidate() {
        if (disposed) return
        epoch++
        publish(LifecycleReadiness.Unchecked)
    }

    public fun dismiss() {
        if (disposed) return
        dismissed = true
        publish(mutable.value.readiness)
    }

    public fun close() {
        disposed = true
        epoch++
    }

    public suspend fun refresh() {
        if (disposed) return
        val mine = ++epoch
        val target = ports.target()
        fun current(): Boolean = !disposed && epoch == mine && ports.target() == target
        if (target == null) {
            publish(LifecycleReadiness.Unavailable)
            return
        }
        publish(LifecycleReadiness.Checking)
        val observed =
            attempt({ Failure.Device() }) {
                val past =
                    history
                        ?: when (val read = ports.history()) {
                            is Outcome.Failed -> return@attempt LifecycleReadiness.Unavailable
                            is Outcome.Ok -> read.value
                        }
                if (!current()) return@attempt null
                history = past
                val now =
                    when (val read = ports.status()) {
                        is Outcome.Failed -> return@attempt LifecycleReadiness.Unavailable
                        is Outcome.Ok -> read.value
                    }
                if (!current()) return@attempt null
                if (!now.installed) return@attempt LifecycleReadiness.Unavailable
                if (!now.serverRunning) return@attempt LifecycleReadiness.Stopped
                val healthy = (ports.authenticatedHealth(target) as? Outcome.Ok)?.value == true
                if (!current()) return@attempt null
                if (!healthy) return@attempt LifecycleReadiness.Unreachable
                if (
                    past.previousServices.any { it != LaunchFacts.SERVER && it != LaunchFacts.TEAM }
                )
                    return@attempt LifecycleReadiness.Partial
                if (LaunchFacts.TEAM in past.previousServices || target.teamConfigured) {
                    if (!target.teamConfigured || LaunchFacts.TEAM !in now.services)
                        return@attempt LifecycleReadiness.Partial
                    val supervisor = (ports.teamSupervisorHealth() as? Outcome.Ok)?.value == true
                    if (!current()) return@attempt null
                    if (!supervisor) return@attempt LifecycleReadiness.Partial
                    val city = (ports.teamCityHealth() as? Outcome.Ok)?.value == true
                    if (!current()) return@attempt null
                    if (!city) return@attempt LifecycleReadiness.Partial
                }
                LifecycleReadiness.Ready
            }
        if (!current()) return
        when (observed) {
            is Outcome.Failed -> publish(LifecycleReadiness.Unavailable)
            is Outcome.Ok -> observed.value?.let(::publish)
        }
    }

    private fun publish(readiness: LifecycleReadiness) {
        mutable.value =
            LifecycleReport(
                readiness,
                history?.exit?.kind,
                history?.exit?.at,
                history?.previousServices?.isNotEmpty() == true,
                dismissed,
            )
    }
}

/**
 * File measurements, never file contents. Missing installation is a successful empty measurement.
 */
public data class ProjectStorageFacts(
    val name: String,
    val bytes: Long,
    val files: Int,
    val privateFiles: Int,
)

public data class AppStorageFacts(
    val projects: List<ProjectStorageFacts> = emptyList(),
    val serverInstalled: Boolean = false,
    val serverBytes: Long = 0,
    val privateDataBytes: Long = 0,
    val savedServers: Int = 0,
)
