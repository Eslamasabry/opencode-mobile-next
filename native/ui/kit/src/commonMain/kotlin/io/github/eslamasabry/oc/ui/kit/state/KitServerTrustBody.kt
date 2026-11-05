package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Flutter kit_server_trust.dart:78-140: the origin and inspected fingerprint are the decision. */
@Composable
public fun KitServerTrustBody(
    origin: String,
    message: String,
    modifier: Modifier = Modifier,
    fingerprint: String? = null,
    fingerprintLabel: String? = null,
    validity: String? = null,
    changedMessage: String? = null,
    inset: Boolean = true,
) {
    val tokens = LocalKitTokens.current
    Column(
        modifier.fillMaxWidth().then(if (inset) Modifier.padding(tokens.gutter.dp) else Modifier)
    ) {
        KitLtr { KitText(origin, role = TypeRole.Headline) }
        Spacer(Modifier.height(Space.S3.dp))
        changedMessage?.let {
            KitText(it)
            Spacer(Modifier.height(Space.S3.dp))
        }
        KitText(message)
        if (fingerprint != null) {
            Spacer(Modifier.height(Space.S3.dp))
            validity?.let {
                KitText(it)
                Spacer(Modifier.height(Space.S3.dp))
            }
            fingerprintLabel?.let { KitText(it, role = TypeRole.Label) }
            Spacer(Modifier.height(Space.S2.dp))
            KitSelectable {
                KitMonoText(
                    fingerprint.replace(":", "").uppercase().chunked(2).joinToString(":")
                ) // F: lib/ui/kit/kit_server_trust.dart:133
            }
        }
    }
}
