package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.Column
import io.github.eslamasabry.oc.ui.kit.frame.kitPageGutter
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import androidx.compose.ui.platform.testTag

/**
 * Flutter KitStateView(size: inline), kit_state_view.dart:708-791,824-835. A list's empty or failed
 * read starts at its text rail with a small tile and reading words. The host supplies plain failure
 * copy and a retry action; the part never draws exception text.
 */
@Composable
public fun KitInlineStateView(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    body: String? = null,
    primary: KitAction? = null,
    secondary: KitAction? = null,
) {
    val tokens = LocalKitTokens.current
    Column(
        modifier
            .kitMaxWidth(CapRole.State)
            .fillMaxWidth()
            .padding(
                start = kitPageGutter(),
                top = tokens.gutter.dp,
                end = kitPageGutter(),
                bottom = Space.S2.dp,
            ),
        horizontalAlignment = Alignment.Start,
    ) {
        // The bare glyph on the title's rail: no tile behind it (audit 2026-11-09 C01).
        KitIcon(icon, Modifier.testTag("kit-state-icon"), size = KitIconSize.Small, tone = TextTone.Secondary)
        Spacer(Modifier.height(Space.S3.dp))
        KitText(title, Modifier.semantics { heading() }, role = TypeRole.Headline)
        body?.let {
            Spacer(Modifier.height(Space.S1.dp))
            KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
        if (primary != null || secondary != null) {
            Spacer(Modifier.height(Space.S3.dp))
            KitActionBlock(primary = primary, secondary = secondary)
        }
    }
}
