package io.github.eslamasabry.oc.feature.plans.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.integrations.usage.SignInState
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindowKind
import io.github.eslamasabry.oc.feature.plans.presentation.PlanConnectionUi
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Native resource copy for Usage plans; formatting stays in the UI layer. */
public class PlansWords internal constructor(private val strings: List<String>) {
    private fun t(index: Int, vararg values: Any?): String {
        var value = strings[index]
        for ((i, part) in values.withIndex()) value =
            value.replace("%" + (i + 1) + "$" + "s", part.toString())
        return value
    }

    val title: String
        get() = t(0)

    val back: String
        get() = t(1)

    val loading: String
        get() = t(2)

    val refresh: String
        get() = t(3)

    val refreshing: String
        get() = t(4)

    val emptyTitle: String
        get() = t(5)

    val emptyBody: String
        get() = t(6)

    val keysLabel: String
        get() = t(7)

    val addLabel: String
        get() = t(8)

    val thisPhone: String
        get() = t(9)

    fun connection(c: PlanConnectionUi): String =
        if (c.isPhone || c.name.isBlank()) thisPhone else c.name

    fun cardTitle(name: String, connection: PlanConnectionUi?): String =
        if (connection == null) name else "$name · ${connection(connection)}"

    fun state(s: SubscriptionState): String =
        when (s) {
            SubscriptionState.Ok -> t(10)
            SubscriptionState.NearLimit -> t(11)
            SubscriptionState.Exhausted -> t(12)
            SubscriptionState.CantReadYet -> t(13)
            SubscriptionState.SignInExpired -> t(14)
            SubscriptionState.NotSignedIn -> t(15)
            SubscriptionState.ReaderBroken -> t(16)
            SubscriptionState.OfflineStale -> t(17)
            SubscriptionState.NoLimits -> t(18)
            SubscriptionState.ApiKeyOnly -> t(19)
        }

    fun fix(fix: UsageFix, where: PlanConnectionUi?): String? {
        val place = where?.let { connection(it) }
        return when (fix) {
            UsageFix.None -> null
            UsageFix.SignIn -> if (place == null) t(20) else t(21, place)
            UsageFix.AddKey -> if (place == null) t(22) else t(23, place)
            UsageFix.SetUp -> if (place == null) t(24) else t(25, place)
            UsageFix.TryAgain -> t(26)
        }
    }

    fun window(kind: UsageWindowKind, label: String): String =
        when (kind) {
            UsageWindowKind.FiveHour -> t(27)
            UsageWindowKind.Weekly -> t(28)
            UsageWindowKind.Monthly -> t(29)
            UsageWindowKind.Other -> label.ifBlank { t(30) }
        }

    fun left(percent: Int): String = t(31, percent)

    fun resetsIn(minutes: Long): String =
        when {
            minutes <= 0 -> t(32)
            minutes < 60 -> t(33, minutes)
            minutes < 48 * 60 -> t(34, minutes / 60)
            else -> t(35, minutes / (24 * 60))
        }

    val resetSince: String
        get() = t(36)

    fun plan(name: String): String = t(37, name)

    fun alertAt(percent: Int): String = t(38, percent)

    fun signedIn(
        connection: PlanConnectionUi,
        state: SignInState,
        online: Boolean,
        readsHere: Boolean,
    ): String {
        val place = connection(connection)
        val what =
            when {
                !online -> t(39)
                state == SignInState.Expired -> t(40)
                state == SignInState.NotSignedIn -> t(41)
                readsHere -> t(42)
                else -> t(43)
            }
        return "$place: $what"
    }

    fun providerName(id: String): String =
        when (id) {
            "codex" -> "Codex"
            "claude" -> "Claude"
            "glm" -> "GLM"
            "minimax" -> "MiniMax"
            else -> id
        }

    val viaOpenCode: String
        get() = t(44)
}

@Composable
public fun plansWords(): PlansWords =
    PlansWords(
        listOf(
            stringResource(Res.string.statePlansCopy0),
            stringResource(Res.string.statePlansCopy1),
            stringResource(Res.string.statePlansCopy2),
            stringResource(Res.string.statePlansCopy3),
            stringResource(Res.string.statePlansCopy4),
            stringResource(Res.string.statePlansCopy5),
            stringResource(Res.string.statePlansCopy6),
            stringResource(Res.string.statePlansCopy7),
            stringResource(Res.string.statePlansCopy8),
            stringResource(Res.string.statePlansCopy9),
            stringResource(Res.string.statePlansCopy10),
            stringResource(Res.string.statePlansCopy11),
            stringResource(Res.string.statePlansCopy12),
            stringResource(Res.string.statePlansCopy13),
            stringResource(Res.string.statePlansCopy14),
            stringResource(Res.string.statePlansCopy15),
            stringResource(Res.string.statePlansCopy16),
            stringResource(Res.string.statePlansCopy17),
            stringResource(Res.string.statePlansCopy18),
            stringResource(Res.string.statePlansCopy19),
            stringResource(Res.string.statePlansCopy20),
            stringResource(Res.string.statePlansCopy21),
            stringResource(Res.string.statePlansCopy22),
            stringResource(Res.string.statePlansCopy23),
            stringResource(Res.string.statePlansCopy24),
            stringResource(Res.string.statePlansCopy25),
            stringResource(Res.string.statePlansCopy26),
            stringResource(Res.string.statePlansCopy27),
            stringResource(Res.string.statePlansCopy28),
            stringResource(Res.string.statePlansCopy29),
            stringResource(Res.string.statePlansCopy30),
            stringResource(Res.string.statePlansCopy31),
            stringResource(Res.string.statePlansCopy32),
            stringResource(Res.string.statePlansCopy33),
            stringResource(Res.string.statePlansCopy34),
            stringResource(Res.string.statePlansCopy35),
            stringResource(Res.string.statePlansCopy36),
            stringResource(Res.string.statePlansCopy37),
            stringResource(Res.string.statePlansCopy38),
            stringResource(Res.string.statePlansCopy39),
            stringResource(Res.string.statePlansCopy40),
            stringResource(Res.string.statePlansCopy41),
            stringResource(Res.string.statePlansCopy42),
            stringResource(Res.string.statePlansCopy43),
            stringResource(Res.string.statePlansCopy44),
        )
    )
