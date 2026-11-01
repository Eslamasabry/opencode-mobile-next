package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.servers.presentation.CivilDate
import io.github.eslamasabry.oc.feature.servers.presentation.CleartextConsentUi
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionFailureUi
import io.github.eslamasabry.oc.feature.servers.presentation.FailureAction
import io.github.eslamasabry.oc.feature.servers.presentation.PinReviewUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.addServerCleartextWarning
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateAccept
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateBody
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateChangedTitle
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateReplace
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateTitle
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateValidity
import io.github.eslamasabry.oc.ui.words.res.serverTrustFingerprint
import io.github.eslamasabry.oc.ui.words.res.serverTrustHttpAccept
import io.github.eslamasabry.oc.ui.words.res.serverTrustHttpPublicBody
import io.github.eslamasabry.oc.ui.words.res.serverTrustHttpTitle
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiTechnicalDetails
import org.jetbrains.compose.resources.stringResource

/** Which kind of `KitField` an editor field is (06 K11: Secret is never prefilled; Url/Code are LTR). */
public enum class ServersFieldKind { Url, Text, Secret, Code }

public class ServersFieldSpec(
    public val kind: ServersFieldKind,
    public val label: String,
    public val value: String,
    public val onValueChange: (String) -> Unit,
    public val error: String?,
    public val focus: Boolean,
)

/**
 * The editor's input part. `KitField` (06 §16, WP K1) is not in `:ui:kit` yet, so the host provides the part here and
 * the feature never draws one (rule 2). Until then the default shows the label and the typed value as kit text, and
 * never a secret. The S1-KITA2 merge replaces this with `KitField` in one line (report D2).
 */
public val LocalServersField: ProvidableCompositionLocal<@Composable (ServersFieldSpec) -> Unit> =
    staticCompositionLocalOf { { spec -> ReadOnlyField(spec) } }

@Composable
private fun ReadOnlyField(spec: ServersFieldSpec) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        KitText(spec.label, KitTextRole.Supporting)
        if (spec.kind != ServersFieldKind.Secret && spec.value.isNotEmpty()) KitText(spec.value, KitTextRole.Body)
        spec.error?.let { KitText(it, KitTextRole.Supporting, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}

/**
 * A failed connection in the verdict style: headline, one sentence, the checks, the one action that fixes it, and the
 * technical text only under "Technical details" (B-NAV-009, B-SEC-104).
 */
@Composable
internal fun FailureVerdict(failure: ConnectionFailureUi, onAction: (FailureAction) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        KitText(failure.kind.title(), KitTextRole.Title)
        KitText(failure.kind.body(), KitTextRole.Body)
        failure.checks.forEach { KitText(it.words(), KitTextRole.Supporting) }
        KitButton(label = failure.primary.label(), onClick = { onAction(failure.primary) })
        val details = failure.details
        if (details != null) {
            KitRow(title = stringResource(Res.string.e7VoiceUiTechnicalDetails), supporting = if (open) details.text else null, onClick = { open = !open })
        }
    }
}

/** Plain HTTP for one origin (plan 04 §4.3): the private sentence or the stronger public one (N4-D4). */
@Composable
internal fun CleartextConsentSheet(consent: CleartextConsentUi?, onAllow: () -> Unit, onDecline: () -> Unit) {
    val last = remember { mutableStateOf(consent) }
    if (consent != null) last.value = consent
    val shown = last.value
    KitSheet(
        visible = consent != null,
        onDismissRequest = onDecline,
        title = stringResource(Res.string.serverTrustHttpTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        subtitle = shown?.origin?.toString(),
    ) {
        if (shown != null) {
            KitText(
                stringResource(if (shown.public) Res.string.serverTrustHttpPublicBody else Res.string.addServerCleartextWarning),
                KitTextRole.Body,
            )
            KitButton(label = stringResource(Res.string.serverTrustHttpAccept), onClick = onAllow)
            KitRow(title = stringResource(Res.string.kitConfirmCancel), supporting = null, onClick = onDecline)
        }
    }
}

/** Compare the fingerprint, then trust it; closing never grants trust (06 `KitServerTrust`, plan 04 §4.5). */
@Composable
internal fun PinReviewSheet(review: PinReviewUi?, onTrust: () -> Unit, onCancel: () -> Unit) {
    val last = remember { mutableStateOf(review) }
    if (review != null) last.value = review
    val shown = last.value
    KitSheet(
        visible = review != null,
        onDismissRequest = onCancel,
        title = stringResource(Res.string.serverTrustCertificateTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        subtitle = shown?.origin?.toString(),
    ) {
        if (shown != null) {
            KitText(stringResource(Res.string.serverTrustCertificateBody), KitTextRole.Body)
            KitText(stringResource(Res.string.serverTrustFingerprint), KitTextRole.Supporting)
            KitText(shown.fingerprint, KitTextRole.Body)
            KitText(
                stringResource(Res.string.serverTrustCertificateValidity, CivilDate.of(shown.notBeforeMs), CivilDate.of(shown.notAfterMs)),
                KitTextRole.Supporting,
            )
            shown.previousFingerprint?.let {
                KitText(stringResource(Res.string.serverTrustCertificateChangedTitle), KitTextRole.Supporting)
                KitText(it, KitTextRole.Supporting)
            }
            KitButton(
                label =
                    stringResource(
                        if (shown.previousFingerprint != null) Res.string.serverTrustCertificateReplace else Res.string.serverTrustCertificateAccept,
                    ),
                onClick = onTrust,
            )
            KitRow(title = stringResource(Res.string.kitConfirmCancel), supporting = null, onClick = onCancel)
        }
    }
}
