package io.github.eslamasabry.oc.feature.plans.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.integrations.usage.ComputerUsageProblem
import io.github.eslamasabry.oc.engine.integrations.usage.SignInState
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindowKind
import io.github.eslamasabry.oc.feature.plans.presentation.PlanConnectionUi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.p12PlansAddLabel
import io.github.eslamasabry.oc.ui.words.res.p12PlansAlertAt
import io.github.eslamasabry.oc.ui.words.res.p12PlansApiKeyOnly
import io.github.eslamasabry.oc.ui.words.res.p12PlansAtConnection
import io.github.eslamasabry.oc.ui.words.res.p12PlansBack
import io.github.eslamasabry.oc.ui.words.res.p12PlansCantReadYet
import io.github.eslamasabry.oc.ui.words.res.p12PlansCardTitle
import io.github.eslamasabry.oc.ui.words.res.p12PlansDisableReader
import io.github.eslamasabry.oc.ui.words.res.p12PlansEmptyBody
import io.github.eslamasabry.oc.ui.words.res.p12PlansEmptyTitle
import io.github.eslamasabry.oc.ui.words.res.p12PlansEnableReader
import io.github.eslamasabry.oc.ui.words.res.p12PlansExhausted
import io.github.eslamasabry.oc.ui.words.res.p12PlansExpired
import io.github.eslamasabry.oc.ui.words.res.p12PlansFiveHour
import io.github.eslamasabry.oc.ui.words.res.p12PlansFixAddKey
import io.github.eslamasabry.oc.ui.words.res.p12PlansFixAddKeyWhere
import io.github.eslamasabry.oc.ui.words.res.p12PlansFixSetUp
import io.github.eslamasabry.oc.ui.words.res.p12PlansFixSetUpWhere
import io.github.eslamasabry.oc.ui.words.res.p12PlansFixSignIn
import io.github.eslamasabry.oc.ui.words.res.p12PlansFixSignInWhere
import io.github.eslamasabry.oc.ui.words.res.p12PlansFound
import io.github.eslamasabry.oc.ui.words.res.p12PlansKeysLabel
import io.github.eslamasabry.oc.ui.words.res.p12PlansLeft
import io.github.eslamasabry.oc.ui.words.res.p12PlansLimit
import io.github.eslamasabry.oc.ui.words.res.p12PlansLoading
import io.github.eslamasabry.oc.ui.words.res.p12PlansMonthly
import io.github.eslamasabry.oc.ui.words.res.p12PlansNearLimit
import io.github.eslamasabry.oc.ui.words.res.p12PlansNoAnswer
import io.github.eslamasabry.oc.ui.words.res.p12PlansNoLimits
import io.github.eslamasabry.oc.ui.words.res.p12PlansNodeMissing
import io.github.eslamasabry.oc.ui.words.res.p12PlansNodeTooOld
import io.github.eslamasabry.oc.ui.words.res.p12PlansNotSignedIn
import io.github.eslamasabry.oc.ui.words.res.p12PlansOffline
import io.github.eslamasabry.oc.ui.words.res.p12PlansOfflineStale
import io.github.eslamasabry.oc.ui.words.res.p12PlansOk
import io.github.eslamasabry.oc.ui.words.res.p12PlansPlan
import io.github.eslamasabry.oc.ui.words.res.p12PlansReadHere
import io.github.eslamasabry.oc.ui.words.res.p12PlansReadNow
import io.github.eslamasabry.oc.ui.words.res.p12PlansReaderBroken
import io.github.eslamasabry.oc.ui.words.res.p12PlansReaderExplanation
import io.github.eslamasabry.oc.ui.words.res.p12PlansReaderFailed
import io.github.eslamasabry.oc.ui.words.res.p12PlansReaderOff
import io.github.eslamasabry.oc.ui.words.res.p12PlansReaderOn
import io.github.eslamasabry.oc.ui.words.res.p12PlansReaders
import io.github.eslamasabry.oc.ui.words.res.p12PlansReading
import io.github.eslamasabry.oc.ui.words.res.p12PlansRefresh
import io.github.eslamasabry.oc.ui.words.res.p12PlansRefreshing
import io.github.eslamasabry.oc.ui.words.res.p12PlansResetSince
import io.github.eslamasabry.oc.ui.words.res.p12PlansResetsDays
import io.github.eslamasabry.oc.ui.words.res.p12PlansResetsHours
import io.github.eslamasabry.oc.ui.words.res.p12PlansResetsMinutes
import io.github.eslamasabry.oc.ui.words.res.p12PlansResetsNow
import io.github.eslamasabry.oc.ui.words.res.p12PlansServerTooOld
import io.github.eslamasabry.oc.ui.words.res.p12PlansSignInExpired
import io.github.eslamasabry.oc.ui.words.res.p12PlansSignedIn
import io.github.eslamasabry.oc.ui.words.res.p12PlansSignedOut
import io.github.eslamasabry.oc.ui.words.res.p12PlansThisPhone
import io.github.eslamasabry.oc.ui.words.res.p12PlansTitle
import io.github.eslamasabry.oc.ui.words.res.p12PlansTryAgain
import io.github.eslamasabry.oc.ui.words.res.p12PlansUnreachable
import io.github.eslamasabry.oc.ui.words.res.p12PlansViaOpenCode
import io.github.eslamasabry.oc.ui.words.res.p12PlansWeekly
import org.jetbrains.compose.resources.stringResource

/** Resource-backed copy. Presentation passes values and fixed states only. */
public class PlansWords internal constructor(private val values: Map<String, String>) {
    private fun t(key: String): String = values.getValue(key)

    private fun f(key: String, vararg args: Any): String {
        var value = t(key)
        args.forEachIndexed { index, arg ->
            value = value.replace("%${index + 1}\$s", arg.toString())
        }
        return value.replace("%%", "%")
    }

    val title: String
        get() = t("Title")

    val back: String
        get() = t("Back")

    val loading: String
        get() = t("Loading")

    val refresh: String
        get() = t("Refresh")

    val refreshing: String
        get() = t("Refreshing")

    val emptyTitle: String
        get() = t("EmptyTitle")

    val emptyBody: String
        get() = t("EmptyBody")

    val keysLabel: String
        get() = t("KeysLabel")

    val addLabel: String
        get() = t("AddLabel")

    val thisPhone: String
        get() = t("ThisPhone")

    val resetSince: String
        get() = t("ResetSince")

    val viaOpenCode: String
        get() = t("ViaOpenCode")

    val readers: String
        get() = t("Readers")

    val readerExplanation: String
        get() = t("ReaderExplanation")

    val readerOn: String
        get() = t("ReaderOn")

    val readerOff: String
        get() = t("ReaderOff")

    val reading: String
        get() = t("Reading")

    val readerFailed: String
        get() = t("ReaderFailed")

    fun connection(c: PlanConnectionUi): String =
        if (c.isPhone || c.name.isBlank()) thisPhone else c.name

    fun cardTitle(name: String, connection: PlanConnectionUi?): String =
        if (connection == null) name else f("CardTitle", name, connection(connection))

    fun state(s: SubscriptionState): String = t(s.name)

    fun fix(fix: UsageFix, where: PlanConnectionUi?): String? =
        when (fix) {
            UsageFix.None -> null
            UsageFix.TryAgain -> t("TryAgain")
            else -> {
                val key =
                    when (fix) {
                        UsageFix.SignIn -> "FixSignIn"
                        UsageFix.AddKey -> "FixAddKey"
                        else -> "FixSetUp"
                    }
                if (where == null) t(key) else f(key + "Where", connection(where))
            }
        }

    fun window(kind: UsageWindowKind, label: String): String =
        when (kind) {
            UsageWindowKind.FiveHour -> t("FiveHour")
            UsageWindowKind.Weekly -> t("Weekly")
            UsageWindowKind.Monthly -> t("Monthly")
            UsageWindowKind.Other -> label.ifBlank { t("Limit") }
        }

    fun left(percent: Int): String = f("Left", percent)

    fun resetsIn(minutes: Long): String =
        when {
            minutes <= 0 -> t("ResetsNow")
            minutes < 60 -> f("ResetsMinutes", minutes)
            minutes < 48 * 60 -> f("ResetsHours", minutes / 60)
            else -> f("ResetsDays", minutes / (24 * 60))
        }

    fun plan(name: String): String = f("Plan", name)

    fun alertAt(percent: Int): String = f("AlertAt", percent)

    fun signedIn(
        connection: PlanConnectionUi,
        state: SignInState,
        online: Boolean,
        readsHere: Boolean,
    ): String {
        val what =
            when {
                !online -> t("Offline")
                state == SignInState.Expired -> t("Expired")
                state == SignInState.NotSignedIn -> t("SignedOut")
                readsHere -> t("ReadHere")
                else -> t("SignedIn")
            }
        return f("AtConnection", connection(connection), what)
    }

    fun providerName(id: String): String =
        when (id) {
            "codex" -> "Codex"
            "claude" -> "Claude"
            "glm" -> "GLM"
            "minimax" -> "MiniMax"
            else -> id
        }

    fun readerAction(name: String, enabled: Boolean): String =
        f(if (enabled) "DisableReader" else "EnableReader", name)

    fun readNow(name: String): String = f("ReadNow", name)

    fun readerProblem(problem: ComputerUsageProblem): String = t(problem.name)

    fun found(providers: List<String>): String =
        f("Found", providers.joinToString(", ") { providerName(it) })
}

@Composable
public fun plansWords(): PlansWords =
    PlansWords(
        mapOf(
            "Title" to stringResource(Res.string.p12PlansTitle),
            "Back" to stringResource(Res.string.p12PlansBack),
            "Loading" to stringResource(Res.string.p12PlansLoading),
            "Refresh" to stringResource(Res.string.p12PlansRefresh),
            "Refreshing" to stringResource(Res.string.p12PlansRefreshing),
            "EmptyTitle" to stringResource(Res.string.p12PlansEmptyTitle),
            "EmptyBody" to stringResource(Res.string.p12PlansEmptyBody),
            "KeysLabel" to stringResource(Res.string.p12PlansKeysLabel),
            "AddLabel" to stringResource(Res.string.p12PlansAddLabel),
            "ThisPhone" to stringResource(Res.string.p12PlansThisPhone),
            "Ok" to stringResource(Res.string.p12PlansOk),
            "NearLimit" to stringResource(Res.string.p12PlansNearLimit),
            "Exhausted" to stringResource(Res.string.p12PlansExhausted),
            "CantReadYet" to stringResource(Res.string.p12PlansCantReadYet),
            "SignInExpired" to stringResource(Res.string.p12PlansSignInExpired),
            "NotSignedIn" to stringResource(Res.string.p12PlansNotSignedIn),
            "ReaderBroken" to stringResource(Res.string.p12PlansReaderBroken),
            "OfflineStale" to stringResource(Res.string.p12PlansOfflineStale),
            "NoLimits" to stringResource(Res.string.p12PlansNoLimits),
            "ApiKeyOnly" to stringResource(Res.string.p12PlansApiKeyOnly),
            "FixSignIn" to stringResource(Res.string.p12PlansFixSignIn),
            "FixSignInWhere" to stringResource(Res.string.p12PlansFixSignInWhere),
            "FixAddKey" to stringResource(Res.string.p12PlansFixAddKey),
            "FixAddKeyWhere" to stringResource(Res.string.p12PlansFixAddKeyWhere),
            "FixSetUp" to stringResource(Res.string.p12PlansFixSetUp),
            "FixSetUpWhere" to stringResource(Res.string.p12PlansFixSetUpWhere),
            "TryAgain" to stringResource(Res.string.p12PlansTryAgain),
            "FiveHour" to stringResource(Res.string.p12PlansFiveHour),
            "Weekly" to stringResource(Res.string.p12PlansWeekly),
            "Monthly" to stringResource(Res.string.p12PlansMonthly),
            "Limit" to stringResource(Res.string.p12PlansLimit),
            "Left" to stringResource(Res.string.p12PlansLeft),
            "ResetsNow" to stringResource(Res.string.p12PlansResetsNow),
            "ResetsMinutes" to stringResource(Res.string.p12PlansResetsMinutes),
            "ResetsHours" to stringResource(Res.string.p12PlansResetsHours),
            "ResetsDays" to stringResource(Res.string.p12PlansResetsDays),
            "ResetSince" to stringResource(Res.string.p12PlansResetSince),
            "Plan" to stringResource(Res.string.p12PlansPlan),
            "AlertAt" to stringResource(Res.string.p12PlansAlertAt),
            "Offline" to stringResource(Res.string.p12PlansOffline),
            "Expired" to stringResource(Res.string.p12PlansExpired),
            "SignedOut" to stringResource(Res.string.p12PlansSignedOut),
            "ReadHere" to stringResource(Res.string.p12PlansReadHere),
            "SignedIn" to stringResource(Res.string.p12PlansSignedIn),
            "AtConnection" to stringResource(Res.string.p12PlansAtConnection),
            "CardTitle" to stringResource(Res.string.p12PlansCardTitle),
            "ViaOpenCode" to stringResource(Res.string.p12PlansViaOpenCode),
            "Readers" to stringResource(Res.string.p12PlansReaders),
            "ReaderExplanation" to stringResource(Res.string.p12PlansReaderExplanation),
            "ReaderOn" to stringResource(Res.string.p12PlansReaderOn),
            "ReaderOff" to stringResource(Res.string.p12PlansReaderOff),
            "EnableReader" to stringResource(Res.string.p12PlansEnableReader),
            "DisableReader" to stringResource(Res.string.p12PlansDisableReader),
            "ReadNow" to stringResource(Res.string.p12PlansReadNow),
            "Reading" to stringResource(Res.string.p12PlansReading),
            "ReaderFailed" to stringResource(Res.string.p12PlansReaderFailed),
            "NodeMissing" to stringResource(Res.string.p12PlansNodeMissing),
            "NodeTooOld" to stringResource(Res.string.p12PlansNodeTooOld),
            "ServerTooOld" to stringResource(Res.string.p12PlansServerTooOld),
            "Unreachable" to stringResource(Res.string.p12PlansUnreachable),
            "NoAnswer" to stringResource(Res.string.p12PlansNoAnswer),
            "Found" to stringResource(Res.string.p12PlansFound),
        )
    )
