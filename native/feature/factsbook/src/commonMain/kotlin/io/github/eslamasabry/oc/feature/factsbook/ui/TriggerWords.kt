package io.github.eslamasabry.oc.feature.factsbook.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.factsbook.EventTrust
import io.github.eslamasabry.oc.engine.factsbook.RuleDefinition
import io.github.eslamasabry.oc.engine.factsbook.RuleProblem
import io.github.eslamasabry.oc.engine.factsbook.TriggerOutcome
import io.github.eslamasabry.oc.engine.factsbook.TriggerOutcomeKind
import io.github.eslamasabry.oc.engine.factsbook.TriggerReason
import io.github.eslamasabry.oc.engine.factsbook.TriggerSourceKind
import io.github.eslamasabry.oc.engine.factsbook.TriggerTemplateId
import io.github.eslamasabry.oc.ui.kit.text.kitDateTime
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/* The app's words for trigger values (F: trigger_text.dart). Plain words only: no ids, no codes. */

@Composable
internal fun sourceKindWords(kind: TriggerSourceKind): String =
    stringResource(
        when (kind) {
            TriggerSourceKind.Share -> Res.string.fbSourceGroupShare
            TriggerSourceKind.ChatSelection -> Res.string.fbSourceGroupChat
            TriggerSourceKind.Note -> Res.string.fbSourceGroupNote
        }
    )

@Composable
internal fun sourceGroupWords(kind: TriggerSourceKind, count: Int): String =
    pluralStringResource(
        when (kind) {
            TriggerSourceKind.Share -> Res.plurals.fbTriggersGroupShare
            TriggerSourceKind.ChatSelection -> Res.plurals.fbTriggersGroupChat
            TriggerSourceKind.Note -> Res.plurals.fbTriggersGroupNote
        },
        count,
        count,
    )

@Composable
internal fun templateWords(id: TriggerTemplateId): String =
    stringResource(
        when (id) {
            TriggerTemplateId.Appointment -> Res.string.fbTemplateAppointment
            TriggerTemplateId.Delivery -> Res.string.fbTemplateDelivery
            TriggerTemplateId.Booking -> Res.string.fbTemplateBooking
            TriggerTemplateId.Subscription -> Res.string.fbTemplateSubscription
            TriggerTemplateId.BillDue -> Res.string.fbTemplateBill
        }
    )

@Composable
internal fun outcomeWords(kind: TriggerOutcomeKind): String =
    stringResource(
        when (kind) {
            TriggerOutcomeKind.Reminder -> Res.string.fbOutcomeReminder
            TriggerOutcomeKind.Fact -> Res.string.fbOutcomeFact
            TriggerOutcomeKind.Conversation -> Res.string.fbOutcomeConversation
        }
    )

@Composable
internal fun outcomesLine(outcomes: List<TriggerOutcome>): String = outcomes.map { outcomeWords(it.kind) }.joinToString(", ")

/** "Looks for an appointment → propose a reminder, offer to talk about it". */
@Composable
internal fun ruleLine(rule: RuleDefinition): String =
    stringResource(Res.string.fbRuleFinds, templateWords(rule.template.id), outcomesLine(rule.outcomes))

@Composable
internal fun triggerReasonWords(reason: TriggerReason): String =
    stringResource(
        when (reason) {
            TriggerReason.Matched -> Res.string.fbReasonMatched
            TriggerReason.NoMatch -> Res.string.fbReasonNoMatch
            TriggerReason.Uncertain -> Res.string.fbReasonUncertain
            TriggerReason.SensitiveUnavailable -> Res.string.fbReasonSensitive
            TriggerReason.SourceCap -> Res.string.fbReasonSourceCap
            TriggerReason.VaultCap -> Res.string.fbReasonVaultCap
            TriggerReason.RuleCap -> Res.string.fbReasonRuleCap
            TriggerReason.StormCollapsed -> Res.string.fbReasonStorm
            TriggerReason.NoEnabledRule -> Res.string.fbReasonNoRule
            TriggerReason.TargetStale -> Res.string.fbReasonTargetStale
            TriggerReason.TargetMissing, TriggerReason.TargetDenied -> Res.string.fbReasonTargetGone
            TriggerReason.Rejected -> Res.string.fbReasonRejectedEvent
        }
    )

@Composable
internal fun trustWords(trust: EventTrust): String =
    stringResource(
        when (trust) {
            EventTrust.UserAuthored -> Res.string.fbTrustAuthored
            EventTrust.UserSelected -> Res.string.fbTrustSelected
            EventTrust.ThirdPartyUntrusted -> Res.string.fbTrustUntrusted
        }
    )

@Composable
internal fun problemWords(problem: RuleProblem): String =
    stringResource(
        when (problem) {
            RuleProblem.NameEmpty, RuleProblem.NameTooLong -> Res.string.fbRuleProblemName
            RuleProblem.TooManyWords, RuleProblem.WordEmpty, RuleProblem.WordTooLong -> Res.string.fbRuleProblemWords
            RuleProblem.NoOutcome -> Res.string.fbRuleProblemOutcome
            RuleProblem.TemplateUnavailable -> Res.string.fbRuleProblemTemplate
            RuleProblem.LimitOutOfRange -> Res.string.fbRuleProblemLimit
            RuleProblem.SenderClaimInvalid -> Res.string.fbRuleProblemSender
        }
    )

/** A date and time in the device's own words; the ISO text when the platform has no formatter. */
@Composable
internal fun whenWords(at: Instant): String = kitDateTime(at.toEpochMilliseconds()) ?: at.toString().substringBefore('.').replace('T', ' ')

private val localIso = Regex("""^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])T([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$""")

/** The local ISO text a test or a proposal gives (`2026-10-15T09:00`) in words; the text itself when unreadable. */
@Composable
internal fun isoWhenWords(iso: String): String {
    val at = if (localIso.matches(iso)) runCatching { LocalDateTime.parse(iso).toInstant(TimeZone.currentSystemDefault()) }.getOrNull() else null
    return at?.let { whenWords(it) } ?: iso
}
