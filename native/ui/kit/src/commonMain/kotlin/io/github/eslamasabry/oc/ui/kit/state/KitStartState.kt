package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.background
import io.github.eslamasabry.oc.ui.kit.frame.kitPageGutter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.motion.KitEntrance
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** One obvious next step on a start page: a glyph, the step in plain words, one short line, where it goes. */
@Immutable
public data class KitStartWay(
    val title: String,
    val onClick: () -> Unit,
    val icon: ImageVector,
    val supporting: String? = null,
)

/**
 * A list's first-run page (owner rules "obvious options first" and "simplify"; design review §2.3 empty state): one
 * calm mark, what the list will hold in one line, one sentence, then the obvious next steps as plain rows with their
 * glyph tiles — never a full-width slab of a button. The screen's one primary action is not here: it stays where it
 * always is (the floating New conversation), so the first page and the hundredth look the same. Leading-aligned on the
 * gutter at reading width, so it reads like the list it stands in for. It arrives with the kit entrance (fade + 8 dp
 * rise over 200 ms; reduced motion shows it at once).
 *
 * States: with ways, without ways
 * Text: reading
 * Cap: Reading
 * Motion: KitEntrance (STANDARD fade + rise); Off = instant
 * Replaces: KitStateView.calm on the Conversations home (lib/ui/screens/chats/chats_home_screen.dart `chats-empty`)
 */
@Composable
public fun KitStartState(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    body: String? = null,
    waysLabel: String? = null,
    ways: List<KitStartWay> = emptyList(),
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    KitEntrance(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().testTag("kit-start-state")) {
            Column(
                Modifier.kitMaxWidth(CapRole.Reading, CapAlign.Start)
                    .fillMaxWidth()
                    .padding(start = kitPageGutter(), end = kitPageGutter(), top = Space.S2.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                // The calm mark: the list's own glyph on a quiet accent wash, no tile box, no glow.
                Box(
                    Modifier.size((tokens.iconTile + Space.S2).dp)
                        .background(colors.accent.copy(alpha = .14f).compositeOver(colors.surface1), CircleShape)
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) { KitGlyph(icon, colors.accent, tokens.smallIcon.dp + Space.S1.dp) }
                Spacer(Modifier.height(Space.S1.dp))
                KitText(title, Modifier.semantics { heading() }, role = TypeRole.Title)
                body?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
            }
            if (ways.isNotEmpty()) {
                Spacer(Modifier.height(Space.S5.dp))
                KitRowGroup(label = waysLabel, leadingTiles = true) {
                    ways.forEach { way ->
                        KitRow(
                            title = way.title,
                            supporting = way.supporting,
                            leading = KitRowLeading.Glyph(way.icon, tile = true),
                            trailing = KitRowTrailing.Chevron,
                            onClick = way.onClick,
                        )
                    }
                }
            }
        }
    }
}
