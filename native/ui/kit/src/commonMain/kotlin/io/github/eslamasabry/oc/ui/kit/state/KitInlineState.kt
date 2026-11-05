package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceTile
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Flutter KitStateView(size: inline): start-aligned, small tile and headline, inside a list. */
@Composable
public fun KitInlineState(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    body: String? = null,
    primary: KitAction? = null,
) {
    val tokens = LocalKitTokens.current
    Column(
        modifier
            .kitMaxWidth(CapRole.State, CapAlign.Start)
            .fillMaxWidth()
            .padding(
                start = tokens.gutter.dp,
                top = tokens.gutter.dp,
                end = tokens.gutter.dp,
                bottom = Space.S2.dp,
            ),
        horizontalAlignment = Alignment.Start,
    ) {
        KitSurfaceTile(icon, tone = TextTone.Secondary)
        Spacer(Modifier.height(Space.S3.dp))
        KitText(title, Modifier.semantics { heading() }, role = TypeRole.Headline)
        body?.let {
            Spacer(Modifier.height(Space.S1.dp))
            KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
        primary?.let {
            Spacer(Modifier.height(Space.S3.dp))
            KitActionBlock(primary = it)
        }
    }
}
