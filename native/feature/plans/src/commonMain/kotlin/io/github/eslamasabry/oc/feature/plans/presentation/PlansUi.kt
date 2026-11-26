package io.github.eslamasabry.oc.feature.plans.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.integrations.usage.ComputerUsageProblem
import io.github.eslamasabry.oc.engine.integrations.usage.SignInState
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSample
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindowKind
import io.github.eslamasabry.oc.engine.integrations.usage.WindowKey
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The engine the app wiring binds: every connection's readings merged into one list. */
public interface PlansSource {
    public val widgetSettings:
        io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetSettings
        get() = io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetSettings()

    public suspend fun setWidgetSettings(
        settings: io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetSettings
    ): Boolean = false

    public val snapshot: StateFlow<UsageSnapshot>

    /** Per-window reading history for pace charts and previous-window details. */
    public val history: StateFlow<Map<WindowKey, List<UsageSample>>>
        get() = noHistory

    /**
     * Reads every source once; returns when done. [force] (the visible Refresh) bypasses reader
     * cadence, as Flutter's `refresh(force: true)` does.
     */
    public suspend fun refresh(force: Boolean = false)

    /** AGENT-1: computer readers are off until the person enables one saved server explicitly. */
    public val computerReaders: StateFlow<List<ComputerReaderUi>>
        get() = noComputerReaders

    public suspend fun setComputerReaderEnabled(id: String, enabled: Boolean): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("computerUsage")))

    public suspend fun readComputerNow(id: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("computerUsage")))

    /**
     * Only explicit confirmation allows a deployment extension to receive the saved server
     * credential.
     */
    public suspend fun collectors(): List<CollectorTarget> = emptyList()

    public suspend fun setCollector(target: CollectorTarget, enabled: Boolean): Boolean = false

    public suspend fun setThreshold(key: String?, percent: Double?): Boolean

    public suspend fun setResetNotice(key: String?, value: Boolean): Boolean

    public val defaultThreshold: Double

    public val resetNotice: Boolean

    /** AGENT-2: the provider-usage collector rows (phone and opted-in servers). */
    public fun collectorRows(): List<PlanCollectorUi> = emptyList()

    public fun alertsOn(): Boolean = false

    public fun canAlert(): Boolean = false

    public fun phoneSignInOwner(): ProfileId? = null

    public suspend fun setAlertsOn(enabled: Boolean): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("usageAlerts")))

    public suspend fun setUpCollector(profile: ProfileId): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("usageCollector")))

    public suspend fun consentRemoteCollector(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("usageCollector")))

    public suspend fun turnOffCollector(profile: ProfileId): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("usageCollector")))

    /** The phone agents' own sign-in truth, keyed by provider id, for cards without numbers. */
    public val agentTruths: StateFlow<Map<String, PlanAgentTruth>>
        get() = noAgentTruths

    /** "Check <agent> again": asks the phone's agents whether they are signed in. */
    public suspend fun checkPhoneAgents(): Unit = Unit
}

/** The binding is an opaque digest, so stale confirmations cannot authorize changed credentials. */
public data class CollectorTarget(
    val profile: ProfileId,
    val name: String,
    val provider: String,
    val enabled: Boolean,
    val binding: String,
)

public enum class CollectorFailure {
    LinuxNotReady,
    NoNode,
    NodeTooOld,
    Copy,
    Prepare,
    Start,
    Remove,
}

public data class CollectorUi(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val failure: CollectorFailure? = null,
    val details: io.github.eslamasabry.oc.core.common.Failure? = null,
)

private val noComputerReaders: StateFlow<List<ComputerReaderUi>> = MutableStateFlow(emptyList())
private val noAgentTruths: StateFlow<Map<String, PlanAgentTruth>> = MutableStateFlow(emptyMap())
private val noHistory: StateFlow<Map<WindowKey, List<UsageSample>>> = MutableStateFlow(emptyMap())

public data class ComputerReaderUi(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val problem: ComputerUsageProblem? = null,
    val found: List<String> = emptyList(),
)

public interface CollectorControls {
    public val state: StateFlow<CollectorUi>

    public suspend fun setUp()

    public suspend fun turnOff()
}

public enum class PlansPhase {
    Loading,
    Empty,
    List,
}

/**
 * A connection as the screen names it; this phone's agent host has no saved name ([isPhone], words
 * say "This phone").
 */
public data class PlanConnectionUi(val name: String, val isPhone: Boolean, val id: String = "")

public data class PlanCollectorUi(
    val profile: ProfileId,
    val name: String,
    val inApp: Boolean,
    val enabled: Boolean,
)

public data class PlanCollectorAsk(val collector: PlanCollectorUi, val enable: Boolean)

public data class PlanWindowUi(
    val kind: UsageWindowKind,
    val label: String,
    val leftPercent: Int,
    val resetsAtMs: Long?,
    val id: String = "",
)

public data class PlanSignInUi(
    val connection: PlanConnectionUi,
    val state: SignInState,
    val online: Boolean,
    val readsFromHere: Boolean,
)

public data class PlanCardUi(
    val key: String,
    val providerId: String,
    val name: String,
    /** Shown only when the provider has more than one card. */
    val connection: PlanConnectionUi?,
    val state: SubscriptionState,
    val plan: String?,
    val windows: ImmutableList<PlanWindowUi>,
    val fix: UsageFix,
    val fixConnection: PlanConnectionUi?,
    val signedInOn: ImmutableList<PlanSignInUi>,
    val resetSince: Boolean,
    val fetchedAtMs: Long?,
    val alertThreshold: Int,
    val canFix: Boolean = false,
    val widgetVisible: Boolean = true,
    val checkedAtMs: Long? = null,
    val readFrom: PlanConnectionUi? = null,
    val hasAlertOverride: Boolean = false,
    val notifyReset: Boolean = false,
    val agent: PlanAgentTruth? = null,
)

/**
 * What the phone's own agent says about itself when a reader found no numbers on this phone. Mirrors
 * `UsageAgentTruth` so Usage › Plans agrees with Usage › Limits, the tile and the chooser.
 */
public sealed interface PlanAgentTruth {
    public data object SignedIn : PlanAgentTruth

    public data object SignedOut : PlanAgentTruth

    public data class Limit(val resets: String?) : PlanAgentTruth

    public data class NotConfirmed(val agentName: String) : PlanAgentTruth
}

public data class PlanKeyUi(
    val name: String,
    val connection: PlanConnectionUi,
    val viaOpenCode: Boolean,
)

public data class PlansUi(
    val phase: PlansPhase = PlansPhase.Loading,
    val cards: ImmutableList<PlanCardUi> = persistentListOf(),
    val keys: ImmutableList<PlanKeyUi> = persistentListOf(),
    /** Provider ids the person can still add (never used anywhere). */
    val addable: ImmutableList<String> = persistentListOf(),
    val refreshing: Boolean = false,
    val nowMs: Long = 0,
    val collectors: ImmutableList<CollectorTarget> = persistentListOf(),
    val confirmCollector: CollectorTarget? = null,
    val collectorFailed: Boolean = false,
    val collector: CollectorUi = CollectorUi(),
    val defaultThreshold: Int = 80,
    val resetNotice: Boolean = false,
    val editing: Boolean = false,
    val editKey: String? = null,
    val thresholdInput: String = "",
    val saving: Boolean = false,
    val failed: Boolean = false,
    val collectorRows: ImmutableList<PlanCollectorUi> = persistentListOf(),
    val alertsAvailable: Boolean = false,
    val alertsOn: Boolean = false,
    val actionBusy: Boolean = false,
    val actionFailed: Boolean = false,
    val ask: PlanCollectorAsk? = null,
    val computerReaders: ImmutableList<ComputerReaderUi> = persistentListOf(),
    val readerBusy: Boolean = false,
    val readerFailed: Boolean = false,
    val widgetSettings: io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetSettings =
        io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetSettings(),
    /** Cache-first: the cards are the last-seen ones and the first live read is on its way (a quiet bar). */
    val updating: Boolean = false,
)

public interface PlansActions {
    public fun back()

    public fun setWidgetVisible(key: String, visible: Boolean) {}

    public fun setWidgetSetupRows(visible: Boolean) {}

    public fun setWidgetWindows(
        windows: io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetWindows
    ) {}

    public fun refresh()

    public fun setComputerReaderEnabled(id: String, enabled: Boolean) {}

    public fun readComputerNow(id: String) {}

    public fun chooseCollector(target: CollectorTarget) {}

    public fun confirmCollector() {}

    public fun cancelCollector() {}

    public fun setUpCollector()

    public fun turnOffCollector()

    public fun editThreshold(key: String?)

    public fun thresholdChanged(value: String)

    public fun saveThreshold()

    public fun useDefaultThreshold()

    public fun toggleResetNotice()

    public fun dismissEditor()

    public fun setAlertsOn(enabled: Boolean)

    public fun askCollector(profile: ProfileId)

    public fun fix(key: String)

    /** "Check <agent> again": asks the phone's agent whether it is signed in, then reads the plans again. */
    public fun checkAgent(): Unit = Unit
}

private fun UsageConnection.ui(): PlanConnectionUi =
    PlanConnectionUi(
        name = name,
        isPhone = kind == UsageConnectionKind.PhoneAgents || id == UsageConnection.PHONE_ID,
        id = id,
    )

private fun UsageSubscription.planCard(): PlanCardUi =
    PlanCardUi(
        key = key,
        providerId = providerId,
        name = displayName,
        connection =
            if (showConnectionName) (readFrom ?: signedInOn.firstOrNull()?.connection)?.ui()
            else null,
        state = state,
        plan = planLabel,
        windows =
            windows
                .map { w ->
                    PlanWindowUi(
                        w.kind,
                        w.label,
                        w.leftPercent.roundToInt().coerceIn(0, 100),
                        w.resetsAt?.toEpochMilliseconds(),
                        w.id,
                    )
                }
                .toImmutableList(),
        fix = fix,
        fixConnection = fixConnection?.ui(),
        signedInOn =
            signedInOn
                .map { PlanSignInUi(it.connection.ui(), it.state, it.online, it.readsFromHere) }
                .toImmutableList(),
        resetSince = resetSinceLastReading,
        fetchedAtMs = fetchedAt?.toEpochMilliseconds(),
        alertThreshold = alertThreshold.roundToInt(),
        checkedAtMs = checkedAt?.toEpochMilliseconds(),
        readFrom = readFrom?.ui(),
        hasAlertOverride = hasAlertOverride,
        notifyReset = notifyOnReset,
    )

/** Pure: the engine snapshot as screen state. */
public fun plansUi(
    snapshot: UsageSnapshot,
    refreshing: Boolean,
    nowMs: Long,
    agents: Map<String, PlanAgentTruth> = emptyMap(),
): PlansUi {
    val cards =
        snapshot.subscriptions
            .sortedBy { s ->
                val windows = listOfNotNull(s.fiveHour, s.weekly).ifEmpty { s.windows.take(2) }
                if (windows.isEmpty()) 101.0
                else windows.filter { it.leftPercent > 0 }.minOfOrNull { it.leftPercent } ?: 0.0
            }
            .map { it.planCard() }
    val keys =
        snapshot.apiKeyAccounts.map {
            PlanKeyUi(it.displayName, it.connection.ui(), it.viaOpenCode)
        }
    val phase =
        when {
            !snapshot.loaded && cards.isEmpty() -> PlansPhase.Loading
            cards.isEmpty() && keys.isEmpty() -> PlansPhase.Empty
            else -> PlansPhase.List
        }
    return PlansUi(
        phase,
        cards.toImmutableList(),
        keys.toImmutableList(),
        snapshot.addable.toImmutableList(),
        refreshing,
        nowMs,
    ).withAgents(agents)
}

/** Applies the phone agents' own state to cards whose reader found no numbers on this phone. */
public fun PlansUi.withAgents(agents: Map<String, PlanAgentTruth>): PlansUi {
    if (agents.isEmpty() && cards.none { it.agent != null }) return this
    return copy(
        cards =
            cards
                .map { card ->
                    val speaksForPhone =
                        card.readFrom?.isPhone
                            ?: (card.signedInOn.isEmpty() ||
                                card.signedInOn.any { it.connection.isPhone })
                    val truth = agents[card.providerId]
                    val agent =
                        when {
                            !speaksForPhone || card.windows.isNotEmpty() -> null
                            card.state == SubscriptionState.NotSignedIn ->
                                truth?.takeIf { it != PlanAgentTruth.SignedOut }
                            card.state == SubscriptionState.NoLimits ->
                                truth as? PlanAgentTruth.Limit
                            else -> null
                        }
                    if (card.agent == agent) card else card.copy(agent = agent)
                }
                .toImmutableList()
    )
}

/** Compact cards favour the five-hour and weekly limits, then the first two reported limits. */
public fun PlanCardUi.cardWindows(): List<PlanWindowUi> {
    val known =
        listOfNotNull(
            windows.firstOrNull { it.kind == UsageWindowKind.FiveHour },
            windows.firstOrNull { it.kind == UsageWindowKind.Weekly },
        )
    return known.ifEmpty { windows.take(2) }
}
