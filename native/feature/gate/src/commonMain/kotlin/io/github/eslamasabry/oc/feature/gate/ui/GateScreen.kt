package io.github.eslamasabry.oc.feature.gate.ui

import io.github.eslamasabry.oc.feature.gate.GateFailureScreen
import io.github.eslamasabry.oc.feature.gate.GatePresenter
import io.github.eslamasabry.oc.feature.gate.GateReentry
import io.github.eslamasabry.oc.feature.gate.GateReentryKind
import io.github.eslamasabry.oc.feature.gate.GateRow
import io.github.eslamasabry.oc.feature.gate.GateUi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.input.rememberTextFieldState
import io.github.eslamasabry.oc.feature.gate.GateEntry
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import kotlin.reflect.KClass
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.migration_close
import io.github.eslamasabry.oc.ui.words.res.migration_deferred_notice
import io.github.eslamasabry.oc.ui.words.res.migration_details
import io.github.eslamasabry.oc.ui.words.res.migration_failed_body
import io.github.eslamasabry.oc.ui.words.res.migration_failed_title
import io.github.eslamasabry.oc.ui.words.res.migration_open_storage_row
import io.github.eslamasabry.oc.ui.words.res.migration_opening
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_back
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_body
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_field_password
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_field_token
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_save
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_save_failed
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_later
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_row_byo
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_row_github
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_row_password
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_row_token
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_title
import io.github.eslamasabry.oc.ui.words.res.migration_reenter_unnamed
import io.github.eslamasabry.oc.ui.words.res.migration_report_row
import io.github.eslamasabry.oc.ui.words.res.migration_restore_confirm
import io.github.eslamasabry.oc.ui.words.res.migration_restore_confirm_action
import io.github.eslamasabry.oc.ui.words.res.migration_restore_confirm_title
import io.github.eslamasabry.oc.ui.words.res.migration_restore_row
import io.github.eslamasabry.oc.ui.words.res.migration_save_failed_title
import io.github.eslamasabry.oc.ui.words.res.migration_space_body
import io.github.eslamasabry.oc.ui.words.res.migration_space_title
import io.github.eslamasabry.oc.ui.words.res.migration_start_fresh_confirm
import io.github.eslamasabry.oc.ui.words.res.migration_start_fresh_confirm_action
import io.github.eslamasabry.oc.ui.words.res.migration_start_fresh_confirm_title
import io.github.eslamasabry.oc.ui.words.res.migration_start_fresh_row
import io.github.eslamasabry.oc.ui.words.res.migration_still_updating
import io.github.eslamasabry.oc.ui.words.res.migration_try_again
import io.github.eslamasabry.oc.ui.words.res.migration_updating
import org.jetbrains.compose.resources.stringResource

/**
 * The update gate (plan 05 §8.2, 07 §5 A01): until the gate opens it draws Opening, Updating or a failure screen;
 * once open it draws [content] with the one-time re-enter sheet and the one deferred-features notice. Kit parts only;
 * no bars, no new levels, every option a visible row that names its target. The change between states fades on the
 * kit's standard timing and is instant under reduced motion.
 */
@Composable
public fun UpdateGate(presenter: GatePresenter, content: @Composable () -> Unit) {
    val ui by presenter.ui.collectAsState()
    GateScreen(
        ui = ui,
        onRow = presenter::onRow,
        onConfirm = presenter::onConfirm,
        onCancelConfirm = presenter::onCancelConfirm,
        onReentry = presenter::onReentry,
        onReentryLater = presenter::onReentryLater,
        onReentrySubmit = presenter::onReentrySubmit,
        onReentryBack = presenter::onReentryBack,
        content = content,
    )
}

/** Stateless: draws [ui]. Goldens and tests call this directly. */
@Composable
public fun GateScreen(
    ui: GateUi,
    onRow: (GateRow) -> Unit,
    onConfirm: () -> Unit,
    onCancelConfirm: () -> Unit,
    onReentry: (GateReentry) -> Unit,
    onReentryLater: () -> Unit,
    onReentrySubmit: (String) -> Unit = {},
    onReentryBack: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val draw: @Composable (GateUi) -> Unit = { state ->
        when (state) {
            is GateUi.Opening ->
                WaitingScreen(if (state.wordless) null else stringResource(Res.string.migration_opening), state.offerRetry, onRow)
            is GateUi.Updating -> WaitingScreen(stringResource(Res.string.migration_updating), state.offerRetry, onRow)
            is GateUi.Failed -> FailedScreen(state, onRow, onConfirm, onCancelConfirm)
            is GateUi.Open -> OpenFrame(state, onReentry, onReentryLater, onReentrySubmit, onReentryBack, content)
        }
    }
    // The kit's cross-fade between screens (instant under reduced motion, 05 §8.2). Keyed by the screen kind, so a
    // step change inside one wait or a new row on the same screen does not fade; each kind draws its latest state.
    val latest = remember { mutableMapOf<KClass<out GateUi>, GateUi>() }
    latest[ui::class] = ui
    KitSwap(target = ui::class, modifier = Modifier.fillMaxSize()) { kind -> draw(latest[kind] ?: ui) }
}

/** Opening / Updating. [title] null = the wordless mark: the ground only, so nothing reads in the wrong language. */
@Composable
private fun WaitingScreen(title: String?, offerRetry: Boolean, onRow: (GateRow) -> Unit) {
    KitScreen(title = title ?: "") {
        if (offerRetry) {
            KitText(
                stringResource(Res.string.migration_still_updating),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                role = TypeRole.Body,
                tone = TextTone.Secondary,
            )
            KitRow(
                title = stringResource(Res.string.migration_try_again),
                supporting = null,
                onClick = { onRow(GateRow.TryAgain) },
            )
        }
    }
}

@Composable
private fun FailedScreen(state: GateUi.Failed, onRow: (GateRow) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val title =
        when (state.screen) {
            GateFailureScreen.Failed -> Res.string.migration_failed_title
            GateFailureScreen.LowSpace -> Res.string.migration_space_title
            GateFailureScreen.SaveFailed -> Res.string.migration_save_failed_title
        }
    val body =
        if (state.screen == GateFailureScreen.LowSpace && state.neededBytes != null) {
            stringResource(Res.string.migration_space_body, GatePresenter.formatBytes(state.neededBytes))
        } else {
            stringResource(Res.string.migration_failed_body)
        }
    KitScreen(title = stringResource(title)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("gate-rows"),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitText(body, role = TypeRole.Body, tone = TextTone.Secondary)
            state.rows.forEach { row ->
                KitRow(title = row.label(), supporting = null, onClick = { onRow(row) })
            }
            if (state.detail != null) {
                KitText(
                    stringResource(Res.string.migration_details, state.detail),
                    role = TypeRole.Caption,
                    tone = TextTone.Tertiary,
                    kind = KitTextKind.Frame,
                )
            }
        }
        val confirming = state.confirming
        val fresh = confirming == GateRow.StartFresh
        val confirmTitle =
            stringResource(
                if (fresh) Res.string.migration_start_fresh_confirm_title else Res.string.migration_restore_confirm_title,
            )
        val confirmBody =
            stringResource(if (fresh) Res.string.migration_start_fresh_confirm else Res.string.migration_restore_confirm)
        val confirmAction =
            stringResource(
                if (fresh) Res.string.migration_start_fresh_confirm_action else Res.string.migration_restore_confirm_action,
            )
        KitSheet(
            visible = confirming != null,
            onDismissRequest = onCancel,
            title = confirmTitle,
            closeLabel = stringResource(Res.string.migration_close),
        ) {
            KitText(confirmBody, role = TypeRole.Body)
            KitButton(confirmAction, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The app with the one status-line notice above it and the one-time re-enter sheet over it. */
@Composable
private fun OpenFrame(
    state: GateUi.Open,
    onReentry: (GateReentry) -> Unit,
    onLater: () -> Unit,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    KitSheetHost(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.deferredNotice) {
                val top = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                KitText(
                    stringResource(Res.string.migration_deferred_notice),
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(top)
                        .padding(horizontal = Space.S4.dp, vertical = Space.S2.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("gate-notice"),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    kind = KitTextKind.Frame,
                )
                Box(Modifier.weight(1f).consumeWindowInsets(top)) { content() }
            } else {
                Box(Modifier.weight(1f)) { content() }
            }
        }
        KitSheet(
            visible = state.reenter.isNotEmpty(),
            onDismissRequest = onLater,
            title = stringResource(Res.string.migration_reenter_title),
            closeLabel = stringResource(Res.string.migration_close),
        ) {
            val entry = state.entry
            if (entry != null) {
                key(entry.item) { EntryForm(entry, onSubmit, onBack) }
            } else {
                KitText(
                    stringResource(Res.string.migration_reenter_body),
                    role = TypeRole.Body,
                    tone = TextTone.Secondary,
                )
                state.reenter.forEach { item ->
                    val target = item.targetName ?: stringResource(Res.string.migration_reenter_unnamed)
                    KitRow(
                        title = item.kind.rowLabel(target),
                        supporting = null,
                        onClick = { onReentry(item) },
                    )
                }
                KitRow(title = stringResource(Res.string.migration_reenter_later), supporting = null, onClick = onLater)
            }
        }
    }
}

/** One row's password or token, typed in the sheet and saved on that same server. Never prefilled, never shown. */
@Composable
private fun EntryForm(entry: GateEntry, onSubmit: (String) -> Unit, onBack: () -> Unit) {
    val field = rememberTextFieldState()
    val target = entry.item.targetName ?: stringResource(Res.string.migration_reenter_unnamed)
    val label =
        stringResource(
            if (entry.item.kind == GateReentryKind.Token) Res.string.migration_reenter_field_token
            else Res.string.migration_reenter_field_password,
            target,
        )
    val submit = { onSubmit(field.text.toString()) }
    KitField(
        state = field,
        label = label,
        kind = KitFieldKind.Secret,
        error = if (entry.failed) stringResource(Res.string.migration_reenter_save_failed) else null,
        onSubmit = submit,
    )
    KitButton(
        stringResource(Res.string.migration_reenter_save),
        onClick = submit,
        modifier = Modifier.fillMaxWidth(),
        working = entry.saving,
    )
    KitRow(title = stringResource(Res.string.migration_reenter_back), supporting = null, onClick = onBack)
}

@Composable
private fun GateRow.label(): String =
    stringResource(
        when (this) {
            GateRow.TryAgain -> Res.string.migration_try_again
            GateRow.OpenStorage -> Res.string.migration_open_storage_row
            GateRow.Restore -> Res.string.migration_restore_row
            GateRow.StartFresh -> Res.string.migration_start_fresh_row
            GateRow.Report -> Res.string.migration_report_row
        },
    )

@Composable
private fun GateReentryKind.rowLabel(target: String): String =
    stringResource(
        when (this) {
            GateReentryKind.Password -> Res.string.migration_reenter_row_password
            GateReentryKind.Token -> Res.string.migration_reenter_row_token
            GateReentryKind.GitHubSignIn -> Res.string.migration_reenter_row_github
            GateReentryKind.ByoSetup -> Res.string.migration_reenter_row_byo
        },
        target,
    )
