package io.github.eslamasabry.oc.feature.plans.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.integrations.usage.SignInState
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindowKind
import io.github.eslamasabry.oc.feature.plans.presentation.PlanCardUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlanConnectionUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlanWindowUi
import io.github.eslamasabry.oc.feature.plans.presentation.cardWindows
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** Resource-backed plan copy shared by the compact reading and its full details. */
public class PlansWords internal constructor() {
    val title: String
        @Composable get() = stringResource(Res.string.m40PlansTitle)

    val back: String
        @Composable get() = stringResource(Res.string.m40PlansBack)

    val loading: String
        @Composable get() = stringResource(Res.string.m40PlansLoading)

    val refresh: String
        @Composable get() = stringResource(Res.string.m40PlansRefresh)

    val refreshing: String
        @Composable get() = stringResource(Res.string.m40PlansRefreshing)

    val emptyTitle: String
        @Composable get() = stringResource(Res.string.m40PlansEmptyTitle)

    val emptyBody: String
        @Composable get() = stringResource(Res.string.m40PlansEmptyBody)

    val keysLabel: String
        @Composable get() = stringResource(Res.string.m40PlansKeys)

    val addLabel: String
        @Composable get() = stringResource(Res.string.m40PlansAdd)

    val thisPhone: String
        @Composable get() = stringResource(Res.string.m40PlansPhone)

    val resetSince: String
        @Composable get() = stringResource(Res.string.m40PlansResetSince)

    val viaOpenCode: String
        @Composable get() = stringResource(Res.string.m40PlansViaOpenCode)

    val needsSetup: String
        @Composable get() = stringResource(Res.string.m40PlansNeedsSetup)

    val details: String
        @Composable get() = stringResource(Res.string.m40PlansDetails)

    val copyDetails: String
        @Composable get() = stringResource(Res.string.m40PlansCopyDetails)

    @Composable
    fun connection(c: PlanConnectionUi): String =
        if (c.isPhone || c.name.isBlank()) thisPhone else c.name

    @Composable
    fun cardTitle(name: String, connection: PlanConnectionUi?): String =
        if (connection == null) name else "$name · ${connection(connection)}"

    @Composable
    fun state(s: SubscriptionState, provider: String = ""): String =
        stringResource(
            when (s) {
                SubscriptionState.Ok -> Res.string.m40PlansOk
                SubscriptionState.NearLimit -> Res.string.m40PlansNear
                SubscriptionState.Exhausted -> Res.string.m40PlansExhausted
                SubscriptionState.CantReadYet -> Res.string.m40PlansCantRead
                SubscriptionState.SignInExpired -> Res.string.m40PlansExpired
                SubscriptionState.NotSignedIn ->
                    if (provider == "glm" || provider == "minimax") Res.string.m40PlansNoKey
                    else Res.string.m40PlansNotSignedIn
                SubscriptionState.ReaderBroken -> Res.string.m40PlansBroken
                SubscriptionState.OfflineStale -> Res.string.m40PlansOffline
                SubscriptionState.NoLimits -> Res.string.m40PlansNoLimits
                SubscriptionState.ApiKeyOnly -> Res.string.m40PlansApiKey
            }
        )

    @Composable
    fun fix(fix: UsageFix, where: PlanConnectionUi?): String? {
        val place = where?.let { connection(it) }
        return when (fix) {
            UsageFix.None -> null
            UsageFix.SignIn ->
                if (place == null) stringResource(Res.string.m40PlansSignIn)
                else stringResource(Res.string.m40PlansSignInOn, place)
            UsageFix.AddKey ->
                if (place == null) stringResource(Res.string.m40PlansAddKey)
                else stringResource(Res.string.m40PlansAddKeyOn, place)
            UsageFix.SetUp ->
                if (place == null) stringResource(Res.string.m40PlansSetUp)
                else stringResource(Res.string.m40PlansSetUpOn, place)
            UsageFix.TryAgain -> stringResource(Res.string.m40PlansTryAgain)
        }
    }

    @Composable
    fun window(kind: UsageWindowKind, label: String, id: String = ""): String =
        when (kind) {
            UsageWindowKind.FiveHour -> stringResource(Res.string.m40PlansFiveHours)
            UsageWindowKind.Weekly -> stringResource(Res.string.m40PlansWeek)
            UsageWindowKind.Monthly -> stringResource(Res.string.m40PlansMonth)
            UsageWindowKind.Other -> {
                val raw = id.trim().lowercase()
                if (raw == "mcp" || raw.startsWith("mcp.") || raw.startsWith("mcp_"))
                    stringResource(Res.string.m40PlansTools)
                else
                    label
                        .ifBlank { id }
                        .replace(Regex("[_.\\-]+"), " ")
                        .split(Regex("\\s+"))
                        .filter { it.isNotBlank() }
                        .joinToString(" ") {
                            it.lowercase().replaceFirstChar { c -> c.titlecase() }
                        }
                        .ifBlank { stringResource(Res.string.m40PlansLimit) }
            }
        }

    @Composable fun left(percent: Int): String = stringResource(Res.string.m40PlansLeft, percent)

    @Composable
    fun resetsIn(minutes: Long): String =
        when {
            minutes <= 0 -> stringResource(Res.string.m40PlansResetNow)
            minutes < 60 -> stringResource(Res.string.m40PlansResetMinutes, minutes)
            minutes < 48 * 60 -> stringResource(Res.string.m40PlansResetHours, minutes / 60)
            else -> stringResource(Res.string.m40PlansResetDays, minutes / (24 * 60))
        }

    @Composable
    fun plan(name: String): String =
        stringResource(Res.string.m40PlansPlan, name.trim().replaceFirstChar { it.titlecase() })

    @Composable
    fun alertAt(percent: Int): String = stringResource(Res.string.m40PlansAlertAt, percent)

    @Composable
    fun signedIn(
        connection: PlanConnectionUi,
        state: SignInState,
        online: Boolean,
        readsHere: Boolean,
    ): String {
        val place = connection(connection)
        val what =
            stringResource(
                when {
                    !online && connection.isPhone -> Res.string.m40PlansPhoneStopped
                    !online -> Res.string.m40PlansOfflineShort
                    state == SignInState.Expired -> Res.string.m40PlansExpiredShort
                    state == SignInState.NotSignedIn -> Res.string.m40PlansNotSignedInShort
                    readsHere -> Res.string.m40PlansReadsHere
                    else -> Res.string.m40PlansSignedIn
                }
            )
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

    @Composable
    fun duration(ms: Long): String {
        val minutes = (ms / 60_000).coerceAtLeast(1)
        val hours = minutes / 60
        return when {
            hours == 0L -> stringResource(Res.string.m40PlansDurationMinutes, minutes)
            hours < 24 && minutes % 60 == 0L ->
                stringResource(Res.string.m40PlansDurationHours, hours)
            hours < 24 ->
                stringResource(Res.string.m40PlansDurationHoursMinutes, hours, minutes % 60)
            hours % 24 == 0L -> stringResource(Res.string.m40PlansDurationDays, hours / 24)
            else -> stringResource(Res.string.m40PlansDurationDaysHours, hours / 24, hours % 24)
        }
    }

    /** The device's local clock; a future reset within six days names its weekday. */
    @Composable
    fun whenAt(at: Long, now: Long): String {
        val zone = TimeZone.currentSystemDefault()
        val local = Instant.fromEpochMilliseconds(at).toLocalDateTime(zone)
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
        val clock =
            "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        if (local.date == today) return clock
        if (at > now && at - now < 6 * 86_400_000L) {
            val weekday =
                stringResource(
                    when (local.dayOfWeek) {
                        DayOfWeek.MONDAY -> Res.string.m40PlansMonday
                        DayOfWeek.TUESDAY -> Res.string.m40PlansTuesday
                        DayOfWeek.WEDNESDAY -> Res.string.m40PlansWednesday
                        DayOfWeek.THURSDAY -> Res.string.m40PlansThursday
                        DayOfWeek.FRIDAY -> Res.string.m40PlansFriday
                        DayOfWeek.SATURDAY -> Res.string.m40PlansSaturday
                        DayOfWeek.SUNDAY -> Res.string.m40PlansSunday
                    }
                )
            return "$weekday $clock"
        }
        return "${local.date} $clock"
    }

    @Composable
    fun summary(card: PlanCardUi, now: Long): String? {
        val windows = card.cardWindows()
        if (windows.isEmpty()) return null
        if (card.state == SubscriptionState.Exhausted) {
            val at =
                card.windows.filter { it.leftPercent == 0 }.mapNotNull { it.resetsAtMs }.maxOrNull()
            return if (at == null) left(0)
            else stringResource(Res.string.m40PlansBackAt, whenAt(at, now))
        }
        return left(windows.minOf { it.leftPercent })
    }

    @Composable
    fun resetDetail(window: PlanWindowUi, now: Long): String? =
        window.resetsAtMs?.let { at ->
            if (at <= now) stringResource(Res.string.m40PlansResetPassed)
            else
                stringResource(Res.string.m40PlansResetsAt, whenAt(at, now)) +
                    " · " +
                    duration(at - now)
        }

    @Composable
    fun checked(card: PlanCardUi, now: Long): String? {
        val fetched = card.fetchedAtMs ?: return null
        val checked = card.checkedAtMs ?: return null
        return if (checked - fetched < 600_000) null
        else stringResource(Res.string.m40PlansCheckedAgo, duration(now - checked))
    }

    @Composable
    fun facts(card: PlanCardUi, now: Long): List<String> = buildList {
        card.readFrom?.let { add(stringResource(Res.string.m40PlansReadFrom, connection(it))) }
        card.fetchedAtMs?.let { add(stringResource(Res.string.m40PlansReadTime, whenAt(it, now))) }
        card.checkedAtMs?.let {
            add(stringResource(Res.string.m40PlansCheckedTime, whenAt(it, now)))
        }
        for (window in card.windows) add(
            listOfNotNull(
                    window(window.kind, window.label, window.id),
                    left(window.leftPercent),
                    resetDetail(window, now),
                )
                .joinToString(" · ")
        )
    }
}

@Composable public fun plansWords(): PlansWords = PlansWords()
