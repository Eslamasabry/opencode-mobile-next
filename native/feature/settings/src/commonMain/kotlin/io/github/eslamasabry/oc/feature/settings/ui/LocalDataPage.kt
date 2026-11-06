package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.HandWords
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Local content stays until the person confirms a specific discard. Uncertain sends are retained.
 */
@Composable
internal fun ColumnScope.LocalDataBody(ui: LocalDataUi, actions: SettingsActions) {
    if (ui.failed) KitNotice(stringResource(Res.string.settings40_discard_failed))
    ui.result?.let { result ->
        KitNotice(
            pluralStringResource(
                when {
                    !result.payloadsCollected -> Res.plurals.settings40_discard_cleanup
                    result.kept > 0 -> Res.plurals.settings40_discard_kept
                    else -> Res.plurals.settings40_discard_done
                },
                result.removed,
                result.removed,
                result.kept,
            )
        )
    }
    val facts = ui.facts
    if (facts == null) {
        KitGroupNote(stringResource(Res.string.settings40_storage_reading))
        return
    }
    if (facts.servers.isNotEmpty())
        KitRowGroup(leadingTiles = true, label = stringResource(Res.string.privacySharedSection)) {
            KitRow(
                title = stringResource(Res.string.shareSessionViewsTitle),
                leading = KitRowLeading.Glyph(KitIcons.visible),
                trailing =
                    KitRowTrailing.Value(
                        if (facts.servers.size == 1)
                            stringResource(
                                if (facts.sharing == 1) Res.string.settingsV3On
                                else Res.string.settingsV3Off
                            )
                        else
                            stringResource(
                                Res.string.settingsV3SharingCount,
                                facts.sharing,
                                facts.servers.size,
                            )
                    ),
                onClick = {
                    if (facts.servers.size == 1)
                        actions.onSavedServer(facts.servers.single(), SettingsOutside.ReadState)
                    else actions.onOpen(SettingsPage.ByServer(SettingsOutside.ReadState))
                },
            )
        }
    Spacer(Modifier.height(Space.S6.dp))
    KitRowGroup(leadingTiles = true, label = stringResource(Res.string.e7SettingsUi76)) {
        KitRow(
            title = stringResource(Res.string.e7SettingsUi77),
            modifier = Modifier.testTag("local-storage-usage"),
            leading = KitRowLeading.Glyph(KitIcons.storage),
            supporting =
                if (facts.queueReadable && facts.draftsReadable)
                    HandWords.e7SettingsStorageSummaryText(
                        localBytes(facts.queueBytes + facts.draftBytes),
                        facts.queued,
                        localBytes(facts.queueBytes),
                        facts.drafts,
                        localBytes(facts.draftBytes),
                        14, // F: lib/state/offline_queue.dart:264
                    )
                else stringResource(Res.string.queueStorageCountUnknown),
            supportingMaxLines = 4, // F: lib/ui/screens/settings/personal_settings_screens.dart:197
        )
        KitRow(
            title = stringResource(Res.string.e7SettingsUi78),
            modifier = Modifier.testTag("clear-queued-prompts"),
            leading = KitRowLeading.Glyph(KitIcons.outbox),
            supporting =
                when {
                    !facts.queueReadable -> stringResource(Res.string.queueStorageUnreadable)
                    facts.queued == 0 -> stringResource(Res.string.e7SettingsUi79)
                    else ->
                        pluralStringResource(
                            Res.plurals.e7SettingsQueueDeleteSummary,
                            facts.queued,
                            facts.queued,
                        )
                },
            supportingMaxLines = 2, // F: lib/ui/screens/settings/personal_settings_screens.dart:299
            enabled = !ui.busy && facts.queueReadable && facts.queued > 0,
            disabledReason =
                stringResource(
                    when {
                        ui.busy -> Res.string.privacyDeleting
                        !facts.queueReadable -> Res.string.queueStorageUnreadable
                        else -> Res.string.e7SettingsUi79
                    }
                ),
            onClick = { actions.onAskDiscard(LocalDataKind.Queue) },
        )
        KitRow(
            title = stringResource(Res.string.e7SettingsUi83),
            modifier = Modifier.testTag("clear-session-drafts"),
            leading = KitRowLeading.Glyph(KitIcons.editNote),
            supporting =
                if (facts.drafts == 0) stringResource(Res.string.e7SettingsUi84)
                else
                    pluralStringResource(
                        Res.plurals.e7SettingsDraftDeleteSummary,
                        facts.drafts,
                        facts.drafts,
                    ),
            supportingMaxLines = 2, // F: lib/ui/screens/settings/personal_settings_screens.dart:299
            enabled = !ui.busy && facts.draftsReadable && facts.drafts > 0,
            disabledReason =
                stringResource(
                    if (ui.busy) Res.string.privacyDeleting else Res.string.e7SettingsUi84
                ),
            onClick = { actions.onAskDiscard(LocalDataKind.Drafts) },
        )
    }
    Spacer(Modifier.height(Space.S6.dp))
    KitConfirm(
        spec =
            ui.confirm?.let { kind ->
                KitConfirmSpec(
                    title =
                        stringResource(
                            if (kind == LocalDataKind.Queue) Res.string.e7SettingsUi80
                            else Res.string.e7SettingsUi85
                        ),
                    confirmLabel =
                        pluralStringResource(
                            if (kind == LocalDataKind.Queue) Res.plurals.privacyDeleteQueuedCount
                            else Res.plurals.privacyDeleteDraftsCount,
                            if (kind == LocalDataKind.Queue) facts.queued else facts.drafts,
                            if (kind == LocalDataKind.Queue) facts.queued else facts.drafts,
                        ),
                    body =
                        pluralStringResource(
                            if (kind == LocalDataKind.Queue) Res.plurals.e7SettingsQueueDeleteBody
                            else Res.plurals.e7SettingsDraftDeleteBody,
                            if (kind == LocalDataKind.Queue) facts.queued else facts.drafts,
                            if (kind == LocalDataKind.Queue) facts.queued else facts.drafts,
                        ),
                    kind = KitConfirmKind.Destructive,
                )
            },
        onConfirm = actions::onConfirmDiscard,
        onCancel = actions::onCancelDiscard,
    )
}

internal fun localBytes(bytes: Long): String {
    val kilo = 1024L // F: lib/ui/screens/settings/personal_settings_screens.dart:30
    val mega = kilo * kilo
    val unit =
        when {
            bytes >= mega -> mega
            bytes >= kilo -> kilo
            else -> 1L
        }
    if (unit == 1L) return "${bytes.coerceAtLeast(0)} B"
    val decimals = 10 // NV: one fractional decimal digit, as Dart toStringAsFixed(1)
    val tenths = (bytes.toDouble() / unit * decimals).roundToLong()
    return "${tenths / decimals}.${tenths % decimals} ${if (unit == kilo) "KB" else "MB"}"
}
