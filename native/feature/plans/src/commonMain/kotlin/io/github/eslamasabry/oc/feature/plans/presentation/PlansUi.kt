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
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindowKind
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
)

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
}

private fun UsageConnection.ui(): PlanConnectionUi =
    PlanConnectionUi(
        name = name,
        isPhone = kind == UsageConnectionKind.PhoneAgents || id == UsageConnection.PHONE_ID,
        id = id,
    )

/** Pure: the engine snapshot as screen state. */
public fun plansUi(snapshot: UsageSnapshot, refreshing: Boolean, nowMs: Long): PlansUi {
    val cards =
        snapshot.subscriptions.map { s ->
            PlanCardUi(
                key = s.key,
                providerId = s.providerId,
                name = s.displayName,
                connection =
                    if (s.showConnectionName)
                        (s.readFrom ?: s.signedInOn.firstOrNull()?.connection)?.ui()
                    else null,
                state = s.state,
                plan = s.planLabel,
                windows =
                    s.windows
                        .map { w ->
                            PlanWindowUi(
                                w.kind,
                                w.label,
                                w.leftPercent.roundToInt().coerceIn(0, 100),
                                w.resetsAt?.toEpochMilliseconds(),
                            )
                        }
                        .toImmutableList(),
                fix = s.fix,
                fixConnection = s.fixConnection?.ui(),
                signedInOn =
                    s.signedInOn
                        .map {
                            PlanSignInUi(it.connection.ui(), it.state, it.online, it.readsFromHere)
                        }
                        .toImmutableList(),
                resetSince = s.resetSinceLastReading,
                fetchedAtMs = s.fetchedAt?.toEpochMilliseconds(),
                alertThreshold = s.alertThreshold.roundToInt(),
            )
        }
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
    )
}
