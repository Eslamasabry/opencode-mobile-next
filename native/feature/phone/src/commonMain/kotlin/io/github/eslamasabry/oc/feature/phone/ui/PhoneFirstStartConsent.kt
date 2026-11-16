package io.github.eslamasabry.oc.feature.phone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.settings.consent.InFlowConsentKind
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneConsentUi
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneFirstStartConsents
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The first-start questions, asked once the built-in server has first started and the moment is [quiet] (the shell
 * says so: the conversation list is showing, no other sheet or dialog is open, no reply is running). A question that
 * is waiting never opens over anything else; once it is on screen it stays until it is answered, however the page
 * under it changes. The queue saves each answer before any Android settings page opens.
 */
@Composable
public fun PhoneFirstStartConsentHost(consents: PhoneFirstStartConsents, quiet: Boolean = true) {
    val consent by consents.pending.collectAsState()
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val show = consent != null && (shown || quiet)
    LaunchedEffect(show, consent == null) { shown = show }
    PhoneFirstStartConsent(if (show) consent else null, failed) { allow ->
        if (!saving) {
            saving = true
            failed = false
            scope.launch {
                try {
                    failed = consents.answer(allow) is Outcome.Failed
                } finally {
                    saving = false
                }
            }
        }
    }
}

@Composable
private fun PhoneFirstStartConsent(
    consent: PhoneConsentUi?,
    failed: Boolean,
    answer: (Boolean) -> Unit,
) {
    val kind = consent?.kind
    val spec =
        if (consent == null) null
        else
            KitConfirmSpec(
                title =
                    stringResource(
                        when (kind) {
                            InFlowConsentKind.BatteryExemption -> Res.string.consentBatteryTitle
                            InFlowConsentKind.MakerAutoStart -> Res.string.consentMakerTitle
                            else -> Res.string.firstRunNotifyTitle
                        }
                    ),
                body =
                    (when (kind) {
                        InFlowConsentKind.BatteryExemption ->
                            stringResource(Res.string.consentBatteryBody)
                        InFlowConsentKind.MakerAutoStart ->
                            if (consent.maker.isBlank())
                                stringResource(Res.string.consentMakerBodyUnnamed)
                            else stringResource(Res.string.consentMakerBody, consent.maker)
                        else -> stringResource(Res.string.firstRunNotifyBody)
                    }) +
                        if (failed) "\n\n" + stringResource(Res.string.consentSaveFailed) else "",
                confirmLabel =
                    stringResource(
                        when (kind) {
                            InFlowConsentKind.BatteryExemption -> Res.string.consentBatteryAllow
                            InFlowConsentKind.MakerAutoStart -> Res.string.consentMakerAllow
                            else -> Res.string.consentNeedsYouAllow
                        }
                    ),
                cancelLabel = stringResource(Res.string.consentNotNow),
                kind = KitConfirmKind.Neutral,
            )
    KitConfirm(spec, onConfirm = { answer(true) }, onCancel = { answer(false) })
}
