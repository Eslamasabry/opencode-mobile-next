package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.motion.KitChevron
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * A turn's work as one quiet line (Dart `KitStepTimeline`, lib/ui/kit/chat/kit_step_timeline.dart): [icon], [label]
 * in the secondary tone and a chevron, no frame. Collapsed it is only this line; [steps] hang off the rail when
 * [expanded] (drawn by the host's rows, see KitToolRow).
 *
 * States: collapsed (this file); expanded rail (order 15, later contracts)
 */
@Composable
public fun KitStepTimeline(
    label: String,
    icon: ImageVector,
    expanded: Boolean,
    onPressed: () -> Unit,
    modifier: Modifier = Modifier,
    spoken: String = label,
    steps: @Composable () -> Unit = {},
) {
    val tokens = LocalKitTokens.current
    val glyph = tokens.smallIcon.dp // F: lib/ui/kit/chat/kit_step_timeline.dart:723
    val first = 20.dp // F: lib/ui/kit/chat/kit_step_timeline.dart:958 (rail.line(20) at 1.0 text scale)
    val pad = maxOf(Space.S1.dp, (tokens.minTarget.dp - first) / 2) // F: lib/ui/kit/chat/kit_step_timeline.dart:962 glyph centred in the 48 dp target
    Column(modifier.fillMaxWidth().widthIn(max = 700.dp)) { // F: lib/ui/kit/kit_layout.dart:48 paneDetailMaxWidth
        KitTappable(
            onClick = onPressed,
            role = KitTappableRole.Button,
            shape = KitShapeId.Tile, // F: lib/ui/kit/chat/kit_step_timeline.dart:1052
            modifier = Modifier.semantics { contentDescription = spoken },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = pad), // F: lib/ui/kit/chat/kit_step_timeline.dart:987
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.width(glyph).height(first), contentAlignment = Alignment.Center) {
                    KitIcon(icon, size = KitIconSize.Small, tone = TextTone.Secondary) // F: lib/ui/kit/chat/kit_step_timeline.dart:982 text2
                }
                Spacer(Modifier.width(Space.S3.dp)) // F: lib/ui/kit/chat/kit_step_timeline.dart:994 column - glyph
                Box(Modifier.weight(1f)) {
                    KitText(label, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 2) // F: lib/ui/kit/chat/kit_step_timeline.dart:1015-1019
                }
                Spacer(Modifier.width(Space.S1.dp)) // F: lib/ui/kit/chat/kit_step_timeline.dart:1022
                Box(Modifier.height(first), contentAlignment = Alignment.Center) { KitChevron(expanded) } // F: lib/ui/kit/chat/kit_step_timeline.dart:1023-1032
            }
        }
        if (expanded) steps()
    }
}
