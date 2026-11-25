package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.model.AutomationAction
import io.github.eslamasabry.oc.core.model.AutomationOutcome
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.AutomationTrigger
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.engine.schedules.ScheduleTime
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.schedule40Friday
import io.github.eslamasabry.oc.ui.words.res.schedule40Monday
import io.github.eslamasabry.oc.ui.words.res.schedule40Saturday
import io.github.eslamasabry.oc.ui.words.res.schedule40Sunday
import io.github.eslamasabry.oc.ui.words.res.schedule40Thursday
import io.github.eslamasabry.oc.ui.words.res.schedule40Tuesday
import io.github.eslamasabry.oc.ui.words.res.schedule40Wednesday
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsActionCode
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsActionPrompt
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsActionPromptAgent
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsActionShell
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsLastDisabled
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsLastExpired
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsLastFailed
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsLastFired
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsLastLate
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsLastSkipped
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsLastUnknown
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsNeverRan
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsNext
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsOnEvent
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsStatusActive
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsStatusDone
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsStatusPaused
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsToday
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsTomorrow
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsTriggerCron
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsTriggerEvent
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsTriggerEventAny
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsTriggerEvery
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsTriggerOnce
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsYesterday
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun automationRowSummary(item: ServerAutomation, nowMs: Long): String {
    val nextPart =
        when {
            item.status == AutomationStatus.Paused ->
                stringResource(Res.string.serverAutomationsStatusPaused)
            item.status == AutomationStatus.Done ->
                stringResource(Res.string.serverAutomationsStatusDone)
            item.nextDueAtMs != null ->
                stringResource(
                    Res.string.serverAutomationsNext,
                    formatAutomationMoment(item.nextDueAtMs, nowMs),
                )
            item.trigger is AutomationTrigger.Event ->
                stringResource(Res.string.serverAutomationsOnEvent)
            else -> stringResource(Res.string.serverAutomationsStatusActive)
        }
    val lastPart = automationLastRunDescription(item, nowMs)
    return "$nextPart · $lastPart"
}

@Composable
internal fun automationStatusLabel(status: AutomationStatus): String =
    stringResource(
        when (status) {
            AutomationStatus.Active -> Res.string.serverAutomationsStatusActive
            AutomationStatus.Paused -> Res.string.serverAutomationsStatusPaused
            AutomationStatus.Done -> Res.string.serverAutomationsStatusDone
            AutomationStatus.Unknown -> Res.string.serverAutomationsStatusActive
        }
    )

@Composable
internal fun automationLastRunDescription(item: ServerAutomation, nowMs: Long): String {
    val ranAt = item.lastRunAtMs ?: return stringResource(Res.string.serverAutomationsNeverRan)
    val whenText = formatAutomationMoment(ranAt, nowMs)
    val res =
        when (item.lastOutcome) {
            AutomationOutcome.Fired -> Res.string.serverAutomationsLastFired
            AutomationOutcome.Late -> Res.string.serverAutomationsLastLate
            AutomationOutcome.Skipped -> Res.string.serverAutomationsLastSkipped
            AutomationOutcome.Disabled -> Res.string.serverAutomationsLastDisabled
            AutomationOutcome.Failed -> Res.string.serverAutomationsLastFailed
            AutomationOutcome.Expired -> Res.string.serverAutomationsLastExpired
            AutomationOutcome.Unknown,
            null -> Res.string.serverAutomationsLastUnknown
        }
    return stringResource(res, whenText)
}

@Composable
internal fun automationTriggerDescription(trigger: AutomationTrigger, nowMs: Long): String? =
    when (trigger) {
        is AutomationTrigger.Every ->
            trigger.duration.takeIf { it.isNotBlank() }?.let {
                stringResource(Res.string.serverAutomationsTriggerEvery, it)
            }
        is AutomationTrigger.Cron ->
            trigger.expression.takeIf { it.isNotBlank() }?.let {
                stringResource(Res.string.serverAutomationsTriggerCron, it)
            }
        is AutomationTrigger.At ->
            trigger.atMs?.let {
                stringResource(
                    Res.string.serverAutomationsTriggerOnce,
                    formatAutomationMoment(it, nowMs),
                )
            }
        is AutomationTrigger.Event -> {
            val source = trigger.source?.takeIf { it.isNotBlank() }
            val kind = trigger.kind?.takeIf { it.isNotBlank() }
            when {
                source != null && kind != null ->
                    stringResource(Res.string.serverAutomationsTriggerEvent, source, kind)
                source != null ->
                    stringResource(Res.string.serverAutomationsTriggerEventAny, source)
                else -> stringResource(Res.string.serverAutomationsOnEvent)
            }
        }
        AutomationTrigger.Unknown -> null
    }

@Composable
internal fun automationActionKindLabel(action: AutomationAction): String =
    when (action) {
        is AutomationAction.Prompt ->
            action.agent?.takeIf { it.isNotBlank() }?.let {
                stringResource(Res.string.serverAutomationsActionPromptAgent, it)
            } ?: stringResource(Res.string.serverAutomationsActionPrompt)
        is AutomationAction.Shell -> stringResource(Res.string.serverAutomationsActionShell)
        is AutomationAction.Code -> stringResource(Res.string.serverAutomationsActionCode)
        AutomationAction.Unknown -> stringResource(Res.string.serverAutomationsActionPrompt)
    }

@Composable
internal fun formatAutomationMoment(ms: Long, nowMs: Long): String {
    val reference = if (nowMs > 0L) nowMs else ms
    val m = ScheduleTime.moment(ms, reference)
    val clock =
        Numbers.digits(
            m.hour.toString().padStart(TIME_DIGITS, '0') +
                ":" +
                m.minute.toString().padStart(TIME_DIGITS, '0')
        )
    val day =
        when (m.dayOffset) {
            0L -> stringResource(Res.string.serverAutomationsToday)
            1L -> stringResource(Res.string.serverAutomationsTomorrow)
            -1L -> stringResource(Res.string.serverAutomationsYesterday)
            in NEXT_WEEK_MIN_DAYS..NEXT_WEEK_MAX_DAYS ->
                stringResource(WEEKDAY_SHORT.getOrElse(m.weekday) { Res.string.schedule40Sunday })
            else -> Numbers.digits(m.date)
        }
    return "$day $clock"
}

private val WEEKDAY_SHORT =
    listOf(
        Res.string.schedule40Monday,
        Res.string.schedule40Tuesday,
        Res.string.schedule40Wednesday,
        Res.string.schedule40Thursday,
        Res.string.schedule40Friday,
        Res.string.schedule40Saturday,
        Res.string.schedule40Sunday,
    )

private const val TIME_DIGITS = 2
private const val NEXT_WEEK_MIN_DAYS = 2L
private const val NEXT_WEEK_MAX_DAYS = 6L
