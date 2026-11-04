package io.github.eslamasabry.oc.android.background

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** A terminal's drain, registered before admission (removes its native PTY). A Failed or throwing drain is kept. */
public typealias ForegroundCleanup = suspend () -> Outcome<Unit>

/** Handle for [SignInForegroundBinding.removeCleanup]. */
public class CleanupToken internal constructor()

/**
 * The canonical phone-account owner per profile supplies the app's shared service port (port of
 * `AgentSignInForegroundRegistry`). Replacing an owner serialises behind the old owner's drain and release; an unbind
 * from an old owner never clears its replacement; a failed drain blocks admission until it is retried.
 * Confined to one thread with [scope] (the main thread in the app).
 */
public class SignInForegroundRegistry(private val scope: CoroutineScope) {
    private val bindings = HashMap<String, SignInForegroundBinding>()
    private val draining = HashMap<String, SignInForegroundBinding>()

    public fun bindingFor(profileId: String): SignInForegroundBinding? = bindings[profileId]

    public fun bind(profileId: String, port: ForegroundLeasePort): SignInForegroundBinding {
        val old = bindings[profileId] ?: draining[profileId]
        if (old != null && old.current && old.port === port) return old
        // Admission awaits this gate and refuses a failed old-owner drain.
        val gate = old?.close() ?: CompletableDeferred(true)
        val binding = SignInForegroundBinding(this, scope, profileId, port, gate)
        bindings[profileId] = binding
        return binding
    }

    /** `Failed(Device)` when a drain failed; the binding keeps that cleanup so calling again retries it. */
    public suspend fun unbind(binding: SignInForegroundBinding): Outcome<Unit> {
        if (bindings[binding.profileId] === binding) bindings.remove(binding.profileId)
        draining[binding.profileId] = binding
        if (!binding.close().await()) return Outcome.Failed(Failure.Device())
        if (draining[binding.profileId] === binding) draining.remove(binding.profileId)
        return Unit.ok()
    }
}

public class SignInForegroundBinding internal constructor(
    private val registry: SignInForegroundRegistry,
    private val scope: CoroutineScope,
    public val profileId: String,
    internal val port: ForegroundLeasePort,
    internal val gate: Deferred<Boolean>,
) {
    private val cleanups = LinkedHashMap<CleanupToken, ForegroundCleanup>()
    internal val leases = LinkedHashSet<BoundForegroundLease>()
    private var valid = true
    private var closing: Deferred<Boolean>? = null

    public val current: Boolean get() = valid && registry.bindingFor(profileId) === this

    /** Null when this binding was replaced or closed (Flutter threw `cancelled`). */
    public fun addCleanup(cleanup: ForegroundCleanup): CleanupToken? {
        if (!current) return null
        val token = CleanupToken()
        cleanups[token] = cleanup
        return token
    }

    public fun removeCleanup(token: CleanupToken) {
        cleanups.remove(token)
    }

    /** A lease on the current owner; on a replaced or closed binding it is refused at once (`cancelled`). */
    public fun reserve(): ForegroundLease {
        val lease = BoundForegroundLease(this, scope)
        if (!current) {
            lease.invalidate(ForegroundLoss(ForegroundFailure.Cancelled))
            return lease
        }
        leases += lease
        scope.launch { lease.start() }
        return lease
    }

    /** True when every drain and release succeeded. Concurrent closes share one run. */
    internal fun close(): Deferred<Boolean> {
        closing?.let { return it }
        val run =
            scope.async(start = CoroutineStart.UNDISPATCHED) {
                try {
                    closeBody()
                } finally {
                    closing = null
                }
            }
        if (!run.isCompleted) closing = run
        return run
    }

    private suspend fun closeBody(): Boolean {
        valid = false
        for (lease in leases.toList()) lease.invalidate(null)
        // Process protection holds until the terminal removed its native PTY. Failed drains keep their cleanup.
        for ((token, cleanup) in cleanups.entries.toList()) {
            val result = attempt({ Failure.Device() }) { cleanup() }
            if (result !is Outcome.Ok || result.value !is Outcome.Ok) return false
            cleanups.remove(token)
        }
        // A replacement that never launched still inherits the older drain.
        if (!gate.await()) return false
        for (lease in leases.toList()) if (lease.release() !is Outcome.Ok) return false
        return true
    }
}

internal class BoundForegroundLease(
    private val binding: SignInForegroundBinding,
    private val scope: CoroutineScope,
) : ForegroundLease {
    private val readiness = CompletableDeferred<ForegroundLoss?>()
    private val losses = MutableSharedFlow<ForegroundLoss>(replay = 1)
    private var delegate: ForegroundLease? = null
    private var subscription: Job? = null
    private var invalid = false
    private var releasing: Deferred<Outcome<Unit>>? = null

    override suspend fun ready(): LeaseAdmission = readiness.await()?.let { LeaseAdmission.Refused(it) } ?: LeaseAdmission.Ready

    override val active: Boolean get() = !invalid && binding.current && (delegate?.active ?: false)

    override val lost: Flow<ForegroundLoss> = losses

    suspend fun start() {
        if (!binding.gate.await()) return fail(ForegroundLoss(ForegroundFailure.Unavailable, "admission-error"))
        if (invalid || !binding.current) return invalidate(null)
        val lease = binding.port.reserve()
        delegate = lease
        subscription = scope.launch(start = CoroutineStart.UNDISPATCHED) { lease.lost.collect { invalidate(it) } }
        val admission = lease.ready()
        if (admission is LeaseAdmission.Refused) return fail(admission.loss)
        if (invalid || !binding.current || !lease.active) {
            invalidate(
                if (binding.current && !lease.active) ForegroundLoss(ForegroundFailure.Unavailable, "service-inactive")
                else null,
            )
            lease.release()
            return
        }
        if (!readiness.isCompleted) readiness.complete(null)
    }

    /** Readiness reports the coded refusal; the delegate is released quietly (a failed release stays for the drain). */
    private suspend fun fail(loss: ForegroundLoss) {
        if (!readiness.isCompleted) readiness.complete(loss)
        invalid = true
        delegate?.release()
    }

    /** [cause] is the service's own loss; without one the owner closed. Before admission it fails [ready] only. */
    fun invalidate(cause: ForegroundLoss?) {
        if (invalid) return
        invalid = true
        val failure = cause ?: ForegroundLoss(ForegroundFailure.Cancelled, "owner-closed")
        if (!readiness.isCompleted) {
            readiness.complete(failure)
            return
        }
        losses.tryEmit(failure)
    }

    override suspend fun release(): Outcome<Unit> {
        val pending = releasing ?: scope.async { releaseBody() }.also { releasing = it }
        val result = pending.await()
        if (result !is Outcome.Ok && releasing === pending) releasing = null
        return result
    }

    private suspend fun releaseBody(): Outcome<Unit> {
        invalidate(null)
        subscription?.cancel()
        val released = delegate?.release() ?: Unit.ok()
        if (released !is Outcome.Ok) return Outcome.Failed(Failure.Device())
        binding.leases -= this
        return Unit.ok()
    }
}
