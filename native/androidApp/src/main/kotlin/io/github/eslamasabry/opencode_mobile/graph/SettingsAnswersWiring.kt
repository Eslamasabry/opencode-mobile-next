package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.*
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.engine.settings.consent.*
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundLive
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveSetting
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveSettings
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.ui.routes.*
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App answers are not Android grants. The actual system action follows the acknowledged explicit
 * answer.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SettingsAnswersSource>())
@Inject
class NativeSettingsAnswers(
    private val stores: PreviewStores,
    private val invitations: NativePermissionInvitations,
    private val registry: ConnectionRegistry,
    private val keepAlive: KeepAliveSettings,
    private val background: BackgroundLive,
    private val notifications: AndroidNotificationSettings,
    private val nav: AppNavigator,
    private val lifetime: NativeAnswerLifetime,
) : SettingsAnswersSource {
    override val changes: Flow<Unit> = flow {
        if (stores.warm() is Outcome.Ok)
            emitAll(
                combine(stores.prefs.changes.onStart { emit(emptySet()) }, registry.live) { _, _ ->
                        Unit
                    }
                    .conflate()
            )
        else emit(Unit)
    }

    override suspend fun read(profile: ProfileId): Outcome<PermissionSettingsFacts> =
        lifetime.operations.withLock {
            if (stores.warm() is Outcome.Failed) return@withLock Failure.StorageRefused().failed()
            val handle = lifetime.handle(profile) ?: return@withLock stale()
            val readable = handle.consent.storageAvailable(profile)
            val answers =
                InFlowConsentKind.entries
                    .map { handle.consent.row(profile, it) }
                    .filter { it.choice != ConsentChoice.Unseen }
                    .sortedBy {
                        when (it.choice) {
                            ConsentChoice.Offered -> 0
                            ConsentChoice.Denied -> 1
                            else -> 2
                        }
                    }
                    .toImmutableList()
            val invitation = invitations.forProfile(profile)
            val count = invitation?.declinedCount()
            if (!handle.valid()) return@withLock stale()
            val graph = registry.live.value[profile]
            PermissionSettingsFacts(
                    profile,
                    handle.saved.profile.name,
                    handle.ticket,
                    answers,
                    readable,
                    count,
                    graph?.session?.capabilities?.value?.savedPermissionList == true,
                )
                .ok()
        }

    override suspend fun allow(
        profile: ProfileId,
        ticket: Long,
        kind: InFlowConsentKind,
        stillCurrent: () -> Boolean,
    ): Outcome<Unit> =
        lifetime.operations.withLock {
            val handle =
                lifetime.handle(profile)?.takeIf {
                    it.ticket == ticket && it.valid() && stillCurrent()
                } ?: return@withLock stale()
            if (handle.consent.row(profile, kind).choice == ConsentChoice.Unseen)
                return@withLock stale()
            val result = handle.consent.changeFromSettings(profile, kind, true)
            if (result is Outcome.Failed) return@withLock result
            if (!handle.valid() || !stillCurrent()) return@withLock stale()
            when (kind) {
                InFlowConsentKind.BatteryExemption -> background.allowUnrestrictedBattery()
                InFlowConsentKind.MakerAutoStart ->
                    if (!keepAlive.open(KeepAliveSetting.Autostart))
                        return@withLock Failure.Unavailable(FeatureId("maker-auto-start")).failed()
                InFlowConsentKind.NeedsYouNotifications -> {
                    val saved = notifications.set(NotificationChange.Questions(true))
                    if (saved is Outcome.Failed) return@withLock saved
                    if (!handle.valid() || !stillCurrent()) return@withLock stale()
                    when (val enabled = background.setEnabled(true)) {
                        is Outcome.Failed -> return@withLock enabled
                        is Outcome.Ok ->
                            if (!enabled.value)
                                return@withLock Failure.Refused(RefusalReason.NotificationsBlocked)
                                    .failed()
                    }
                }
            }
            Unit.ok()
        }

    override suspend fun offerAgain(
        profile: ProfileId,
        ticket: Long,
        stillCurrent: () -> Boolean,
    ): Outcome<Unit> =
        lifetime.operations.withLock {
            val handle =
                lifetime.handle(profile)?.takeIf {
                    it.ticket == ticket && it.valid() && stillCurrent()
                } ?: return@withLock stale()
            val owner = invitations.forProfile(profile) ?: return@withLock stale()
            if (!handle.valid() || !stillCurrent()) return@withLock stale()
            if (owner.forgetDeclined() && handle.valid()) Unit.ok()
            else Failure.StorageRefused().failed()
        }

    override fun openHome(kind: InFlowConsentKind) {
        nav.open(
            if (kind == InFlowConsentKind.NeedsYouNotifications) SettingsRoute.Notifications
            else PhoneRoute.KeepRunning
        )
    }

    override fun openGrants(profile: ProfileId) {
        if (registry.live.value[profile]?.session?.capabilities?.value?.savedPermissionList == true)
            nav.open(ServersRoute.Permissions(profile))
    }

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()
}

/**
 * The deletion authority has no connection dependency, so profile close cannot cycle through its
 * registry.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@Inject
class NativeAnswerLifetime(private val stores: PreviewStores) : ProfileCloser {
    private val lock = Any()
    val operations = Mutex()
    private var next = 0L
    private val closed = HashSet<ProfileId>()
    private val handles = HashMap<ProfileId, Handle>()

    inner class Handle(val saved: LoadedProfile, val ticket: Long) {
        fun valid(): Boolean =
            synchronized(lock) {
                saved.id !in closed &&
                    handles[saved.id] === this &&
                    stores.profiles.profile(saved.id) === saved
            }

        private val guarded =
            object : KeyValueStore by stores.prefs {
                override suspend fun commit(
                    lane: WriterLane,
                    edit: PrefEdit.() -> Unit,
                ): Outcome<Unit> {
                    if (!valid()) return stale()
                    val profileVersion = stores.prefs.get(ProfileKeys.PROFILES)
                    val key = ConsentKeys.inFlow(saved.id)
                    val old = stores.prefs.get(key)
                    if (!valid()) return stale()
                    return when (
                        val saved =
                            stores.prefs.compareAndCommit(
                                lane,
                                mapOf(ProfileKeys.PROFILES to profileVersion, key to old),
                                edit,
                            )
                    ) {
                        is Outcome.Failed -> saved
                        is Outcome.Ok -> if (saved.value && valid()) Unit.ok() else stale()
                    }
                }
            }
        val consent =
            InFlowConsentStore(guarded, stores.lanes, SavedProfiles { it == saved.id && valid() })
    }

    fun handle(profile: ProfileId): Handle? =
        synchronized(lock) {
            if (profile in closed) return@synchronized null
            val saved = stores.profiles.profile(profile) ?: return@synchronized null
            handles[profile]?.takeIf { it.saved === saved }
                ?: Handle(saved, ++next).also { handles[profile] = it }
        }

    override val owner = ":engine:settings:in-flow-answers"

    override fun stopAdmission(profile: ProfileId) {
        synchronized(lock) {
            closed += profile
            handles[profile]?.consent?.closeProfile(profile)
        }
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = operations.withLock {
        Unit.ok()
    }

    override fun reopen(profile: ProfileId) {
        synchronized(lock) {
            closed -= profile
            handles.remove(profile)
        }
    }

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()
}

/** Both Settings entry points share the existing real Android notification adapter. */
@BindingContainer
@ContributesTo(AppScope::class)
object NotificationChoiceBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun notifications(
        application: android.app.Application,
        stores: PreviewStores,
        prefs: AppPreferences,
        scope: AppCoroutineScope,
    ): AndroidNotificationSettings =
        AndroidNotificationSettings(
            application,
            stores,
            prefs,
            kotlinx.coroutines.CoroutineScope(scope.coroutineContext),
        )
}
