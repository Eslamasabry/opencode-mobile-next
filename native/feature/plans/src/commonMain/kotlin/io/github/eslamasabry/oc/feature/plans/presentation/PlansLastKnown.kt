package io.github.eslamasabry.oc.feature.plans.presentation

import io.github.eslamasabry.oc.engine.integrations.usage.SignInState
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindowKind
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): Plans opens on the cards it last read, then the live read replaces them in
 * place. Plans merge every connection, so the app keys this to the whole set of saved servers: a changed or deleted
 * server never shows the old cards. Names, percentages and times only; no keys, tokens or accounts.
 */
public interface PlansLastKnown {
    public fun peek(): KeptPlans?

    public suspend fun read(): KeptPlans?

    public suspend fun keep(kept: KeptPlans)
}

@Serializable
public data class KeptPlans(
    val cards: List<Card> = emptyList(),
    val keys: List<Key> = emptyList(),
    val addable: List<String> = emptyList(),
) {
    @Serializable
    public data class Connection(val name: String, val isPhone: Boolean, val id: String = "")

    @Serializable
    public data class Window(
        val kind: UsageWindowKind,
        val label: String,
        val leftPercent: Int,
        val resetsAtMs: Long? = null,
        val id: String = "",
    )

    @Serializable
    public data class SignIn(val connection: Connection, val state: SignInState, val online: Boolean, val readsFromHere: Boolean)

    @Serializable
    public data class Card(
        val key: String,
        val providerId: String,
        val name: String,
        val connection: Connection? = null,
        val state: SubscriptionState,
        val plan: String? = null,
        val windows: List<Window> = emptyList(),
        val fix: UsageFix = UsageFix.None,
        val fixConnection: Connection? = null,
        val signedInOn: List<SignIn> = emptyList(),
        val resetSince: Boolean = false,
        val fetchedAtMs: Long? = null,
        val alertThreshold: Int = 80,
        val checkedAtMs: Long? = null,
        val readFrom: Connection? = null,
        val hasAlertOverride: Boolean = false,
        val notifyReset: Boolean = false,
    )

    @Serializable public data class Key(val name: String, val connection: Connection, val viaOpenCode: Boolean)

    /** The first frame: the kept cards as the list (or the empty state), updating quietly. */
    internal fun frame(nowMs: Long): PlansUi =
        PlansUi(
            phase = if (cards.isEmpty() && keys.isEmpty()) PlansPhase.Empty else PlansPhase.List,
            cards = cards.map { it.ui() }.toImmutableList(),
            keys = keys.map { PlanKeyUi(it.name, it.connection.ui(), it.viaOpenCode) }.toImmutableList(),
            addable = addable.toImmutableList(),
            nowMs = nowMs,
            updating = true,
        )

    internal companion object {
        const val MAX_CARDS = 60

        private fun Connection.ui() = PlanConnectionUi(name, isPhone, id)

        private fun PlanConnectionUi.kept() = Connection(name, isPhone, id)

        private fun Card.ui() =
            PlanCardUi(
                key = key,
                providerId = providerId,
                name = name,
                connection = connection?.ui(),
                state = state,
                plan = plan,
                windows = windows.map { PlanWindowUi(it.kind, it.label, it.leftPercent, it.resetsAtMs, it.id) }.toImmutableList(),
                fix = fix,
                fixConnection = fixConnection?.ui(),
                signedInOn = signedInOn.map { PlanSignInUi(it.connection.ui(), it.state, it.online, it.readsFromHere) }.toImmutableList(),
                resetSince = resetSince,
                fetchedAtMs = fetchedAtMs,
                alertThreshold = alertThreshold,
                checkedAtMs = checkedAtMs,
                readFrom = readFrom?.ui(),
                hasAlertOverride = hasAlertOverride,
                notifyReset = notifyReset,
            )

        fun of(ui: PlansUi): KeptPlans =
            KeptPlans(
                cards =
                    ui.cards.take(MAX_CARDS).map { c ->
                        Card(
                            key = c.key,
                            providerId = c.providerId,
                            name = c.name,
                            connection = c.connection?.kept(),
                            state = c.state,
                            plan = c.plan,
                            windows = c.windows.map { Window(it.kind, it.label, it.leftPercent, it.resetsAtMs, it.id) },
                            fix = c.fix,
                            fixConnection = c.fixConnection?.kept(),
                            signedInOn = c.signedInOn.map { SignIn(it.connection.kept(), it.state, it.online, it.readsFromHere) },
                            resetSince = c.resetSince,
                            fetchedAtMs = c.fetchedAtMs,
                            alertThreshold = c.alertThreshold,
                            checkedAtMs = c.checkedAtMs,
                            readFrom = c.readFrom?.kept(),
                            hasAlertOverride = c.hasAlertOverride,
                            notifyReset = c.notifyReset,
                        )
                    },
                keys = ui.keys.take(MAX_CARDS).map { Key(it.name, it.connection.kept(), it.viaOpenCode) },
                addable = ui.addable.take(MAX_CARDS),
            )
    }
}
