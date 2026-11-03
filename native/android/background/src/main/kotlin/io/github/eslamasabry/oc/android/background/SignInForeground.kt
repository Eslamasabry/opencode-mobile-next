package io.github.eslamasabry.oc.android.background

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** Flutter `AgentSignInForegroundFailure`. */
public enum class ForegroundFailure { Unsupported, Paused, Permission, Unavailable, Cancelled }

/**
 * Why a lease failed or stopped. [code] is from a fixed vocabulary (`service-not-started`, `service-refused`,
 * `service-receipt-unsaved`, `android-time-limit`, `paused-by-you`, `service-gone`, `live-turned-off`, `app-closed`,
 * `owner-closed`, `pause-unreadable`, …): shown under Details, never platform or CLI text.
 */
public data class ForegroundLoss(val reason: ForegroundFailure, val code: String? = null)

public sealed interface LeaseAdmission {
    public data object Ready : LeaseAdmission

    /** Refused before admission; a refusal is not a loss (only [ForegroundLease.ready] reports it). */
    public data class Refused(val loss: ForegroundLoss) : LeaseAdmission
}

/** A temporary claim on the live service (Flutter `AgentSignInForegroundLease`). */
public interface ForegroundLease {
    /** Suspends until Android runs the service, or answers the coded refusal. */
    public suspend fun ready(): LeaseAdmission

    public val active: Boolean

    /** After admission: the service stopped, with what stopped it. Before admission a failure goes to [ready] only. */
    public val lost: Flow<ForegroundLoss>

    /** Ends the claim; `Failed(Device)` when stopping a service this claim started failed (retry by calling again). */
    public suspend fun release(): Outcome<Unit>
}

/** Reservation precedes any asynchronous admission (Flutter `AgentSignInForegroundPort`). */
public fun interface ForegroundLeasePort {
    public fun reserve(): ForegroundLease
}

/**
 * The sign-in claim on the live service, independent of the saved preference (port of `_SignInForegroundState`).
 * Android acknowledges activation; nothing keeps this claim alive or restarts a service Android or the person stopped.
 * Confined with its [LiveModeController].
 */
internal class SignInForegroundState(private val controller: LiveModeController) {
    private val leases = LinkedHashSet<SignInLease>()
    private var activation: Job? = null
    private var nativeEnable: Deferred<ServiceReply<ServiceStatus>>? = null
    private var stopping: Deferred<Boolean>? = null
    private var generation = 0
    private var stopRevision = 0
    private var owned = false

    val hasClaims: Boolean get() = leases.any { it.valid || it.held }
    val preservesUserClaim: Boolean get() = owned || hasClaims
    private val wanted: Boolean get() = leases.any { it.valid }
    private val holdsService: Boolean get() = leases.any { it.valid || it.held }

    fun reserve(): ForegroundLease {
        val lease = SignInLease(this)
        leases += lease // Admission is reserved before any asynchronous work.
        when {
            controller.disposed -> lease.invalidate(ForegroundFailure.Cancelled)
            !controller.support.supported() -> lease.invalidate(ForegroundFailure.Unsupported)
            controller.active && leases.any { it.valid && it.admitted } -> lease.admit()
            else -> ensureActivation()
        }
        return lease
    }

    internal val disposed: Boolean get() = controller.disposed

    private fun ensureActivation() {
        if (activation != null || !wanted || controller.disposed) return
        val generation = generation
        activation =
            controller.scope.launch {
                try {
                    activate(generation)
                } finally {
                    if (activation === currentCoroutineContext()[Job]) {
                        activation = null
                        // Only a new, explicit reservation after cancellation can start again.
                        if (leases.any { it.valid && !it.admitted }) ensureActivation()
                    }
                }
            }
    }

    private fun current(generation: Int): Boolean {
        if (generation != this.generation || !wanted || controller.disposed) return false
        if (!controller.support.supported()) {
            invalidate(ForegroundFailure.Unsupported)
            return false
        }
        return true
    }

    private suspend fun activate(generation: Int) {
        var startedHere = false
        stopping?.await()
        if (!current(generation)) return
        val read =
            when (val reply = controller.call { controller.port.pause() }) {
                is ServiceReply.Refused -> return refused(generation, reply.refusal, startedHere)
                is ServiceReply.Ok -> reply.value
            }
        if (!current(generation)) return
        if (!read.supported) {
            invalidate(ForegroundFailure.Unsupported, "pause-unreadable")
            return
        }
        // A durable pause (Android's time limit, an app update, the person's own Pause) stops live mode coming back by
        // itself. A sign-in is the person asking now, with the app in front, so it may start the service.
        if (read.active) {
            // Keep ownership if an earlier admitted lease owns this service; a running service is borrowed only.
            controller.pause = read
            controller.setActive(true)
        } else {
            startedHere = !controller.enabled
            if (startedHere) owned = true
            val status =
                when (val reply = invokeEnable()) {
                    is ServiceReply.Refused -> return refused(generation, reply.refusal, startedHere)
                    is ServiceReply.Ok -> reply.value
                }
            if (!current(generation)) {
                if (startedHere && status.active) {
                    owned = true
                    stopIfUnclaimed()
                }
                return
            }
            val receipt = status.pause ?: PauseState.Unsupported
            if (!status.active || !receipt.supported || !receipt.active) {
                invalidate(
                    ForegroundFailure.Unavailable,
                    if (!status.active) notStartedCode(status.startFailure) else "service-receipt",
                )
                if (status.active && startedHere) stopIfUnclaimed() else owned = false
                return
            }
            controller.pause = receipt
            controller.setActive(true)
            controller.notificationGranted = status.notificationGranted
            controller.batteryOptimizationIgnored = status.batteryOptimizationIgnored
            controller.stoppedByAndroidTimeout = false
        }
        for (lease in leases.toList()) if (lease.valid) lease.admit()
        controller.publish()
    }

    /** A refused step: permission for a notification denial, otherwise the fixed native code (or none). */
    private fun refused(generation: Int, refusal: ServiceRefusal, startedHere: Boolean) {
        if (current(generation)) {
            val code =
                when {
                    refusal.code == ServiceRefusal.UNKNOWN || refusal.code == ServiceRefusal.NO_HOST -> "service-error"
                    NATIVE_CODE.matches(refusal.code) -> "service-${refusal.code}"
                    else -> "service-platform"
                }
            invalidate(
                if (refusal.code == ServiceRefusal.NOTIFICATION_DENIED) ForegroundFailure.Permission
                else ForegroundFailure.Unavailable,
                code,
            )
        }
        if (startedHere) owned = false
    }

    /** Explicit enable and temporary admission share one native request. */
    suspend fun invokeEnable(): ServiceReply<ServiceStatus> {
        val pending = nativeEnable ?: controller.scope.async { enableNative() }.also { nativeEnable = it }
        return pending.await()
    }

    private suspend fun enableNative(): ServiceReply<ServiceStatus> {
        val stopRevision = stopRevision
        try {
            val reply = controller.call { controller.port.enable() }
            if (reply is ServiceReply.Ok &&
                stopRevision != this.stopRevision &&
                reply.value.active &&
                !controller.enabled &&
                controller.support.supported()
            ) {
                // An explicit stop overtook Android's start acknowledgement: the cancelled request must not resurrect
                // the service after that stop.
                owned = true
                val stopped = controller.call { controller.port.disable() }
                if (stopped is ServiceReply.Ok && !stopped.value.active) {
                    owned = false
                    controller.setActive(false)
                }
                // A failed stop retains ownership for a later cleanup attempt.
                return ServiceReply.Ok(reply.value.copy(active = false))
            }
            return reply
        } finally {
            nativeEnable = null
        }
    }

    suspend fun enableUserClaim(): Boolean {
        val previous = controller.enabled
        val revision = controller.pauseRevision
        controller.enabled = true
        controller.publish()
        if (!controller.active) {
            ensureActivation()
            activation?.join()
        }
        if (controller.disposed || revision != controller.pauseRevision) {
            return controller.enabled && !controller.disposed
        }
        if (!controller.active) {
            controller.enabled = previous
            controller.publish()
            return previous
        }
        writeOptIn(true)
        if (revision != controller.pauseRevision && !controller.enabled) writeOptIn(false)
        return controller.enabled
    }

    fun invalidate(reason: ForegroundFailure, code: String? = null) {
        ++generation
        for (lease in leases.toList()) lease.invalidate(reason, code)
    }

    /** [code] names what stopped it: Android's time limit, the person's Pause, a status read that found it gone. */
    fun serviceStopped(reason: ForegroundFailure, code: String?) {
        ++stopRevision
        owned = false
        invalidate(reason, code)
    }

    fun cancelForUser() {
        ++stopRevision
        invalidate(ForegroundFailure.Cancelled, "live-turned-off")
    }

    suspend fun disableUserClaim(): Boolean {
        val revision = ++controller.pauseRevision
        controller.enabled = false
        controller.publish()
        writeOptIn(false)
        when (val reply = controller.call { controller.port.disable() }) {
            is ServiceReply.Ok ->
                if (revision == controller.pauseRevision && !controller.disposed) {
                    controller.applyStatus(reply.value)
                    controller.applyPauseState(PauseState.Off)
                }
            is ServiceReply.Refused ->
                if (revision == controller.pauseRevision) controller.lastError = LiveModeError.CouldNotChange
        }
        controller.publish()
        return false
    }

    suspend fun release(lease: SignInLease): Boolean {
        lease.invalidate(ForegroundFailure.Cancelled)
        lease.held = false
        leases -= lease
        return stopIfUnclaimed()
    }

    /** True when nothing owned is left running (or nothing was owned). */
    private suspend fun stopIfUnclaimed(): Boolean {
        if (!owned || controller.enabled || holdsService || !controller.support.supported()) return true
        stopping?.let { return it.await() }
        val running = controller.scope.async { stopOwned() }
        stopping = running
        try {
            return running.await()
        } finally {
            if (stopping === running) stopping = null
        }
    }

    private suspend fun stopOwned(): Boolean {
        // A pending enable is cleaned when its actual receipt arrives, never by blocking cancellation on it.
        if (nativeEnable != null) return true
        val reply = controller.call { controller.port.disable() }
        // Never claim Android stopped the owned service without an inactive receipt; ownership stays for a retry.
        if (reply !is ServiceReply.Ok || reply.value.active) return false
        owned = false
        controller.setActive(false)
        controller.pause = PauseState.Off
        controller.publish()
        return true
    }

    private suspend fun writeOptIn(value: Boolean) {
        controller.writeOptIn(value)
    }

    private companion object {
        /** Native refusal codes are fixed identifiers (`permission_in_progress`, `foreground_service_failed`). */
        val NATIVE_CODE = Regex("^[a-z_]{1,40}$")

        /** Why Android did not start the service, from the service's fixed words; anything else is not repeated. */
        fun notStartedCode(reason: String?): String =
            when (reason) {
                "refused" -> "service-refused"
                "receiptUnsaved" -> "service-receipt-unsaved"
                else -> "service-not-started"
            }
    }
}

internal class SignInLease(private val owner: SignInForegroundState) : ForegroundLease {
    private val readiness = CompletableDeferred<ForegroundLoss?>()
    private val losses = MutableSharedFlow<ForegroundLoss>(replay = 1)
    var valid = true
        private set
    var admitted = false
        private set
    var held = false

    override suspend fun ready(): LeaseAdmission = readiness.await()?.let { LeaseAdmission.Refused(it) } ?: LeaseAdmission.Ready

    override val active: Boolean get() = valid && admitted && !owner.disposed

    override val lost: Flow<ForegroundLoss> = losses

    fun admit() {
        if (!valid || admitted) return
        admitted = true
        held = true
        readiness.complete(null)
    }

    /** Before admission the failure goes to [ready] only, with its code (a loss then hid why Android refused). */
    fun invalidate(reason: ForegroundFailure, code: String? = null) {
        if (!valid) return
        valid = false
        val failure = ForegroundLoss(reason, code)
        if (!readiness.isCompleted) {
            readiness.complete(failure)
            return
        }
        losses.tryEmit(failure)
    }

    override suspend fun release(): Outcome<Unit> = if (owner.release(this)) Unit.ok() else Outcome.Failed(Failure.Device())
}
