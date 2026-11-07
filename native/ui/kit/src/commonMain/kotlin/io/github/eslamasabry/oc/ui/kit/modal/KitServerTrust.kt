package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitLtr
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitSelectable
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.addServerCleartextWarning
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateAccept
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateBody
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateChangedBody
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateChangedTitle
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateReplace
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateTitle
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateValidity
import io.github.eslamasabry.oc.ui.words.res.serverTrustFingerprint
import io.github.eslamasabry.oc.ui.words.res.serverTrustHttpAccept
import io.github.eslamasabry.oc.ui.words.res.serverTrustHttpPublicBody
import io.github.eslamasabry.oc.ui.words.res.serverTrustHttpTitle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Which words the trust warning uses, chosen as lib/ui/kit/kit_server_trust.dart chooses them. */
public object KitServerTrustRules {
    /** The sheet's title: a changed certificate, a certificate to review, or plain HTTP. */
    public fun title(certificate: Boolean, changed: Boolean): StringResource =
        when {
            certificate && changed -> Res.string.serverTrustCertificateChangedTitle
            certificate -> Res.string.serverTrustCertificateTitle
            else -> Res.string.serverTrustHttpTitle
        }

    /** The accept button's words. */
    public fun accept(certificate: Boolean, changed: Boolean): StringResource =
        when {
            certificate && changed -> Res.string.serverTrustCertificateReplace
            certificate -> Res.string.serverTrustCertificateAccept
            else -> Res.string.serverTrustHttpAccept
        }

    /** The main paragraph: the certificate's, or for HTTP the private-network or public-network warning. */
    public fun body(certificate: Boolean, privateNetwork: Boolean): StringResource =
        when {
            certificate -> Res.string.serverTrustCertificateBody
            privateNetwork -> Res.string.addServerCleartextWarning
            else -> Res.string.serverTrustHttpPublicBody
        }

    /**
     * The SHA-256 fingerprint in capitals, a colon after every pair but the last (Dart `RegExp(r'.{2}(?!$)')`,
     * kit_server_trust.dart:122-125).
     */
    public fun fingerprint(sha256: String): String = sha256.uppercase().chunked(2).joinToString(":") // F: lib/ui/kit/kit_server_trust.dart:123
}

/**
 * The visible warning and fingerprint are the decision itself (Dart `KitServerTrustBody`), so the fingerprint stays
 * expanded, wraps, and remains left to right in Arabic. [validFrom] and [validTo] are the dates already written as full
 * dates in the person's locale (Flutter `formatFullDate`); the line shows only when both are given.
 *
 * States: none — it renders already inspected data
 * Text: reading
 * Cap: Sheet (the sheet that holds it)
 * Motion: none
 * Replaces: KitServerTrustBody (lib/ui/kit/kit_server_trust.dart)
 */
@Composable
public fun KitServerTrustBody(
    origin: String,
    modifier: Modifier = Modifier,
    fingerprintSha256: String? = null,
    privateNetwork: Boolean = false,
    certificateChanged: Boolean = false,
    validFrom: String? = null,
    validTo: String? = null,
) {
    val fingerprint = fingerprintSha256
    Column(modifier.fillMaxWidth().padding(LocalKitTokens.current.gutter.dp)) {
        KitLtr { KitText(origin, role = TypeRole.Headline, tone = TextTone.Primary) }
        Spacer(Modifier.height(Space.S3.dp))
        if (fingerprint != null && certificateChanged) {
            KitText(stringResource(Res.string.serverTrustCertificateChangedBody))
            Spacer(Modifier.height(Space.S3.dp))
        }
        KitText(stringResource(KitServerTrustRules.body(fingerprint != null, privateNetwork)))
        if (fingerprint != null) {
            Spacer(Modifier.height(Space.S3.dp))
            if (validFrom != null && validTo != null) {
                KitText(stringResource(Res.string.serverTrustCertificateValidity, validFrom, validTo))
                Spacer(Modifier.height(Space.S3.dp))
            }
            KitText(stringResource(Res.string.serverTrustFingerprint), role = TypeRole.Label)
            Spacer(Modifier.height(Space.S2.dp))
            KitSelectable {
                KitMonoText(KitServerTrustRules.fingerprint(fingerprint), Modifier.testTag("server-trust-fingerprint"))
            }
        }
    }
}

/**
 * A deliberate, server-scoped transport choice (Dart `showKitServerTrust`), as screen state: shown while [visible].
 * Accept calls [onAccept]; closing (Close, the scrim, Back) calls [onDismiss] and never grants trust.
 *
 * States: closed, open (HTTP, certificate, changed certificate)
 * Text: frame (header), reading (body)
 * Cap: Sheet (640)
 * Motion: the sheet's own (KitSheet)
 * Replaces: showKitServerTrust (lib/ui/kit/kit_server_trust.dart)
 */
@Composable
public fun KitServerTrust(
    visible: Boolean,
    origin: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    fingerprintSha256: String? = null,
    privateNetwork: Boolean = false,
    certificateChanged: Boolean = false,
    validFrom: String? = null,
    validTo: String? = null,
) {
    val certificate = fingerprintSha256 != null
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(KitServerTrustRules.title(certificate, certificateChanged)),
        icon = KitIcons.warning,
        primary = KitAction(stringResource(KitServerTrustRules.accept(certificate, certificateChanged)), onAccept),
    ) {
        KitServerTrustBody(origin, Modifier.testTag("server-trust-sheet"), fingerprintSha256, privateNetwork, certificateChanged, validFrom, validTo)
    }
}
