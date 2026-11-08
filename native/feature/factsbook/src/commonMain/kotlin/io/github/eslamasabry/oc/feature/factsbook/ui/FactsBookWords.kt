package io.github.eslamasabry.oc.feature.factsbook.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveEntry
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveReason
import io.github.eslamasabry.oc.engine.factsbook.FactProvenance
import io.github.eslamasabry.oc.engine.factsbook.FactsBookError
import io.github.eslamasabry.oc.engine.factsbook.ThingKind
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitProvenance
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.text.kitDateTime
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

/*
 * The app's words for FactsBook values (F: lib/ui/screens/factsbook/factsbook_text.dart). Plain words only: never the
 * node's own text and never exception text; the code and request id go under Details.
 */

internal fun kitProvenanceOf(provenance: FactProvenance): KitProvenance =
    when (provenance) {
        FactProvenance.Told -> KitProvenance.Told
        FactProvenance.Seen -> KitProvenance.Seen
        FactProvenance.Calculated -> KitProvenance.Calculated
        // An unverified hand edit reads like a guess, never as told.
        FactProvenance.Guessed, FactProvenance.External -> KitProvenance.Guessed
    }

private val idShape = Regex("^(?:[a-z]{1,3}_[0-9A-Z]{26}|[0-9a-f]{64})$")

/** "a shared message": the node's words, or the app's words when the source is an id. Null for none. */
@Composable
internal fun sourceWords(source: String?): String? {
    if (source == null || source.isBlank()) return null
    if (idShape.matches(source)) return stringResource(Res.string.fbSourceSaved)
    return when (source) {
        "chat", "chat_selection" -> stringResource(Res.string.fbSourceChat)
        "share" -> stringResource(Res.string.fbSourceShare)
        "note" -> stringResource(Res.string.fbSourceNote)
        "import" -> stringResource(Res.string.fbSourceImport)
        "statement" -> stringResource(Res.string.fbSourceStatement)
        else -> source
    }
}

@Composable
internal fun kindWords(kind: ThingKind): String =
    stringResource(
        when (kind) {
            ThingKind.Person -> Res.string.fbKindPerson
            ThingKind.Document -> Res.string.fbKindDocument
            ThingKind.Subscription -> Res.string.fbKindSubscription
            ThingKind.Warranty -> Res.string.fbKindWarranty
            ThingKind.Bill -> Res.string.fbKindBill
            ThingKind.Invoice -> Res.string.fbKindInvoice
            ThingKind.Card -> Res.string.fbKindCard
            ThingKind.Account -> Res.string.fbKindAccount
            ThingKind.Organisation -> Res.string.fbKindOrganization
            ThingKind.Place -> Res.string.fbKindPlace
            ThingKind.Project -> Res.string.fbKindProject
            ThingKind.Topic -> Res.string.fbKindTopic
            ThingKind.Event -> Res.string.fbKindEvent
            ThingKind.Health -> Res.string.fbKindHealth
            ThingKind.Other -> Res.string.fbKindOther
        }
    )

/** An area's own name, for its row. An area the app does not know keeps the node's slug. */
@Composable
internal fun areaName(area: String): String =
    when (area) {
        "money" -> stringResource(Res.string.fbAreaNameMoney)
        "health" -> stringResource(Res.string.fbAreaNameHealth)
        "family" -> stringResource(Res.string.fbAreaNameFamily)
        "work" -> stringResource(Res.string.fbAreaNameWork)
        "general" -> stringResource(Res.string.fbAreaNameGeneral)
        "home" -> stringResource(Res.string.fbAreaNameHome)
        "travel" -> stringResource(Res.string.fbAreaNameTravel)
        "unsorted" -> stringResource(Res.string.fbAreaNameUnsorted)
        "id" -> stringResource(Res.string.fbAreaNameId)
        else -> area
    }

/** Why a capture is kept. [canUnlock] false: "unlock it" is no way out yet. */
@Composable
internal fun reasonWords(entry: CouldNotSaveEntry, canUnlock: Boolean): String =
    stringResource(
        when (entry.reason) {
            CouldNotSaveReason.NodeUnreachable -> Res.string.fbReasonUnreachable
            CouldNotSaveReason.StorageFull -> Res.string.fbReasonStorageFull
            CouldNotSaveReason.CloudQuota -> Res.string.fbReasonCloudQuota
            CouldNotSaveReason.KeyLocked -> if (canUnlock) Res.string.fbReasonKeyLocked else Res.string.fbReasonKeyLockedNotYet
            CouldNotSaveReason.ValidationRejected -> Res.string.fbReasonRejected
            CouldNotSaveReason.ClassificationFailed -> Res.string.fbReasonClassification
            CouldNotSaveReason.ParserError -> Res.string.fbReasonParserError
            CouldNotSaveReason.OutboxFull -> Res.string.fbReasonOutboxFull
        }
    )

/** What went wrong, as a sentence with a way forward (F: `failureWords`). */
@Composable
internal fun failureWords(failure: FactsBookError?): String {
    val code = failure?.code
    if (code == "slow_down") {
        val wait = failure.retryAfterSeconds
        return if (wait == null) stringResource(Res.string.fbErrorSlow) else stringResource(Res.string.fbErrorSlowAfter, wait.toInt())
    }
    return stringResource(
        when (code) {
            "locked_no_key", "sensitive_locked" -> Res.string.fbErrorLocked
            "not_found" -> Res.string.fbErrorNotFound
            "node_unreachable", "not_working_device" -> Res.string.fbErrorUnreachable
            "starting" -> Res.string.fbErrorStarting
            "busy" -> Res.string.fbErrorBusy
            "unauthorized" -> Res.string.fbErrorUnauthorized
            "not_enabled", "not_qualified" -> Res.string.fbErrorNotEnabled
            "stale_approval", "precondition_failed", "conflict" -> Res.string.fbErrorStale
            "owner_required" -> Res.string.fbErrorOwner
            "forbidden_scope", "not_approved" -> Res.string.fbErrorForbidden
            "address_not_allowed" -> Res.string.fbErrorAddress
            "bad_response" -> Res.string.fbErrorBadResponse
            "bad_request" -> Res.string.fbErrorBadRequest
            "outbox_full" -> Res.string.fbErrorNodeFull
            "index_rebuilding" -> Res.string.fbErrorIndexRebuilding
            "device_auth_unavailable" -> Res.string.fbErrorAuthUnavailable
            "device_auth_cancelled" -> Res.string.fbErrorAuthCancelled
            "device_auth_locked" -> Res.string.fbErrorAuthLocked
            "device_auth_failed" -> Res.string.fbErrorAuthFailed
            else -> Res.string.fbErrorGeneric
        }
    )
}

/** The technical line for Details: the node's code and request id. */
@Composable
internal fun failureDetails(failure: FactsBookError?): String? {
    failure ?: return null
    val code = stringResource(Res.string.fbErrorCode, failure.code)
    val id = failure.requestId ?: return code
    return code + "\n" + stringResource(Res.string.fbErrorRequest, id)
}

/** A name from the data, isolated only where the layout runs right to left. */
@Composable
internal fun bidiName(value: String): String =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) KitBidi.auto(value) else value

/** The date a fact was seen, in the device's own words; the ISO day when the platform has no formatter. */
@Composable
internal fun observedWords(at: Instant): String =
    kitDateTime(at.toEpochMilliseconds()) ?: at.toString().substringBefore('T')

/** The mark at the start of a thing's row (F: `kindIcon`). */
internal fun kindIcon(kind: ThingKind): ImageVector =
    when (kind) {
        ThingKind.Person -> KitIcons.person
        ThingKind.Document -> KitIcons.fileText
        ThingKind.Subscription -> KitIcons.sync
        ThingKind.Warranty -> KitIcons.shield
        ThingKind.Bill, ThingKind.Invoice -> KitIcons.article
        ThingKind.Card -> KitIcons.category
        ThingKind.Account -> KitIcons.account
        ThingKind.Organisation -> KitIcons.folders
        ThingKind.Place -> KitIcons.globe
        ThingKind.Project -> KitIcons.projects
        ThingKind.Topic -> KitIcons.note
        ThingKind.Event -> KitIcons.calendar
        ThingKind.Health -> KitIcons.shieldFilled
        ThingKind.Other -> KitIcons.idea
    }

/**
 * The one line a fact row speaks of (Dart `KitFactRow.lineOf`), in the kit's own words. The native kit draws this line
 * inside KitFactRow only; a public `lineOf` is proposed to the kit owner (report), and until then the same strings are
 * read here so a list row and a fact card say the same thing.
 */
@Composable
internal fun provenanceLine(provenance: KitProvenance, observed: String, source: String?, waitingForOk: Boolean): String {
    val from = source?.takeIf { it.isNotEmpty() }?.let { bidiName(it) }
    val base =
        when (provenance) {
            KitProvenance.Told -> stringResource(Res.string.kitFactToldLine, observed)
            KitProvenance.Seen ->
                if (from == null) stringResource(Res.string.kitFactSeenNoSourceLine, observed)
                else stringResource(Res.string.kitFactSeenLine, from, observed)
            KitProvenance.Guessed ->
                if (from == null) stringResource(Res.string.kitFactGuessedLine, observed)
                else stringResource(Res.string.kitFactGuessedFromLine, observed, from)
            KitProvenance.Calculated -> stringResource(Res.string.kitFactCalculatedLine, observed)
        }
    return if (waitingForOk) stringResource(Res.string.kitFactWaitingLine, base) else base
}

/** A proposal's risk in plain words (F: `riskWords`). */
@Composable
internal fun riskWords(risk: String): String =
    when (risk) {
        "money" -> stringResource(Res.string.fbCareRiskMoney)
        "health" -> stringResource(Res.string.fbCareRiskHealth)
        "id" -> stringResource(Res.string.fbCareRiskId)
        "contradiction" -> stringResource(Res.string.fbCareRiskContradiction)
        "low_confidence" -> stringResource(Res.string.fbCareRiskLowConfidence)
        else -> risk
    }
