package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.cache.LastKnown
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

/**
 * One process-wide reader/mutation gate for the phone's shared pinned agent payloads. It also owns [installGate], the
 * one gate of the phone's install jobs: the setup engine and the agents' installer share components and the one
 * installer slot, so each takes it with tryLock for its whole START PHASE (its checks, until its job is registered as
 * running in its own record); a failed tryLock is busy and the try is kept. After registration it is released and each
 * side's own "a job is running" check covers the rest.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class PhonePayloadGuard(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val transientOwners: TransientOwners,
    private val redactor: io.github.eslamasabry.oc.core.security.Redactor,
    private val lastKnown: LastKnown,
) : ProfileCloser {
    override val owner = ":engine:agents/payloads"

    /** The one gate of the phone's install jobs (see the class note). */
    val installGate = kotlinx.coroutines.sync.Mutex()
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
        val transient = transientOwners.ownerOf(alias) != null
        // The saved helper `<owner>-agents` (This phone's agent rows since "one This phone") belongs to its phone:
        // before, it was checked as a phone of its own and every history read was refused as stale.
        val owner = ownerOf(alias)
        val callback: () -> Outcome<PaseoPayloadLease> = {
            if (owner == null) stale(OWNER) else reserve(spec, owner, transient)
        }
        if (owner != null)
            synchronized(lock) {
                callbacks[alias] = callback
                callbackOwners[alias] = ProfileId(owner.phone)
            }
        return callback
    }

    /** [PhoneAgentRules.payloadOwner] over today's prefs and saved profiles. */
    private fun ownerOf(alias: ProfileId): PhoneAgentRules.PayloadOwner? =
        PhoneAgentRules.payloadOwner(
            alias.raw,
            transientOwners.ownerOf(alias)?.raw,
            (stores.prefs.get("oc.phoneAgentOwner.${alias.raw}") as? PrefValue.Str)?.value,
            (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value,
            stores.profiles.profiles.value.mapTo(HashSet()) { it.id.raw },
        )

    /** Uses the original authenticated live-factory binding; never reconstructs a phone target. */
    fun reserveForProfile(profile: ProfileId): Outcome<PaseoPayloadLease> =
        synchronized(lock) { callbacks[profile] }?.invoke() ?: stale()

    /**
     * The lease's owner still holds: the connection lives, neither it nor its phone was closed, the phone is still
     * saved and still the app's own OpenCode, and the connection still resolves to the same owner (a transient alias
     * by its native proof, the saved helper by its phone).
     */
    private fun sourceCurrent(spec: GatewaySpec, owner: PhoneAgentRules.PayloadOwner, transient: Boolean): Boolean {
        val phone = ProfileId(owner.phone)
        return spec.scope.coroutineContext[Job]?.isActive == true &&
            phone !in closed &&
            spec.profile.id !in closed &&
            stores.profiles.profile(phone) != null &&
            (transientOwners.ownerOf(spec.profile.id) != null) == transient &&
            ownerOf(spec.profile.id) == owner &&
            PhoneAgentRules.payloadOwnerHolds(
                owner,
                (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value,
                stores.profiles.profiles.value.mapTo(HashSet()) { it.id.raw },
            )
    }

    private fun reserve(
        spec: GatewaySpec,
        owner: PhoneAgentRules.PayloadOwner,
        transient: Boolean,
    ): Outcome<PaseoPayloadLease> =
        synchronized(lock) {
            if (!sourceCurrent(spec, owner, transient)) return@synchronized stale(OWNER)
            if (mutating || SetupRunner.get(app).running || readers >= 32)
                return@synchronized busy()
            readers++
            var released = false
            object : PaseoPayloadLease {
                    override suspend fun awaitReady(): Outcome<Unit> {
                        // The key the helper's starter writes: its owner's (AppLocalAgentHostStarter, the handoff).
                        val password =
                            (stores.secrets.read("oc.agentHostSecret.${owner.helper}")
                                    as? Outcome.Ok)
                                ?.value
                        if (!PhoneAgentRules.payloadSecretMatches(password, spec.credentials.secret))
                            return stale(SECRET)
                        val running =
                            withContext(dispatchers.io) {
                                (attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                                        BuiltinLinux.get(app)
                                            .agentHost
                                            .status(owner.helper)["running"] == true &&
                                            !SetupRunner.get(app).running
                                    }
                                        as? Outcome.Ok)
                                    ?.value == true
                            }
                        return synchronized(lock) {
                            if (released || !sourceCurrent(spec, owner, transient)) stale(OWNER)
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

    /** Sweeps kept pages for the phone agents' helper profile when phone agents are removed. */
    suspend fun sweepAgentHelperLastKnown(owner: ProfileId? = null) {
        sweepPhoneAgentLastKnown(lastKnown, stores, owner)
    }

    private fun busy(): Outcome<Nothing> = Failure.Busy(BusyKind.Overloaded).failed()

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

    /** A lease refusal whose Details name the check that refused ([OWNER], [SECRET]). */
    private fun stale(check: String): Outcome<Nothing> =
        Failure.Refused(RefusalReason.StaleScope, redactor.details(check)).failed()

    private companion object {
        const val OWNER = "payload owner"
        const val SECRET = "payload secret"
    }
}
