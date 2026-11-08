package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequence
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequenceMark
import io.github.eslamasabry.opencode_mobile.R

/**
 * B-180: the one-time "Help improve OpenCode Mobile?" question, the twin of Flutter 2.1.5 `askTelemetryConsentOnce`
 * (lib/ui/widgets/telemetry_consent.dart): the same title, body, the two consequence lines and the two answers, as one
 * kit confirm sheet. "Share anonymous usage" turns on the Settings › Privacy and data switch; "Not now", back or the
 * scrim leave it off. Either way it never returns (`oc.telemetryConsentAsked` was set before it showed).
 */
@Composable
internal fun TelemetryConsentHost(continuity: TelemetryContinuity, content: @Composable () -> Unit) {
    content()
    val asking by continuity.question.collectAsStateWithLifecycle()
    KitConfirm(
        spec =
            if (!asking) null
            else
                KitConfirmSpec(
                    title = stringResource(R.string.andr_telemetryConsentTitle),
                    confirmLabel = stringResource(R.string.andr_telemetryConsentShare),
                    body = stringResource(R.string.andr_telemetryConsentBody),
                    cancelLabel = stringResource(R.string.andr_telemetryConsentNotNow),
                    consequenceItems =
                        listOf(
                            KitConsequence(stringResource(R.string.andr_telemetryConsentSends)),
                            KitConsequence(
                                stringResource(R.string.andr_telemetryConsentNever),
                                mark = KitConsequenceMark.Kept,
                            ),
                        ),
                    icon = KitIcons.shield,
                ),
        onConfirm = { continuity.answerQuestion(share = true) },
        onCancel = { continuity.answerQuestion(share = false) },
    )
}
