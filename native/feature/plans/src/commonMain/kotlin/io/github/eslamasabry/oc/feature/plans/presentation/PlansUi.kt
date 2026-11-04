package io.github.eslamasabry.oc.feature.plans.presentation

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
import kotlinx.coroutines.flow.StateFlow

/** The engine the app wiring binds: every connection's readings merged into one list. */
public interface PlansSource {
    public val snapshot: StateFlow<UsageSnapshot>

    /** Reads every source once; returns when done. */
    public suspend fun refresh()

    public suspend fun setThreshold(key: String?, percent: Double?): Boolean

    public suspend fun setResetNotice(key: String?, value: Boolean): Boolean

    public val defaultThreshold: Double

    public val resetNotice: Boolean
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
public data class PlanConnectionUi(val name: String, val isPhone: Boolean)

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
    val defaultThreshold: Int = 80,
    val resetNotice: Boolean = false,
    val editing: Boolean = false,
    val editKey: String? = null,
    val thresholdInput: String = "",
    val saving: Boolean = false,
    val failed: Boolean = false,
)

public interface PlansActions {
    public fun back()

    public fun refresh()

    public fun editThreshold(key: String?)

    public fun thresholdChanged(value: String)

    public fun saveThreshold()

    public fun useDefaultThreshold()

    public fun toggleResetNotice()

    public fun dismissEditor()
}

private fun UsageConnection.ui(): PlanConnectionUi =
    PlanConnectionUi(
        name = name,
        isPhone = kind == UsageConnectionKind.PhoneAgents || id == UsageConnection.PHONE_ID,
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
