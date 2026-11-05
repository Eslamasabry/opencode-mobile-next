package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.state.KitStepState
import io.github.eslamasabry.oc.ui.kit.state.StepMark
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * Flutter's marked KitRow with its secondary action below the text rail
 * (TailscalePhoneSteps._step).
 */
@Composable
public fun KitStatusRow(
    title: String,
    state: KitStepState,
    supporting: String,
    modifier: Modifier = Modifier,
    action: KitAction? = null,
) {
    val tokens = LocalKitTokens.current
    Row(
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .heightIn(min = tokens.rowHeightTwo.dp)
            .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        Box(
            Modifier.size(FlutterKitTokens.MARK_SLOT_SIZE.dp).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            StepMark(state)
        }
        Column(Modifier.weight(1f)) {
            Column(Modifier.clearAndSetSemantics { contentDescription = "$title, $supporting" }) {
                KitText(
                    title,
                    role = TypeRole.RowTitle,
                    maxLines = 2,
                ) // F: lib/ui/screens/tailscale_setup_screen.dart:214
                Spacer(Modifier.height(Space.S1.dp))
                KitText(
                    supporting,
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    maxLines = 4,
                ) // F: lib/ui/screens/tailscale_setup_screen.dart:220
            }
            if (action != null) {
                Spacer(Modifier.height(Space.S2.dp))
                KitButton(action, role = KitButtonRole.Secondary)
            }
        }
    }
}
