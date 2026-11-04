package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.domain.FencedSessionReadStateGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.engine.settings.consent.*
import io.github.eslamasabry.oc.engine.settings.scope.SettingsOwners
import io.github.eslamasabry.oc.feature.chat.world.ChatReadSharing
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One shared consent authority, private until a per-server grant is acknowledged. */
data class ReadSharingFacts(val enabled: Boolean, val saving: Boolean, val defaultEnabled: Boolean)

@SingleIn(AppScope::class)
@Inject
class NativeReadStateConsent(private val stores: PreviewStores, scope: AppCoroutineScope) :
    ProfileCloser, ProfileResourceCleaner {
    private val ownerRevision = MutableStateFlow(0L)
    private val owners =
        object : SettingsOwners {
            override val revision: StateFlow<Long> = ownerRevision

            override fun fingerprint(profileId: ProfileId): String? {
                val p = stores.profiles.profile(profileId)?.profile ?: return null
                // Persist only the digest; transport strings (including URL queries) never escape
                // this owner.
                val identity =
                    listOf(p.backend.name, p.baseUrl, p.username, p.codexDirectory).joinToString(
                        ""
                    ) {
                        "${it.length}:$it"
                    }
                return MessageDigest.getInstance("SHA-256")
                    .digest(identity.toByteArray())
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
            }
        }
    private val consent by lazy { ReadStateConsentStore(stores.prefs, stores.lanes, owners) }
    val revision: StateFlow<Long>
        get() = consent.revision

    private val closed = java.util.concurrent.ConcurrentHashMap.newKeySet<ProfileId>()

    init {
        scope.launch {
            if (stores.warm() is Outcome.Ok)
                stores.profiles.profiles.collect { ownerRevision.value++ }
        }
    }

    suspend fun snapshot(profile: ProfileId): ReadSharingFacts {
        if (stores.warm() is Outcome.Failed || stores.profiles.profile(profile) == null)
            return ReadSharingFacts(false, false, false)
        return ReadSharingFacts(
            profile !in closed && consent.enabled(profile),
            consent.saving(profile),
            consent.defaultFor(profile),
        )
    }

    suspend fun setEnabled(
        profile: ProfileId,
        on: Boolean,
        isCurrent: () -> Boolean = { true },
    ): Outcome<Unit> {
        if (stores.warm() is Outcome.Failed) return Failure.StorageRefused().failed()
        val saved =
            stores.profiles.profile(profile)
                ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        return consent.setEnabledIfCurrent(profile, on) {
            profile !in closed && stores.profiles.profile(profile) === saved && isCurrent()
        }
    }

    suspend fun reset(profile: ProfileId): Outcome<Unit> {
        if (stores.warm() is Outcome.Failed) return Failure.StorageRefused().failed()
        return setEnabled(profile, consent.defaultFor(profile))
    }

    internal fun ticket(profile: ProfileId): SharingTicket? =
        if (profile in closed) null else consent.ticket(profile)

    internal fun stillShared(ticket: SharingTicket): Boolean =
        ticket.profileId !in closed && consent.stillShared(ticket)

    override val owner = ":engine:settings:read-state"
    override val order = DeletionStep.STASH - 1
    override val guardsSweep = true

    override fun stopAdmission(profile: ProfileId) {
        closed += profile
        consent.closeProfile(profile)
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = consent.drainProfile(profile)

    override fun reopen(profile: ProfileId) {
        closed -= profile
        consent.reopenProfile(profile)
    }

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        consent.forgetProfile(profileId).map { 0 }

    companion object {
        fun register(scope: ProfileScope) {
            ConsentKeys.shapes.forEach(scope::register)
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object ReadSharingBindings {
    @Provides @IntoSet fun closer(owner: NativeReadStateConsent): ProfileCloser = owner

    @Provides @IntoSet fun cleaner(owner: NativeReadStateConsent): ProfileResourceCleaner = owner
}

/**
 * No background receipts, queue, wall-clock watermark, retry or local rollback after an uncertain
 * POST.
 */
@ContributesIntoSet(AppScope::class, binding = binding<ChatReadSharing>())
@SingleIn(AppScope::class)
@Inject
class NativeReadReceipts(
    private val consent: NativeReadStateConsent,
    private val stores: PreviewStores,
    private val activities: ResumedActivities,
    private val nav: AppNavigator,
) : ChatReadSharing {
    override fun visible(ref: ConversationRef): Boolean =
        activities.current != null && (nav.top.value as? ChatRoute)?.ref == ref

    override suspend fun shared(ref: ConversationRef): Boolean =
        consent.snapshot(ref.profileId).enabled

    override suspend fun publish(
        ref: ConversationRef,
        gateway: ServerGateway,
        idleMs: Long,
        stillCurrent: () -> Boolean,
    ) {
        val activity = activities.current ?: return
        if (!visible(ref) || !stillCurrent() || idleMs <= 0) return
        if (stores.warm() is Outcome.Failed) return
        val profile = stores.profiles.profile(ref.profileId) ?: return
        val ticket = consent.ticket(ref.profileId) ?: return
        val location = gateway.location.value
        fun admitted(): Boolean =
            activities.current === activity &&
                visible(ref) &&
                stillCurrent() &&
                stores.profiles.profile(ref.profileId) === profile &&
                gateway.location.value == location &&
                consent.stillShared(ticket)
        val target = gateway.facet<FencedSessionReadStateGateway>() ?: return
        if (!admitted()) return
        target.viewSession(ref.sessionId, idleMs, ::admitted)
        // The local displayed watermark was already committed in memory. Failure is never retried.
    }
}
