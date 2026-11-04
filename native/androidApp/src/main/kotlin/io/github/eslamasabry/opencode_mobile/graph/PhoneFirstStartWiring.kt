package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.settings.consent.InFlowConsentKind
import io.github.eslamasabry.oc.engine.settings.consent.InFlowConsentStore
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundLive
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveAdvice
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveSetting
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveSettings
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveStepKind
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneConsentUi
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneFirstStartConsents
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneMaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Claimed, saved app answers first; an Android settings page is never treated as a permission
 * grant.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NativePhoneFirstStartConsents(
    private val stores: PreviewStores,
    private val settings: KeepAliveSettings,
    private val background: BackgroundLive,
) : PhoneFirstStartConsents {
    private val consent by lazy { InFlowConsentStore(stores.prefs, stores.lanes) }
    private val lane = Mutex()
    private val closed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var activeProfile: ProfileId? = null

    private data class Ask(val profile: ProfileId, val ui: PhoneConsentUi)

    private val asks = ArrayDeque<Ask>()
    private val visible = MutableStateFlow<PhoneConsentUi?>(null)
    override val pending: StateFlow<PhoneConsentUi?> = visible.asStateFlow()

    suspend fun started(profile: ProfileId) {
        val facts = settings.info() ?: return
        val maker = PhoneMaker.of(facts.manufacturer, facts.brand)
        request(
            profile,
            facts.batteryOptimizationIgnored,
            KeepAliveAdvice.steps(maker).any { it.kind == KeepAliveStepKind.Autostart },
            facts.manufacturer,
        )
    }

    override suspend fun request(
        profile: ProfileId,
        batteryAlreadyExempt: Boolean,
        makerHasAutoStartStep: Boolean,
        maker: String,
    ) {
        if (stores.warm() is Outcome.Failed) return
        lane.withLock {
            val claimed =
                consent.phoneServerFirstStart(profile, batteryAlreadyExempt, makerHasAutoStartStep)
            if (claimed is Outcome.Ok)
                claimed.value.forEach { asks.addLast(Ask(profile, PhoneConsentUi(it, maker))) }
            publish()
        }
    }

    override suspend fun answer(allow: Boolean): Outcome<Unit> = lane.withLock {
        publish()
        val ask =
            asks.firstOrNull()
                ?: return@withLock Outcome.Failed(Failure.Refused(RefusalReason.AlreadyAnswered))
        when (val saved = consent.answer(ask.profile, ask.ui.kind, allow)) {
            is Outcome.Failed -> saved
            is Outcome.Ok -> {
                asks.removeFirst()
                if (allow && ask.ui.kind == InFlowConsentKind.BatteryExemption)
                    background.allowUnrestrictedBattery()
                else if (allow) settings.open(KeepAliveSetting.Autostart)
                publish()
                saved
            }
        }
    }

    fun stopAdmission(profile: ProfileId) {
        closed.add(profile.raw)
        consent.closeProfile(profile)
        if (activeProfile == profile) visible.value = null
    }

    suspend fun drain(profile: ProfileId): Outcome<Unit> = lane.withLock {
        asks.removeAll { it.profile == profile }
        publish()
        Outcome.Ok(Unit)
    }

    fun reopen(profile: ProfileId) {
        consent.reopenProfile(profile)
        closed.remove(profile.raw)
    }

    private fun prune() {
        asks.removeAll { it.profile.raw in closed || stores.profiles.profile(it.profile) == null }
    }

    private fun publish() {
        prune()
        activeProfile = asks.firstOrNull()?.profile
        visible.value = asks.firstOrNull()?.ui
    }
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class PhoneConsentDeletion(private val consents: NativePhoneFirstStartConsents) : ProfileCloser {
    override val owner: String = ":engine:settings"

    override fun stopAdmission(profile: ProfileId) = consents.stopAdmission(profile)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = consents.drain(profile)

    override fun reopen(profile: ProfileId) = consents.reopen(profile)
}
