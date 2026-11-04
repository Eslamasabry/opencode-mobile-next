package io.github.eslamasabry.oc.engine.integrations.host

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlin.io.encoding.Base64

/**
 * The ephemeral connection to a verified host runtime: loopback base URL plus the device's Basic credentials. Never
 * persisted as a profile; every process gets a newly verified lease from [ByoHostController.resume].
 */
public class ByoHostConnection(
    public val profileId: String,
    public val baseUrl: String,
    public val username: String,
    public val password: String,
    public val serverVersion: String?,
) {
    override fun toString(): String = "ByoHostConnection(<redacted>)"
}

/**
 * One phone profile's durable adoption job (Flutter `ByoHostController`). No SSH or provider text is UI state; the
 * controller owns its tunnel, never the remote service or sessions. One action at a time.
 */
public class ByoHostController(
    public val profileId: String,
    private val runner: ByoHostSshRunner,
    private val store: ByoHostStore,
    private val bundle: ByoHostBundle? = null,
    private val clearLocalData: (suspend (String) -> Boolean)? = null,
    private val enabled: Boolean = false,
    private val random: HostRandom = HostRandom.Secure,
) {
    private val state = MutableStateFlow(ByoHostSnapshot(ByoHostPhase.Idle))
    public val snapshot: StateFlow<ByoHostSnapshot> = state.asStateFlow()
    private var tunnel: ByoHostTunnel? = null
    private var secrets: ByoHostSecrets? = null
    private var previewTarget: ByoHostTarget? = null
    private var previewKey: ByoHostKey? = null
    private val busy = Mutex()
    private var disposed = false

    init {
        require(byoHostSafeId(profileId)) { "unsafe profile id" }
    }

    /** The verified loopback connection, only while Ready. */
    public fun connection(): HostResult<ByoHostConnection> {
        val t = tunnel
        val s = secrets
        if (state.value.phase != ByoHostPhase.Ready || t == null || s == null) return HostResult.Failed(ByoHostFailureCode.Transport)
        return HostResult.Ok(ByoHostConnection(profileId, "http://127.0.0.1:${t.localPort}", profileId, s.deviceToken, state.value.record?.openCodeVersion))
    }

    private fun emit(phase: ByoHostPhase, record: ByoHostRecord? = null, key: ByoHostKey? = null, failure: ByoHostFailureCode? = null) {
        state.value = ByoHostSnapshot(phase, record ?: state.value.record, key, failure)
    }

    private suspend fun <T> action(block: suspend () -> T): HostResult<T> {
        if (!busy.tryLock()) return HostResult.Failed(ByoHostFailureCode.Busy)
        try {
            if (!enabled || disposed) return HostResult.Failed(ByoHostFailureCode.Disabled)
            val result = hostResult(ByoHostFailureCode.Transport) { block() }
            if (result is HostResult.Failed) {
                emit(
                    if (result.code == ByoHostFailureCode.RevocationFailed && tunnel != null) ByoHostPhase.Ready else ByoHostPhase.Failed,
                    failure = result.code,
                )
            }
            return result
        } finally {
            busy.unlock()
        }
    }

    /** Keyscan supplies a candidate, not trust. The person verifies it out of band. */
    public suspend fun inspect(target: ByoHostTarget): HostResult<ByoHostKey> =
        action {
            emit(ByoHostPhase.Checking)
            if (!runner.available()) abort(ByoHostFailureCode.Unavailable)
            if (store.readRecord(profileId).orAbort() != null) abort(ByoHostFailureCode.Busy)
            val key = runner.inspectKey(target).orAbort()
            previewTarget = target
            previewKey = key
            emit(ByoHostPhase.NeedsTrust, key = key)
            key
        }

    /** Public enrollment for key-only SSH accounts; the journal is reserved before the Keystore generates a key. */
    public suspend fun prepareSshIdentity(verifiedFingerprint: String): HostResult<ByoHostIdentity> =
        action {
            val target = previewTarget
            val key = previewKey
            if (target == null || key == null || key.fingerprint != verifiedFingerprint) abort(ByoHostFailureCode.NeedsTrust)
            val existing = store.readRecord(profileId).orAbort()
            if (existing != null && existing.phase != ByoHostPhase.NeedsTrust) abort(ByoHostFailureCode.Busy)
            val reservation = ByoHostRecord(profileId, target, key, ByoHostPhase.NeedsTrust)
            checkKey(reservation)
            store.saveRecord(reservation).orAbort()
            val identity = runner.generateIdentity(profileId).orAbort()
            emit(ByoHostPhase.NeedsTrust, record = reservation, key = key)
            identity
        }

    /** Persists the exact device tuple before any remote mutation; a killed app retries the same tuple. */
    public suspend fun adopt(verifiedFingerprint: String, login: ByoHostLogin): HostResult<Unit> =
        try {
            action {
                val target = previewTarget
                val key = previewKey
                if (target == null || key == null || verifiedFingerprint != key.fingerprint) abort(ByoHostFailureCode.NeedsTrust)
                val pkg = bundle ?: abort(ByoHostFailureCode.BundleUnavailable)
                val existing = store.readRecord(profileId).orAbort()
                if (existing != null &&
                    (existing.phase != ByoHostPhase.NeedsTrust || existing.hostKey.publicKey != key.publicKey || existing.target != target)
                ) {
                    abort(ByoHostFailureCode.Busy)
                }
                val identity = runner.generateIdentity(profileId).orAbort()
                val token = Base64.UrlSafe.encode(random.bytes(32)).replace("=", "")
                val fresh = ByoHostSecrets(identity, token)
                store.saveSecrets(profileId, fresh).orAbort()
                val record = ByoHostRecord(profileId, target, key, ByoHostPhase.Installing)
                store.saveRecord(record).orAbort()
                secrets = fresh
                emit(ByoHostPhase.Installing, record = record)
                install(record, fresh, login, pkg)
            }
        } finally {
            login.consume()
        }

    private suspend fun install(record: ByoHostRecord, secrets: ByoHostSecrets, login: ByoHostLogin, pkg: ByoHostBundle) {
        checkKey(record)
        val descriptor =
            runner.install(profileId, record.target, record.hostKey, login, secrets.identity, secrets.deviceToken, pkg).orAbort()
        validateDescriptor(descriptor)
        if (descriptor.bundleVersion != pkg.version || descriptor.openCodeVersion != pkg.openCodeVersion) {
            abort(ByoHostFailureCode.IdentityChanged)
        }
        val installed = record.copy(
            phase = ByoHostPhase.Disconnected,
            hostId = descriptor.hostId,
            bundleVersion = descriptor.bundleVersion,
            openCodeVersion = descriptor.openCodeVersion,
            remotePort = descriptor.port,
        )
        store.saveRecord(installed).orAbort()
        emit(ByoHostPhase.Disconnected, record = installed)
        connect(installed, secrets)
    }

    /** No initial admin password survives. An interrupted install needs a fresh one-shot login. */
    public suspend fun resume(login: ByoHostLogin? = null): HostResult<Unit> =
        try {
            action {
                val record = store.readRecord(profileId).orAbort()
                val saved = store.readSecrets(profileId).orAbort()
                if (record != null && record.phase == ByoHostPhase.NeedsTrust && saved == null) {
                    checkKey(record)
                    previewTarget = record.target
                    previewKey = record.hostKey
                    emit(ByoHostPhase.NeedsTrust, record = record, key = record.hostKey)
                    return@action
                }
                if (record == null || saved == null || record.revoked) abort(ByoHostFailureCode.Storage)
                secrets = saved
                emit(ByoHostPhase.Disconnected, record = record)
                if (!runner.available()) abort(ByoHostFailureCode.Unavailable)
                if (record.remotePort == null) {
                    if (login == null) abort(ByoHostFailureCode.Uncertain)
                    install(record, saved, login, bundle ?: abort(ByoHostFailureCode.BundleUnavailable))
                } else {
                    connect(record, saved)
                }
            }
        } finally {
            login?.consume()
        }

    /** A changed SSH host key is refused before any forwarding or remote mutation. */
    private suspend fun checkKey(record: ByoHostRecord) {
        val key = runner.inspectKey(record.target).orAbort()
        if (key.publicKey != record.hostKey.publicKey || key.fingerprint != record.hostKey.fingerprint) {
            abort(ByoHostFailureCode.HostKeyChanged)
        }
    }

    private fun validateDescriptor(d: ByoHostDescriptor) {
        if (!byoHostSafeId(d.hostId) || d.port !in 1024..65535 || !SEMVER.matches(d.bundleVersion) || !SEMVER.matches(d.openCodeVersion)) {
            abort(ByoHostFailureCode.Protocol)
        }
    }

    /** A changed supervisor identity closes the replacement tunnel before anything uses it. */
    private suspend fun connect(record: ByoHostRecord, secrets: ByoHostSecrets) {
        checkKey(record)
        emit(ByoHostPhase.Connecting, record = record)
        closeTunnel()
        val fresh =
            runner.forward(profileId, record.target, record.hostKey, secrets.identity, record.remotePort ?: abort(ByoHostFailureCode.Storage)).orAbort()
        try {
            val d = runner.describe(fresh, profileId, secrets.deviceToken).orAbort()
            validateDescriptor(d)
            if (d.hostId != record.hostId || d.port != record.remotePort || d.bundleVersion != record.bundleVersion ||
                d.openCodeVersion != record.openCodeVersion
            ) {
                abort(ByoHostFailureCode.IdentityChanged)
            }
            // Persist no loopback port; each process gets a newly verified lease.
            val ready = record.copy(phase = ByoHostPhase.Disconnected)
            store.saveRecord(ready).orAbort()
            tunnel = fresh
            this.secrets = secrets
            emit(ByoHostPhase.Ready, record = ready)
        } catch (e: Throwable) {
            quietly { fresh.close() }
            throw e
        }
    }

    /** Revocation is confirmed before local erasure; a failed or uncertain revocation keeps everything. */
    public suspend fun remove(revokeAccess: Boolean): HostResult<Unit> =
        action {
            val record = store.readRecord(profileId).orAbort()
            if (record == null) {
                closeTunnel()
                runner.cleanup(profileId).orAbort()
                if (clearLocalData?.invoke(profileId) == false) abort(ByoHostFailureCode.Storage)
                store.remove(profileId).orAbort() // includes an orphan envelope after a failed save
                secrets = null
                state.value = ByoHostSnapshot(ByoHostPhase.Removed)
                return@action
            }
            if (revokeAccess && !record.revoked) {
                val s = secrets ?: store.readSecrets(profileId).orAbort() ?: abort(ByoHostFailureCode.Storage)
                val revoked =
                    hostResult(ByoHostFailureCode.RevocationFailed) {
                        if (tunnel == null) connect(record, s)
                        runner.revoke(tunnel ?: abort(ByoHostFailureCode.RevocationFailed), profileId, s.deviceToken)
                    }.valueOrNull() == true
                if (!revoked) abort(ByoHostFailureCode.RevocationFailed)
                store.saveRecord(record.copy(phase = ByoHostPhase.Removing, revoked = true)).orAbort()
            }
            emit(ByoHostPhase.Removing, record = record)
            closeTunnel()
            runner.cleanup(profileId).orAbort()
            val cleared = clearLocalData?.invoke(profileId) ?: true
            if (!cleared) {
                // The shared cascade may have swept our record before failing: restore a non-secret cleanup journal.
                val pending = store.readRecord(profileId).valueOrNull()
                store.saveRecord(pending ?: record.copy(phase = ByoHostPhase.Removing, revoked = revokeAccess || record.revoked))
                abort(ByoHostFailureCode.Storage)
            }
            store.remove(profileId).orAbort()
            secrets = null
            previewTarget = null
            previewKey = null
            state.value = ByoHostSnapshot(ByoHostPhase.Removed)
        }

    private suspend fun closeTunnel() {
        val previous = tunnel
        tunnel = null
        previous?.close()
    }

    /** Closes local transports only; never uninstalls or stops the persistent host. */
    public suspend fun dispose(): HostResult<Unit> {
        if (!busy.tryLock()) return HostResult.Failed(ByoHostFailureCode.Busy)
        try {
            disposed = true
            quietly { closeTunnel() }
            secrets = null
            return HostResult.Ok(Unit)
        } finally {
            busy.unlock()
        }
    }
}
