package io.github.eslamasabry.oc.engine.byohost

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlin.io.encoding.Base64

/**
 * The ephemeral, authenticated gateway profile for a verified host (Dart `connectedProfile`). It is never upserted in
 * the profile store: the loopback port and the device token live only as long as this process's tunnel. The username
 * is the phone's profile id and the password is the per-phone proxy device token.
 */
public data class ByoHostGatewayProfile(
    val profileId: ProfileId,
    val name: String,
    val origin: ServerOrigin,
    val username: String,
    val password: String,
    val serverVersion: String?,
    val backend: BackendKind = BackendKind.OpenCode2,
    val transientTransport: Boolean = true,
) {
    val baseUrl: String get() = origin.toString()

    /** The connection-facing shape; the password goes to the gateway factory directly, never to the SecretStore. */
    public fun toServerProfile(): ServerProfile =
        ServerProfile(profileId, name, backend, origin, ProfileTrust(null, null), managedOnPhone = false, username)

    override fun toString(): String = "ByoHostGatewayProfile(${profileId.raw}, <redacted>)"
}

/**
 * Port of lib/state/byo_host_controller.dart. A phone profile owns one durable adoption job; no SSH or provider text is
 * ever state. A controller's lifetime owns its tunnel, not the remote service or its sessions.
 *
 * Commands are one serial lane: a command that starts while another runs is refused with [ByoHostFailureCode.Busy]
 * (Dart `_busy`), and a disabled or disposed controller refuses with [ByoHostFailureCode.Disabled]; neither changes the
 * snapshot. Every other failure moves the snapshot to Failed (or keeps Ready for a refused revocation while the
 * tunnel is up) with the exact code in [ByoHostSnapshot.failure].
 *
 * [clearLocalData] is the app's full shared-data cascade for the profile; it throws when it refuses.
 */
public class ByoHostController(
    public val profileId: String,
    private val runner: ByoHostSshRunner,
    private val store: ByoHostStore,
    private val random: ByoHostRandom,
    private val bundle: ByoHostBundle? = null,
    private val clearLocalData: (suspend (String) -> Unit)? = null,
    private val enabled: Boolean = false,
) {
    init {
        require(byoHostSafeId(profileId)) { "invalid BYO host profile id" }
    }

    private val state = MutableStateFlow(ByoHostSnapshot(ByoHostPhase.Idle))
    public val snapshot: StateFlow<ByoHostSnapshot> = state.asStateFlow()

    private val lane = Mutex()
    private var tunnel: ByoHostTunnel? = null
    private var secrets: ByoHostSecrets? = null
    private var previewTarget: ByoHostTarget? = null
    private var previewKey: ByoHostKey? = null
    private var disposed = false

    /** The authenticated ephemeral gateway profile; [ByoHostFailureCode.Transport] unless Ready with a live tunnel. */
    public fun connectedProfile(name: String): Outcome<ByoHostGatewayProfile> {
        val live = tunnel
        val held = secrets
        val current = state.value
        if (current.phase != ByoHostPhase.Ready || live == null || held == null) {
            return ByoHostFailureCode.Transport.asFailure().failed()
        }
        return ByoHostGatewayProfile(
            profileId = ProfileId(profileId),
            name = name,
            origin = ServerOrigin("http", "127.0.0.1", live.localPort),
            username = profileId,
            password = held.deviceToken,
            serverVersion = current.record?.openCodeVersion,
        ).ok()
    }

    private fun emit(
        phase: ByoHostPhase,
        record: ByoHostRecord? = null,
        key: ByoHostKey? = null,
        failure: ByoHostFailureCode? = null,
    ) {
        state.value = ByoHostSnapshot(phase, record ?: state.value.record, key, failure)
    }

    private suspend fun <T> action(block: suspend () -> T): Outcome<T> {
        if (!lane.tryLock()) return ByoHostFailureCode.Busy.asFailure().failed()
        try {
            if (!enabled || disposed) return ByoHostFailureCode.Disabled.asFailure().failed()
            var code = ByoHostFailureCode.Transport
            val result =
                attempt({ error ->
                    code = (error as? ByoHostError)?.code ?: ByoHostFailureCode.Transport
                    code.asFailure()
                }) { block() }
            if (result is Outcome.Failed) {
                val keepReady = code == ByoHostFailureCode.RevocationFailed && tunnel != null
                emit(if (keepReady) ByoHostPhase.Ready else ByoHostPhase.Failed, failure = code)
            }
            return result
        } finally {
            lane.unlock()
        }
    }

    private fun fail(code: ByoHostFailureCode): Nothing = throw ByoHostError(code)

    /** Keyscan supplies a candidate, not trust. The person verifies the fingerprint out of band. */
    public suspend fun inspect(target: ByoHostTarget): Outcome<ByoHostKey> =
        action {
            emit(ByoHostPhase.Checking)
            if (!runner.available()) fail(ByoHostFailureCode.Unavailable)
            if (store.readRecord(profileId) != null) fail(ByoHostFailureCode.Busy)
            val key = runner.inspectKey(target)
            previewTarget = target
            previewKey = key
            emit(ByoHostPhase.NeedsTrust, key = key)
            key
        }

    /**
     * Enrollment for owner-managed key-only SSH accounts. The journal is reserved before the Keystore identity is
     * generated, so losing the app never loses the profile id. Returns the alias and public key only.
     */
    public suspend fun prepareSshIdentity(verifiedFingerprint: String): Outcome<ByoHostIdentity> =
        action {
            val target = previewTarget
            val key = previewKey
            if (target == null || key == null || key.fingerprint != verifiedFingerprint) {
                fail(ByoHostFailureCode.NeedsTrust)
            }
            val existing = store.readRecord(profileId)
            if (existing != null && existing.phase != ByoHostPhase.NeedsTrust) fail(ByoHostFailureCode.Busy)
            val reservation = ByoHostRecord(profileId, target, key, ByoHostPhase.NeedsTrust)
            checkKey(reservation)
            store.saveRecord(reservation)
            val identity = runner.generateIdentity(profileId)
            emit(ByoHostPhase.NeedsTrust, record = reservation, key = key)
            identity
        }

    /**
     * Persists the exact device tuple before any remote change, then installs and connects. A killed app retries the
     * same tuple; it never invents a second device. [login] is wiped whatever happens.
     */
    public suspend fun adopt(verifiedFingerprint: String, login: ByoHostLogin): Outcome<Unit> =
        try {
            action {
                val target = previewTarget
                val key = previewKey
                if (target == null || key == null || verifiedFingerprint != key.fingerprint) {
                    fail(ByoHostFailureCode.NeedsTrust)
                }
                val pkg = bundle ?: fail(ByoHostFailureCode.BundleUnavailable)
                val existing = store.readRecord(profileId)
                if (existing != null &&
                    (
                        existing.phase != ByoHostPhase.NeedsTrust ||
                            existing.hostKey.publicKey != key.publicKey ||
                            existing.target != target
                        )
                ) {
                    fail(ByoHostFailureCode.Busy)
                }
                val identity = runner.generateIdentity(profileId)
                val token = Base64.UrlSafe.encode(random.bytes(32)).trimEnd('=')
                val fresh = ByoHostSecrets(identity, token)
                store.saveSecrets(profileId, fresh)
                val record = ByoHostRecord(profileId, target, key, ByoHostPhase.Installing)
                store.saveRecord(record)
                secrets = fresh
                emit(ByoHostPhase.Installing, record = record)
                install(record, fresh, login, pkg)
            }
        } finally {
            login.consume()
        }

    private suspend fun install(record: ByoHostRecord, held: ByoHostSecrets, login: ByoHostLogin, pkg: ByoHostBundle) {
        checkKey(record)
        val descriptor =
            runner.install(profileId, record.target, record.hostKey, login, held.identity, held.deviceToken, pkg)
        validate(descriptor)
        if (descriptor.bundleVersion != pkg.version || descriptor.openCodeVersion != pkg.openCodeVersion) {
            fail(ByoHostFailureCode.IdentityChanged)
        }
        val installed = record.next(ByoHostPhase.Disconnected, descriptor = descriptor)
        store.saveRecord(installed)
        emit(ByoHostPhase.Disconnected, record = installed)
        connect(installed, held)
    }

    /**
     * Restores after a restart. No admin password or private key survives: an interrupted installation needs a fresh
     * one-shot [login] (otherwise [ByoHostFailureCode.Uncertain]); an ordinary reconnect uses the restricted key.
     */
    public suspend fun resume(login: ByoHostLogin? = null): Outcome<Unit> =
        try {
            action {
                val record = store.readRecord(profileId)
                val held = store.readSecrets(profileId)
                if (record != null && record.phase == ByoHostPhase.NeedsTrust && held == null) {
                    checkKey(record)
                    previewTarget = record.target
                    previewKey = record.hostKey
                    emit(ByoHostPhase.NeedsTrust, record = record, key = record.hostKey)
                    return@action
                }
                if (record == null || held == null || record.revoked) fail(ByoHostFailureCode.Storage)
                secrets = held
                emit(ByoHostPhase.Disconnected, record = record)
                if (!runner.available()) fail(ByoHostFailureCode.Unavailable)
                if (record.remotePort == null) {
                    if (login == null) fail(ByoHostFailureCode.Uncertain)
                    val pkg = bundle ?: fail(ByoHostFailureCode.BundleUnavailable)
                    install(record, held, login, pkg)
                } else {
                    connect(record, held)
                }
            }
        } finally {
            login?.consume()
        }

    private suspend fun checkKey(record: ByoHostRecord) {
        val key = runner.inspectKey(record.target)
        if (key.publicKey != record.hostKey.publicKey || key.fingerprint != record.hostKey.fingerprint) {
            fail(ByoHostFailureCode.HostKeyChanged)
        }
    }

    private fun validate(descriptor: ByoHostDescriptor) {
        if (!byoHostSafeId(descriptor.hostId) || descriptor.port !in 1024..65535 ||
            !isSemVer(descriptor.bundleVersion) || !isSemVer(descriptor.openCodeVersion)
        ) {
            fail(ByoHostFailureCode.Protocol)
        }
    }

    private suspend fun connect(record: ByoHostRecord, held: ByoHostSecrets) {
        checkKey(record)
        emit(ByoHostPhase.Connecting, record = record)
        closeTunnel()
        val opened = runner.forward(profileId, record.target, record.hostKey, held.identity, record.remotePort!!)
        try {
            val descriptor = runner.describe(opened, profileId, held.deviceToken)
            validate(descriptor)
            if (descriptor.hostId != record.hostId || descriptor.port != record.remotePort ||
                descriptor.bundleVersion != record.bundleVersion ||
                descriptor.openCodeVersion != record.openCodeVersion
            ) {
                fail(ByoHostFailureCode.IdentityChanged)
            }
            // Persist no loopback port; each process gets a newly verified lease.
            val ready = record.next(ByoHostPhase.Disconnected)
            store.saveRecord(ready)
            tunnel = opened
            secrets = held
            emit(ByoHostPhase.Ready, record = ready)
        } catch (error: Throwable) {
            opened.close()
            throw error
        }
    }

    private fun ByoHostRecord.next(
        phase: ByoHostPhase,
        descriptor: ByoHostDescriptor? = null,
        revoked: Boolean? = null,
    ): ByoHostRecord =
        copy(
            phase = phase,
            hostId = descriptor?.hostId ?: hostId,
            bundleVersion = descriptor?.bundleVersion ?: bundleVersion,
            openCodeVersion = descriptor?.openCodeVersion ?: openCodeVersion,
            remotePort = descriptor?.port ?: remotePort,
            revoked = revoked ?: this.revoked,
        )

    /**
     * Removes the connection. With [revokeAccess] false it only forgets this phone's side: the host is never revoked
     * or stopped. With [revokeAccess] the revocation receipt is saved before local erasure, any failed or uncertain
     * revocation keeps the record, the credentials and the live connection, and a confirmed revocation is never
     * repeated after a later local deletion failure.
     */
    public suspend fun remove(revokeAccess: Boolean): Outcome<Unit> =
        action {
            val record = store.readRecord(profileId)
            if (record == null) {
                closeTunnel()
                runner.cleanup(profileId)
                clearLocalData?.invoke(profileId)
                store.remove(profileId)
                secrets = null
                state.value = ByoHostSnapshot(ByoHostPhase.Removed)
                return@action
            }
            if (revokeAccess && !record.revoked) {
                val held = secrets ?: store.readSecrets(profileId) ?: fail(ByoHostFailureCode.Storage)
                val confirmed =
                    attempt({ ByoHostFailureCode.RevocationFailed.asFailure() }) {
                        if (tunnel == null) connect(record, held)
                        runner.revoke(tunnel!!, profileId, held.deviceToken)
                    }
                if (confirmed != Outcome.Ok(true)) fail(ByoHostFailureCode.RevocationFailed)
                store.saveRecord(record.next(ByoHostPhase.Removing, revoked = true))
            }
            emit(ByoHostPhase.Removing, record = record)
            closeTunnel()
            runner.cleanup(profileId)
            val cleared = attempt({ ByoHostFailureCode.Storage.asFailure() }) { clearLocalData?.invoke(profileId) }
            if (cleared is Outcome.Failed) {
                // The shared preference cascade may have swept our record before failing. Restore a non-secret
                // cleanup journal so an app restart can still find it.
                val pending = store.readRecord(profileId)
                store.saveRecord(
                    pending ?: record.next(ByoHostPhase.Removing, revoked = revokeAccess || record.revoked),
                )
                fail(ByoHostFailureCode.Storage)
            }
            store.remove(profileId)
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

    /** Closes local transports only; never uninstalls or stops the persistent host. Busy while a command runs. */
    public suspend fun dispose(): Outcome<Unit> {
        if (lane.isLocked) return ByoHostFailureCode.Busy.asFailure().failed()
        disposed = true
        closeTunnel()
        secrets = null
        return Unit.ok()
    }
}
