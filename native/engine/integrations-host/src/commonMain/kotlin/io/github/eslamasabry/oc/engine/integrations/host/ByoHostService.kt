package io.github.eslamasabry.oc.engine.integrations.host

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One local SSH runner and durable store shared by independent host controllers (B-AGENT-049). The
 * composition owner must explicitly enable the service and supply the reviewed bundle for the exact
 * app version. An absent bundle never reports an available enrollment path.
 *
 * Releasing one controller closes only its local tunnel. It does not dispose the shared runner,
 * erase another machine, revoke remote access, or stop a host. Durable removal belongs to
 * [ByoHostController.remove], before [release].
 */
public class ByoHostService(
    private val store: ByoHostStore,
    runner: ByoHostSshRunner,
    private val bundle: ByoHostBundle? = null,
    private val clearLocalData: (suspend (String) -> Boolean)? = null,
    private val enabled: Boolean = false,
    private val random: HostRandom = HostRandom.Secure,
) {
    private val runner = SharedHostRunner(runner)
    private val lifecycle = Mutex()
    private val controllers = mutableMapOf<String, ByoHostController>()
    private var closing = false
    private var disposed = false

    public suspend fun available(): Boolean = lifecycle.withLock {
        enabled &&
            !closing &&
            !disposed &&
            bundle != null &&
            hostResult(ByoHostFailureCode.Unavailable) { runner.available() }.valueOrNull() == true
    }

    public suspend fun machines(): HostResult<ImmutableList<ByoHostRecord>> = lifecycle.withLock {
        if (!admits()) return@withLock HostResult.Failed(ByoHostFailureCode.Disabled)
        hostResult(ByoHostFailureCode.Storage) { store.listRecords().orAbort() }
    }

    public suspend fun machine(profileId: String): HostResult<ByoHostController> =
        lifecycle.withLock {
            if (!admits()) return@withLock HostResult.Failed(ByoHostFailureCode.Disabled)
            if (!byoHostSafeId(profileId))
                return@withLock HostResult.Failed(ByoHostFailureCode.InvalidTarget)
            HostResult.Ok(controller(profileId))
        }

    public suspend fun newMachine(): HostResult<ByoHostController> = lifecycle.withLock {
        if (!admits()) return@withLock HostResult.Failed(ByoHostFailureCode.Disabled)
        if (bundle == null) return@withLock HostResult.Failed(ByoHostFailureCode.BundleUnavailable)
        hostResult(ByoHostFailureCode.Storage) {
            // Reserve a fresh identity without overwriting a durable enrollment after restart.
            repeat(8) {
                val id =
                    "host-" +
                        random.bytes(16).joinToString("") {
                            (it.toInt() and 0xff).toString(16).padStart(2, '0')
                        }
                if (id !in controllers && store.readRecord(id).orAbort() == null) {
                    return@hostResult controller(id)
                }
            }
            abort(ByoHostFailureCode.Busy)
        }
    }

    private fun admits(): Boolean = enabled && !closing && !disposed

    private fun controller(profileId: String): ByoHostController =
        controllers.getOrPut(profileId) {
            ByoHostController(profileId, runner, store, bundle, clearLocalData, enabled, random)
        }

    public suspend fun release(profileId: String): HostResult<Unit> = lifecycle.withLock {
        if (!byoHostSafeId(profileId))
            return@withLock HostResult.Failed(ByoHostFailureCode.InvalidTarget)
        val controller = controllers[profileId] ?: return@withLock HostResult.Ok(Unit)
        when (val result = controller.dispose()) {
            is HostResult.Failed -> result // A running action retains its owner for a later retry.
            is HostResult.Ok -> {
                controllers.remove(profileId)
                result
            }
        }
    }

    /**
     * A busy controller keeps the shared runner alive; disposal can be retried after its action.
     */
    public suspend fun dispose(): HostResult<Unit> = lifecycle.withLock {
        if (disposed) return@withLock HostResult.Ok(Unit)
        closing = true
        for ((id, controller) in controllers.toMap()) {
            when (val result = controller.dispose()) {
                is HostResult.Failed -> return@withLock result
                is HostResult.Ok -> controllers.remove(id)
            }
        }
        when (val result = hostResult(ByoHostFailureCode.Transport) { runner.dispose() }) {
            is HostResult.Failed -> result
            is HostResult.Ok -> {
                disposed = true
                result
            }
        }
    }
}

/** The SSH runner's mutable resource ledger is shared, so every operation uses the same lane. */
private class SharedHostRunner(private val delegate: ByoHostSshRunner) : ByoHostSshRunner {
    private val lane = Mutex()

    override suspend fun available(): Boolean = lane.withLock { delegate.available() }

    override suspend fun inspectKey(target: ByoHostTarget): HostResult<ByoHostKey> = lane.withLock {
        delegate.inspectKey(target)
    }

    override suspend fun generateIdentity(profileId: String): HostResult<ByoHostIdentity> =
        lane.withLock {
            delegate.generateIdentity(profileId)
        }

    override suspend fun install(
        profileId: String,
        target: ByoHostTarget,
        hostKey: ByoHostKey,
        login: ByoHostLogin,
        identity: ByoHostIdentity,
        deviceToken: String,
        bundle: ByoHostBundle,
    ): HostResult<ByoHostDescriptor> = lane.withLock {
        delegate.install(profileId, target, hostKey, login, identity, deviceToken, bundle)
    }

    override suspend fun forward(
        profileId: String,
        target: ByoHostTarget,
        hostKey: ByoHostKey,
        identity: ByoHostIdentity,
        remotePort: Int,
    ): HostResult<ByoHostTunnel> = lane.withLock {
        when (val result = delegate.forward(profileId, target, hostKey, identity, remotePort)) {
            is HostResult.Failed -> result
            is HostResult.Ok -> HostResult.Ok(SharedTunnel(result.value))
        }
    }

    override suspend fun describe(
        tunnel: ByoHostTunnel,
        deviceId: String,
        deviceToken: String,
    ): HostResult<ByoHostDescriptor> = lane.withLock {
        delegate.describe((tunnel as SharedTunnel).tunnel, deviceId, deviceToken)
    }

    override suspend fun revoke(
        tunnel: ByoHostTunnel,
        deviceId: String,
        deviceToken: String,
    ): Boolean = lane.withLock {
        delegate.revoke((tunnel as SharedTunnel).tunnel, deviceId, deviceToken)
    }

    override suspend fun cleanup(profileId: String): HostResult<Unit> = lane.withLock {
        delegate.cleanup(profileId)
    }

    override suspend fun dispose() = lane.withLock { delegate.dispose() }

    private inner class SharedTunnel(val tunnel: ByoHostTunnel) : ByoHostTunnel {
        override val localPort: Int
            get() = tunnel.localPort

        override suspend fun close() = lane.withLock { tunnel.close() }
    }
}
