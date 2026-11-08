package io.github.eslamasabry.oc.feature.usage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.github.eslamasabry.oc.feature.usage.presentation.ResetIn
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.content.usage.KitLimitBar
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
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.s9LimitsAboutPace
import io.github.eslamasabry.oc.ui.words.res.s9LimitsAhead
import io.github.eslamasabry.oc.ui.words.res.s9LimitsBroken
import io.github.eslamasabry.oc.ui.words.res.s9LimitsCantRead
import io.github.eslamasabry.oc.ui.words.res.s9LimitsCopyDetails
import io.github.eslamasabry.oc.ui.words.res.s9LimitsDays
import io.github.eslamasabry.oc.ui.words.res.s9LimitsDaysHours
import io.github.eslamasabry.oc.ui.words.res.s9LimitsDetails
import io.github.eslamasabry.oc.ui.words.res.s9LimitsEmptyBody
import io.github.eslamasabry.oc.ui.words.res.s9LimitsEmptyTitle
import io.github.eslamasabry.oc.ui.words.res.s9LimitsExpired
import io.github.eslamasabry.oc.ui.words.res.s9LimitsFiveHours
import io.github.eslamasabry.oc.ui.words.res.s9LimitsHours
import io.github.eslamasabry.oc.ui.words.res.s9LimitsHoursMinutes
import io.github.eslamasabry.oc.ui.words.res.s9LimitsKeyNoLimits
import io.github.eslamasabry.oc.ui.words.res.s9LimitsKeys
import io.github.eslamasabry.oc.ui.words.res.s9LimitsLeft
import io.github.eslamasabry.oc.ui.words.res.s9LimitsMinutes
import io.github.eslamasabry.oc.ui.words.res.s9LimitsMonth
import io.github.eslamasabry.oc.ui.words.res.s9LimitsNoLimits
import io.github.eslamasabry.oc.ui.words.res.s9LimitsNoReset
import io.github.eslamasabry.oc.ui.words.res.s9LimitsNotSignedIn
import io.github.eslamasabry.oc.ui.words.res.s9LimitsOfflineAgo
import io.github.eslamasabry.oc.ui.words.res.s9LimitsOfflineNone
import io.github.eslamasabry.oc.ui.words.res.s9LimitsOther
import io.github.eslamasabry.oc.ui.words.res.s9LimitsPaceSpoken
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
import io.github.eslamasabry.oc.ui.words.res.usageYes
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
                    items(state.accounts, key = { "account-${it.key}" }) { account -> Account(account, state.nowMs) }
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
                    if (state.accounts.any { a -> a.groups.any { g -> g.windows.any { it.evenLeft != null } } })
                        item(key = "about-pace") {
                            KitText(
                                stringResource(Res.string.s9LimitsAboutPace),
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
private fun Account(account: LimitAccountUi, nowMs: Long) {
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
            if (account.reportsNothing) {
                KitText(stateWords(account.state), role = TypeRole.Secondary, tone = TextTone.Secondary)
            }
            for (group in account.groups) {
                if (group.compact) CompactGroup(group) else Headline(group.windows.first())
            }
            Age(account, nowMs)
            Details(account)
        }
    }
}

/** One window alone in its length: the big "N% left" and its countdown on one line, the bar, then the pace. */
@Composable
private fun Headline(window: LimitWindowUi) {
    val name = windowName(window.kind, window.label)
    val left = leftWords(window.leftPercent)
    val reset = resetWords(window.resetIn)
    val pace = paceWords(window)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitText(name, role = TypeRole.Label, tone = TextTone.Secondary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            KitText(left, Modifier.weight(1f).testTag("limit-left"), role = TypeRole.Title, tabular = true)
            KitText(reset, role = TypeRole.Secondary, tone = TextTone.Secondary, tabular = true)
        }
        KitLimitBar(
            left = window.left.toFloat(),
            evenLeft = window.evenLeft?.toFloat(),
            semantics = listOfNotNull(name, left, reset, pace?.spoken).joinToString(", "),
        )
        pace?.let { KitText(it.shown, role = TypeRole.Secondary, tone = it.tone) }
    }
}

/** Several limits of one length: two columns of label · % · reset, each with its own small bar and mark. */
@Composable
private fun CompactGroup(group: LimitGroupUi) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        KitText(windowName(group.kind, ""), role = TypeRole.Label, tone = TextTone.Secondary)
        for (pair in group.windows.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
                for (window in pair) Cell(window, Modifier.weight(1f))
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

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
    val notes = buildList {
        for (group in account.groups) for (w in group.windows) {
            val at = w.resetsAt ?: continue
            add(stringResource(Res.string.s9LimitsResetsAt, windowName(w.kind, w.label), stamp(at)))
        }
        account.fetchedAt?.let { add(stringResource(Res.string.s9LimitsReadAt, stamp(it))) }
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

private fun stamp(s: UsageStamp): String = "${s.day} ${s.clock}"

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

@Composable private fun leftWords(percent: Int): String = stringResource(Res.string.s9LimitsLeft, "$percent%")

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
    when {
        days > 0 && hours > 0 -> stringResource(Res.string.s9LimitsDaysHours, "$days", "$hours")
        days > 0 -> stringResource(Res.string.s9LimitsDays, "$days")
        hours > 0 && minutes > 0 -> stringResource(Res.string.s9LimitsHoursMinutes, "$hours", "$minutes")
        hours > 0 -> stringResource(Res.string.s9LimitsHours, "$hours")
        else -> stringResource(Res.string.s9LimitsMinutes, "${minutes.coerceAtLeast(1)}")
    }

@Composable
private fun span(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(1)
    return parts(minutes / 1_440, minutes % 1_440 / 60, minutes % 60)
}

/** The plain pace line: its tone, the words shown, and the words read aloud (which also say where the mark is). */
private data class PaceWords(val tone: TextTone, val shown: String, val spoken: String)

@Composable
private fun paceWords(window: LimitWindowUi): PaceWords? {
    val even = window.evenLeft ?: return null
    val mark = stringResource(Res.string.s9LimitsPaceSpoken, "${(even * 100).toInt()}%")
    return when (window.pace) {
        LimitPace.Ahead ->
            stringResource(Res.string.s9LimitsAhead).let { PaceWords(TextTone.Attention, it, "$it. $mark") }
        LimitPace.WithinPace ->
            stringResource(Res.string.s9LimitsWithinPace).let { PaceWords(TextTone.Secondary, it, "$it. $mark") }
        LimitPace.Unknown -> null
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
