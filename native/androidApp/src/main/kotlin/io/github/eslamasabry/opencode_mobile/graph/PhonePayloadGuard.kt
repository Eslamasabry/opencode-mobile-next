package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.engine.connection.TransientOwners
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.protocol.paseo.work.PaseoPayloadLease
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.SetupRunner
import java.net.URI
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One process-wide reader/mutation gate for the phone's shared pinned agent payloads. */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class PhonePayloadGuard(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val transientOwners: TransientOwners,
) : ProfileCloser {
    override val owner = ":engine:agents/payloads"
    private val lock = Any()
    private var readers = 0
    private var mutating = false
    private val closed = HashSet<ProfileId>()
    private val callbacks = HashMap<ProfileId, () -> Outcome<PaseoPayloadLease>>()
    private val callbackOwners = HashMap<ProfileId, ProfileId>()

    class Mutation internal constructor(private val releaseNow: () -> Unit) {
        private var ended = false

        fun release() {
            synchronized(this) {
                if (!ended) {
                    ended = true
                    releaseNow()
                }
            }
        }
    }

    fun beginMutation(profile: ProfileId?): Outcome<Mutation> =
        synchronized(lock) {
            if (mutating || readers != 0 || (profile != null && profile in closed))
                return@synchronized busy()
            mutating = true
            Mutation { synchronized(lock) { mutating = false } }.ok()
        }

    /** Local sign-in uses the installed CLI directly; it never needs or starts its daemon. */
    fun reserveNativeSignIn(profile: ProfileId): Outcome<PaseoPayloadLease> =
        synchronized(lock) {
            fun current(): Boolean =
                profile !in closed &&
                    stores.profiles.profile(profile) != null &&
                    (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value ==
                        profile.raw
            if (!current()) return@synchronized stale()
            if (mutating || SetupRunner.get(app).running || readers >= 32)
                return@synchronized busy()
            readers++
            var released = false
            object : PaseoPayloadLease {
                    override suspend fun awaitReady(): Outcome<Unit> {
                        val installed =
                            withContext(dispatchers.io) {
                                BuiltinLinux.get(app).installed && !SetupRunner.get(app).running
                            }
                        return synchronized(lock) {
                            if (released || !current()) stale()
                            else if (!installed || mutating) busy() else Unit.ok()
                        }
                    }

                    override fun release() {
                        synchronized(lock) {
                            if (!released) {
                                released = true
                                readers--
                            }
                        }
                    }
                }
                .ok()
        }

    /**
     * Remote computers keep ordinary gateway behaviour. Unowned phone-loopback endpoints fail
     * closed.
     */
    fun callbackFor(spec: GatewaySpec): (() -> Outcome<PaseoPayloadLease>)? {
        val uri =
            try {
                URI(spec.profile.origin.toString())
            } catch (_: Exception) {
                return null
            }
        if (uri.host !in setOf("127.0.0.1", "localhost", "::1", "[::1]") || uri.port != 4099)
            return null
        val alias = spec.profile.id
        // A transient helper is admitted by the registry before its factory runs. Its native
        // ownership proof is the authority; an alias prefix or a preference is never sufficient.
        val transientOwner = transientOwners.ownerOf(alias)
        val owner = transientOwner ?: alias.takeIf { stores.profiles.profile(it) != null }
        val callback: () -> Outcome<PaseoPayloadLease> = {
            if (owner == null) stale() else reserve(spec, owner, transientOwner != null)
        }
        if (owner != null)
            synchronized(lock) {
                callbacks[alias] = callback
                callbackOwners[alias] = owner
            }
        return callback
    }

    /** Uses the original authenticated live-factory binding; never reconstructs a phone target. */
    fun reserveForProfile(profile: ProfileId): Outcome<PaseoPayloadLease> =
        synchronized(lock) { callbacks[profile] }?.invoke() ?: stale()

    private fun sourceCurrent(spec: GatewaySpec, profile: ProfileId, transient: Boolean): Boolean =
        spec.scope.coroutineContext[Job]?.isActive == true &&
            profile !in closed &&
            spec.profile.id !in closed &&
            stores.profiles.profile(profile) != null &&
            (if (transient) transientOwners.ownerOf(spec.profile.id) == profile
            else spec.profile.id == profile && transientOwners.ownerOf(spec.profile.id) == null) &&
            (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value == profile.raw

    private fun reserve(
        spec: GatewaySpec,
        profile: ProfileId,
        transient: Boolean,
    ): Outcome<PaseoPayloadLease> =
        synchronized(lock) {
            if (!sourceCurrent(spec, profile, transient)) return@synchronized stale()
            if (mutating || SetupRunner.get(app).running || readers >= 32)
                return@synchronized busy()
            readers++
            var released = false
            object : PaseoPayloadLease {
                    override suspend fun awaitReady(): Outcome<Unit> {
                        val password =
                            (stores.secrets.read("oc.agentHostSecret.${profile.raw}")
                                    as? Outcome.Ok)
                                ?.value
                        if (password.isNullOrEmpty() || password != spec.credentials.secret)
                            return stale()
                        val running =
                            withContext(dispatchers.io) {
                                (attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                                        BuiltinLinux.get(app)
                                            .agentHost
                                            .status(profile.raw)["running"] == true &&
                                            !SetupRunner.get(app).running
                                    }
                                        as? Outcome.Ok)
                                    ?.value == true
                            }
                        return synchronized(lock) {
                            if (released || !sourceCurrent(spec, profile, transient)) stale()
                            else if (!running || mutating) busy() else Unit.ok()
                        }
                    }

                    override fun release() {
                        synchronized(lock) {
                            if (!released) {
                                released = true
                                readers--
                            }
                        }
                    }
                }
                .ok()
        }

    override fun stopAdmission(profile: ProfileId) {
        synchronized(lock) {
            closed += profile
            val aliases = callbackOwners.filterValues { it == profile }.keys.toList()
            for (alias in aliases) {
                callbacks.remove(alias)
                callbackOwners.remove(alias)
            }
            callbacks.remove(profile)
            callbackOwners.remove(profile)
        }
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        val drained =
            withTimeoutOrNull(15_000) {
                while (synchronized(lock) { readers != 0 || mutating }) delay(50)
                true
            } ?: false
        return if (drained) Unit.ok() else busy()
    }

    override fun reopen(profile: ProfileId) {
        synchronized(lock) { closed -= profile }
    }

    private fun busy(): Outcome<Nothing> = Failure.Busy(BusyKind.Overloaded).failed()

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()
}
