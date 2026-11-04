package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.host.*
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.feature.integrations.presentation.*
import java.net.InetAddress
import java.security.SecureRandom
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Production owner for native host inspection, public-key preparation and retained host recovery.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<HostPanelSource>())
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
class NativeByoHosts(
    app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    redactor: Redactor,
) : HostPanelSource, ProfileCloser, ProfileResourceCleaner {
    private val serial = Mutex()
    private val closed = HashSet<String>()
    private val controllers = HashMap<String, ByoHostController>()
    private val nativeSigner = AndroidHostSigner(app)
    private val signer = ChannelByoHostSigner { method, arguments ->
        withContext(dispatchers.io) { nativeSigner.invoke(method, arguments) }
    }
    private val runner =
        BuiltinByoHostSshRunner(
            AndroidHostShell(app, dispatchers),
            AndroidHostFiles(app, dispatchers),
            signer,
            ByoHostTailnetResolver { host ->
                withContext(dispatchers.io) {
                    InetAddress.getAllByName(host).mapNotNull { it.hostAddress }
                }
            },
            AndroidHostPorts(dispatchers),
            AndroidHostHttp(dispatchers, redactor),
        )
    private val store =
        PersistentByoHostStore(stores.prefs, stores.secrets, stores.lanes.app, signer)
    private val random = SecureRandom()
    override val owner: String = ":engine:integrations-host"
    override val order: Int = DeletionStep.DEVICE_SURFACES + 12
    override val guardsSweep: Boolean = true

    init {
        stores.profileScope.register(
            KeyShape(
                PersistentByoHostStore.RECORD_PREFIX,
                KeyScope.Profile,
                PrefType.Str,
                owner,
                "pinned host adoption journal without credentials",
            )
        )
        stores.profileScope.registerSecret(PersistentByoHostStore.SECRET_PREFIX, owner)
    }

    private fun admitting(id: String): Boolean = synchronized(closed) { id !in closed }

    private fun controller(id: String): ByoHostController =
        controllers.getOrPut(id) {
            // A reviewed OpenCode 2 host archive has not been published. Inspection and recovery
            // are real;
            // adopt deliberately refuses BundleUnavailable until the owner supplies reviewed
            // archive pins.
            ByoHostController(id, runner, store, enabled = true)
        }

    private suspend fun <T> operation(
        id: String?,
        block: suspend () -> HostResult<T>,
    ): HostResult<T> = serial.withLock {
        if (stores.warm() is Outcome.Failed)
            return@withLock HostResult.Failed(ByoHostFailureCode.Storage)
        if (id != null && (!byoHostSafeId(id) || !admitting(id)))
            return@withLock HostResult.Failed(ByoHostFailureCode.Storage)
        try {
            block()
        } catch (error: kotlin.coroutines.cancellation.CancellationException) {
            throw error
        } catch (_: Throwable) {
            HostResult.Failed(ByoHostFailureCode.Storage)
        }
    }

    override fun newId(): String =
        "host-" +
            ByteArray(16).also(random::nextBytes).joinToString("") {
                "%02x".format(it.toInt() and 255)
            }

    override suspend fun machines():
        HostResult<kotlinx.collections.immutable.ImmutableList<HostMachineUi>> =
        operation(null) {
            when (val records = store.listRecords()) {
                is HostResult.Failed -> records
                is HostResult.Ok -> {
                    for (record in records.value) {
                        if (!admitting(record.profileId))
                            return@operation HostResult.Failed(ByoHostFailureCode.Busy)
                        when (val recovered = runner.recoverOrphans(record.profileId)) {
                            is HostResult.Failed -> return@operation recovered
                            is HostResult.Ok -> Unit
                        }
                    }
                    HostResult.Ok(
                        records.value
                            .map { HostMachineUi(it.profileId, it.phase) }
                            .toImmutableList()
                    )
                }
            }
        }

    override suspend fun inspect(id: String, address: String, port: String): HostResult<String> =
        operation(id) {
            val target =
                ByoHostTarget.parse(
                    address,
                    port.toIntOrNull()
                        ?: return@operation HostResult.Failed(ByoHostFailureCode.InvalidTarget),
                ) ?: return@operation HostResult.Failed(ByoHostFailureCode.InvalidTarget)
            when (val result = controller(id).inspect(target)) {
                is HostResult.Failed -> result
                is HostResult.Ok -> HostResult.Ok(result.value.fingerprint)
            }
        }

    override suspend fun prepareIdentity(id: String, fingerprint: String): HostResult<String> =
        operation(id) {
            when (val result = controller(id).prepareSshIdentity(fingerprint)) {
                is HostResult.Failed -> result
                is HostResult.Ok -> HostResult.Ok(result.value.publicKey)
            }
        }

    override suspend fun resume(id: String): HostResult<HostVerificationUi> =
        operation(id) {
            val current = controller(id)
            when (val result = current.resume()) {
                is HostResult.Failed -> result
                is HostResult.Ok ->
                    HostResult.Ok(
                        HostVerificationUi(
                            current.snapshot.value.phase,
                            current.snapshot.value.hostKey?.fingerprint,
                        )
                    )
            }
        }

    override suspend fun remove(id: String, revoke: Boolean): HostResult<Unit> =
        operation(id) {
            controller(id).remove(revoke).also { result ->
                if (result is HostResult.Ok) {
                    controllers.remove(id)?.dispose()
                }
            }
        }

    override fun stopAdmission(profile: ProfileId) {
        synchronized(closed) { closed += profile.raw }
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = serial.withLock {
        when (val disposed = controllers.remove(profile.raw)?.dispose()) {
            is HostResult.Failed -> Outcome.Failed(disposed.code.toFailure())
            else -> Unit.ok()
        }
    }

    override fun reopen(profile: ProfileId) {
        synchronized(closed) { closed -= profile.raw }
    }

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        serial.withLock {
            val record = store.readRecord(profileId.raw)
            if (record is HostResult.Failed) return@withLock Outcome.Failed(record.code.toFailure())
            val secret = store.readSecrets(profileId.raw)
            if (secret is HostResult.Failed) return@withLock Outcome.Failed(secret.code.toFailure())
            if ((record as HostResult.Ok).value == null && (secret as HostResult.Ok).value == null)
                return@withLock 0.ok()
            when (val cleaned = runner.cleanup(profileId.raw)) {
                is HostResult.Failed -> return@withLock Outcome.Failed(cleaned.code.toFailure())
                is HostResult.Ok -> Unit
            }
            when (val removed = store.remove(profileId.raw)) {
                is HostResult.Failed -> Outcome.Failed(removed.code.toFailure())
                is HostResult.Ok -> 1.ok()
            }
        }
}
