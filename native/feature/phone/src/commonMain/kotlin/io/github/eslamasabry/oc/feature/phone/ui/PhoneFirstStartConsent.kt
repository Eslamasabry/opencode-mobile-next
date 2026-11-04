package io.github.eslamasabry.oc.feature.phone.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.settings.consent.InFlowConsentKind
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneActions
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneUi
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * The same question is asked once at the person's first successful start, never at process startup.
 */
@Composable
internal fun PhoneFirstStartConsent(state: ThisPhoneUi, actions: ThisPhoneActions) {
    val consent = state.phoneConsent
    val kind = consent?.kind
    val spec =
        if (consent == null || state.confirm != null) null
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
                        if (state.consentFailure)
                            "\n\n" + stringResource(Res.string.consentSaveFailed)
                        else "",
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
    KitConfirm(
        spec,
        onConfirm = { if (!state.consentSaving) actions.answerConsent(true) },
        onCancel = { if (!state.consentSaving) actions.answerConsent(false) },
    )
}
