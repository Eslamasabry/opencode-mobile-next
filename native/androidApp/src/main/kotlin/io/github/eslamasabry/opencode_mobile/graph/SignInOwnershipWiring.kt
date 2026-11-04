package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.background.CleanupToken
import io.github.eslamasabry.oc.android.background.ForegroundCleanup
import io.github.eslamasabry.oc.android.background.ForegroundLease
import io.github.eslamasabry.oc.android.background.ForegroundLeasePort
import io.github.eslamasabry.oc.android.background.SignInForegroundBinding
import io.github.eslamasabry.oc.android.background.SignInForegroundRegistry
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The phone account owns its service binding; screens only borrow leases from that owner. */
@SingleIn(AppScope::class)
@Inject
class SignInOwnership(
    private val hub: BackgroundHub,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    scope: AppCoroutineScope,
) {
    private val main = CoroutineScope(scope.coroutineContext + dispatchers.main)
    private val registry = SignInForegroundRegistry(main)
    private val retired = mutableSetOf<String>()
    private val bindings = mutableMapOf<String, SignInForegroundBinding>()

    /** Called on the main lane after the stores and controller have warmed. */
    suspend fun reserve(stillWanted: () -> Boolean): ForegroundLease? {
        val controller = hub.controller()
        val owner =
            (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value ?: return null
        if (!stillWanted() || owner in retired || stores.profiles.profile(ProfileId(owner)) == null)
            return null
        val existing = bindings[owner]
        if (existing != null && !existing.current) {
            // Retry the exact failed old drain before making a replacement: a failed Deferred
            // must not become a permanent ancestor gate for every later reservation.
            if (registry.unbind(existing) is Outcome.Failed) return null
            if (bindings[owner] === existing) bindings.remove(owner)
        }
        if (
            !stillWanted() ||
                owner in retired ||
                stores.profiles.profile(ProfileId(owner)) == null ||
                (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value != owner
        )
            return null
        val binding =
            bindings[owner]?.takeIf { it.current }
                ?: registry.bind(owner, ForegroundLeasePort { controller.reserve() }).also {
                    bindings[owner] = it
                }
        return binding.reserve()
    }

    /** Register before opening the PTY: an owner drain waits even for a late native handle. */
    suspend fun watch(owner: String, cleanup: ForegroundCleanup): Cleanup? =
        withContext(dispatchers.main) {
            if (owner in retired) return@withContext null
            val binding = bindings[owner] ?: return@withContext null
            binding.addCleanup(cleanup)?.let { Cleanup(binding, it) }
        }

    class Cleanup(val binding: SignInForegroundBinding, val token: CleanupToken)

    fun forget(cleanup: Cleanup) {
        main.launch { cleanup.binding.removeCleanup(cleanup.token) }
    }

    fun stopAdmission(profile: ProfileId) {
        retired += profile.raw
        main.launch { drain(profile) }
    }

    suspend fun drain(profile: ProfileId): Outcome<Unit> =
        withContext(dispatchers.main) {
            val binding = bindings[profile.raw] ?: return@withContext Unit.ok()
            when (val result = registry.unbind(binding)) {
                is Outcome.Failed -> result
                is Outcome.Ok -> {
                    if (bindings[profile.raw] === binding) bindings.remove(profile.raw)
                    Unit.ok()
                }
            }
        }

    fun reopen(profile: ProfileId) {
        retired -= profile.raw
    }
}

@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@Inject
class SignInOwnershipCloser(private val ownership: SignInOwnership) : ProfileCloser {
    override val owner = ":android:background:sign-in"

    override fun stopAdmission(profile: ProfileId) = ownership.stopAdmission(profile)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = ownership.drain(profile)

    override fun reopen(profile: ProfileId) = ownership.reopen(profile)
}
