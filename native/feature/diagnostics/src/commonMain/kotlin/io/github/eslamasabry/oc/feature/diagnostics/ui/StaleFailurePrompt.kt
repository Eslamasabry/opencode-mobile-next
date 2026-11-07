package io.github.eslamasabry.oc.feature.diagnostics.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.diagnostics.presentation.StaleFailureActions
import io.github.eslamasabry.oc.feature.diagnostics.presentation.StaleFailureUi
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.staleFailureBody
import io.github.eslamasabry.oc.ui.words.res.staleFailureReportAnyway
import io.github.eslamasabry.oc.ui.words.res.staleFailureTitle
import io.github.eslamasabry.oc.ui.words.res.staleFailureUpdateFirst
import org.jetbrains.compose.resources.stringResource

/**
 * "Report this failure" for a failure an older app version saved (E19-S03): one plain line, "This
 * happened on 2.1.1. Version 3.0.0 may already fix it.", with Update first and Report anyway.
 * When no update is waiting (the running app is already newer) Report anyway is the one answer.
 * Kit parts only: KitConfirm opens as a sheet or dialog with the kit's own motion, which follows
 * reduced motion. Shown while [state] is non-null; host it inside a KitSheetHost.
 */
@Composable
public fun StaleFailurePrompt(state: StaleFailureUi?, actions: StaleFailureActions) {
    val report = stringResource(Res.string.staleFailureReportAnyway)
    val update = stringResource(Res.string.staleFailureUpdateFirst)
    val spec =
        state?.let {
            KitConfirmSpec(
                title = stringResource(Res.string.staleFailureTitle),
                confirmLabel = if (it.canUpdate) update else report,
                kind = KitConfirmKind.Neutral,
                body = stringResource(Res.string.staleFailureBody, it.happenedOn, it.fixedIn),
                icon = KitIcons.info,
            )
        }
    KitConfirm(
        spec = spec,
        onConfirm = {
            if (state?.canUpdate == true) actions.onUpdateFirst() else actions.onReportAnyway()
        },
        onCancel = actions::onDismissStale,
        alternative =
            if (state?.canUpdate == true) KitAction(report, onClick = actions::onReportAnyway)
            else null,
    )
}
