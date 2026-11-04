package io.github.eslamasabry.oc.engine.runtime.setupassistant

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.domain.SetupInspection
import io.github.eslamasabry.oc.core.domain.SetupInspectionGateway
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public enum class SetupInspectionPhase {
    Idle,
    Loading,
    Ready,
    Offline,
    Failed,
}

public data class SetupInspectionState(
    val phase: SetupInspectionPhase = SetupInspectionPhase.Idle,
    val inspection: SetupInspection? = null,
    val failure: Failure? = null,
)

/** One refresh's gateway, and the check that it still belongs to the live connection and folder. */
public class SetupInspectionBinding(
    public val gateway: SetupInspectionGateway,
    public val current: () -> Boolean,
)

/**
 * Location-bound inspection. Each refresh binds to the connection live at that moment, so a
 * reconnect is picked up; offline, close, or a new location invalidates a pending response before
 * publication.
 */
public class SetupInspectionController(private val bind: () -> SetupInspectionBinding?) {
    private val operation = Mutex()
    private val mutable = MutableStateFlow(SetupInspectionState())
    public val state: StateFlow<SetupInspectionState> = mutable.asStateFlow()
    @Volatile private var online = true
    @Volatile private var closed = false
    private val generation = AtomicInt(0)

    public fun setOnline(value: Boolean) {
        if (online == value) return
        online = value
        generation.incrementAndFetch()
        if (!value && !closed)
            mutable.value = mutable.value.copy(phase = SetupInspectionPhase.Offline)
    }

    public suspend fun refresh(): Outcome<Unit> {
        if (!operation.tryLock()) return Outcome.Failed(Failure.Busy(BusyKind.Overloaded))
        try {
            val bound = bind()
            if (closed || !online || bound == null || !bound.current()) return stale()
            val current = bound.current
            val mine = generation.load()
            mutable.value = SetupInspectionState(SetupInspectionPhase.Loading)
            val reply =
                when (
                    val inspected = attempt({ Failure.Device() }) { bound.gateway.inspectSetup() }
                ) {
                    is Outcome.Ok -> inspected.value
                    is Outcome.Failed -> inspected
                }
            if (closed || !online || !current() || mine != generation.load()) return stale()
            return when (reply) {
                is Outcome.Ok -> {
                    mutable.value = SetupInspectionState(SetupInspectionPhase.Ready, reply.value)
                    Outcome.Ok(Unit)
                }
                is Outcome.Failed -> {
                    mutable.value =
                        SetupInspectionState(SetupInspectionPhase.Failed, failure = reply.failure)
                    reply
                }
            }
        } finally {
            operation.unlock()
        }
    }

    public val isClosed: Boolean
        get() = closed

    public fun stopAdmission() {
        closed = true
        generation.incrementAndFetch()
    }

    public suspend fun close() {
        closed = true
        generation.incrementAndFetch()
        operation.withLock { mutable.value = SetupInspectionState() }
    }

    private fun stale(): Outcome.Failed {
        // Offline only when the connection is really gone; a superseded refresh yields to the newer.
        if (!closed)
            mutable.value =
                SetupInspectionState(
                    if (online && bind() != null) SetupInspectionPhase.Idle
                    else SetupInspectionPhase.Offline
                )
        return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
    }
}
