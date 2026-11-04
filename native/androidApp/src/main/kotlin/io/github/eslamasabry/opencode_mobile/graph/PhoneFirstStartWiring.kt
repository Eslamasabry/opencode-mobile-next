package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.runtime.Composable
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
import io.github.eslamasabry.oc.feature.phone.ui.PhoneFirstStartConsentHost
import io.github.eslamasabry.oc.ui.shell.ShellOverlay
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

    private data class Ask(val profile: ProfileId, val ui: PhoneConsentUi, val failedOnce: Boolean = false)

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
            is Outcome.Failed -> {
                // The question stays once so the person sees the failure; a second refused save
                // stops asking this profile (Flutter stops after a refused save), so an unwritable
                // store never traps the person in the dialog.
                if (ask.failedOnce) asks.removeAll { it.profile == ask.profile }
                else asks[0] = ask.copy(failedOnce = true)
                publish()
                saved
            }
            is Outcome.Ok -> {
                asks.removeFirst()
                if (allow)
                    when (ask.ui.kind) {
                        InFlowConsentKind.BatteryExemption -> background.allowUnrestrictedBattery()
                        InFlowConsentKind.MakerAutoStart -> settings.open(KeepAliveSetting.Autostart)
                        InFlowConsentKind.NeedsYouNotifications -> Unit // no Android page here
                    }
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

/** Asks the first-start questions above whatever screen is open (Flutter asks wherever it started). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ShellOverlay>())
class PhoneConsentOverlay(private val consents: PhoneFirstStartConsents) : ShellOverlay {
    @Composable override fun Content() = PhoneFirstStartConsentHost(consents)
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class PhoneConsentDeletion(private val consents: NativePhoneFirstStartConsents) : ProfileCloser {
    override val owner: String = ":engine:settings"

    override fun stopAdmission(profile: ProfileId) = consents.stopAdmission(profile)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = consents.drain(profile)

    override fun reopen(profile: ProfileId) = consents.reopen(profile)
}
