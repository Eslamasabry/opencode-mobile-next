package io.github.eslamasabry.oc.feature.servers.account.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.account.AccountLoginStatus
import io.github.eslamasabry.oc.engine.agents.account.AccountPanelState
import io.github.eslamasabry.oc.engine.agents.account.AccountPanelStatus
import io.github.eslamasabry.oc.engine.agents.account.AccountRateBucket
import io.github.eslamasabry.oc.engine.agents.account.AccountRateWindow
import io.github.eslamasabry.oc.engine.agents.account.AccountTokenUsage
import io.github.eslamasabry.oc.engine.agents.account.AgentRuntimeAccount
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): the account page opens on the account, allowance and usage it last showed
 * (with their "updated" time), then the live read replaces them in place. Bound by the app to its last-known store.
 */
public interface AgentAccountLastKnown {
    public fun peek(profile: ProfileId): KeptAgentAccount?

    public suspend fun read(profile: ProfileId): KeptAgentAccount?

    public suspend fun keep(profile: ProfileId, kept: KeptAgentAccount)
}

/**
 * The account facts on screen: sign-in kind, email, plan, allowance windows and token totals. A sign-in code, link
 * or any token is never part of this; the panel's transient login state is not kept.
 */
@Serializable
public data class KeptAgentAccount(
    val type: String? = null,
    val email: String? = null,
    val plan: String? = null,
    val requiresSignIn: Boolean = false,
    val limits: List<Bucket>? = null,
    val lifetimeTokens: Long? = null,
    val peakDailyTokens: Long? = null,
    val hasUsage: Boolean = false,
    val updatedAtMs: Long? = null,
) {
    @Serializable
    public data class Window(val usedPercent: Int, val durationMinutes: Int? = null, val resetsAtMs: Long? = null)

    @Serializable
    public data class Bucket(val name: String? = null, val primary: Window? = null, val secondary: Window? = null)

    /** A ready panel with nothing to tap: no sign-in or cancel is offered from a remembered account. */
    public fun toPanel(): AccountPanelState =
        AccountPanelState(
            status = AccountPanelStatus.Ready,
            account = AgentRuntimeAccount(type, email, plan, requiresSignIn),
            limits =
                limits
                    ?.map { b -> AccountRateBucket(b.name, b.primary?.toLive(), b.secondary?.toLive()) }
                    ?.toImmutableList(),
            usage = if (hasUsage) AccountTokenUsage(lifetimeTokens, peakDailyTokens) else null,
            updatedAt = updatedAtMs?.let(Instant::fromEpochMilliseconds),
        )

    private fun Window.toLive() = AccountRateWindow(usedPercent, durationMinutes, resetsAtMs)

    public companion object {
        public const val MAX_BUCKETS: Int = 12
        private const val MAX_TEXT = 200

        /** Null when the panel is not a finished account read (nothing worth keeping). */
        public fun of(panel: AccountPanelState): KeptAgentAccount? {
            val account = panel.account ?: return null
            if (panel.status != AccountPanelStatus.Ready || panel.metricsLoading) return null
            return KeptAgentAccount(
                account.type?.take(MAX_TEXT),
                account.email?.take(MAX_TEXT),
                account.plan?.take(MAX_TEXT),
                account.requiresSignIn,
                panel.limits?.take(MAX_BUCKETS)?.map { b ->
                    Bucket(b.name?.take(MAX_TEXT), b.primary?.let(::window), b.secondary?.let(::window))
                },
                panel.usage?.lifetimeTokens,
                panel.usage?.peakDailyTokens,
                panel.usage != null,
                panel.updatedAt?.toEpochMilliseconds(),
            )
        }

        private fun window(w: AccountRateWindow) = Window(w.usedPercent, w.durationMinutes, w.resetsAtEpochMillis)
    }
}

/**
 * What the page draws for a live [panel]: while the live read has not answered (the controller clears the account and
 * says Loading), the kept account stays on screen and [Shown.updating] is true; a quiet line, never a skeleton.
 */
public data class Shown(val panel: AccountPanelState, val updating: Boolean)

public fun shownPanel(live: AccountPanelState, kept: KeptAgentAccount?): Shown =
    if (kept != null && live.status == AccountPanelStatus.Loading && live.account == null &&
        live.loginStatus == AccountLoginStatus.Idle) Shown(kept.toPanel(), true)
    else Shown(live, false)
