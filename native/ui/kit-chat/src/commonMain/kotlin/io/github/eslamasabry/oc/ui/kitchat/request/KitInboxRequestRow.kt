package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kitchat.tool.KitMonoLtr
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** The waiting Inbox row (`kit_request_row.dart`): place, question, command and trailing answers. */
@Composable
public fun KitInboxRequestRow(
    title: String,
    place: String,
    openLabel: String,
    openActionLabel: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    command: String? = null,
    allowLabel: String? = null,
    rejectLabel: String? = null,
    onAllow: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null,
    primary: Boolean = false,
    enabled: KitEnabled = KitEnabled.Enabled,
    ifIgnored: String? = null,
    openEnabled: KitEnabled = enabled,
) {
    val colors = LocalKitColors.current
    val scale = kitTextScale()
    val deciding = onAllow != null && onReject != null && allowLabel != null && rejectLabel != null
    Column(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth().testTag("kit-inbox-request-row")) {
        KitTappable(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = openLabel
                liveRegion = LiveRegionMode.Polite
            },
            enabled = openEnabled.isEnabled,
            disabledReason = openEnabled.reason,
            shape = KitShapeId.Button,
        ) {
            Column(Modifier.fillMaxWidth()) {
                val line = LocalKitTypography.current.style(TypeRole.Secondary).lineHeight.value * scale
                Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalAlignment = Alignment.Top) {
                    Box(Modifier.height(line.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size((FlutterKitTokens.NEEDS_YOU_DOT_SIZE * scale.coerceIn(1f, 1.5f)).dp).background(colors.attention, CircleShape)) // F: lib/ui/kit/kit_request_row.dart:163-164
                    }
                    Spacer(Modifier.width(Space.S2.dp))
                    KitText(place, Modifier.weight(1f), role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 2) // F: lib/ui/kit/kit_request_row.dart:196
                }
                Spacer(Modifier.height(Space.S1.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                    KitText(title, Modifier.weight(1f), role = TypeRole.Headline)
                    if (!deciding && scale < 1.3f) KitButton(openActionLabel, onOpen, role = KitButtonRole.Secondary, enabled = openEnabled, semanticsLabel = openLabel) // F: lib/ui/kit/kit_request_row.dart:112
                }
            }
        }
        if (!deciding && scale >= 1.3f) { // F: lib/ui/kit/kit_request_row.dart:112
            Spacer(Modifier.height(Space.S3.dp))
            KitButton(openActionLabel, onOpen, Modifier.align(Alignment.End), role = KitButtonRole.Secondary, enabled = openEnabled, semanticsLabel = openLabel)
        }
        if (!command.isNullOrBlank()) {
            Spacer(Modifier.height(Space.S3.dp))
            val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Code, KitShapeId.Code, SurfaceLevel.Ground, ownFill = colors.ground))
            Box(Modifier.fillMaxWidth().then(look.modifier).padding(horizontal = Space.S3.dp, vertical = Space.S2.dp)) {
                KitMonoLtr(command, maxLines = 2) // F: lib/ui/kit/kit_request_card.dart:667
            }
        }
        if (deciding) {
            Spacer(Modifier.height(Space.S3.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                KitButton(rejectLabel!!, onReject!!, role = KitButtonRole.Tertiary, enabled = enabled)
                KitButton(allowLabel!!, onAllow!!, role = if (primary) KitButtonRole.Primary else KitButtonRole.Secondary, enabled = enabled, maxLines = 1)
            }
        }
        if (ifIgnored != null) {
            Spacer(Modifier.height(Space.S3.dp))
            KitText(ifIgnored, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
        enabled.reason?.let {
            Spacer(Modifier.height(Space.S2.dp))
            KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
    }
}
