package io.github.eslamasabry.oc.feature.usage.ui

import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.kit.foundation.kitFontScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindowKind
import io.github.eslamasabry.oc.engine.usage.UsageStamp
import io.github.eslamasabry.oc.feature.usage.presentation.LimitAccountRef
import io.github.eslamasabry.oc.feature.usage.presentation.LimitAccountUi
import io.github.eslamasabry.oc.feature.usage.presentation.LimitGroupUi
import io.github.eslamasabry.oc.feature.usage.presentation.LimitPace
import io.github.eslamasabry.oc.feature.usage.presentation.LimitWindowUi
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsActions
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsPhase
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsUi
import io.github.eslamasabry.oc.feature.usage.presentation.PaceAxisTick
import io.github.eslamasabry.oc.feature.usage.presentation.PaceForecast
import io.github.eslamasabry.oc.feature.usage.presentation.PaceMoment
import io.github.eslamasabry.oc.feature.usage.presentation.PaceRateComparison
import io.github.eslamasabry.oc.feature.usage.presentation.PaceRateUnit
import io.github.eslamasabry.oc.feature.usage.presentation.PaceSwitchHint
import io.github.eslamasabry.oc.feature.usage.presentation.PaceTickKind
import io.github.eslamasabry.oc.feature.usage.presentation.ResetIn
import io.github.eslamasabry.oc.feature.usage.presentation.UsageAgentTruth
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.content.usage.KitLimitBar
import io.github.eslamasabry.oc.ui.kit.content.usage.KitPaceAxisMark
import io.github.eslamasabry.oc.ui.kit.content.usage.KitPaceBendChart
import io.github.eslamasabry.oc.ui.kit.content.usage.KitPaceBendData
import io.github.eslamasabry.oc.ui.kit.content.usage.KitPaceBendLabels
import io.github.eslamasabry.oc.ui.kit.content.usage.KitPaceBendState
import io.github.eslamasabry.oc.ui.kit.content.usage.KitPaceSentence
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.motion.KitRefresh
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.text.KitDurationStyle
import io.github.eslamasabry.oc.ui.kit.text.kitDurationParts
import io.github.eslamasabry.oc.ui.kit.text.kitDurationWords
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsCheckSignInAgain
import io.github.eslamasabry.oc.ui.words.res.agentsStateLimit
import io.github.eslamasabry.oc.ui.words.res.agentsStateLimitReset
import io.github.eslamasabry.oc.ui.words.res.agentsStateSignInNotConfirmed
import io.github.eslamasabry.oc.ui.words.res.composerLimitFriday
import io.github.eslamasabry.oc.ui.words.res.composerLimitMonday
import io.github.eslamasabry.oc.ui.words.res.composerLimitSaturday
import io.github.eslamasabry.oc.ui.words.res.composerLimitSunday
import io.github.eslamasabry.oc.ui.words.res.composerLimitThursday
import io.github.eslamasabry.oc.ui.words.res.composerLimitTuesday
import io.github.eslamasabry.oc.ui.words.res.composerLimitWednesday
import io.github.eslamasabry.oc.ui.words.res.m40PlansFriday
import io.github.eslamasabry.oc.ui.words.res.m40PlansMonday
import io.github.eslamasabry.oc.ui.words.res.m40PlansSaturday
import io.github.eslamasabry.oc.ui.words.res.m40PlansSunday
import io.github.eslamasabry.oc.ui.words.res.m40PlansThursday
import io.github.eslamasabry.oc.ui.words.res.m40PlansTuesday
import io.github.eslamasabry.oc.ui.words.res.m40PlansWednesday
import io.github.eslamasabry.oc.ui.words.res.s9LimitsAboutBend
import io.github.eslamasabry.oc.ui.words.res.s9LimitsAboutPace
import io.github.eslamasabry.oc.ui.words.res.s9LimitsAhead
import io.github.eslamasabry.oc.ui.words.res.s9LimitsBroken
import io.github.eslamasabry.oc.ui.words.res.s9LimitsCantRead
import io.github.eslamasabry.oc.ui.words.res.s9LimitsCopyDetails
import io.github.eslamasabry.oc.ui.words.res.s9LimitsDetails
import io.github.eslamasabry.oc.ui.words.res.s9LimitsEmptyBody
import io.github.eslamasabry.oc.ui.words.res.s9LimitsEmptyTitle
import io.github.eslamasabry.oc.ui.words.res.s9LimitsExpired
import io.github.eslamasabry.oc.ui.words.res.s9LimitsFiveHours
import io.github.eslamasabry.oc.ui.words.res.s9LimitsKeyNoLimits
import io.github.eslamasabry.oc.ui.words.res.s9LimitsKeys
import io.github.eslamasabry.oc.ui.words.res.s9LimitsLeft
import io.github.eslamasabry.oc.ui.words.res.s9LimitsMonth
import io.github.eslamasabry.oc.ui.words.res.s9LimitsNoLimits
import io.github.eslamasabry.oc.ui.words.res.s9LimitsNoReset
import io.github.eslamasabry.oc.ui.words.res.s9LimitsNotSignedIn
import io.github.eslamasabry.oc.ui.words.res.s9LimitsOfflineAgo
import io.github.eslamasabry.oc.ui.words.res.s9LimitsOfflineNone
import io.github.eslamasabry.oc.ui.words.res.s9LimitsOther
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceCompareDay
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceCompareHour
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceDayAt
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceDetailsNoHistory
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceEvenLabel
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceHintSlow
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceHintSwitch
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceLasts
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceLeftAtReset
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceNoneLeft
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceNowLabel
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceOutReset
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceOutSwitch
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceOutUntil
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceResetLabel
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceRunsOut
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceRunsOutIn
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceSpoken
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceStartLabel
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceTodayAt
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceTomorrowAt
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceTooLittle
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPlan
import io.github.eslamasabry.oc.ui.words.res.s9LimitsReadAgo
import io.github.eslamasabry.oc.ui.words.res.s9LimitsReadAt
import io.github.eslamasabry.oc.ui.words.res.s9LimitsResetPassed
import io.github.eslamasabry.oc.ui.words.res.s9LimitsResetsAt
import io.github.eslamasabry.oc.ui.words.res.s9LimitsResetsIn
import io.github.eslamasabry.oc.ui.words.res.s9LimitsResetsUnderMinute
import io.github.eslamasabry.oc.ui.words.res.s9LimitsThisPhone
import io.github.eslamasabry.oc.ui.words.res.s9LimitsWeek
import io.github.eslamasabry.oc.ui.words.res.s9LimitsWithinPace
import io.github.eslamasabry.oc.ui.words.res.usageDetailAccount
import io.github.eslamasabry.oc.ui.words.res.usageNo
import io.github.eslamasabry.oc.ui.words.res.usageToday
import io.github.eslamasabry.oc.ui.words.res.usageYes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.compose.resources.stringResource

/**
 * Usage › Limits (B-159): one section per plan account, one block per window length. A window that is the only one
 * of its length reads as a big "N% left", its countdown, a bar with the even-pace mark and one plain pace line.
 * Several limits of one length (models, tiers) sit in two compact columns, each with its own small bar and mark.
 * Exact reset times and when the numbers were read are under Details. Pull down, or the bar's Refresh, reads again.
 */
@Composable
public fun LimitsContent(state: LimitsUi, actions: LimitsActions, modifier: Modifier = Modifier) {
    val gutter = LocalKitTokens.current.gutter
    KitRefresh(state.refreshing, actions::refresh, modifier.fillMaxSize().testTag("usage-limits")) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Space.S3.dp, bottom = Space.S6.dp),
        ) {
            when (state.phase) {
                LimitsPhase.Loading -> item(key = "loading") { KitSkeletonRows() }
                LimitsPhase.Empty ->
                    item(key = "empty") {
                        KitStateView(
                            kind = KitStateKind.Empty,
                            title = stringResource(Res.string.s9LimitsEmptyTitle),
                            body = stringResource(Res.string.s9LimitsEmptyBody),
                            modifier = Modifier.padding(horizontal = gutter.dp),
                        )
                    }
                LimitsPhase.List -> {
                    items(state.accounts, key = { "account-${it.key}" }) { account -> Account(account, state.nowMs, actions) }
                    if (state.keys.isNotEmpty())
                        item(key = "keys") {
                            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                            KitRowGroup(label = stringResource(Res.string.s9LimitsKeys)) {
                                for (key in state.keys) {
                                    KitRow(
                                        title = key.name,
                                        supporting = stringResource(Res.string.s9LimitsKeyNoLimits),
                                        server = if (key.connectionIsPhone) stringResource(Res.string.s9LimitsThisPhone)
                                        else key.connection,
                                    )
                                }
                            }
                        }
                    val hasBend =
                        state.accounts.any { a ->
                            a.groups.any { g -> !g.compact && g.windows.any { it.forecast != null } }
                        }
                    val hasEven =
                        state.accounts.any { a -> a.groups.any { g -> g.windows.any { it.evenLeft != null } } }
                    if (hasBend || hasEven)
                        item(key = "about-pace") {
                            val aboutRes = if (hasBend) Res.string.s9LimitsAboutBend else Res.string.s9LimitsAboutPace
                            KitText(
                                stringResource(aboutRes),
                                Modifier.padding(horizontal = gutter.dp, vertical = LocalKitTokens.current.sectionGap.dp),
                                role = TypeRole.Secondary,
                                tone = TextTone.Secondary,
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun Account(account: LimitAccountUi, nowMs: Long, actions: LimitsActions) {
    val gutter = LocalKitTokens.current.gutter
    val place =
        account.connection?.let { if (account.connectionIsPhone) stringResource(Res.string.s9LimitsThisPhone) else it }
    val title = listOfNotNull(account.name, place).joinToString(" · ")
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
        KitSectionLabel(title)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S4.dp),
        ) {
            account.plan?.let {
                KitText(stringResource(Res.string.s9LimitsPlan, it), role = TypeRole.Secondary, tone = TextTone.Secondary)
            }
            if (account.reportsNothing) AgentOrStateWords(account, actions)
            for (group in account.groups) {
                if (group.compact) CompactGroup(group) else Headline(group.windows.first(), account.switchHint)
            }
            Age(account, nowMs)
            Details(account)
        }
    }
}

/** One window alone in its length: "N% left", countdown, forecast sentence, bend chart, rate comparison and hint. */
@Composable
private fun Headline(window: LimitWindowUi, switchHint: PaceSwitchHint?) {
    val name = windowName(window.kind, window.label)
    val left = leftWords(window.leftPercent)
    val reset = resetWords(window.resetIn)
    val forecast = window.forecast
    val leftRole = if (forecast?.frame?.shortWindow == true) TypeRole.Headline else TypeRole.Title
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitText(name, role = TypeRole.Label, tone = TextTone.Secondary)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            KitText(left, Modifier.padding(end = Space.S3.dp).testTag("limit-left"), role = leftRole, tabular = true)
            KitText(reset, role = TypeRole.Secondary, tone = TextTone.Secondary, tabular = true)
        }
        if (forecast == null) {
            KitLimitBar(
                left = window.left.toFloat(),
                evenLeft = null,
                semantics = listOfNotNull(name, left, reset).joinToString(", "),
            )
        } else {
            HeadlineForecast(name, left, reset, forecast, switchHint)
        }
    }
}

@Composable
private fun HeadlineForecast(
    name: String,
    left: String,
    reset: String,
    forecast: PaceForecast,
    switchHint: PaceSwitchHint?,
) {
    val sentence = forecastSentence(forecast)
    val attention = forecast is PaceForecast.RunsOut || forecast is PaceForecast.Out
    val comparison = (forecast as? PaceForecast.RunsOut)?.let { comparisonWords(it.comparison) }
    val hint = nextStepHint(forecast, switchHint)
    val evenSpoken =
        if (forecast is PaceForecast.Lasts || forecast is PaceForecast.RunsOut) {
            stringResource(Res.string.s9LimitsPaceSpoken, pctWords((forecast.frame.evenLeft * PERCENT_INT).toInt()))
        } else {
            null
        }
    KitPaceSentence(text = sentence, attention = attention, short = forecast.frame.shortWindow)
    KitPaceBendChart(
        data =
            KitPaceBendData(
                elapsed = forecast.frame.elapsed.toFloat(),
                left = forecast.frame.left.toFloat(),
                state = bendState(forecast),
                axisMarks = forecast.frame.axis.map { axisMark(it) },
                labels =
                    KitPaceBendLabels(
                        reset = stringResource(Res.string.s9LimitsPaceResetLabel),
                        evenPace = stringResource(Res.string.s9LimitsPaceEvenLabel),
                    ),
                short = forecast.frame.shortWindow,
            ),
        semantics = listOfNotNull(name, left, reset, evenSpoken, comparison, sentence).joinToString(". "),
        modifier = Modifier.testTag("pace-bend-chart"),
    )
    comparison?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, tabular = true) }
    hint?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Primary) }
}

/**
 * Several limits of one length: two columns of label · % · reset, each with its own small bar and mark; one column
 * from large text ([limitColumns]), so a label is never cut or broken inside a word in half a phone's width.
 */
@Composable
private fun CompactGroup(group: LimitGroupUi) {
    val columns = limitColumns(kitFontScale())
    val heading = windowName(group.kind, "")
    val names = group.windows.map { windowName(it.kind, it.label) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        if (heading !in names) {
            KitText(heading, role = TypeRole.Label, tone = TextTone.Secondary)
        }
        for (pair in group.windows.chunked(columns)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
                for (window in pair) Cell(window, Modifier.weight(1f))
                repeat(columns - pair.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Compact limits per row: two, one from large text (the size the person chose, [kitFontScale]). */
internal fun limitColumns(chosenScale: Float): Int = if (TextScalePolicy.isLargeText(chosenScale)) 1 else 2

@Composable
private fun Cell(window: LimitWindowUi, modifier: Modifier) {
    val name = windowName(window.kind, window.label)
    val left = leftWords(window.leftPercent)
    val reset = resetWords(window.resetIn)
    val pace = paceWords(window)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        KitText(name, role = TypeRole.Secondary, maxLines = 2)
        KitText(left, role = TypeRole.Secondary, tabular = true)
        KitLimitBar(
            left = window.left.toFloat(),
            evenLeft = window.evenLeft?.toFloat(),
            semantics = listOfNotNull(name, left, reset, pace?.spoken).joinToString(", "),
            compact = true,
        )
        KitText(reset, role = TypeRole.Caption, tone = TextTone.Secondary, tabular = true)
    }
}

/** The age of the numbers, only when it matters: old, offline, or past a reset. */
@Composable
private fun Age(account: LimitAccountUi, nowMs: Long) {
    if (!account.stale) return
    val fetched = account.fetchedAtMs
    when {
        account.state == SubscriptionState.OfflineStale && fetched != null ->
            KitNotice(
                stringResource(Res.string.s9LimitsOfflineAgo, span(nowMs - fetched)),
                tone = StatusTone.Attention,
                liveRegion = false,
            )
        account.state == SubscriptionState.OfflineStale && !account.reportsNothing ->
            KitNotice(stringResource(Res.string.s9LimitsOfflineNone), tone = StatusTone.Attention, liveRegion = false)
        fetched != null ->
            KitText(
                stringResource(Res.string.s9LimitsReadAgo, span(nowMs - fetched)),
                role = TypeRole.Secondary,
                tone = TextTone.Attention,
            )
    }
}

@Composable
private fun Details(account: LimitAccountUi) {
    val hasForecast = account.groups.any { g -> !g.compact && g.windows.any { it.forecast != null } }
    val notes = buildList {
        for (group in account.groups) for (w in group.windows) {
            val at = w.resetsAt ?: continue
            add(stringResource(Res.string.s9LimitsResetsAt, windowName(w.kind, w.label), stamp(at)))
        }
        account.fetchedAt?.let { add(stringResource(Res.string.s9LimitsReadAt, stamp(it))) }
        if (hasForecast) add(stringResource(Res.string.s9LimitsPaceDetailsNoHistory))
    }
    // Flutter PR #136: the signed-in account, copyable when readable; "Yes" / "No" otherwise.
    val redactor = LocalKitRedactor.current
    val values =
        account.account?.let { ref ->
            val shown =
                when (ref) {
                    is LimitAccountRef.Named -> ref.text
                    LimitAccountRef.Known -> stringResource(Res.string.usageYes)
                    LimitAccountRef.Unknown -> stringResource(Res.string.usageNo)
                }
            listOf(
                KitTechnicalValue(
                    stringResource(Res.string.usageDetailAccount),
                    redactor.details(shown),
                    copyable = ref is LimitAccountRef.Named,
                )
            )
        }.orEmpty()
    if (notes.isEmpty() && values.isEmpty()) return
    KitDetailsFold(
        stringResource(Res.string.s9LimitsDetails),
        stringResource(Res.string.s9LimitsCopyDetails),
        notes = notes,
        values = values,
    )
}

private fun stamp(s: UsageStamp): String = Numbers.digits("${s.day} ${s.clock}")

@Composable
private fun windowName(kind: UsageWindowKind, label: String): String {
    if (label.isNotBlank()) return label.trim()
    return stringResource(
        when (kind) {
            UsageWindowKind.FiveHour -> Res.string.s9LimitsFiveHours
            UsageWindowKind.Weekly -> Res.string.s9LimitsWeek
            UsageWindowKind.Monthly -> Res.string.s9LimitsMonth
            UsageWindowKind.Other -> Res.string.s9LimitsOther
        }
    )
}

@Composable
private fun leftWords(percent: Int): String =
    stringResource(Res.string.s9LimitsLeft, "${Numbers.format(percent)}%")

@Composable
private fun resetWords(reset: ResetIn?): String =
    when (reset) {
        null -> stringResource(Res.string.s9LimitsNoReset)
        ResetIn.Passed -> stringResource(Res.string.s9LimitsResetPassed)
        ResetIn.UnderMinute -> stringResource(Res.string.s9LimitsResetsUnderMinute)
        is ResetIn.Left -> stringResource(Res.string.s9LimitsResetsIn, parts(reset.days, reset.hours, reset.minutes))
    }

/** "1h 38m", "2d 4h", "2h", "3d", "12m": the two largest units, a zero second unit left out. */
@Composable
private fun parts(days: Long, hours: Long, minutes: Long): String =
    kitDurationParts(days = days, hours = hours, minutes = minutes.coerceAtLeast(if (days == 0L && hours == 0L) 1L else 0L), style = KitDurationStyle.Compact)

@Composable
private fun span(ms: Long): String =
    kitDurationWords(ms.milliseconds.coerceAtLeast(1.minutes), style = KitDurationStyle.Compact)

/** The plain pace line: its tone, the words shown, and the words read aloud (which also say where the mark is). */
private data class PaceWords(val tone: TextTone, val shown: String, val spoken: String)

@Composable
private fun paceWords(window: LimitWindowUi): PaceWords? {
    val even = window.evenLeft ?: return null
    val mark = stringResource(Res.string.s9LimitsPaceSpoken, "${Numbers.format((even * 100).toInt())}%")
    return when (window.pace) {
        LimitPace.Ahead ->
            stringResource(Res.string.s9LimitsAhead).let { PaceWords(TextTone.Attention, it, "$it. $mark") }
        LimitPace.WithinPace ->
            stringResource(Res.string.s9LimitsWithinPace).let { PaceWords(TextTone.Secondary, it, "$it. $mark") }
        LimitPace.Unknown -> null
    }
}

private const val PERCENT_INT = 100

private val fullWeekdays =
    listOf(
        Res.string.composerLimitMonday,
        Res.string.composerLimitTuesday,
        Res.string.composerLimitWednesday,
        Res.string.composerLimitThursday,
        Res.string.composerLimitFriday,
        Res.string.composerLimitSaturday,
        Res.string.composerLimitSunday,
    )

private val shortWeekdays =
    listOf(
        Res.string.m40PlansMonday,
        Res.string.m40PlansTuesday,
        Res.string.m40PlansWednesday,
        Res.string.m40PlansThursday,
        Res.string.m40PlansFriday,
        Res.string.m40PlansSaturday,
        Res.string.m40PlansSunday,
    )

private fun pctWords(percent: Int): String = "${Numbers.format(percent)}%"

@Composable
private fun forecastSentence(forecast: PaceForecast): String =
    when (forecast) {
        is PaceForecast.TooLittle -> stringResource(Res.string.s9LimitsPaceTooLittle)
        is PaceForecast.Lasts -> stringResource(Res.string.s9LimitsPaceLasts)
        is PaceForecast.RunsOut -> {
            val g = forecast.gapBeforeReset
            val gap = parts(g.days, g.hours, g.minutes)
            val whenText = momentWords(forecast.runsOutMoment)
            val res =
                if (forecast.runsOutMoment is PaceMoment.Relative) {
                    Res.string.s9LimitsPaceRunsOutIn
                } else {
                    Res.string.s9LimitsPaceRunsOut
                }
            stringResource(res, whenText, gap)
        }
        is PaceForecast.Out ->
            if (forecast.resetsMoment is PaceMoment.Relative) {
                stringResource(Res.string.s9LimitsPaceOutReset)
            } else {
                stringResource(Res.string.s9LimitsPaceOutUntil, momentWords(forecast.resetsMoment))
            }
    }

@Composable
private fun momentWords(moment: PaceMoment): String =
    when (moment) {
        is PaceMoment.Today -> stringResource(Res.string.s9LimitsPaceTodayAt, Numbers.digits(moment.clock))
        is PaceMoment.Tomorrow -> stringResource(Res.string.s9LimitsPaceTomorrowAt, Numbers.digits(moment.clock))
        is PaceMoment.Weekday -> {
            val day = stringResource(fullWeekdays.getOrElse(moment.isoDay - 1) { Res.string.composerLimitMonday })
            stringResource(Res.string.s9LimitsPaceDayAt, day, Numbers.digits(moment.clock))
        }
        is PaceMoment.Relative -> parts(moment.days, moment.hours, moment.minutes)
    }

@Composable
private fun comparisonWords(comparison: PaceRateComparison): String {
    val res =
        when (comparison.unit) {
            PaceRateUnit.PerDay -> Res.string.s9LimitsPaceCompareDay
            PaceRateUnit.PerHour -> Res.string.s9LimitsPaceCompareHour
        }
    return stringResource(res, pctWords(comparison.sinceResetPercent), pctWords(comparison.neededToLastPercent))
}

@Composable
private fun nextStepHint(forecast: PaceForecast, switchHint: PaceSwitchHint?): String? =
    when (forecast) {
        is PaceForecast.RunsOut ->
            switchHint?.let {
                stringResource(Res.string.s9LimitsPaceHintSwitch, it.accountName, pctWords(it.leftPercent))
            } ?: stringResource(Res.string.s9LimitsPaceHintSlow)
        is PaceForecast.Out ->
            switchHint?.let {
                stringResource(Res.string.s9LimitsPaceOutSwitch, it.accountName, pctWords(it.leftPercent))
            }
        is PaceForecast.TooLittle,
        is PaceForecast.Lasts -> null
    }

@Composable
private fun bendState(forecast: PaceForecast): KitPaceBendState =
    when (forecast) {
        is PaceForecast.TooLittle -> KitPaceBendState.TooLittle
        is PaceForecast.Lasts ->
            KitPaceBendState.Lasts(
                atReset = forecast.atReset.toFloat(),
                landingLabel = stringResource(Res.string.s9LimitsPaceLeftAtReset, pctWords(forecast.atResetPercent)),
            )
        is PaceForecast.RunsOut ->
            KitPaceBendState.RunsOut(
                runsOutElapsed = forecast.runsOutElapsed.toFloat(),
                noneLeftLabel = stringResource(Res.string.s9LimitsPaceNoneLeft),
            )
        is PaceForecast.Out -> KitPaceBendState.Out(stringResource(Res.string.s9LimitsPaceNoneLeft))
    }

@Composable
private fun axisMark(tick: PaceAxisTick): KitPaceAxisMark {
    val f = tick.fraction.toFloat()
    return when (val kind = tick.kind) {
        PaceTickKind.Divider -> KitPaceAxisMark(f, isDivider = true)
        PaceTickKind.Today -> KitPaceAxisMark(f, label = stringResource(Res.string.usageToday), emphasized = true)
        is PaceTickKind.Weekday -> {
            val day = stringResource(shortWeekdays.getOrElse(kind.isoDay - 1) { Res.string.m40PlansMonday })
            KitPaceAxisMark(f, label = day)
        }
        is PaceTickKind.Clock -> KitPaceAxisMark(f, label = Numbers.digits(kind.text))
        PaceTickKind.Now ->
            KitPaceAxisMark(f, label = stringResource(Res.string.s9LimitsPaceNowLabel), emphasized = true)
        PaceTickKind.Start -> KitPaceAxisMark(f, label = stringResource(Res.string.s9LimitsPaceStartLabel))
        PaceTickKind.Reset -> KitPaceAxisMark(f, label = stringResource(Res.string.s9LimitsPaceResetLabel))
    }
}

/** What an account without numbers says: the agent's own state when it is known, else the reader's. */
@Composable
private fun AgentOrStateWords(account: LimitAccountUi, actions: LimitsActions) {
    when (val agent = account.agent) {
        is UsageAgentTruth.Limit ->
            KitText(
                agent.resets?.let { stringResource(Res.string.agentsStateLimitReset, it) }
                    ?: stringResource(Res.string.agentsStateLimit),
                Modifier.testTag("limit-agent"),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        is UsageAgentTruth.NotConfirmed ->
            KitNotice(
                stringResource(Res.string.agentsStateSignInNotConfirmed),
                tone = StatusTone.Attention,
                liveRegion = false,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.agentsCheckSignInAgain, agent.agentName),
                            actions::checkAgent,
                        )
                    ),
            )
        UsageAgentTruth.SignedIn,
        UsageAgentTruth.SignedOut,
        null -> {
            val state = if (account.agent == UsageAgentTruth.SignedIn) SubscriptionState.CantReadYet else account.state
            KitText(stateWords(state), role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
    }
}

@Composable
private fun stateWords(state: SubscriptionState): String =
    stringResource(
        when (state) {
            SubscriptionState.NotSignedIn -> Res.string.s9LimitsNotSignedIn
            SubscriptionState.SignInExpired -> Res.string.s9LimitsExpired
            SubscriptionState.CantReadYet -> Res.string.s9LimitsCantRead
            SubscriptionState.ReaderBroken -> Res.string.s9LimitsBroken
            SubscriptionState.OfflineStale -> Res.string.s9LimitsOfflineNone
            SubscriptionState.Ok,
            SubscriptionState.NearLimit,
            SubscriptionState.Exhausted,
            SubscriptionState.NoLimits,
            SubscriptionState.ApiKeyOnly -> Res.string.s9LimitsNoLimits
        }
    )
