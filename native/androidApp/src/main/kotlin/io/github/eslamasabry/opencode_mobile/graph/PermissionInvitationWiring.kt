package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.scope.*
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.engine.requests.consent.*

/**
 * A shared owner for cards and Settings; every stored edit compares its exact saved-server
 * generation.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PermissionInvitationAuthority>())
@Inject
class NativePermissionInvitations(private val stores: PreviewStores) :
    PermissionInvitationAuthority, ProfileCloser {
    private val mutableRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    override val revision: kotlinx.coroutines.flow.StateFlow<Long> = mutableRevision
    private val lock = Any()
    private val closed = HashSet<ProfileId>()
    private val epochs = HashMap<ProfileId, Long>()
    private val current = HashMap<ProfileId, Handle>()
    private val all = HashMap<ProfileId, MutableList<Handle>>()

    private inner class Handle(val saved: LoadedProfile, val epoch: Long) {
        private val profile = saved.id
        private val key = "oc.permissionConsent.${profile.raw}"
        private var read = false
        private var consentVersion: PrefValue? = null
        private var profilesVersion: PrefValue? = null

        fun valid(): Boolean =
            synchronized(lock) {
                profile !in closed &&
                    epochs[profile] == epoch &&
                    stores.profiles.profile(profile) === saved
            }

        val invitation =
            AlwaysAllowInvitation(
                object : ConsentStorage {
                    override suspend fun read(): Outcome<String?> {
                        if (!valid()) return Failure.Refused(RefusalReason.StaleScope).failed()
                        val profiles = stores.prefs.get(ProfileKeys.PROFILES)
                        val value = stores.prefs.get(key)
                        if (value != null && value !is PrefValue.Str)
                            return Failure.StorageRefused().failed()
                        if (!valid()) return Failure.Refused(RefusalReason.StaleScope).failed()
                        profilesVersion = profiles
                        consentVersion = value
                        read = true
                        return (value as? PrefValue.Str)?.value.ok()
                    }

                    override suspend fun write(json: String): Outcome<Unit> {
                        if (!read || !valid())
                            return Failure.Refused(RefusalReason.StaleScope).failed()
                        val result =
                            stores.prefs.compareAndCommit(
                                stores.lanes.forProfile(profile),
                                mapOf(
                                    ProfileKeys.PROFILES to profilesVersion,
                                    key to consentVersion,
                                ),
                            ) {
                                putString(key, json)
                            }
                        return when (result) {
                            is Outcome.Failed -> result
                            is Outcome.Ok ->
                                if (result.value && valid()) {
                                    consentVersion = PrefValue.Str(json)
                                    mutableRevision.value++
                                    Unit.ok()
                                } else Failure.StorageRefused().failed()
                        }
                    }
                }
            )
    }

    override suspend fun forProfile(profileId: ProfileId): AlwaysAllowInvitation? {
        if (stores.warm() is Outcome.Failed) return null
        return synchronized(lock) {
            if (profileId in closed) return@synchronized null
            val saved = stores.profiles.profile(profileId) ?: return@synchronized null
            val epoch = epochs.getOrPut(profileId) { 0L }
            val handle =
                current[profileId]?.takeIf { it.saved === saved && it.epoch == epoch }
                    ?: Handle(saved, epoch).also {
                        current[profileId] = it
                        all.getOrPut(profileId) { ArrayList() }.add(it)
                    }
            handle.invitation
        }
    }

    override val owner = ":engine:requests:permission-invitations"

    override fun stopAdmission(profile: ProfileId) {
        synchronized(lock) {
            closed += profile
            epochs[profile] = (epochs[profile] ?: 0L) + 1
            current.remove(profile)
            mutableRevision.value++
        }
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        val handles = synchronized(lock) { all[profile]?.toList().orEmpty() }
        handles.forEach { it.invitation.awaitIdle() }
        synchronized(lock) { all.remove(profile) }
        return Unit.ok()
    }

    override fun reopen(profile: ProfileId) {
        synchronized(lock) { closed -= profile }
    }

    companion object {
        fun register(scope: ProfileScope) {
            scope.register(
                KeyShape(
                    "oc.permissionConsent.",
                    KeyScope.Profile,
                    io.github.eslamasabry.oc.core.data.kv.PrefType.Str,
                    ":engine:requests",
                    "permission invitation decisions",
                )
            )
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object InvitationLifecycleBindings {
    @Provides @IntoSet fun closer(owner: NativePermissionInvitations): ProfileCloser = owner
}
